package com.akadi.doommc.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * A self contained Doom style raycaster. Renders into a 320x200 ARGB buffer and
 * runs its own 35Hz simulation, exactly like the original. It knows nothing at
 * all about Minecraft - the mod just feeds it key states and reads the pixels.
 */
public final class DoomGame {
	public static final int W = 320;
	public static final int H = 200;
	/** Doom runs at 35Hz, Minecraft ticks at 20. These drive the integer step below. */
	private static final int GAME_HZ = 35;
	private static final int SERVER_HZ = 20;

	// ------------------------------------------------------------ input slots
	public static final int K_FWD = 0;
	public static final int K_BACK = 1;
	public static final int K_TURN_L = 2;
	public static final int K_TURN_R = 3;
	public static final int K_STRAFE_L = 4;
	public static final int K_STRAFE_R = 5;
	public static final int K_FIRE = 6;
	public static final int K_USE = 7;
	public static final int K_RUN = 8;
	public static final int K_W1 = 9;
	public static final int K_W2 = 10;
	public static final int K_W3 = 11;
	public static final int K_ENTER = 12;
	public static final int K_COUNT = 13;

	// ------------------------------------------------------------ game states
	private static final int ST_TITLE = 0;
	private static final int ST_PLAY = 1;
	private static final int ST_DEAD = 2;
	private static final int ST_LEVEL_DONE = 3;
	private static final int ST_VICTORY = 4;

	// ------------------------------------------------------------- weapon defs
	private static final int WP_PISTOL = 0;
	private static final int WP_SHOTGUN = 1;
	private static final int WP_CHAINGUN = 2;
	private static final int[] WP_COOLDOWN = {10, 26, 3};
	private static final int[] WP_PELLETS = {1, 8, 1};
	private static final int[] WP_DAMAGE = {14, 10, 11};
	private static final double[] WP_SPREAD = {0.006, 0.10, 0.035};
	/** 0 = bullets, 1 = shells */
	private static final int[] WP_AMMOTYPE = {0, 1, 0};

	public final int[] fb = new int[W * H];

	private final double[] zbuf = new double[W];
	private final boolean[] keys = new boolean[K_COUNT];
	private final boolean[] held = new boolean[K_COUNT];

	/** Event names the Minecraft layer turns into vanilla sounds. */
	public final Queue<String> sounds = new ArrayDeque<>();

	private int state = ST_TITLE;
	private boolean paused;
	private int levelIndex;
	private Level level;
	private int stepAccum;
	private int tickCount;
	private int nextShotId = 1;
	private double pendingTurn;

	// player
	private double px, py;
	private double dirX = 1, dirY = 0;
	private double planeX = 0, planeY = 0.66;
	private int health, armor;
	private final int[] ammo = new int[2];
	private final boolean[] hasWeapon = new boolean[3];
	private int weapon;
	private int fireCooldown;
	private int muzzle;
	private double bob;
	private int pitch;
	private int painFlash;
	private int pickupFlash;
	private int armorFlash;
	private int kills;
	private int totalMonsters;

	private String message = "";
	private int messageTimer;

	private final List<Mob> mobs = new ArrayList<>();
	private final List<Item> items = new ArrayList<>();
	private final List<Shot> shots = new ArrayList<>();
	private final List<Puff> puffs = new ArrayList<>();
	private final Map<Integer, Integer> openDoors = new HashMap<>();

	private int rngState = 0x1BADB002;

	public DoomGame() {
		// deliberately no Art.init() here - a dedicated server simulates but never
		// draws, and there is no point building every texture on it
		startLevel(0, true);
		state = ST_TITLE;
	}

	// ============================================================ public api

	public void keyDown(int action) {
		if (action >= 0 && action < K_COUNT) keys[action] = true;
	}

	public void keyUp(int action) {
		if (action >= 0 && action < K_COUNT) keys[action] = false;
	}

	public void releaseAll() {
		java.util.Arrays.fill(keys, false);
	}

	public boolean isRunning() {
		return state == ST_PLAY;
	}

	public String statusLine() {
		return switch (state) {
			case ST_TITLE -> "MENU";
			case ST_DEAD -> "OLDUN";
			case ST_LEVEL_DONE -> "BOLUM BITTI";
			case ST_VICTORY -> "OYUN BITTI";
			default -> level.name;
		};
	}

	/** Frozen games still redraw, so the wall keeps showing where you left off. */
	public void setPaused(boolean value) {
		if (paused == value) return;
		paused = value;
		stepAccum = 0;
		if (paused) releaseAll();
	}

	public boolean isPaused() {
		return paused;
	}

	/** Whole key state at once, as it arrives from the controlling client. */
	public void setKeys(int mask) {
		for (int i = 0; i < K_COUNT; i++) {
			keys[i] = (mask & (1 << i)) != 0;
		}
	}

	/**
	 * Mouse look. The client batches up how far the mouse moved since the last packet
	 * and sends it as one angle; we apply it on the next tick.
	 */
	public void addTurn(double radians) {
		if (Double.isNaN(radians) || Double.isInfinite(radians)) return;
		pendingTurn += radians;
		// a hostile or badly lagged client must not be able to spin the camera wildly
		pendingTurn = Math.max(-1.2, Math.min(1.2, pendingTurn));
	}

	/**
	 * One Minecraft tick's worth of simulation. Doom wants 35Hz and the server ticks
	 * at 20, so we step whole game ticks off an integer accumulator - exactly 35 per
	 * second, with none of the drift a floating point clock would bring.
	 */
	public void serverTick() {
		if (paused) return;
		stepAccum += GAME_HZ;
		while (stepAccum >= SERVER_HZ) {
			stepAccum -= SERVER_HZ;
			tick();
		}
	}

	/** A single simulation step, for tests and offline tools. */
	public void tickOnce() {
		tick();
	}

	/** Draw the current state into {@link #fb}. Only clients ever call this. */
	public void renderFrame() {
		Art.init();
		render();
		if (paused) drawPausedOverlay();
	}

