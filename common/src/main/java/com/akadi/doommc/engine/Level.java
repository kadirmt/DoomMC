package com.akadi.doommc.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * The three hand drawn maps, plus the parsed grid the raycaster walks over.
 *
 * Legend:
 *   # brick   T tech    S stone   C computer   B flesh   H hell rock
 *   D door    X exit wall
 *   . floor   E exit trigger      P player start
 *   i imp     z zombie  d demon   b baron
 *   h health  a ammo    r armour  2 shotgun    3 chaingun
 */
public final class Level {

	private static final String[][] MAPS = {
			{ // ---------------------------------------------------- E1M1 HANGAR
					"TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT",
					"T....T.........TTTTTTTTTTTTTTTTT",
					"T.P..T....z....TTTTTTTTTTTTTTTTT",
					"T....T.........TTTTTTTTTTTTTTTTT",
					"T....T....TTTTTTTTTTTTTTT......T",
					"T....D.........a.........D.....T",
					"T....T....TTTTTTTTTTTTTTT......T",
					"T....T.........TTTTTTTTTTTTT...T",
					"T..h.T....z....TTTTTTTTTTTTT...T",
					"TTTTTTTTTT.....TTTTTTTTTTTTT...T",
					"TTTTTTTTTT.....TTTTTTTTTTTTT...T",
					"CCCCCCCCCC..2..TTTTTTTTTTTTT...T",
					"C..............................T",
					"C....i.........i.........i.....T",
					"C..............................T",
					"CCCCCCCCCCCCCCCCCCCCCCCC...CCCCT",
					"TTTTTTTTTTTTTTTTTTTTTTTT...TTTTT",
					"T..............................T",
					"T...r....BBBBBDBBBBB...........T",
					"T........B.........B...........T",
					"T...z....B....E....B......d....T",
					"T........B.........B......h....T",
					"T........BBBBBBBBBBB...........T",
					"TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTT",
			},
			{ // -------------------------------------------------- E1M2 REFINERY
					"SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS",
					"S.....S........S........S......S",
					"S..P..S...z....S...i....S...h..S",
					"S.....S........S........S......S",
					"S.....D........D........D......S",
					"S.....S........S........S......S",
					"S..2..S...z....S...i....S...3..S",
					"S.....S........S........S......S",
					"SSSSSSSSSSSSSSSS....SSSSSSSSSSSS",
					"S.....a...............h........S",
					"S...SS....SS....SS....SS.......S",
					"S...SS....SS....SS....SS.......S",
					"S....d.....d.....d.....d.......S",
					"S...SS....SS....SS....SS.......S",
					"S...SS..r.SS....SS....SS.......S",
					"S.....h...............a........S",
					"SSSSSSSSSSSSSSSSSSSSSSSS...SSSSS",
					"SSSSSSSSSSSSSSSSSSSSTTTT...TTTTS",
					"SSSSSSSSSSSSSSSSSSSST.........TS",
					"SSSSSSSSSSSSSSSSSSSST...b.....TS",
					"SSSSSSSSSSSSSSSSSSSST....E....TS",
					"SSSSSSSSSSSSSSSSSSSST...h.....TS",
					"SSSSSSSSSSSSSSSSSSSSTTTTTTTTTTTS",
					"SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS",
			},
			{ // --------------------------------------------------- E1M3 INFERNO
					"HHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHH",
					"H.............H................H",
					"H...P.........H......i.........H",
					"H.............H................H",
					"H.....HHHHHHHHH......HHH.HHH...H",
					"H.....H..............H.....H...H",
					"H..h..H....d.........H..r..H.2.H",
					"H.....H..............H.....H...H",
					"HHHHH.HHHHHHHHHHHHHHHH.....HHHHH",
					"H..............................H",
					"H..BBBBBBBBBBBBBBBB............H",
					"H..B..............B............H",
					"H..B...b......b...B....a.......H",
					"H..B..............B............H",
					"H..BBBBBBBBBDBBBBBB............H",
					"H......................H....H..H",
					"H...3..................H..E.H..H",
					"H......................H....H..H",
					"HHHHHHHH.HHHHHHH.......HHHDHH..H",
					"H..............H...............H",
					"H....i....i....H....d..........H",
					"H..............H...............H",
					"H..............H...............H",
					"HHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHH",
			},
	};

	private static final String[] NAMES = {"E1M1: HANGAR", "E1M2: REFINERY", "E1M3: INFERNO"};
	private static final int[] FLOORS = {Art.F_TILE, Art.F_TECH, Art.F_HELL};
	private static final int[] CEILS = {Art.F_CEIL, Art.F_DARK, Art.F_BLOOD};
	private static final float[] BRIGHT = {1.0f, 0.92f, 0.85f};

