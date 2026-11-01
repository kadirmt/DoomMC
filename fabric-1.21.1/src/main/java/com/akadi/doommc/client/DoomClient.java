package com.akadi.doommc.client;

import com.akadi.doommc.engine.DoomSnapshot;
import com.akadi.doommc.net.DoomPayloads;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Receives snapshots, draws them, and plays the sounds every viewer should hear. */
public final class DoomClient {

	/** Forget a screen we have not heard about for this long - it was probably mined. */
	private static final long STALE_NANOS = 3_000_000_000L;
	private static final int MAX_SOUNDS_PER_PACKET = 4;

	private static final Map<BlockPos, DoomSession> SESSIONS = new LinkedHashMap<>();

	private DoomClient() {
	}

	public static Collection<DoomSession> sessions() {
		return SESSIONS.values();
	}

	public static boolean isEmpty() {
		return SESSIONS.isEmpty();
	}

	// ------------------------------------------------------------- incoming

	public static void onFrame(DoomPayloads.Frame payload) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) return;

		DoomSnapshot snapshot;
		try {
			snapshot = DoomSnapshot.fromBytes(payload.data());
		} catch (RuntimeException e) {
			// a version mismatch between client and server jar - say so once and drop it
			if (mc.player != null) {
				mc.player.sendMessage(Text.translatable("message.doommc.version_mismatch"), true);
			}
			return;
		}

		BlockPos origin = payload.screen();
		Direction facing = Direction.byId(payload.facing());
		DoomSession session = SESSIONS.get(origin);
		if (session == null || session.isClosed()) {
			session = new DoomSession(origin, facing);
			SESSIONS.put(session.origin, session);
		}
		session.accept(snapshot, facing);
		playSounds(session, snapshot);
	}

	public static void onSession(DoomPayloads.Session payload) {
		MinecraftClient mc = MinecraftClient.getInstance();
		switch (payload.action()) {
			case DoomPayloads.SESSION_GRANTED -> mc.setScreen(new DoomControlScreen(payload.screen()));
			case DoomPayloads.SESSION_ENDED -> {
				DoomSession gone = SESSIONS.remove(payload.screen());
				if (gone != null) gone.close();
				if (mc.currentScreen instanceof DoomControlScreen control
						&& control.screenPos().equals(payload.screen())) {
					mc.setScreen(null);
				}
			}
			default -> {
			}
		}
	}

	// -------------------------------------------------------------- drawing

	/** Called once per rendered frame, before the world is drawn. */
	public static void redrawAll() {
		if (SESSIONS.isEmpty()) return;
		// the wall's text follows whatever language this player runs Minecraft in
		com.akadi.doommc.engine.Lang.set(
				MinecraftClient.getInstance().getLanguageManager().getLanguage());
		long now = System.nanoTime();
		for (DoomSession session : new ArrayList<>(SESSIONS.values())) {
			if (now - session.lastPacketAt() > STALE_NANOS) {
				SESSIONS.remove(session.origin);
				session.close();
				continue;
			}
			session.redraw();
		}
	}

	public static void closeAll() {
		for (DoomSession session : SESSIONS.values()) session.close();
		SESSIONS.clear();
	}

	// --------------------------------------------------------------- sounds

	private static void playSounds(DoomSession session, DoomSnapshot snapshot) {
		if (snapshot.sounds.length == 0) return;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.world == null) return;
		Vec3d at = session.center();
		int played = 0;
		for (String event : snapshot.sounds) {
			if (played++ >= MAX_SOUNDS_PER_PACKET) break;
			Cue cue = cueFor(event);
			if (cue == null) continue;
			mc.world.playSound(null, at.x, at.y, at.z, cue.sound, SoundCategory.RECORDS,
					cue.volume, cue.pitch);
		}
	}

	private record Cue(SoundEvent sound, float volume, float pitch) {
	}

	private static Cue cueFor(String event) {
		return switch (event) {
			case "pistol" -> new Cue(SoundEvents.BLOCK_DISPENSER_LAUNCH, 0.9f, 1.7f);
			case "shotgun" -> new Cue(SoundEvents.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.8f, 1.5f);
			case "chaingun" -> new Cue(SoundEvents.BLOCK_DISPENSER_LAUNCH, 0.7f, 2.0f);
			case "dryfire" -> new Cue(SoundEvents.BLOCK_LEVER_CLICK, 0.6f, 0.6f);
			case "switch" -> new Cue(SoundEvents.BLOCK_LEVER_CLICK, 0.6f, 1.4f);
			case "door" -> new Cue(SoundEvents.BLOCK_IRON_DOOR_OPEN, 0.9f, 0.9f);
			case "pickup" -> new Cue(SoundEvents.ENTITY_ITEM_PICKUP, 0.9f, 1.3f);
			case "hurt" -> new Cue(SoundEvents.ENTITY_PLAYER_HURT, 0.9f, 1.0f);
			case "player_death" -> new Cue(SoundEvents.ENTITY_PLAYER_HURT, 1.0f, 0.5f);
			case "mob_pain" -> new Cue(SoundEvents.ENTITY_ZOMBIE_HURT, 0.8f, 1.1f);
			case "mob_death" -> new Cue(SoundEvents.ENTITY_ZOMBIE_DEATH, 0.9f, 0.9f);
			case "mob_melee" -> new Cue(SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, 0.9f, 0.8f);
			case "mob_shoot" -> new Cue(SoundEvents.BLOCK_DISPENSER_LAUNCH, 0.7f, 1.2f);
			case "fireball" -> new Cue(SoundEvents.ENTITY_BLAZE_SHOOT, 0.8f, 1.2f);
			case "exit" -> new Cue(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
			default -> null;
		};
	}
}
