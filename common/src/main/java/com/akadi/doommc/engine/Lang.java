package com.akadi.doommc.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * In-engine string table. The simulation stores KEYS (they travel in snapshots),
 * and each client resolves them against its own Minecraft language at draw time -
 * so two players watching the same wall can read it in different languages.
 *
 * <p>The bitmap font is plain A-Z, so every language is written accent-free,
 * exactly the way 1993 would have done it.
 */
public final class Lang {
	private static final int TR = 0;
	private static final int EN = 1;
	private static final int DE = 2;
	private static final int ES = 3;

	private static volatile int current = EN;

	private static final Map<String, String[]> T = new HashMap<>();

	private static void put(String key, String tr, String en, String de, String es) {
		T.put(key, new String[]{tr, en, de, es});
	}

	static {
		// HUD labels
		put("hud.health", "SAGLIK", "HEALTH", "LEBEN", "SALUD");
		put("hud.armor", "ZIRH", "ARMOR", "PANZER", "ARMADURA");
		put("hud.ammo", "MERMI", "AMMO", "MUNITION", "BALAS");
		put("hud.weapon", "SILAH", "WEAPON", "WAFFE", "ARMA");
		put("hud.kills", "OLDU", "KILLS", "KILLS", "BAJAS");

		// weapons
		put("wp.0", "TABANCA", "PISTOL", "PISTOLE", "PISTOLA");
		put("wp.1", "POMPALI", "SHOTGUN", "SCHROTFLINTE", "ESCOPETA");
		put("wp.2", "MAKINELI", "CHAINGUN", "MINIGUN", "AMETRALLADORA");

		// pickup and status messages
		put("msg.start", "SICAK ETTEN BASLAYALIM", "RIP AND TEAR", "AUF IN DEN KAMPF", "A POR ELLOS");
		put("msg.died", "OLDUN", "YOU DIED", "DU BIST TOT", "HAS MUERTO");
		put("msg.noammo", "MERMI YOK", "OUT OF AMMO", "KEINE MUNITION", "SIN BALAS");
		put("msg.nodoor", "BURADA ACILACAK BIRSEY YOK", "NOTHING TO OPEN HERE",
				"HIER IST NICHTS ZU OEFFNEN", "NADA QUE ABRIR AQUI");
		put("msg.health", "SAGLIK PAKETI", "MEDIKIT", "MEDIKIT", "BOTIQUIN");
		put("msg.ammo", "CEPHANE", "AMMO", "MUNITION", "MUNICION");
		put("msg.armor", "ZIRH", "ARMOR", "PANZERUNG", "ARMADURA");
		put("msg.shotgun", "POMPALI TUFEK!", "YOU GOT THE SHOTGUN!", "SCHROTFLINTE!", "ESCOPETA!");
		put("msg.chaingun", "MAKINELI!", "YOU GOT THE CHAINGUN!", "MINIGUN!", "AMETRALLADORA!");

		// full screen overlays
		put("ov.dead", "OLDUN", "YOU DIED", "TOT", "MUERTO");
		put("ov.retry", "ENTER = TEKRAR DENE", "ENTER = TRY AGAIN", "ENTER = NOCHMAL", "ENTER = REINTENTAR");
		put("ov.leveldone", "BOLUM TAMAM", "LEVEL COMPLETE", "LEVEL GESCHAFFT", "NIVEL COMPLETADO");
		put("ov.killed", "OLDURULEN", "KILLS", "GETOETET", "BAJAS");
		put("ov.continue", "ENTER = DEVAM", "ENTER = CONTINUE", "ENTER = WEITER", "ENTER = SEGUIR");
		put("ov.win1", "CEHENNEM TEMIZ", "HELL IS CLEANSED", "HOELLE GESAEUBERT", "INFIERNO LIMPIO");
		put("ov.win2", "TEBRIKLER", "CONGRATULATIONS", "GLUECKWUNSCH", "FELICIDADES");
		put("ov.win3", "ENTER = BASTAN BASLA", "ENTER = PLAY AGAIN", "ENTER = NEUSTART", "ENTER = OTRA VEZ");
		put("ov.paused", "DURAKLATILDI", "PAUSED", "PAUSIERT", "PAUSADO");
		put("ov.paused2", "KUMANDAYLA SAG TIK = DEVAM", "RIGHT CLICK REMOTE = RESUME",
				"RECHTSKLICK = WEITER", "CLIC DERECHO = SEGUIR");

		// title screen
		put("t.help1", "FARE ILE NISAN AL    W/S ILERI GERI", "MOUSE TO AIM    W/S MOVE",
				"MAUS ZIELEN    W/S LAUFEN", "RATON APUNTA    W/S MUEVE");
		put("t.help2", "A/D YAN ADIM    SOL TIK ATES    SPACE KAPI", "A/D STRAFE    CLICK FIRE    SPACE DOOR",
				"A/D SEITWAERTS    KLICK FEUER    SPACE TUER", "A/D LADO    CLIC DISPARA    SPACE PUERTA");
		put("t.help3", "SHIFT KOS    1 2 3 SILAH    F10 CIKIS", "SHIFT RUN    1 2 3 WEAPON    F10 QUIT",
				"SHIFT RENNEN    1 2 3 WAFFE    F10 ENDE", "SHIFT CORRE    1 2 3 ARMA    F10 SALIR");
		put("t.start", "ENTER = BASLA", "ENTER = START", "ENTER = START", "ENTER = EMPEZAR");
	}

	private Lang() {
	}

	/** Feed it a Minecraft language code like "tr_tr" or "de_at"; unknown falls back to English. */
	public static void set(String mcCode) {
		if (mcCode == null) {
			current = EN;
			return;
		}
		String c = mcCode.toLowerCase();
		if (c.startsWith("tr")) current = TR;
		else if (c.startsWith("de")) current = DE;
		else if (c.startsWith("es")) current = ES;
		else current = EN;
	}

	/** Translate a key; anything unknown (like level names) passes through untouched. */
	public static String get(String key) {
		String[] row = T.get(key);
		return row == null ? key : row[current];
	}
}
