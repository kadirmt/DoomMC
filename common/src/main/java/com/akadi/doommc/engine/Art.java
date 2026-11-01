package com.akadi.doommc.engine;

/**
 * Every texture and sprite in the game is generated here at load time.
 * That keeps the mod jar free of any id Software assets and means there is
 * nothing to download or install - the engine is entirely self contained.
 */
public final class Art {
	public static final int TEX = 64;

	// wall texture ids
	public static final int W_BRICK = 0;
	public static final int W_TECH = 1;
	public static final int W_BLOOD = 2;
	public static final int W_DOOR = 3;
	public static final int W_EXIT = 4;
	public static final int W_STONE = 5;
	public static final int W_COMP = 6;
	public static final int W_HELL = 7;
	public static final int W_COUNT = 8;

	// flat (floor / ceiling) ids
	public static final int F_TILE = 0;
	public static final int F_TECH = 1;
	public static final int F_CEIL = 2;
	public static final int F_BLOOD = 3;
	public static final int F_DARK = 4;
	public static final int F_HELL = 5;
	public static final int F_COUNT = 6;

	// monster types
	public static final int M_IMP = 0;
	public static final int M_ZOMBIE = 1;
	public static final int M_DEMON = 2;
	public static final int M_BARON = 3;
	public static final int M_COUNT = 4;

	// monster frames
	public static final int FR_WALK_A = 0;
	public static final int FR_WALK_B = 1;
	public static final int FR_ATTACK = 2;
	public static final int FR_PAIN = 3;
	public static final int FR_DEAD = 4;
	public static final int FR_COUNT = 5;

	// pickups
	public static final int I_HEALTH = 0;
	public static final int I_AMMO = 1;
	public static final int I_ARMOR = 2;
	public static final int I_SHOTGUN = 3;
	public static final int I_CHAINGUN = 4;
	public static final int I_COUNT = 5;

	public static int[][] walls;
	public static int[][] flats;
	public static PixMap[][] mobs;
	public static PixMap[] items;
	public static PixMap[][] guns;
	public static PixMap fireball;
	public static PixMap[] puffs;

	private static boolean ready;

	private Art() {
	}

	public static synchronized void init() {
		if (ready) return;
		ready = true;

		walls = new int[W_COUNT][];
		walls[W_BRICK] = brick();
		walls[W_TECH] = tech();
		walls[W_BLOOD] = flesh();
		walls[W_DOOR] = door();
		walls[W_EXIT] = exitWall();
		walls[W_STONE] = stone();
		walls[W_COMP] = computer();
		walls[W_HELL] = hellRock();

		flats = new int[F_COUNT][];
		flats[F_TILE] = tiles(0xFF6E6E62, 0xFF565650);
		flats[F_TECH] = techFloor();
		flats[F_CEIL] = noiseFlat(0xFF4A4A4E, 14, 991);
		flats[F_BLOOD] = noiseFlat(0xFF5C1212, 26, 7717);
		flats[F_DARK] = noiseFlat(0xFF1E1E24, 8, 331);
		flats[F_HELL] = noiseFlat(0xFF4C2018, 30, 6151);

		mobs = new PixMap[M_COUNT][FR_COUNT];
		for (int t = 0; t < M_COUNT; t++) {
			for (int f = 0; f < FR_COUNT; f++) {
				mobs[t][f] = monster(t, f);
			}
		}

		items = new PixMap[I_COUNT];
		items[I_HEALTH] = healthKit();
		items[I_AMMO] = ammoBox();
		items[I_ARMOR] = armorVest();
		items[I_SHOTGUN] = gunPickup(0xFF8A6A3A, 0xFF3A3A42);
		items[I_CHAINGUN] = gunPickup(0xFF5A5A62, 0xFF2A2A30);

		// drawn big for detail, then shrunk so the fist does not hide half the room
		double gunScale = 0.62;
		guns = new PixMap[3][2];
		guns[0][0] = pistol(false).scaled(gunScale);
		guns[0][1] = pistol(true).scaled(gunScale);
		guns[1][0] = shotgun(false).scaled(gunScale);
		guns[1][1] = shotgun(true).scaled(gunScale);
		guns[2][0] = chaingun(false).scaled(gunScale);
		guns[2][1] = chaingun(true).scaled(gunScale);

		fireball = fireballSprite();
		puffs = new PixMap[3];
		for (int i = 0; i < 3; i++) puffs[i] = puff(i);
	}