	private void drawPausedOverlay() {
		dim(0.55f, 0, H);
		Fonts.drawCentered(fb, W, H, Lang.get("ov.paused"), 78, 0xFFFFD040, 3);
		Fonts.drawCentered(fb, W, H, Lang.get("ov.paused2"), 108, 0xFFBBBBBB, 1);
	}

	// ============================================================== simulation

	private void tick() {
		tickCount++;

		switch (state) {
			case ST_TITLE -> {
				if (pressed(K_ENTER) || pressed(K_FIRE)) {
					startLevel(0, true);
					state = ST_PLAY;
					say("msg.start");
				}
			}
			case ST_DEAD -> {
				pitch = Math.max(pitch - 2, -46);
				if (pressed(K_ENTER) || pressed(K_FIRE)) {
					startLevel(levelIndex, true);
					state = ST_PLAY;
				}
			}
			case ST_LEVEL_DONE -> {
				if (pressed(K_ENTER) || pressed(K_FIRE)) {
					if (levelIndex + 1 >= Level.COUNT) {
						state = ST_VICTORY;
					} else {
						startLevel(levelIndex + 1, false);
						state = ST_PLAY;
					}
				}
			}
			case ST_VICTORY -> {
				if (pressed(K_ENTER) || pressed(K_FIRE)) {
					startLevel(0, true);
					state = ST_PLAY;
				}
			}
			default -> tickPlay();
		}

		System.arraycopy(keys, 0, held, 0, K_COUNT);
		if (messageTimer > 0) messageTimer--;
		if (painFlash > 0) painFlash--;
		if (pickupFlash > 0) pickupFlash--;
		if (armorFlash > 0) armorFlash--;
		if (muzzle > 0) muzzle--;
	}

	private void tickPlay() {
		double speed = keys[K_RUN] ? 0.115 : 0.068;
		double turn = keys[K_RUN] ? 0.075 : 0.048;
		boolean moving = false;

		// screen-left is (dir - plane), so turning left must rotate dir toward -plane
		if (keys[K_TURN_L]) rotate(-turn);
		if (keys[K_TURN_R]) rotate(turn);
		if (pendingTurn != 0) {
			rotate(pendingTurn);
			pendingTurn = 0;
		}

		double mx = 0, my = 0;
		if (keys[K_FWD]) { mx += dirX; my += dirY; moving = true; }
		if (keys[K_BACK]) { mx -= dirX; my -= dirY; moving = true; }
		if (keys[K_STRAFE_L]) { mx += planeX * -1; my += planeY * -1; moving = true; }
		if (keys[K_STRAFE_R]) { mx += planeX; my += planeY; moving = true; }
		double len = Math.sqrt(mx * mx + my * my);
		if (len > 0.0001) {
			mx = mx / len * speed;
			my = my / len * speed;
			tryMove(mx, my);
		}

		bob = moving ? bob + (keys[K_RUN] ? 0.42 : 0.28) : bob * 0.85;
		pitch = (int) (Math.sin(bob) * 1.6);

		// weapon switching
		if (pressed(K_W1) && hasWeapon[0]) selectWeapon(0);
		if (pressed(K_W2) && hasWeapon[1]) selectWeapon(1);
		if (pressed(K_W3) && hasWeapon[2]) selectWeapon(2);

		if (fireCooldown > 0) fireCooldown--;
		if (keys[K_FIRE] && fireCooldown == 0) fire();

		if (pressed(K_USE)) use();

		tickDoors();
		tickMobs();
		tickShots();
		tickPuffs();
		tickPickups();

		int tileX = (int) px, tileY = (int) py;
		if (level.isExit(tileX, tileY)) {
			state = ST_LEVEL_DONE;
			sounds.add("exit");
		}

		if (health <= 0) {
			state = ST_DEAD;
			sounds.add("player_death");
			say("msg.died");
		}
	}

	private void rotate(double a) {
		double odx = dirX;
		dirX = dirX * Math.cos(a) - dirY * Math.sin(a);
		dirY = odx * Math.sin(a) + dirY * Math.cos(a);
		double opx = planeX;
		planeX = planeX * Math.cos(a) - planeY * Math.sin(a);
		planeY = opx * Math.sin(a) + planeY * Math.cos(a);
	}

	private void tryMove(double dx, double dy) {
		double r = 0.22;
		if (!blockedForBody(px + dx + Math.signum(dx) * r, py)) px += dx;
		if (!blockedForBody(px, py + dy + Math.signum(dy) * r)) py += dy;
	}

	private boolean blockedForBody(double x, double y) {
		if (level.solid((int) x, (int) y)) return true;
		for (Mob m : mobs) {
			if (m.dead) continue;
			double d = Math.hypot(m.x - x, m.y - y);
			if (d < m.radius + 0.2) return true;
		}
		return false;
	}

	private void selectWeapon(int w) {
		if (weapon == w) return;
		weapon = w;
		fireCooldown = 8;
		say("wp." + w);
		sounds.add("switch");
	}

	private void fire() {
		int at = WP_AMMOTYPE[weapon];
		if (ammo[at] <= 0) {
			// fall back to whatever still has rounds
			for (int i = 0; i < 3; i++) {
				if (hasWeapon[i] && ammo[WP_AMMOTYPE[i]] > 0) {
					selectWeapon(i);
					return;
				}
			}
			say("msg.noammo");
			fireCooldown = 12;
			sounds.add("dryfire");
			return;
		}
		ammo[at]--;
		fireCooldown = WP_COOLDOWN[weapon];
		muzzle = 4;
		sounds.add(switch (weapon) {
			case WP_SHOTGUN -> "shotgun";
			case WP_CHAINGUN -> "chaingun";
			default -> "pistol";
		});

		for (int p = 0; p < WP_PELLETS[weapon]; p++) {
			double spread = (rnd() - 0.5) * WP_SPREAD[weapon] * 2;
			double rx = dirX * Math.cos(spread) - dirY * Math.sin(spread);
			double ry = dirX * Math.sin(spread) + dirY * Math.cos(spread);
			hitscan(rx, ry, WP_DAMAGE[weapon]);
		}
	}

