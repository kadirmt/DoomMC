package com.akadi.doommc.server;

import com.akadi.doommc.DoomMC;
import com.akadi.doommc.block.ScreenBlock;
import com.akadi.doommc.block.ScreenLayout;
import com.akadi.doommc.engine.DoomGame;
import com.akadi.doommc.net.DoomNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The authority. Every screen's game runs here, on the server, and the result is
 * broadcast to everyone nearby. Single player works the same way, because single
 * player runs an integrated server - there is no second code path.
 */
@Mod.EventBusSubscriber(modid = DoomMC.MOD_ID)
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

	private record Key(ResourceKey<Level> dimension, BlockPos pos) {
	}

	private static final class Session {
		final ServerLevel world;
		final BlockPos origin;
		final Direction facing;
		final DoomGame game = new DoomGame();
		UUID controller;
		int idleTicks;
		int sinceSend;

		Session(ServerLevel world, BlockPos origin, Direction facing) {
			this.world = world;
			this.origin = origin;
			this.facing = facing;
			this.game.setPaused(true);
		}

		Vec3 center() {
			ScreenLayout layout = ScreenLayout.of(origin, facing);
			BlockPos far = layout.posAt(layout.width() - 1, layout.height() - 1);
			return new Vec3(
					(origin.getX() + far.getX()) / 2.0 + 0.5,
					(origin.getY() + far.getY()) / 2.0 + 0.5,
					(origin.getZ() + far.getZ()) / 2.0 + 0.5);
		}

		List<ServerPlayer> viewers() {
			Vec3 c = center();
			List<ServerPlayer> out = new ArrayList<>();
			for (ServerPlayer p : world.players()) {
				if (p.distanceToSqr(c) <= VIEW_RANGE * VIEW_RANGE) out.add(p);
			}
			return out;
		}
	}

	// ------------------------------------------------------------- requests

	/** A player right clicked with a paired remote: hand them the controls. */
	public static void requestControl(ServerPlayer player, BlockPos origin) {
		ServerLevel world = player.serverLevel();
		BlockState state = world.getBlockState(origin);
		if (!DoomMC.isScreen(state) || !state.getValue(ScreenBlock.ORIGIN)) {
			player.displayClientMessage(Component.translatable("message.doommc.screen_missing"), true);
			return;
		}

		Key key = new Key(world.dimension(), origin.immutable());
		Session session = SESSIONS.get(key);
		if (session == null) {
			session = new Session(world, origin.immutable(), state.getValue(ScreenBlock.FACING));
			SESSIONS.put(key, session);
		}

		if (session.controller != null && !session.controller.equals(player.getUUID())
				&& world.getServer().getPlayerList().getPlayer(session.controller) != null) {
			DoomNet.sendSession(player, origin, DoomNet.SESSION_BUSY);
			player.displayClientMessage(Component.translatable("message.doommc.busy"), true);
			return;
		}

		session.controller = player.getUUID();
		session.idleTicks = 0;
		session.game.setPaused(false);
		DoomNet.sendSession(player, origin, DoomNet.SESSION_GRANTED);
	}

	/** The controller closed their control screen. Freeze the game where it stands. */
	public static void releaseControl(ServerPlayer player, BlockPos origin) {
		Session session = find(player, origin);
		if (session == null) return;
		if (!player.getUUID().equals(session.controller)) return;
		session.controller = null;
		session.game.setPaused(true);
		session.game.setKeys(0);
		broadcast(session);
	}

	/** Sneak plus right click: tear the whole session down and blank the wall. */
	public static void stop(ServerPlayer player, BlockPos origin) {
		Key key = new Key(player.serverLevel().dimension(), origin.immutable());
		Session session = SESSIONS.remove(key);
		if (session == null) return;
		for (ServerPlayer viewer : session.viewers()) {
			DoomNet.sendSession(viewer, origin, DoomNet.SESSION_ENDED);
		}
		player.displayClientMessage(Component.translatable("message.doommc.stopped"), true);
	}

	/** Key state and mouse movement from whoever is driving. */
	public static void input(ServerPlayer player, BlockPos origin, int keys, float turn) {
		Session session = find(player, origin);
		if (session == null) return;
		if (!player.getUUID().equals(session.controller)) return;
		if (player.distanceToSqr(session.center()) > VIEW_RANGE * VIEW_RANGE) return;
		session.game.setKeys(keys);
		session.game.addTurn(turn);
		session.idleTicks = 0;
	}

	private static Session find(ServerPlayer player, BlockPos origin) {
		return SESSIONS.get(new Key(player.serverLevel().dimension(), origin.immutable()));
	}

	// ----------------------------------------------------------------- tick

	@SubscribeEvent
	public static void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != TickEvent.Phase.END) return;
		tick();
	}

	@SubscribeEvent
	public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) return;
		for (Session session : SESSIONS.values()) {
			if (player.getUUID().equals(session.controller)) {
				session.controller = null;
				session.game.setPaused(true);
				session.game.setKeys(0);
			}
		}
	}

	@SubscribeEvent
	public static void onServerStopping(ServerStoppingEvent event) {
		SESSIONS.clear();
	}

	private static void tick() {
		if (SESSIONS.isEmpty()) return;

		Iterator<Map.Entry<Key, Session>> it = SESSIONS.entrySet().iterator();
		while (it.hasNext()) {
			Session session = it.next().getValue();

			// the wall may have been mined out from under the game
			BlockState state = session.world.getBlockState(session.origin);
			if (!DoomMC.isScreen(state) || !state.getValue(ScreenBlock.ORIGIN)) {
				for (ServerPlayer viewer : session.viewers()) {
					DoomNet.sendSession(viewer, session.origin, DoomNet.SESSION_ENDED);
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
		for (ServerPlayer viewer : session.viewers()) {
			DoomNet.sendFrame(viewer, session.origin, session.facing.get3DDataValue(), data);
		}
	}
}