	// ---------------------------------------------------------------- helpers

	/** Multiply the rgb channels of an ARGB colour, preserving alpha. */
	public static int mul(int argb, float f) {
		int a = argb & 0xFF000000;
		int r = Math.min(255, (int) (((argb >> 16) & 0xFF) * f));
		int g = Math.min(255, (int) (((argb >> 8) & 0xFF) * f));
		int b = Math.min(255, (int) ((argb & 0xFF) * f));
		return a | (r << 16) | (g << 8) | b;
	}

	public static int rgb(int r, int g, int b) {
		return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static int clamp(int v) {
		return v < 0 ? 0 : (v > 255 ? 255 : v);
	}

	/** Deterministic little noise source so textures are identical every launch. */
	private static final class Rnd {
		private int s;

		Rnd(int seed) {
			this.s = seed | 1;
		}

		int next() {
			s ^= s << 13;
			s ^= s >>> 17;
			s ^= s << 5;
			return s;
		}

		/** Uniform in [-n, n]. */
		int jitter(int n) {
			return (next() & 0x7FFFFFFF) % (2 * n + 1) - n;
		}

		int range(int n) {
			return (next() & 0x7FFFFFFF) % n;
		}
	}

	private static int[] blank(int base) {
		int[] t = new int[TEX * TEX];
		java.util.Arrays.fill(t, base);
		return t;
	}

	private static void speckle(int[] t, Rnd r, int amount) {
		for (int i = 0; i < t.length; i++) {
			int j = r.jitter(amount);
			t[i] = rgb(((t[i] >> 16) & 0xFF) + j, ((t[i] >> 8) & 0xFF) + j, (t[i] & 0xFF) + j);
		}
	}

	// -------------------------------------------------------------- wall art

	private static int[] brick() {
		int[] t = blank(0xFF2E2A28);
		Rnd r = new Rnd(1337);
		int bh = 16, bw = 32;
		for (int y = 0; y < TEX; y++) {
			int row = y / bh;
			int off = (row % 2 == 0) ? 0 : bw / 2;
			for (int x = 0; x < TEX; x++) {
				int lx = Math.floorMod(x + off, bw);
				int ly = y % bh;
				if (lx < 2 || ly < 2) continue; // mortar
				int shade = r.jitter(16);
				int base = 0x8C2E1E;
				int rr = ((base >> 16) & 0xFF) + shade;
				int gg = ((base >> 8) & 0xFF) + shade;
				int bb = (base & 0xFF) + shade;
				// a touch of top lighting on each brick
				if (ly < 4) { rr += 18; gg += 12; bb += 10; }
				if (ly > bh - 4) { rr -= 18; gg -= 12; bb -= 10; }
				t[y * TEX + x] = rgb(rr, gg, bb);
			}
		}
		return t;
	}

	private static int[] tech() {
		int[] t = blank(0xFF5A606A);
		Rnd r = new Rnd(4242);
		speckle(t, r, 8);
		for (int y = 0; y < TEX; y++) {
			for (int x = 0; x < TEX; x++) {
				int i = y * TEX + x;
				// panel seams
				if (x % 32 == 0 || y % 32 == 0) t[i] = mul(t[i], 0.45f);
				if (x % 32 == 31 || y % 32 == 31) t[i] = mul(t[i], 0.7f);
				// rust band across the middle
				if (y >= 28 && y <= 36) {
					int d = Math.abs(y - 32);
					t[i] = blend(t[i], 0xFF7A4A22, 1.0f - d / 5.0f);
				}
			}
		}
		// rivets
		for (int py = 6; py < TEX; py += 32) {
			for (int px = 6; px < TEX; px += 32) {
				dot(t, px, py, 2, 0xFF9AA0AA);
				dot(t, px + 20, py, 2, 0xFF9AA0AA);
				dot(t, px, py + 20, 2, 0xFF9AA0AA);
				dot(t, px + 20, py + 20, 2, 0xFF9AA0AA);
			}
		}
		return t;
	}

	private static int[] flesh() {
		int[] t = blank(0xFF7E1A18);
		Rnd r = new Rnd(90210);
		speckle(t, r, 22);
		// veins
		for (int v = 0; v < 14; v++) {
			int x = r.range(TEX), y = r.range(TEX);
			int len = 10 + r.range(26);
			int dx = r.jitter(1), dy = 1;
			for (int i = 0; i < len; i++) {
				x = Math.floorMod(x + dx + r.jitter(1), TEX);
				y = Math.floorMod(y + dy, TEX);
				t[y * TEX + x] = 0xFF4A0A0A;
				t[y * TEX + Math.floorMod(x + 1, TEX)] = 0xFF5C0E0E;
			}
		}
		// pulsing lumps
		for (int i = 0; i < 10; i++) {
			int cx = r.range(TEX), cy = r.range(TEX), rad = 3 + r.range(5);
			for (int y = -rad; y <= rad; y++) {
				for (int x = -rad; x <= rad; x++) {
					if (x * x + y * y > rad * rad) continue;
					int px = Math.floorMod(cx + x, TEX), py = Math.floorMod(cy + y, TEX);
					float k = 1.0f - (float) Math.sqrt(x * x + y * y) / rad;
					t[py * TEX + px] = blend(t[py * TEX + px], 0xFFA83030, k * 0.8f);
				}
			}
		}
		return t;
	}

	private static int[] door() {
		int[] t = blank(0xFF6A7078);
		Rnd r = new Rnd(555);
		speckle(t, r, 6);
		for (int y = 0; y < TEX; y++) {
			for (int x = 0; x < TEX; x++) {
				int i = y * TEX + x;
				// central seam - it reads as a two leaf door
				if (x == 31 || x == 32) t[i] = 0xFF2A2E33;
				// hazard stripes top and bottom
				if (y < 10 || y >= TEX - 10) {
					boolean yellow = ((x + y) / 6) % 2 == 0;
					t[i] = yellow ? 0xFFC8A82A : 0xFF1E1E22;
				}
				if (y == 10 || y == TEX - 11) t[i] = 0xFF33383E;
			}
		}
		// vertical ribs
		for (int x = 8; x < TEX; x += 16) {
			for (int y = 13; y < TEX - 13; y++) {
				t[y * TEX + x] = mul(t[y * TEX + x], 0.6f);
				t[y * TEX + x + 1] = mul(t[y * TEX + x + 1], 1.25f);
			}
		}
		return t;
	}

	private static int[] exitWall() {
		int[] t = blank(0xFF2A3A2E);
		Rnd r = new Rnd(31337);
		speckle(t, r, 8);
		// glowing green panel
		for (int y = 14; y < 50; y++) {
			for (int x = 10; x < 54; x++) {
				float k = 1.0f - Math.max(Math.abs(y - 32) / 18.0f, Math.abs(x - 32) / 22.0f);
				t[y * TEX + x] = blend(0xFF1E5A2E, 0xFF6BFF8A, k);
			}
		}
		for (int x = 8; x < 56; x++) {
			t[12 * TEX + x] = 0xFF9AFFB0;
			t[51 * TEX + x] = 0xFF9AFFB0;
		}
		return t;
	}

	private static int[] stone() {
		int[] t = blank(0xFF6E6E6E);
		Rnd r = new Rnd(24680);
		speckle(t, r, 20);
		for (int i = 0; i < 22; i++) {
			int x = r.range(TEX), y = r.range(TEX);
			int len = 8 + r.range(20);
			for (int k = 0; k < len; k++) {
				x = Math.floorMod(x + r.jitter(1), TEX);
				y = Math.floorMod(y + 1, TEX);
				t[y * TEX + x] = mul(t[y * TEX + x], 0.55f);
			}
		}
		// block seams
		for (int x = 0; x < TEX; x++) t[31 * TEX + x] = mul(t[31 * TEX + x], 0.5f);
		for (int y = 0; y < 32; y++) t[y * TEX + 15] = mul(t[y * TEX + 15], 0.5f);
		for (int y = 32; y < TEX; y++) t[y * TEX + 47] = mul(t[y * TEX + 47], 0.5f);
		return t;
	}

	private static int[] computer() {
		int[] t = blank(0xFF2A2E38);
		Rnd r = new Rnd(1010);
		speckle(t, r, 6);
		int[] lamp = {0xFFFF4040, 0xFF40FF60, 0xFF4080FF, 0xFFFFC040};
		for (int by = 4; by < TEX - 8; by += 14) {
			for (int bx = 4; bx < TEX - 6; bx += 10) {
				// screen bezel
				for (int y = by; y < by + 10; y++) {
					for (int x = bx; x < bx + 8; x++) {
						t[y * TEX + x] = 0xFF14161C;
					}
				}
				int c = lamp[r.range(lamp.length)];
				for (int k = 0; k < 3; k++) {
					int ly = by + 2 + k * 2;
					int w = 2 + r.range(5);
					for (int x = bx + 1; x < bx + 1 + w; x++) {
						t[ly * TEX + x] = c;
					}
				}
			}
		}
		return t;
	}

	private static int[] hellRock() {
		int[] t = blank(0xFF52201A);
		Rnd r = new Rnd(66613);
		speckle(t, r, 26);
		for (int i = 0; i < 30; i++) {
			int x = r.range(TEX), y = r.range(TEX);
			int len = 6 + r.range(18);
			for (int k = 0; k < len; k++) {
				x = Math.floorMod(x + r.jitter(1), TEX);
				y = Math.floorMod(y + 1, TEX);
				t[y * TEX + x] = 0xFF2A0E0A;
			}
		}
		// a few embers
		for (int i = 0; i < 18; i++) {
			int x = r.range(TEX), y = r.range(TEX);
			dot(t, x, y, 1, 0xFFD8642A);
		}
		return t;
	}

	private static int[] tiles(int a, int b) {
		int[] t = new int[TEX * TEX];
		Rnd r = new Rnd(778);
		for (int y = 0; y < TEX; y++) {
			for (int x = 0; x < TEX; x++) {
				boolean alt = ((x / 32) + (y / 32)) % 2 == 0;
				int c = alt ? a : b;
				if (x % 32 < 2 || y % 32 < 2) c = mul(c, 0.6f);
				int j = r.jitter(7);
				t[y * TEX + x] = rgb(((c >> 16) & 0xFF) + j, ((c >> 8) & 0xFF) + j, (c & 0xFF) + j);
			}
		}
		return t;
	}

	private static int[] techFloor() {
		int[] t = blank(0xFF3A4048);
		Rnd r = new Rnd(9182);
		speckle(t, r, 8);
		for (int y = 0; y < TEX; y++) {
			for (int x = 0; x < TEX; x++) {
				if (x % 16 == 0 || y % 16 == 0) t[y * TEX + x] = 0xFF262C33;
			}
		}
		for (int py = 8; py < TEX; py += 16) {
			for (int px = 8; px < TEX; px += 16) {
				dot(t, px, py, 2, 0xFF555D68);
			}
		}
		return t;
	}

	private static int[] noiseFlat(int base, int amount, int seed) {
		int[] t = blank(base);
		speckle(t, new Rnd(seed), amount);
		return t;
	}

	private static void dot(int[] t, int cx, int cy, int r, int c) {
		for (int y = -r; y <= r; y++) {
			for (int x = -r; x <= r; x++) {
				if (x * x + y * y > r * r) continue;
				t[Math.floorMod(cy + y, TEX) * TEX + Math.floorMod(cx + x, TEX)] = c;
			}
		}
	}

	private static int blend(int a, int b, float k) {
		if (k <= 0) return a;
		if (k >= 1) return b;
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return rgb((int) (ar + (br - ar) * k), (int) (ag + (bg - ag) * k), (int) (ab + (bb - ab) * k));
	}

	// ------------------------------------------------------------ monsters

	private static PixMap monster(int type, int frame) {
		PixMap p = new PixMap(64, 64);
		p.clear(0);

		int body, dark, skin, eye, horn;
		double bw, bh, hr, cy;
		boolean horns, quad;
		switch (type) {
			case M_ZOMBIE -> {
				body = 0xFF4E5B38; dark = 0xFF333D24; skin = 0xFFB8A184; eye = 0xFFFF5030;
				horn = 0xFF6B6B5A; bw = 11; bh = 13; hr = 7.5; cy = 40; horns = false; quad = false;
			}
			case M_DEMON -> {
				body = 0xFFC0706E; dark = 0xFF8A4644; skin = 0xFFDE9694; eye = 0xFFFF2020;
				horn = 0xFFE8DCC0; bw = 17; bh = 12; hr = 10; cy = 42; horns = false; quad = true;
			}
			case M_BARON -> {
				body = 0xFF9A4030; dark = 0xFF5E2118; skin = 0xFF7C8A3C; eye = 0xFF60FF60;
				horn = 0xFFE8DCC0; bw = 16; bh = 17; hr = 10; cy = 36; horns = true; quad = false;
			}
			default -> { // imp
				body = 0xFF8B5A2B; dark = 0xFF5C3A1A; skin = 0xFFA8703A; eye = 0xFFFFE040;
				horn = 0xFFD8CBA8; bw = 12; bh = 14; hr = 8; cy = 40; horns = true; quad = false;
			}
		}

		if (frame == FR_DEAD) {
			// a low mound of gore where the thing used to be
			p.fillEllipse(32, 58, bw + 7, 5, dark);
			p.fillEllipse(32, 56, bw + 2, 4, body);
			p.fillEllipse(24, 59, 7, 3, 0xFF6A0E0E);
			p.fillEllipse(41, 60, 8, 3, 0xFF7A1212);
			p.fillEllipse(32, 61, bw + 11, 3, 0xFF5A0A0A);
			return p;
		}

		boolean pain = frame == FR_PAIN;
		boolean attack = frame == FR_ATTACK;
		int step = (frame == FR_WALK_B) ? 1 : -1;
		int bodyC = pain ? blend(body, 0xFFFF6060, 0.45f) : body;
		int darkC = pain ? blend(dark, 0xFFFF4040, 0.35f) : dark;

		double headY = cy - bh - hr + 4;

		// legs
		if (quad) {
			p.fillRect(20, (int) (cy + bh) - 2, 6, 12 + step, darkC);
			p.fillRect(38, (int) (cy + bh) - 2, 6, 12 - step, darkC);
			p.fillRect(27, (int) (cy + bh) - 2, 5, 12 - step, darkC);
			p.fillRect(33, (int) (cy + bh) - 2, 5, 12 + step, darkC);
		} else {
			p.fillRect(25, (int) (cy + bh) - 3, 6, 14 + step * 2, darkC);
			p.fillRect(34, (int) (cy + bh) - 3, 6, 14 - step * 2, darkC);
			p.fillRect(24, 60 + step, 8, 3, 0xFF2A2018);
			p.fillRect(33, 60 - step, 8, 3, 0xFF2A2018);
		}

		// torso
		p.shadedEllipse(32, cy, bw, bh, bodyC);

		// arms
		int armY = (int) (cy - bh * 0.4);
		int reach = attack ? 10 : 0;
		if (quad) {
			p.fillEllipse(32 - bw - 2, cy + 2, 5, 5, darkC);
			p.fillEllipse(32 + bw + 2, cy + 2, 5, 5, darkC);
		} else {
			p.fillRect((int) (32 - bw - 4), armY - reach / 2, 5, 16 - reach, darkC);
			p.fillRect((int) (32 + bw - 1), armY - reach / 2, 5, 16 - reach, darkC);
			// claws / hands
			p.fillEllipse(32 - bw - 2, armY + 16 - reach, 3.5, 3.5, skin);
			p.fillEllipse(32 + bw + 1, armY + 16 - reach, 3.5, 3.5, skin);
		}

		// head
		p.shadedEllipse(32, headY, hr, hr * 0.9, pain ? blend(skin, 0xFFFF7070, 0.4f) : skin);

		if (horns) {
			p.fillTri((int) (32 - hr + 1), (int) (headY - hr * 0.4), (int) (32 - hr - 6),
					(int) (headY - hr * 1.7), (int) (32 - hr + 4), (int) (headY - hr * 0.9), horn);
			p.fillTri((int) (32 + hr - 1), (int) (headY - hr * 0.4), (int) (32 + hr + 6),
					(int) (headY - hr * 1.7), (int) (32 + hr - 4), (int) (headY - hr * 0.9), horn);
		}

		if (type == M_ZOMBIE) {
			// helmet
			p.fillEllipse(32, headY - 2, hr + 1, hr * 0.6, 0xFF5F6650);
			p.fillRect((int) (32 - hr - 1), (int) (headY - 2), (int) (hr * 2 + 2), 2, 0xFF474D3C);
		}

		// eyes - always glowing, they are the readable bit at distance
		double ey = headY - hr * 0.1;
		p.fillEllipse(32 - hr * 0.42, ey, 2.2, 1.8, eye);
		p.fillEllipse(32 + hr * 0.42, ey, 2.2, 1.8, eye);

		// mouth
		if (quad) {
			// pinky gets a huge grin full of teeth
			p.fillEllipse(32, headY + hr * 0.45, hr * 0.8, hr * 0.35, 0xFF3A0A0A);
			for (int i = -3; i <= 3; i++) {
				p.fillRect((int) (32 + i * 2.4), (int) (headY + hr * 0.2), 2, 3, 0xFFF0EAD8);
				p.fillRect((int) (32 + i * 2.4), (int) (headY + hr * 0.6), 2, 3, 0xFFF0EAD8);
			}
		} else if (attack) {
			p.fillEllipse(32, headY + hr * 0.5, 3.5, 2.5, 0xFF2A0606);
		} else {
			p.fillRect((int) (32 - hr * 0.4), (int) (headY + hr * 0.5), (int) (hr * 0.8), 1, 0xFF2A0606);
		}

		return p;
	}

	// ------------------------------------------------------------- pickups

	private static PixMap healthKit() {
		PixMap p = new PixMap(32, 32);
		p.clear(0);
		p.fillRect(4, 8, 24, 18, 0xFFE8E8E0);
		p.rectOutline(4, 8, 24, 18, 0xFF9A9A92);
		p.fillRect(14, 11, 4, 12, 0xFFD01818);
		p.fillRect(9, 15, 14, 4, 0xFFD01818);
		p.fillRect(4, 6, 24, 3, 0xFFB8B8B0);
		return p;
	}

	private static PixMap ammoBox() {
		PixMap p = new PixMap(32, 32);
		p.clear(0);
		p.fillRect(5, 13, 22, 13, 0xFF6A5A2A);
		p.rectOutline(5, 13, 22, 13, 0xFF483C1A);
		p.fillRect(5, 10, 22, 4, 0xFF7C6A34);
		for (int i = 0; i < 4; i++) {
			p.fillRect(8 + i * 4, 6, 3, 6, 0xFFD8B040);
			p.fillRect(8 + i * 4, 6, 3, 2, 0xFFA8262A);
		}
		return p;
	}

	private static PixMap armorVest() {
		PixMap p = new PixMap(32, 32);
		p.clear(0);
		p.fillTri(16, 4, 4, 12, 28, 12, 0xFF2AA84A);
		p.fillRect(5, 12, 22, 10, 0xFF2AA84A);
		p.fillTri(5, 22, 27, 22, 16, 29, 0xFF2AA84A);
		p.fillRect(14, 8, 4, 16, 0xFF1A7A34);
		p.rectOutline(5, 12, 22, 10, 0xFF7CE89A);
		return p;
	}

	private static PixMap gunPickup(int a, int b) {
		PixMap p = new PixMap(32, 32);
		p.clear(0);
		p.fillRect(3, 14, 26, 5, b);
		p.fillRect(6, 19, 10, 4, a);
		p.fillRect(12, 19, 4, 8, b);
		p.fillRect(20, 12, 8, 3, b);
		return p;
	}

	// ------------------------------------------------------ first person guns

	private static final int SKIN = 0xFFC69A70;
	private static final int SKIN_D = 0xFF9A7250;
	private static final int SKIN_L = 0xFFE0B487;

	/** The knuckle grooves that make a flat rectangle read as a fist. */
	private static void fist(PixMap p, int x, int y, int w, int h) {
		p.fillRect(x, y, w, h, SKIN);
		p.fillRect(x, y, w, 5, SKIN_L);
		int fingers = 4;
		int fw = w / fingers;
		for (int i = 1; i < fingers; i++) {
			p.fillRect(x + i * fw - 2, y + 6, 3, h - 6, SKIN_D);
		}
		p.fillRect(x, y + h - 4, w, 4, SKIN_D);
	}

	private static void flash(PixMap p, int cx, int cy, double r) {
		p.fillEllipse(cx, cy, r * 1.55, r, 0xFFC0400A);
		p.fillEllipse(cx, cy, r * 1.25, r * 0.8, 0xFFFF7A18);
		p.fillEllipse(cx, cy, r * 0.85, r * 0.55, 0xFFFFC844);
		p.fillEllipse(cx, cy, r * 0.45, r * 0.3, 0xFFFFF6D0);
	}

	private static PixMap pistol(boolean firing) {
		PixMap p = new PixMap(150, 122);
		p.clear(0);
		int metal = 0xFF4A4E56, metalL = 0xFF6A7078, dk = 0xFF23262B;

		p.fillRect(66, 6, 20, 22, dk);          // barrel
		p.fillRect(70, 6, 12, 5, 0xFF101216);   // bore
		p.fillRect(52, 26, 46, 32, metal);      // slide
		p.fillRect(52, 26, 46, 6, metalL);
		p.fillRect(52, 52, 46, 6, dk);
		p.fillRect(46, 56, 58, 22, dk);         // frame + trigger guard
		p.fillRect(56, 66, 36, 10, 0xFF15171A);
		fist(p, 50, 72, 50, 50);                // hand wrapped round the grip
		p.fillRect(96, 78, 16, 30, SKIN_D);     // thumb
		if (firing) flash(p, 76, 4, 22);
		return p;
	}

	private static PixMap shotgun(boolean firing) {
		PixMap p = new PixMap(182, 130);
		p.clear(0);
		int barrel = 0xFF3E434B, barrelL = 0xFF5C626C, wood = 0xFF7A5228, woodD = 0xFF4E3216;

		p.fillRect(76, 0, 32, 72, barrel);      // barrel
		p.fillRect(76, 0, 11, 72, barrelL);
		p.fillRect(82, 0, 20, 6, 0xFF101216);   // muzzle
		p.fillRect(70, 56, 50, 40, woodD);      // receiver
		p.fillRect(70, 56, 50, 8, 0xFF625C50);
		p.fillRect(58, 62, 68, 20, woodD);      // pump
		p.fillRect(58, 66, 68, 12, wood);
		fist(p, 46, 78, 54, 52);                // forward hand on the pump
		fist(p, 104, 90, 50, 40);               // trigger hand
		if (firing) flash(p, 92, 0, 30);
		return p;
	}

	private static PixMap chaingun(boolean firing) {
		PixMap p = new PixMap(200, 140);
		p.clear(0);
		int metal = 0xFF5C626C, dk = 0xFF2E323A, housing = 0xFF3A3F47;

		for (int i = 0; i < 4; i++) {
			int bx = 56 + i * 24;
			int off = (i % 2) * 5;
			p.fillRect(bx, off, 19, 74 - off, i % 2 == 0 ? metal : dk);
			p.fillRect(bx, off, 6, 74 - off, i % 2 == 0 ? 0xFF787E88 : 0xFF444952);
			p.fillRect(bx + 3, off, 12, 5, 0xFF101216);
		}
		p.fillRect(44, 70, 116, 34, housing);   // housing
		p.fillRect(44, 70, 116, 8, metal);
		p.fillRect(44, 98, 116, 6, dk);
		p.fillRect(150, 76, 24, 22, dk);        // ammo feed
		fist(p, 56, 100, 46, 40);
		fist(p, 108, 100, 46, 40);
		if (firing) {
			for (int i = 0; i < 4; i++) flash(p, 65 + i * 24, (i % 2) * 5, 16);
		}
		return p;
	}

	// ---------------------------------------------------------- projectiles

	private static PixMap fireballSprite() {
		PixMap p = new PixMap(24, 24);
		p.clear(0);
		for (int y = 0; y < 24; y++) {
			for (int x = 0; x < 24; x++) {
				double d = Math.sqrt((x - 12) * (x - 12) + (y - 12) * (y - 12)) / 11.0;
				if (d > 1) continue;
				int c;
				if (d < 0.35) c = 0xFFFFF6D0;
				else if (d < 0.6) c = 0xFFFFC844;
				else if (d < 0.82) c = 0xFFFF7A18;
				else c = 0xFFC03A08;
				p.set(x, y, c);
			}
		}
		return p;
	}

	private static PixMap puff(int stage) {
		PixMap p = new PixMap(20, 20);
		p.clear(0);
		double r = 4 + stage * 4;
		int c = switch (stage) {
			case 0 -> 0xFFDCDCDC;
			case 1 -> 0xFFA0A0A0;
			default -> 0xFF6A6A6A;
		};
		p.fillEllipse(10, 10, r, r * 0.85, c);
		p.fillEllipse(10 - r * 0.4, 10 - r * 0.3, r * 0.5, r * 0.45, mul(c, 1.15f));
		return p;
	}
}