	private void hitscan(double rx, double ry, int damage) {
		double x = px, y = py;
		for (int step = 0; step < 900; step++) {
			x += rx * 0.022;
			y += ry * 0.022;
			if (level.solid((int) x, (int) y)) {
				puffs.add(new Puff(x - rx * 0.05, y - ry * 0.05, 0, false));
				return;
			}
			for (Mob m : mobs) {
				if (m.dead) continue;
				if (Math.hypot(m.x - x, m.y - y) < m.radius) {
					hurtMob(m, damage);
					puffs.add(new Puff(x, y, 0, true));
					return;
				}
			}
		}
	}

	private void hurtMob(Mob m, int damage) {
		m.health -= damage;
		m.awake = true;
		if (m.health <= 0) {
			m.dead = true;
			m.frame = Art.FR_DEAD;
			kills++;
			sounds.add("mob_death");
			return;
		}
		if (rnd() < 0.35) {
			m.painTimer = 9;
			sounds.add("mob_pain");
		}
	}

	private void use() {
		// look for a door within reach along the view direction
		double x = px, y = py;
		for (int i = 0; i < 90; i++) {
			x += dirX * 0.02;
			y += dirY * 0.02;
			int tx = (int) x, ty = (int) y;
			if (level.isDoor(tx, ty)) {
				openDoors.put(ty * level.w + tx, 140);
				sounds.add("door");
				return;
			}
			if (level.solid(tx, ty)) break;
		}
		say("msg.nodoor");
	}