	public static final int COUNT = MAPS.length;

	public final String name;
	public final int w;
	public final int h;
	/** -1 means open floor, anything else is an Art wall texture id. */
	public final int[] wall;
	public final boolean[] door;
	public final float[] doorOpen;
	public final boolean[] exit;
	public final int floorTex;
	public final int ceilTex;
	public final float bright;
	public final double spawnX;
	public final double spawnY;
	/** {monster type, tileX, tileY} */
	public final List<int[]> monsterSpawns = new ArrayList<>();
	/** {item type, tileX, tileY} */
	public final List<int[]> itemSpawns = new ArrayList<>();

	public Level(int index) {
		String[] map = MAPS[index];
		this.name = NAMES[index];
		this.h = map.length;
		this.w = map[0].length();
		this.wall = new int[w * h];
		this.door = new boolean[w * h];
		this.doorOpen = new float[w * h];
		this.exit = new boolean[w * h];
		this.floorTex = FLOORS[index];
		this.ceilTex = CEILS[index];
		this.bright = BRIGHT[index];

		double sx = 1.5, sy = 1.5;

		for (int y = 0; y < h; y++) {
			String row = map[y];
			if (row.length() != w) {
				throw new IllegalStateException("Level " + index + " row " + y + " is "
						+ row.length() + " wide, expected " + w);
			}
			for (int x = 0; x < w; x++) {
				int i = y * w + x;
				char c = row.charAt(x);
				wall[i] = -1;
				switch (c) {
					case '#' -> wall[i] = Art.W_BRICK;
					case 'T' -> wall[i] = Art.W_TECH;
					case 'S' -> wall[i] = Art.W_STONE;
					case 'C' -> wall[i] = Art.W_COMP;
					case 'B' -> wall[i] = Art.W_BLOOD;
					case 'H' -> wall[i] = Art.W_HELL;
					case 'X' -> wall[i] = Art.W_EXIT;
					case 'D' -> {
						wall[i] = Art.W_DOOR;
						door[i] = true;
					}
					case 'P' -> {
						sx = x + 0.5;
						sy = y + 0.5;
					}
					case 'E' -> exit[i] = true;
					case 'i' -> monsterSpawns.add(new int[]{Art.M_IMP, x, y});
					case 'z' -> monsterSpawns.add(new int[]{Art.M_ZOMBIE, x, y});
					case 'd' -> monsterSpawns.add(new int[]{Art.M_DEMON, x, y});
					case 'b' -> monsterSpawns.add(new int[]{Art.M_BARON, x, y});
					case 'h' -> itemSpawns.add(new int[]{Art.I_HEALTH, x, y});
					case 'a' -> itemSpawns.add(new int[]{Art.I_AMMO, x, y});
					case 'r' -> itemSpawns.add(new int[]{Art.I_ARMOR, x, y});
					case '2' -> itemSpawns.add(new int[]{Art.I_SHOTGUN, x, y});
					case '3' -> itemSpawns.add(new int[]{Art.I_CHAINGUN, x, y});
					default -> {
					}
				}
			}
		}
		this.spawnX = sx;
		this.spawnY = sy;
	}

	public boolean inBounds(int x, int y) {
		return x >= 0 && y >= 0 && x < w && y < h;
	}

	public int wallAt(int x, int y) {
		if (!inBounds(x, y)) return Art.W_STONE;
		return wall[y * w + x];
	}

	public boolean isDoor(int x, int y) {
		return inBounds(x, y) && door[y * w + x];
	}

	public float openAt(int x, int y) {
		if (!inBounds(x, y)) return 0f;
		return doorOpen[y * w + x];
	}

	/** True when the tile stops both bullets and bodies. */
	public boolean solid(int x, int y) {
		if (!inBounds(x, y)) return true;
		int i = y * w + x;
		if (wall[i] < 0) return false;
		if (door[i]) return doorOpen[i] < 0.9f;
		return true;
	}

	/** True when the tile stops the renderer's rays. Fully open doors are see-through. */
	public boolean blocksSight(int x, int y) {
		if (!inBounds(x, y)) return true;
		int i = y * w + x;
		if (wall[i] < 0) return false;
		if (door[i]) return doorOpen[i] < 1.0f;
		return true;
	}

	public boolean isExit(int x, int y) {
		return inBounds(x, y) && exit[y * w + x];
	}

	public void tickDoors(java.util.Set<Integer> opening) {
		for (int i = 0; i < doorOpen.length; i++) {
			if (!door[i]) continue;
			if (opening.contains(i)) {
				doorOpen[i] = Math.min(1f, doorOpen[i] + 0.07f);
			} else if (doorOpen[i] > 0f) {
				doorOpen[i] = Math.max(0f, doorOpen[i] - 0.04f);
			}
		}
	}
}
