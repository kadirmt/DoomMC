package com.akadi.doommc.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Everything a client needs to draw one frame, and nothing else. The server owns the
 * simulation and ships one of these per tick; clients never run game logic.
 *
 * <p>Around 600 bytes for a busy level, so a screen costs roughly 12 KB/s per viewer.
 * Streaming the actual 320x200 picture would have been 1.3 MB/s.
 */
public final class DoomSnapshot {
	/** Bumped whenever the wire format changes, so a stale client fails loudly. */
	public static final int VERSION = 1;

	public int state;
	public boolean paused;
	public int levelIndex;
	public int tickCount;

	public float px, py, dirX, dirY;
	public int pitch;
	public float bob;

	public int health, armor;
	public int bullets, shells;
	public int weaponMask;
	public int weapon;
	public int muzzle, fireCooldown;
	public int painFlash, pickupFlash, armorFlash;
	public int kills, totalMonsters;

	public String message = "";
	public int messageTimer;

	public int[] doorIndex = new int[0];
	public float[] doorOpen = new float[0];

	public byte[] mobType = new byte[0];
	public float[] mobX = new float[0];
	public float[] mobY = new float[0];
	public byte[] mobFrame = new byte[0];

	public byte[] itemType = new byte[0];
	public float[] itemX = new float[0];
	public float[] itemY = new float[0];

	public int[] shotId = new int[0];
	public float[] shotX = new float[0];
	public float[] shotY = new float[0];
	public float[] shotScale = new float[0];

	public float[] puffX = new float[0];
	public float[] puffY = new float[0];
	public byte[] puffLife = new byte[0];
	public boolean[] puffBlood = new boolean[0];

	/** Sound events raised this tick, replayed by every client that can see the screen. */
	public String[] sounds = new String[0];

	// ------------------------------------------------------------------- wire

	public byte[] toBytes() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(768);
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeByte(VERSION);
			out.writeByte(state);
			out.writeBoolean(paused);
			out.writeByte(levelIndex);
			out.writeInt(tickCount);

			out.writeFloat(px);
			out.writeFloat(py);
			out.writeFloat(dirX);
			out.writeFloat(dirY);
			out.writeByte(pitch);
			out.writeFloat(bob);

			out.writeShort(health);
			out.writeShort(armor);
			out.writeShort(bullets);
			out.writeShort(shells);
			out.writeByte(weaponMask);
			out.writeByte(weapon);
			out.writeByte(muzzle);
			out.writeByte(fireCooldown);
			out.writeByte(painFlash);
			out.writeByte(pickupFlash);
			out.writeByte(armorFlash);
			out.writeShort(kills);
			out.writeShort(totalMonsters);

			out.writeUTF(message == null ? "" : message);
			out.writeShort(messageTimer);

			out.writeByte(doorIndex.length);
			for (int i = 0; i < doorIndex.length; i++) {
				out.writeShort(doorIndex[i]);
				out.writeByte((int) (doorOpen[i] * 255));
			}

			out.writeByte(mobType.length);
			for (int i = 0; i < mobType.length; i++) {
				out.writeByte(mobType[i]);
				out.writeFloat(mobX[i]);
				out.writeFloat(mobY[i]);
				out.writeByte(mobFrame[i]);
			}

			out.writeByte(itemType.length);
			for (int i = 0; i < itemType.length; i++) {
				out.writeByte(itemType[i]);
				out.writeFloat(itemX[i]);
				out.writeFloat(itemY[i]);
			}

			out.writeByte(shotId.length);
			for (int i = 0; i < shotId.length; i++) {
				out.writeInt(shotId[i]);
				out.writeFloat(shotX[i]);
				out.writeFloat(shotY[i]);
				out.writeFloat(shotScale[i]);
			}