	private void tickDoors() {
		Set<Integer> active = new HashSet<>();
		var it = openDoors.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			int t = e.getValue() - 1;
			// keep a door open while something is standing in it
			int idx = e.getKey();
			int dx = idx % level.w, dy = idx / level.w;
			if (Math.hypot(px - (dx + 0.5), py - (dy + 0.5)) < 1.4) t = Math.max(t, 40);
			if (t <= 0) {
				it.remove();
			} else {
				e.setValue(t);
				active.add(idx);
			}
		}
		// monsters shoulder doors open on their own
		for (Mob m : mobs) {
			if (m.dead || !m.awake) continue;
			int mx = (int) m.x, my = (int) m.y;
			for (int oy = -1; oy <= 1; oy++) {
				for (int ox = -1; ox <= 1; ox++) {
					if (level.isDoor(mx + ox, my + oy)) {
						int idx = (my + oy) * level.w + (mx + ox);
						openDoors.put(idx, 60);
						active.add(idx);
					}
				}
			}
		}
		level.tickDoors(active);
	}

	private void tickMobs() {
		for (Mob m : mobs) {
			if (m.dead) continue;
			if (m.painTimer > 0) {
				m.painTimer--;
				m.frame = Art.FR_PAIN;
				continue;
			}
			if (m.attackCd > 0) m.attackCd--;

			double dist = Math.hypot(px - m.x, py - m.y);
			boolean sees = dist < 16 && lineOfSight(m.x, m.y, px, py);
			if (sees && dist < 13) m.awake = true;
			if (!m.awake) {
				m.frame = Art.FR_WALK_A;
				continue;
			}

			MobStats s = STATS[m.type];

			if (sees && dist <= s.attackRange && m.attackCd == 0) {
				m.attackCd = s.attackCooldown;
				m.frame = Art.FR_ATTACK;
				m.attackAnim = 8;
				doMobAttack(m, s, dist);
				continue;
			}

			if (m.attackAnim > 0) {
				m.attackAnim--;
				m.frame = Art.FR_ATTACK;
			} else {
				m.frame = ((tickCount + m.seed) / 8) % 2 == 0 ? Art.FR_WALK_A : Art.FR_WALK_B;
			}

			// walk toward the player, sliding along walls when we clip a corner
			if (dist > s.keepDistance) {
				double nx = (px - m.x) / dist, ny = (py - m.y) / dist;
				// a little wobble stops packs from stacking into one line
				double wob = Math.sin((tickCount + m.seed * 13) * 0.07) * 0.35;
				double wx = nx * Math.cos(wob) - ny * Math.sin(wob);
				double wy = nx * Math.sin(wob) + ny * Math.cos(wob);
				moveMob(m, wx * s.speed, wy * s.speed);
			}
		}
	}

	private void doMobAttack(Mob m, MobStats s, double dist) {
		double nx = (px - m.x) / dist, ny = (py - m.y) / dist;
		if (s.melee) {
			hurtPlayer(s.damage + (int) (rnd() * 6));
			sounds.add("mob_melee");
		} else if (s.projectile) {
			Shot sh = new Shot();
			sh.id = nextShotId++;
			sh.x = m.x + nx * 0.4;
			sh.y = m.y + ny * 0.4;
			sh.dx = nx * 0.11;
			sh.dy = ny * 0.11;
			sh.damage = s.damage;
			sh.scale = m.type == Art.M_BARON ? 0.38 : 0.26;
			shots.add(sh);
			sounds.add("fireball");
		} else {
			// hitscan zombie - misses a fair bit at range
			if (rnd() < 0.72 - dist * 0.02) {
				hurtPlayer(s.damage + (int) (rnd() * 5));
			}
			sounds.add("mob_shoot");
		}
	}

	private void moveMob(Mob m, double dx, double dy) {
		if (!mobBlocked(m, m.x + dx, m.y)) m.x += dx;
		if (!mobBlocked(m, m.x, m.y + dy)) m.y += dy;
	}

	private boolean mobBlocked(Mob self, double x, double y) {
		if (level.solid((int) (x - self.radius), (int) y)) return true;
		if (level.solid((int) (x + self.radius), (int) y)) return true;
		if (level.solid((int) x, (int) (y - self.radius))) return true;
		if (level.solid((int) x, (int) (y + self.radius))) return true;
		for (Mob o : mobs) {
			if (o == self || o.dead) continue;
			if (Math.hypot(o.x - x, o.y - y) < (o.radius + self.radius) * 0.85) return true;
		}
		return false;
	}

	private boolean lineOfSight(double x0, double y0, double x1, double y1) {
		double dx = x1 - x0, dy = y1 - y0;
		double d = Math.hypot(dx, dy);
		int steps = (int) (d / 0.08) + 1;
		dx /= steps;
		dy /= steps;
		double x = x0, y = y0;
		for (int i = 0; i < steps; i++) {
			x += dx;
			y += dy;
			if (level.solid((int) x, (int) y)) return false;
		}
		return true;
	}

	private void tickShots() {
		shots.removeIf(s -> {
			for (int i = 0; i < 3; i++) {
				s.x += s.dx / 3.0;
				s.y += s.dy / 3.0;
				if (level.solid((int) s.x, (int) s.y)) {
					puffs.add(new Puff(s.x, s.y, 0, false));
					return true;
				}
				if (Math.hypot(px - s.x, py - s.y) < 0.4) {
					hurtPlayer(s.damage + (int) (rnd() * 8));
					return true;
				}
			}
			s.life++;
			return s.life > 260;
		});
	}

	private void tickPuffs() {
		puffs.removeIf(p -> ++p.life > 12);
	}

	private void tickPickups() {
		for (Item it : items) {
			if (it.taken) continue;
			if (Math.hypot(px - it.x, py - it.y) > 0.55) continue;
			switch (it.type) {
				case Art.I_HEALTH -> {
					if (health >= 100) continue;
					health = Math.min(100, health + 25);
					say("msg.health");
					pickupFlash = 8;
				}
				case Art.I_AMMO -> {
					ammo[0] = Math.min(300, ammo[0] + 25);
					ammo[1] = Math.min(80, ammo[1] + 6);
					say("msg.ammo");
					pickupFlash = 8;
				}
				case Art.I_ARMOR -> {
					if (armor >= 100) continue;
					armor = 100;
					say("msg.armor");
					armorFlash = 10;
				}
				case Art.I_SHOTGUN -> {
					hasWeapon[1] = true;
					ammo[1] = Math.min(80, ammo[1] + 12);
					selectWeapon(1);
					say("msg.shotgun");
					pickupFlash = 12;
				}
				case Art.I_CHAINGUN -> {
					hasWeapon[2] = true;
					ammo[0] = Math.min(300, ammo[0] + 60);
					selectWeapon(2);
					say("msg.chaingun");
					pickupFlash = 12;
				}
				default -> {
				}
			}
			it.taken = true;
			sounds.add("pickup");
		}
	}

	private void hurtPlayer(int amount) {
		if (armor > 0) {
			int absorbed = Math.min(armor, amount / 2);
			armor -= absorbed;
			amount -= absorbed;
		}
		health -= amount;
		painFlash = 9;
		sounds.add("hurt");
	}

	private void say(String s) {
		message = s;
		messageTimer = 90;
	}

	private boolean pressed(int action) {
		return keys[action] && !held[action];
	}

	private double rnd() {
		rngState ^= rngState << 13;
		rngState ^= rngState >>> 17;
		rngState ^= rngState << 5;
		return ((rngState >>> 8) & 0xFFFFFF) / (double) 0x1000000;
	}

	// ============================================================== networking

	private static final int MAX_LIST = 255;

	/** Snapshot the drawable state. The server calls this once per tick. */
	public DoomSnapshot captureSnapshot() {
		DoomSnapshot s = new DoomSnapshot();
		s.state = state;
		s.paused = paused;
		s.levelIndex = levelIndex;
		s.tickCount = tickCount;
		s.px = (float) px;
		s.py = (float) py;
		s.dirX = (float) dirX;
		s.dirY = (float) dirY;
		s.pitch = pitch;
		s.bob = (float) bob;
		s.health = health;
		s.armor = armor;
		s.bullets = ammo[0];
		s.shells = ammo[1];
		s.weaponMask = (hasWeapon[0] ? 1 : 0) | (hasWeapon[1] ? 2 : 0) | (hasWeapon[2] ? 4 : 0);
		s.weapon = weapon;
		s.muzzle = muzzle;
		s.fireCooldown = Math.min(MAX_LIST, fireCooldown);
		s.painFlash = painFlash;
		s.pickupFlash = pickupFlash;
		s.armorFlash = armorFlash;
		s.kills = kills;
		s.totalMonsters = totalMonsters;
		s.message = message;
		s.messageTimer = messageTimer;

		int doors = 0;
		for (boolean d : level.door) if (d) doors++;
		doors = Math.min(doors, MAX_LIST);
		s.doorIndex = new int[doors];
		s.doorOpen = new float[doors];
		int di = 0;
		for (int i = 0; i < level.door.length && di < doors; i++) {
			if (!level.door[i]) continue;
			s.doorIndex[di] = i;
			s.doorOpen[di] = level.doorOpen[i];
			di++;
		}

		int mobCount = Math.min(mobs.size(), MAX_LIST);
		s.mobType = new byte[mobCount];
		s.mobX = new float[mobCount];
		s.mobY = new float[mobCount];
		s.mobFrame = new byte[mobCount];
		for (int i = 0; i < mobCount; i++) {
			Mob m = mobs.get(i);
			s.mobType[i] = (byte) m.type;
			s.mobX[i] = (float) m.x;
			s.mobY[i] = (float) m.y;
			s.mobFrame[i] = (byte) m.frame;
		}

		List<Item> alive = new ArrayList<>();
		for (Item it : items) if (!it.taken) alive.add(it);
		int itemCount = Math.min(alive.size(), MAX_LIST);
		s.itemType = new byte[itemCount];
		s.itemX = new float[itemCount];
		s.itemY = new float[itemCount];
		for (int i = 0; i < itemCount; i++) {
			Item it = alive.get(i);
			s.itemType[i] = (byte) it.type;
			s.itemX[i] = (float) it.x;
			s.itemY[i] = (float) it.y;
		}

		int shotCount = Math.min(shots.size(), MAX_LIST);
		s.shotId = new int[shotCount];
		s.shotX = new float[shotCount];
		s.shotY = new float[shotCount];
		s.shotScale = new float[shotCount];
		for (int i = 0; i < shotCount; i++) {
			Shot sh = shots.get(i);
			s.shotId[i] = sh.id;
			s.shotX[i] = (float) sh.x;
			s.shotY[i] = (float) sh.y;
			s.shotScale[i] = (float) sh.scale;
		}

		int puffCount = Math.min(puffs.size(), MAX_LIST);
		s.puffX = new float[puffCount];
		s.puffY = new float[puffCount];
		s.puffLife = new byte[puffCount];
		s.puffBlood = new boolean[puffCount];
		for (int i = 0; i < puffCount; i++) {
			Puff p = puffs.get(i);
			s.puffX[i] = (float) p.x;
			s.puffY[i] = (float) p.y;
			s.puffLife[i] = (byte) p.life;
			s.puffBlood[i] = p.blood;
		}

		List<String> cues = new ArrayList<>();
		String cue;
		while ((cue = sounds.poll()) != null && cues.size() < 8) cues.add(cue);
		sounds.clear();
		s.sounds = cues.toArray(new String[0]);

		return s;
	}

	/**
	 * Load a snapshot into this instance so {@link #renderFrame()} can draw it.
	 * A client's DoomGame is only ever a canvas - it never runs a single game tick.
	 */
	public void applySnapshot(DoomSnapshot s) {
		if (level == null || levelIndex != s.levelIndex) {
			levelIndex = Math.max(0, Math.min(Level.COUNT - 1, s.levelIndex));
			level = new Level(levelIndex);
		}
		state = s.state;
		paused = s.paused;
		tickCount = s.tickCount;
		px = s.px;
		py = s.py;
		dirX = s.dirX;
		dirY = s.dirY;
		// the camera plane is always the direction turned 90 degrees and scaled for the fov
		planeX = -dirY * 0.66;
		planeY = dirX * 0.66;
		pitch = s.pitch;
		bob = s.bob;
		health = s.health;
		armor = s.armor;
		ammo[0] = s.bullets;
		ammo[1] = s.shells;
		hasWeapon[0] = (s.weaponMask & 1) != 0;
		hasWeapon[1] = (s.weaponMask & 2) != 0;
		hasWeapon[2] = (s.weaponMask & 4) != 0;
		weapon = Math.max(0, Math.min(2, s.weapon));
		muzzle = s.muzzle;
		fireCooldown = s.fireCooldown;
		painFlash = s.painFlash;
		pickupFlash = s.pickupFlash;
		armorFlash = s.armorFlash;
		kills = s.kills;
		totalMonsters = s.totalMonsters;
		message = s.message == null ? "" : s.message;
		messageTimer = s.messageTimer;

		java.util.Arrays.fill(level.doorOpen, 0f);
		for (int i = 0; i < s.doorIndex.length; i++) {
			int idx = s.doorIndex[i];
			if (idx >= 0 && idx < level.doorOpen.length) level.doorOpen[idx] = s.doorOpen[i];
		}

		mobs.clear();
		for (int i = 0; i < s.mobType.length; i++) {
			Mob m = new Mob();
			m.type = clamp(s.mobType[i], Art.M_COUNT);
			m.frame = clamp(s.mobFrame[i], Art.FR_COUNT);
			m.x = s.mobX[i];
			m.y = s.mobY[i];
			m.dead = m.frame == Art.FR_DEAD;
			m.radius = STATS[m.type].radius;
			mobs.add(m);
		}

		items.clear();
		for (int i = 0; i < s.itemType.length; i++) {
			Item it = new Item();
			it.type = clamp(s.itemType[i], Art.I_COUNT);
			it.x = s.itemX[i];
			it.y = s.itemY[i];
			items.add(it);
		}

		shots.clear();
		for (int i = 0; i < s.shotId.length; i++) {
			Shot sh = new Shot();
			sh.id = s.shotId[i];
			sh.x = s.shotX[i];
			sh.y = s.shotY[i];
			sh.scale = s.shotScale[i];
			shots.add(sh);
		}

		puffs.clear();
		for (int i = 0; i < s.puffX.length; i++) {
			Puff p = new Puff(s.puffX[i], s.puffY[i], 0, s.puffBlood[i]);
			p.life = Math.max(0, s.puffLife[i]);
			puffs.add(p);
		}
	}

	private static int clamp(int value, int exclusiveMax) {
		if (value < 0) return 0;
		return value >= exclusiveMax ? exclusiveMax - 1 : value;
	}

	// ---------------------------------------------------------- level setup

	private void startLevel(int index, boolean resetLoadout) {
		levelIndex = index;
		level = new Level(index);
		px = level.spawnX;
		py = level.spawnY;
		dirX = 1;
		dirY = 0;
		planeX = 0;
		planeY = 0.66;
		pitch = 0;
		bob = 0;
		fireCooldown = 0;
		painFlash = pickupFlash = armorFlash = 0;
		mobs.clear();
		items.clear();
		shots.clear();
		puffs.clear();
		openDoors.clear();
		kills = 0;

		if (resetLoadout) {
			health = 100;
			armor = 0;
			ammo[0] = 50;
			ammo[1] = 0;
			hasWeapon[0] = true;
			hasWeapon[1] = false;
			hasWeapon[2] = false;
			weapon = 0;
		} else {
			health = Math.max(health, 45);
			ammo[0] = Math.max(ammo[0], 30);
		}

		int seed = 1;
		for (int[] sp : level.monsterSpawns) {
			Mob m = new Mob();
			m.type = sp[0];
			m.x = sp[1] + 0.5;
			m.y = sp[2] + 0.5;
			m.health = STATS[m.type].health;
			m.radius = STATS[m.type].radius;
			m.seed = seed++;
			mobs.add(m);
		}
		totalMonsters = mobs.size();

		for (int[] sp : level.itemSpawns) {
			Item it = new Item();
			it.type = sp[0];
			it.x = sp[1] + 0.5;
			it.y = sp[2] + 0.5;
			items.add(it);
		}

		// face whichever way has the most room in front
		for (int i = 0; i < 4; i++) {
			if (!level.solid((int) (px + dirX * 1.5), (int) (py + dirY * 1.5))) break;
			rotate(Math.PI / 2);
		}

		say(level.name);
	}

	// ============================================================== rendering

	private void render() {
		if (state == ST_TITLE) {
			drawTitle();
			return;
		}

		drawFloorAndCeiling();
		drawWalls();
		drawSprites();
		if (state == ST_PLAY || state == ST_LEVEL_DONE) drawWeapon();
		drawOverlayTint();
		drawHud();

		switch (state) {
			case ST_DEAD -> {
				Fonts.drawCentered(fb, W, H, Lang.get("ov.dead"), 66, 0xFFFF3020, 4);
				Fonts.drawCentered(fb, W, H, Lang.get("ov.retry"), 104, 0xFFDDDDDD, 1);
			}
			case ST_LEVEL_DONE -> {
				dim(0.45f, 40, 120);
				Fonts.drawCentered(fb, W, H, Lang.get("ov.leveldone"), 52, 0xFFFFD040, 3);
				Fonts.drawCentered(fb, W, H, level.name, 82, 0xFFCCCCCC, 1);
				Fonts.drawCentered(fb, W, H, Lang.get("ov.killed") + ": " + kills + " / " + totalMonsters,
						96, 0xFFCCCCCC, 1);
				Fonts.drawCentered(fb, W, H, Lang.get("ov.continue"), 112, 0xFF80FF80, 1);
			}
			case ST_VICTORY -> {
				dim(0.35f, 0, H);
				Fonts.drawCentered(fb, W, H, Lang.get("ov.win1"), 60, 0xFFFFD040, 3);
				Fonts.drawCentered(fb, W, H, Lang.get("ov.win2"), 92, 0xFFFFFFFF, 2);
				Fonts.drawCentered(fb, W, H, Lang.get("ov.win3"), 118, 0xFF80FF80, 1);
			}
			default -> {
			}
		}
	}

	private void drawFloorAndCeiling() {
		int[] floorTex = Art.flats[level.floorTex];
		int[] ceilTex = Art.flats[level.ceilTex];
		int horizon = H / 2 + pitch;

		for (int y = 0; y < H; y++) {
			int p = y - horizon;
			boolean isFloor = p > 0;
			int q = isFloor ? p : -p;
			if (q == 0) q = 1;

			double rowDistance = (0.5 * H) / q;
			double rayDirX0 = dirX - planeX, rayDirY0 = dirY - planeY;
			double rayDirX1 = dirX + planeX, rayDirY1 = dirY + planeY;
			double stepX = rowDistance * (rayDirX1 - rayDirX0) / W;
			double stepY = rowDistance * (rayDirY1 - rayDirY0) / W;
			double fx = px + rowDistance * rayDirX0;
			double fy = py + rowDistance * rayDirY0;

			float shade = shadeFor(rowDistance) * (isFloor ? 1.0f : 0.72f);
			int[] tex = isFloor ? floorTex : ceilTex;
			int base = y * W;

			for (int x = 0; x < W; x++) {
				int tx = (int) (Art.TEX * (fx - Math.floor(fx))) & (Art.TEX - 1);
				int ty = (int) (Art.TEX * (fy - Math.floor(fy))) & (Art.TEX - 1);
				fx += stepX;
				fy += stepY;
				fb[base + x] = Art.mul(tex[Art.TEX * ty + tx], shade);
			}
		}
	}

	private void drawWalls() {
		int horizon = H / 2 + pitch;

		for (int x = 0; x < W; x++) {
			double cameraX = 2.0 * x / W - 1.0;
			double rayX = dirX + planeX * cameraX;
			double rayY = dirY + planeY * cameraX;

			int mapX = (int) px, mapY = (int) py;
			double deltaX = rayX == 0 ? 1e30 : Math.abs(1 / rayX);
			double deltaY = rayY == 0 ? 1e30 : Math.abs(1 / rayY);
			int stepX, stepY;
			double sideX, sideY;

			if (rayX < 0) { stepX = -1; sideX = (px - mapX) * deltaX; }
			else { stepX = 1; sideX = (mapX + 1.0 - px) * deltaX; }
			if (rayY < 0) { stepY = -1; sideY = (py - mapY) * deltaY; }
			else { stepY = 1; sideY = (mapY + 1.0 - py) * deltaY; }

			int side = 0;
			boolean hit = false;
			int guard = 0;
			while (!hit && guard++ < 256) {
				if (sideX < sideY) { sideX += deltaX; mapX += stepX; side = 0; }
				else { sideY += deltaY; mapY += stepY; side = 1; }
				if (level.blocksSight(mapX, mapY)) hit = true;
			}

			double perp = side == 0
					? (sideX - deltaX)
					: (sideY - deltaY);
			if (perp < 0.02) perp = 0.02;
			zbuf[x] = perp;

			int lineHeight = (int) (H / perp);
			int drawStart = -lineHeight / 2 + horizon;
			int drawEnd = lineHeight / 2 + horizon;

			int texId = level.wallAt(mapX, mapY);
			if (texId < 0) texId = Art.W_STONE;
			int[] tex = Art.walls[texId];

			double wallX = side == 0 ? py + perp * rayY : px + perp * rayX;
			wallX -= Math.floor(wallX);
			int texX = (int) (wallX * Art.TEX);
			if (side == 0 && rayX > 0) texX = Art.TEX - texX - 1;
			if (side == 1 && rayY < 0) texX = Art.TEX - texX - 1;
			texX = Math.max(0, Math.min(Art.TEX - 1, texX));

			float shade = shadeFor(perp) * (side == 1 ? 0.72f : 1.0f);

			// a door that is part way up slides its slab toward the ceiling
			int shift = 0;
			boolean isDoor = level.isDoor(mapX, mapY);
			if (isDoor) shift = (int) (level.openAt(mapX, mapY) * lineHeight);

			int y0 = Math.max(0, drawStart);
			int y1 = Math.min(H - 1, drawEnd);
			for (int y = y0; y <= y1; y++) {
				double v = (y - drawStart + shift) / (double) lineHeight;
				if (v >= 1.0) {
					// the gap under a rising door - dark, since we cannot see past it yet
					fb[y * W + x] = 0xFF000000;
					continue;
				}
				int texY = (int) (v * Art.TEX);
				if (texY < 0) texY = 0;
				if (texY > Art.TEX - 1) texY = Art.TEX - 1;
				fb[y * W + x] = Art.mul(tex[texY * Art.TEX + texX], shade);
			}
		}
	}

	private void drawSprites() {
		List<Renderable> list = new ArrayList<>();
		for (Mob m : mobs) {
			list.add(new Renderable(m.x, m.y, Art.mobs[m.type][m.frame],
					STATS[m.type].spriteScale, true, m.dead ? 0.25 : 1.0));
		}
		for (Item it : items) {
			if (it.taken) continue;
			list.add(new Renderable(it.x, it.y, Art.items[it.type], 0.42, true, 1.0));
		}
		for (Shot s : shots) {
			list.add(new Renderable(s.x, s.y, Art.fireball, s.scale, false, 1.0));
		}
		for (Puff p : puffs) {
			int stage = Math.min(2, p.life / 4);
			list.add(new Renderable(p.x, p.y, Art.puffs[stage], 0.22 + stage * 0.05, false, p.blood ? -1 : 1));
		}

		list.sort((a, b) -> Double.compare(
				(b.x - px) * (b.x - px) + (b.y - py) * (b.y - py),
				(a.x - px) * (a.x - px) + (a.y - py) * (a.y - py)));

		int horizon = H / 2 + pitch;
		double invDet = 1.0 / (planeX * dirY - dirX * planeY);

		for (Renderable r : list) {
			double sx = r.x - px, sy = r.y - py;
			double tX = invDet * (dirY * sx - dirX * sy);
			double tY = invDet * (-planeY * sx + planeX * sy);
			if (tY < 0.12) continue;

			int screenX = (int) ((W / 2.0) * (1 + tX / tY));
			int fullH = (int) (H / tY);
			int sprH = (int) (fullH * r.scale);
			int sprW = sprH;
			if (sprH <= 0) continue;

			int centerY = horizon + (r.grounded ? (fullH / 2 - sprH / 2) : 0);
			int startY = centerY - sprH / 2;
			int startX = screenX - sprW / 2;

			float shade = shadeFor(tY);
			// corpses sit in shadow, blood puffs are tinted
			if (r.tint < 0) shade *= 1.0f;

			for (int x = 0; x < sprW; x++) {
				int screenXX = startX + x;
				if (screenXX < 0 || screenXX >= W) continue;
				if (tY >= zbuf[screenXX]) continue;
				int texX = x * r.art.w / sprW;
				for (int y = 0; y < sprH; y++) {
					int screenYY = startY + y;
					if (screenYY < 0 || screenYY >= H) continue;
					int texY = y * r.art.h / sprH;
					int c = r.art.px[texY * r.art.w + texX];
					if ((c & 0xFF000000) == 0) continue;
					if (r.tint < 0) c = (c & 0xFF000000) | 0x00A01010;
					// fireballs and muzzle puffs glow, so they ignore distance shading
					fb[screenYY * W + screenXX] = r.grounded ? Art.mul(c, shade) : c;
				}
			}
		}
	}

	private void drawWeapon() {
		if (!hasWeapon[weapon]) return;
		PixMap art = Art.guns[weapon][muzzle > 0 ? 1 : 0];
		int bx = (int) (Math.sin(bob * 0.5) * 6);
		int by = (int) (Math.abs(Math.cos(bob * 0.5)) * 5);
		int recoil = fireCooldown > WP_COOLDOWN[weapon] - 3 ? 6 : 0;
		// the hands run off the bottom and behind the status bar, like the original
		int x = (W - art.w) / 2 + bx + 6;
		int y = H - art.h - 6 + by + recoil;
		art.blitShaded(fb, W, H, x, y, muzzle > 0 ? 1.35f : 1.0f);
	}

	private void drawOverlayTint() {
		if (painFlash > 0) tint(0xFFFF0000, painFlash / 22.0f);
		if (pickupFlash > 0) tint(0xFFFFD060, pickupFlash / 40.0f);
		if (armorFlash > 0) tint(0xFF40FF60, armorFlash / 40.0f);
	}

	private void tint(int color, float k) {
		if (k <= 0) return;
		if (k > 0.55f) k = 0.55f;
		int cr = (color >> 16) & 0xFF, cg = (color >> 8) & 0xFF, cb = color & 0xFF;
		for (int i = 0; i < fb.length; i++) {
			int c = fb[i];
			int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
			fb[i] = Art.rgb((int) (r + (cr - r) * k), (int) (g + (cg - g) * k), (int) (b + (cb - b) * k));
		}
	}

	private void dim(float k, int y0, int y1) {
		for (int y = Math.max(0, y0); y < Math.min(H, y1); y++) {
			for (int x = 0; x < W; x++) {
				fb[y * W + x] = Art.mul(fb[y * W + x], 1 - k);
			}
		}
	}

	private void drawHud() {
		int barY = H - 30;
		// backing bar
		for (int y = barY; y < H; y++) {
			for (int x = 0; x < W; x++) {
				int c = 0xFF23262C;
				if (y == barY) c = 0xFF585F6A;
				if (y == barY + 1) c = 0xFF3A3F48;
				fb[y * W + x] = c;
			}
		}

		int hc = health > 60 ? 0xFF60FF60 : (health > 25 ? 0xFFFFD040 : 0xFFFF4030);
		Fonts.draw(fb, W, H, Lang.get("hud.health"), 8, barY + 5, 0xFF9AA3AE, 1);
		Fonts.draw(fb, W, H, Math.max(0, health) + "%", 8, barY + 15, hc, 2);

		Fonts.draw(fb, W, H, Lang.get("hud.armor"), 78, barY + 5, 0xFF9AA3AE, 1);
		Fonts.draw(fb, W, H, armor + "%", 78, barY + 15, 0xFF60C0FF, 2);

		Fonts.draw(fb, W, H, Lang.get("hud.ammo"), 148, barY + 5, 0xFF9AA3AE, 1);
		Fonts.draw(fb, W, H, String.valueOf(ammo[WP_AMMOTYPE[weapon]]), 148, barY + 15, 0xFFFFD040, 2);

		Fonts.draw(fb, W, H, Lang.get("hud.weapon"), 214, barY + 5, 0xFF9AA3AE, 1);
		for (int i = 0; i < 3; i++) {
			int col = !hasWeapon[i] ? 0xFF4A4F58 : (i == weapon ? 0xFFFFD040 : 0xFF9AA3AE);
			Fonts.draw(fb, W, H, String.valueOf(i + 1), 214 + i * 14, barY + 15, col, 2);
		}

		Fonts.draw(fb, W, H, Lang.get("hud.kills"), 268, barY + 5, 0xFF9AA3AE, 1);
		Fonts.draw(fb, W, H, kills + "/" + totalMonsters, 268, barY + 16, 0xFFDDDDDD, 1);

		// a thin crosshair - the original had none, but nobody was aiming at a wall
		// twenty five blocks away either
		if (state == ST_PLAY) {
			int cx = W / 2;
			int cy = (H - 30) / 2;
			for (int i = 3; i <= 7; i++) {
				fb[cy * W + cx - i] = 0xFF7CE89A;
				fb[cy * W + cx + i] = 0xFF7CE89A;
				fb[(cy - i) * W + cx] = 0xFF7CE89A;
				fb[(cy + i) * W + cx] = 0xFF7CE89A;
			}
		}

		if (messageTimer > 0) {
			Fonts.drawShadow(fb, W, H, Lang.get(message), 6, 6, 0xFFFFE080, 1);
		}
		Fonts.drawShadow(fb, W, H, level.name, W - Fonts.width(level.name, 1) - 6, 6, 0xFF8894A2, 1);
	}

	private void drawTitle() {
		// smouldering background
		for (int y = 0; y < H; y++) {
			for (int x = 0; x < W; x++) {
				double d = Math.hypot(x - W / 2.0, y - H * 0.42) / 150.0;
				int r = (int) (150 * Math.max(0, 1 - d));
				int g = (int) (40 * Math.max(0, 1 - d * 1.5));
				fb[y * W + x] = Art.rgb(20 + r, 8 + g, 10);
			}
		}
		Fonts.drawCentered(fb, W, H, "DOOM", 24, 0xFFFF3A18, 8);
		Fonts.drawCentered(fb, W, H, "MINECRAFT EDITION", 78, 0xFFFFC060, 2);

		Fonts.drawCentered(fb, W, H, Lang.get("t.help1"), 108, 0xFFDDDDDD, 1);
		Fonts.drawCentered(fb, W, H, Lang.get("t.help2"), 120, 0xFFDDDDDD, 1);
		Fonts.drawCentered(fb, W, H, Lang.get("t.help3"), 132, 0xFFDDDDDD, 1);

		int pulse = (int) (Math.abs(Math.sin(tickCount * 0.06)) * 120) + 135;
		Fonts.drawCentered(fb, W, H, Lang.get("t.start"), 162, Art.rgb(pulse, pulse, 60), 2);
	}

	private float shadeFor(double dist) {
		float f = (float) (1.35 - dist * 0.075) * level.bright;
		if (f > 1.0f) f = 1.0f;
		if (f < 0.16f) f = 0.16f;
		return f;
	}

	// ================================================================ records

	private static final class Mob {
		int type;
		double x, y;
		int health;
		double radius;
		boolean dead;
		boolean awake;
		int painTimer;
		int attackCd;
		int attackAnim;
		int frame;
		int seed;
	}

	private static final class Item {
		int type;
		double x, y;
		boolean taken;
	}

	private static final class Shot {
		int id;
		double x, y, dx, dy;
		int damage;
		int life;
		double scale = 0.26;
	}

	private static final class Puff {
		double x, y, z;
		int life;
		boolean blood;

		Puff(double x, double y, double z, boolean blood) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.blood = blood;
		}
	}

	private record Renderable(double x, double y, PixMap art, double scale, boolean grounded, double tint) {
	}

	private static final class MobStats {
		final int health;
		final double speed;
		final double radius;
		final double spriteScale;
		final double attackRange;
		final double keepDistance;
		final int attackCooldown;
		final int damage;
		final boolean melee;
		final boolean projectile;

		MobStats(int health, double speed, double radius, double spriteScale, double attackRange,
				double keepDistance, int attackCooldown, int damage, boolean melee, boolean projectile) {
			this.health = health;
			this.speed = speed;
			this.radius = radius;
			this.spriteScale = spriteScale;
			this.attackRange = attackRange;
			this.keepDistance = keepDistance;
			this.attackCooldown = attackCooldown;
			this.damage = damage;
			this.melee = melee;
			this.projectile = projectile;
		}
	}

	private static final MobStats[] STATS = new MobStats[Art.M_COUNT];

	static {
		//                              hp  speed  rad  sprite range keep  cd  dmg melee proj
		STATS[Art.M_IMP] = new MobStats(60, 0.030, 0.34, 0.80, 11.0, 3.2, 62, 11, false, true);
		STATS[Art.M_ZOMBIE] = new MobStats(30, 0.026, 0.32, 0.74, 12.0, 4.0, 52, 7, false, false);
		STATS[Art.M_DEMON] = new MobStats(95, 0.058, 0.40, 0.82, 1.35, 0.0, 26, 13, true, false);
		STATS[Art.M_BARON] = new MobStats(320, 0.030, 0.48, 1.05, 12.0, 3.0, 46, 22, false, true);
	}
}
