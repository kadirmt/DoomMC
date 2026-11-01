package com.akadi.doommc.server;

import com.akadi.doommc.DoomMC;
import com.akadi.doommc.block.ScreenBlock;
import com.akadi.doommc.block.ScreenLayout;
import com.akadi.doommc.engine.DoomGame;
import com.akadi.doommc.net.DoomPayloads;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The authority. Every screen's game runs here, on the server, and the result is
 * broadcast to everyone nearby. Single player works the same way, because single
 * player runs an integrated server - there is no second code path.
 */
public final class ServerDoom {

	/** How far away you can still see a screen playing. */
	private static final double VIEW_RANGE = 96.0;
	/** A paused game changes nothing, so it only needs the odd keepalive frame. */
	private static final int PAUSED_RESEND_TICKS = 10;
	/** Drop a session nobody has driven for half an hour. Until then your progress waits. */
	private static final int IDLE_TIMEOUT_TICKS = 20 * 60 * 30;

	private static final Map<Key, Session> SESSIONS = new HashMap<>();

	private ServerDoom() {
	}

	private record Key(net.minecraft.registry.RegistryKey<World> dimension, BlockPos pos) {
	}

	private static final class Session {
		final ServerWorld world;
		final BlockPos origin;
		final Direction facing;
		final DoomGame game = new DoomGame();
		UUID controller;
		int idleTicks;
		int sinceSend;

		Session(ServerWorld world, BlockPos origin, Direction facing) {
			this.world = world;
			this.origin = origin;
			this.facing = facing;
			this.game.setPaused(true);
		}

		Vec3d center() {
			ScreenLayout layout = ScreenLayout.of(origin, facing);
			BlockPos far = layout.posAt(layout.width() - 1, layout.height() - 1);
			return new Vec3d(
					(origin.getX() + far.getX()) / 2.0 + 0.5,
					(origin.getY() + far.getY()) / 2.0 + 0.5,
					(origin.getZ() + far.getZ()) / 2.0 + 0.5);
		}
	}

	// ------------------------------------------------------------- requests

	/** A player right clicked with a paired remote: hand them the controls. */
	public static void requestControl(ServerPlayerEntity player, BlockPos origin) {
		ServerWorld world = player.getServerWorld();
		BlockState state = world.getBlockState(origin);
		if (!DoomMC.isScreen(state) || !state.get(ScreenBlock.ORIGIN)) {
			player.sendMessage(Text.translatable("message.doommc.screen_missing"), true);
			return;
		}

		Key key = new Key(world.getRegistryKey(), origin.toImmutable());
		Session session = SESSIONS.get(key);
		if (session == null) {
			session = new Session(world, origin.toImmutable(), state.get(ScreenBlock.FACING));
			SESSIONS.put(key, session);
		}

		if (session.controller != null && !session.controller.equals(player.getUuid())
				&& world.getServer().getPlayerManager().getPlayer(session.controller) != null) {
			ServerPlayNetworking.send(player, new DoomPayloads.Session(origin, DoomPayloads.SESSION_BUSY));
			player.sendMessage(Text.translatable("message.doommc.busy"), true);
			return;
		}

		session.controller = player.getUuid();
		session.idleTicks = 0;
		session.game.setPaused(false);
		ServerPlayNetworking.send(player, new DoomPayloads.Session(origin, DoomPayloads.SESSION_GRANTED));
	}

	/** The controller closed their control screen. Freeze the game where it stands. */
	public static void releaseControl(ServerPlayerEntity player, BlockPos origin) {
		Session session = find(player, origin);
		if (session == null) return;
		if (!player.getUuid().equals(session.controller)) return;
		session.controller = null;
		session.game.setPaused(true);
		session.game.setKeys(0);
		broadcast(session);
	}

	/** Sneak plus right click: tear the whole session down and blank the wall. */
	public static void stop(ServerPlayerEntity player, BlockPos origin) {
		Key key = new Key(player.getServerWorld().getRegistryKey(), origin.toImmutable());
		Session session = SESSIONS.remove(key);
		if (session == null) return;
		for (ServerPlayerEntity viewer : PlayerLookup.around(session.world, session.center(), VIEW_RANGE)) {
			ServerPlayNetworking.send(viewer, new DoomPayloads.Session(origin, DoomPayloads.SESSION_ENDED));
		}
		player.sendMessage(Text.translatable("message.doommc.stopped"), true);
	}

	/** Key state and mouse movement from whoever is driving. */
	public static void input(ServerPlayerEntity player, BlockPos origin, int keys, float turn) {
		Session session = find(player, origin);
		if (session == null) return;
		if (!player.getUuid().equals(session.controller)) return;
		// nobody should be driving a screen they have wandered away from
		if (player.squaredDistanceTo(session.center()) > VIEW_RANGE * VIEW_RANGE) return;
		session.game.setKeys(keys);
		session.game.addTurn(turn);
		session.idleTicks = 0;
	}

	public static void onDisconnect(ServerPlayerEntity player) {
		for (Session session : SESSIONS.values()) {
			if (player.getUuid().equals(session.controller)) {
				session.controller = null;
				session.game.setPaused(true);
				session.game.setKeys(0);
			}
		}
	}

	public static void onServerStopping() {
		SESSIONS.clear();
	}

	private static Session find(ServerPlayerEntity player, BlockPos origin) {
		return SESSIONS.get(new Key(player.getServerWorld().getRegistryKey(), origin.toImmutable()));
	}

	// ----------------------------------------------------------------- tick

	public static void tick(MinecraftServer server) {
		if (SESSIONS.isEmpty()) return;

		Iterator<Map.Entry<Key, Session>> it = SESSIONS.entrySet().iterator();
		while (it.hasNext()) {
			Session session = it.next().getValue();

			// the wall may have been mined out from under the game
			BlockState state = session.world.getBlockState(session.origin);
			if (!DoomMC.isScreen(state) || !state.get(ScreenBlock.ORIGIN)) {
				for (ServerPlayerEntity viewer :
						PlayerLookup.around(session.world, session.center(), VIEW_RANGE)) {
					ServerPlayNetworking.send(viewer,
							new DoomPayloads.Session(session.origin, DoomPayloads.SESSION_ENDED));
				}
				it.remove();
				continue;
			}

			session.game.serverTick();

			boolean paused = session.game.isPaused();
			session.sinceSend++;
			if (!paused || session.sinceSend >= PAUSED_RESEND_TICKS) {
				session.sinceSend = 0;
				broadcast(session);
			}

			if (session.controller == null) {
				if (++session.idleTicks > IDLE_TIMEOUT_TICKS) it.remove();
			} else {
				session.idleTicks = 0;
			}
		}
	}

	private static void broadcast(Session session) {
		byte[] data = session.game.captureSnapshot().toBytes();
		DoomPayloads.Frame frame = new DoomPayloads.Frame(
				session.origin, session.facing.getId(), data);
		for (ServerPlayerEntity viewer : PlayerLookup.around(session.world, session.center(), VIEW_RANGE)) {
			ServerPlayNetworking.send(viewer, frame);
		}
	}
}