			out.writeByte(puffX.length);
			for (int i = 0; i < puffX.length; i++) {
				out.writeFloat(puffX[i]);
				out.writeFloat(puffY[i]);
				out.writeByte(puffLife[i]);
				out.writeBoolean(puffBlood[i]);
			}

			out.writeByte(sounds.length);
			for (String s : sounds) out.writeUTF(s);
		} catch (IOException e) {
			throw new IllegalStateException("snapshot encode failed", e);
		}
		return bytes.toByteArray();
	}

	public static DoomSnapshot fromBytes(byte[] data) {
		DoomSnapshot s = new DoomSnapshot();
		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
			int version = in.readUnsignedByte();
			if (version != VERSION) {
				throw new IllegalStateException("snapshot version " + version + ", expected " + VERSION);
			}
			s.state = in.readUnsignedByte();
			s.paused = in.readBoolean();
			s.levelIndex = in.readUnsignedByte();
			s.tickCount = in.readInt();

			s.px = in.readFloat();
			s.py = in.readFloat();
			s.dirX = in.readFloat();
			s.dirY = in.readFloat();
			s.pitch = in.readByte();
			s.bob = in.readFloat();

			s.health = in.readShort();
			s.armor = in.readShort();
			s.bullets = in.readShort();
			s.shells = in.readShort();
			s.weaponMask = in.readUnsignedByte();
			s.weapon = in.readUnsignedByte();
			s.muzzle = in.readUnsignedByte();
			s.fireCooldown = in.readUnsignedByte();
			s.painFlash = in.readUnsignedByte();
			s.pickupFlash = in.readUnsignedByte();
			s.armorFlash = in.readUnsignedByte();
			s.kills = in.readShort();
			s.totalMonsters = in.readShort();

			s.message = in.readUTF();
			s.messageTimer = in.readShort();

			int doors = in.readUnsignedByte();
			s.doorIndex = new int[doors];
			s.doorOpen = new float[doors];
			for (int i = 0; i < doors; i++) {
				s.doorIndex[i] = in.readUnsignedShort();
				s.doorOpen[i] = in.readUnsignedByte() / 255f;
			}

			int mobs = in.readUnsignedByte();
			s.mobType = new byte[mobs];
			s.mobX = new float[mobs];
			s.mobY = new float[mobs];
			s.mobFrame = new byte[mobs];
			for (int i = 0; i < mobs; i++) {
				s.mobType[i] = in.readByte();
				s.mobX[i] = in.readFloat();
				s.mobY[i] = in.readFloat();
				s.mobFrame[i] = in.readByte();
			}

			int items = in.readUnsignedByte();
			s.itemType = new byte[items];
			s.itemX = new float[items];
			s.itemY = new float[items];
			for (int i = 0; i < items; i++) {
				s.itemType[i] = in.readByte();
				s.itemX[i] = in.readFloat();
				s.itemY[i] = in.readFloat();
			}

			int shots = in.readUnsignedByte();
			s.shotId = new int[shots];
			s.shotX = new float[shots];
			s.shotY = new float[shots];
			s.shotScale = new float[shots];
			for (int i = 0; i < shots; i++) {
				s.shotId[i] = in.readInt();
				s.shotX[i] = in.readFloat();
				s.shotY[i] = in.readFloat();
				s.shotScale[i] = in.readFloat();
			}

			int puffs = in.readUnsignedByte();
			s.puffX = new float[puffs];
			s.puffY = new float[puffs];
			s.puffLife = new byte[puffs];
			s.puffBlood = new boolean[puffs];
			for (int i = 0; i < puffs; i++) {
				s.puffX[i] = in.readFloat();
				s.puffY[i] = in.readFloat();
				s.puffLife[i] = in.readByte();
				s.puffBlood[i] = in.readBoolean();
			}

			int sounds = in.readUnsignedByte();
			s.sounds = new String[sounds];
			for (int i = 0; i < sounds; i++) s.sounds[i] = in.readUTF();
		} catch (IOException e) {
			throw new IllegalStateException("snapshot decode failed", e);
		}
		return s;
	}

	// ------------------------------------------------------------ smoothing

	/**
	 * Blend two consecutive snapshots so the wall runs at the client's framerate
	 * instead of the server's 20 ticks a second. Anything that cannot be matched up
	 * between the two simply snaps to {@code b}.
	 */
	public static DoomSnapshot interpolate(DoomSnapshot a, DoomSnapshot b, float t) {
		if (a == null || b == null) return b;
		if (a.levelIndex != b.levelIndex || a.state != b.state || b.paused) return b;
		if (t <= 0f) return a;
		if (t >= 1f) return b;

		DoomSnapshot out = b.shallowCopy();
		out.px = lerp(a.px, b.px, t);
		out.py = lerp(a.py, b.py, t);
		out.bob = lerp(a.bob, b.bob, t);

		float dx = lerp(a.dirX, b.dirX, t);
		float dy = lerp(a.dirY, b.dirY, t);
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len > 1e-5f) {
			out.dirX = dx / len;
			out.dirY = dy / len;
		}

		// the monster list is stable within a level, so index matching is safe
		if (a.mobType.length == b.mobType.length) {
			out.mobX = new float[b.mobX.length];
			out.mobY = new float[b.mobY.length];
			for (int i = 0; i < b.mobX.length; i++) {
				if (a.mobType[i] != b.mobType[i]) {
					out.mobX[i] = b.mobX[i];
					out.mobY[i] = b.mobY[i];
				} else {
					out.mobX[i] = lerp(a.mobX[i], b.mobX[i], t);
					out.mobY[i] = lerp(a.mobY[i], b.mobY[i], t);
				}
			}
		}

		// fireballs come and go, so match them by id rather than by position
		if (b.shotId.length > 0) {
			out.shotX = b.shotX.clone();
			out.shotY = b.shotY.clone();
			for (int i = 0; i < b.shotId.length; i++) {
				int j = indexOf(a.shotId, b.shotId[i]);
				if (j < 0) continue;
				out.shotX[i] = lerp(a.shotX[j], b.shotX[i], t);
				out.shotY[i] = lerp(a.shotY[j], b.shotY[i], t);
			}
		}
		return out;
	}

	private static int indexOf(int[] ids, int id) {
		for (int i = 0; i < ids.length; i++) if (ids[i] == id) return i;
		return -1;
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private DoomSnapshot shallowCopy() {
		DoomSnapshot c = new DoomSnapshot();
		c.state = state;
		c.paused = paused;
		c.levelIndex = levelIndex;
		c.tickCount = tickCount;
		c.px = px;
		c.py = py;
		c.dirX = dirX;
		c.dirY = dirY;
		c.pitch = pitch;
		c.bob = bob;
		c.health = health;
		c.armor = armor;
		c.bullets = bullets;
		c.shells = shells;
		c.weaponMask = weaponMask;
		c.weapon = weapon;
		c.muzzle = muzzle;
		c.fireCooldown = fireCooldown;
		c.painFlash = painFlash;
		c.pickupFlash = pickupFlash;
		c.armorFlash = armorFlash;
		c.kills = kills;
		c.totalMonsters = totalMonsters;
		c.message = message;
		c.messageTimer = messageTimer;
		c.doorIndex = doorIndex;
		c.doorOpen = doorOpen;
		c.mobType = mobType;
		c.mobX = mobX;
		c.mobY = mobY;
		c.mobFrame = mobFrame;
		c.itemType = itemType;
		c.itemX = itemX;
		c.itemY = itemY;
		c.shotId = shotId;
		c.shotX = shotX;
		c.shotY = shotY;
		c.shotScale = shotScale;
		c.puffX = puffX;
		c.puffY = puffY;
		c.puffLife = puffLife;
		c.puffBlood = puffBlood;
		c.sounds = sounds;
		return c;
	}
}
