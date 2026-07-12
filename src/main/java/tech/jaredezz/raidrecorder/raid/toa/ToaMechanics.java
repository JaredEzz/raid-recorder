package tech.jaredezz.raidrecorder.raid.toa;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.HitsplatID;
import net.runelite.api.gameval.NpcID;
import tech.jaredezz.raidrecorder.capture.UnverifiedRegistry;
import tech.jaredezz.raidrecorder.raid.MechanicContext;
import tech.jaredezz.raidrecorder.raid.MechanicTag;

/**
 * The ToA mechanic taxonomy: names every identifiable damage source and whether it's dodgeable.
 *
 * <p>NPC ids come from {@code net.runelite.api.gameval.NpcID} (compile-checked). GraphicsObject /
 * Projectile / animation ids have no gameval constants; every raw value below is sourced from the
 * ToaMistakeTracker plugin (QuestingPet, files cited inline), which ships on the Plugin Hub —
 * community-verified, but not compiler-verified, so each is registered in the
 * {@link UnverifiedRegistry} and echoed in KNOWN_UNKNOWNS.md. A wrong value degrades a hit's tag
 * to the room's default; nothing crashes.</p>
 */
final class ToaMechanics
{
	// ---- GraphicsObject ids (TODO(verify) — source: ToaMistakeTracker detectors) ----
	private static final int GO_HET_BEAM_VERTICAL = v("GO_HET_BEAM_VERTICAL(HetPuzzleDetector)", 2064);
	private static final int GO_HET_BEAM_HORIZONTAL = v("GO_HET_BEAM_HORIZONTAL(HetPuzzleDetector)", 2114);
	private static final int GO_HET_BEAM_CRASH = v("GO_HET_BEAM_CRASH(tombs-of-amascut BeamTimerTracker)", 2120);
	private static final int GO_HET_ORB_OF_DARKNESS = v("GO_HET_ORB_OF_DARKNESS(HetPuzzleDetector)", 379);
	private static final int GO_APMEKEN_VOLATILE_EXPLOSION = v("GO_APMEKEN_VOLATILE(ApmekenPuzzleDetector)", 131);
	private static final int GO_AKKHA_QUADRANT_MIN = v("GO_AKKHA_QUADRANT_2256_2259(AkkhaDetector)", 2256);
	private static final int GO_AKKHA_QUADRANT_MAX = 2259;
	private static final int GO_AKKHA_UNSTABLE_ORB = v("GO_AKKHA_UNSTABLE_ORB(AkkhaDetector)", 2260);
	private static final int GO_BABA_BOULDER_SHADOW_LONG = v("GO_BABA_BOULDER_SHADOW_2250(BabaDetector)", 2250);
	private static final int GO_BABA_BOULDER_SHADOW_SHORT = v("GO_BABA_BOULDER_SHADOW_2251(BabaDetector)", 2251);
	private static final int GO_BABA_SLAM = v("GO_BABA_SLAM_1103(BabaDetector)", 1103);
	private static final int GO_BABA_RUBBLE_EXPLOSION = v("GO_BABA_RUBBLE_1463(BabaDetector)", 1463);
	private static final int GO_KEPHRI_BOMB_SHADOW_1 = v("GO_KEPHRI_BOMB_SHADOW_1447(KephriDetector)", 1447);
	private static final int GO_KEPHRI_BOMB_SHADOW_2 = v("GO_KEPHRI_BOMB_SHADOW_1446(KephriDetector)", 1446);
	private static final int GO_KEPHRI_BOMB_SHADOW_3 = v("GO_KEPHRI_BOMB_SHADOW_2111(KephriDetector)", 2111);
	private static final int GO_KEPHRI_EXPLOSION_MIN = v("GO_KEPHRI_EXPLOSION_2156_2159(KephriDetector)", 2156);
	private static final int GO_KEPHRI_EXPLOSION_MAX = 2159;
	private static final int GO_ZEBAK_EARTHQUAKE = v("GO_ZEBAK_EARTHQUAKE_2184(ZebakDetector)", 2184);
	private static final int GO_WARDENS_DDR = v("GO_WARDENS_DDR_2235(WardensP1P2Detector)", 2235);
	private static final int GO_WARDENS_WINDMILL_SHADOW = v("GO_WARDENS_WINDMILL_2236(WardensP1P2Detector)", 2236);
	private static final int GO_WARDENS_WINDMILL_HIT = v("GO_WARDENS_WINDMILL_HIT_2234(WardensP1P2Detector)", 2234);
	private static final int GO_WARDENS_LIGHTNING_1 = v("GO_WARDENS_LIGHTNING_2199(WardensP1P2Detector)", 2199);
	private static final int GO_WARDENS_LIGHTNING_2 = v("GO_WARDENS_LIGHTNING_2200(WardensP1P2Detector)", 2200);
	private static final int GO_WARDENS_BOMB = v("GO_WARDENS_BOMB_2198(WardensP1P2Detector)", 2198);
	private static final int GO_WARDENS_P3_LIGHTNING = v("GO_WARDENS_P3_LIGHTNING_2197(WardensP3Detector)", 2197);
	private static final int GO_CRONDIS_WATER_HAZARD = v("GO_CRONDIS_WATER_HAZARD_2129(observed live 2026-07-12, no independent source)", 2129);

	// ---- Projectile ids (TODO(verify) — same sources) ----
	private static final int PJ_KEPHRI_FIREBALL = v("PJ_KEPHRI_FIREBALL_2266(KephriDetector)", 2266);
	private static final int PJ_KEPHRI_EXPLODING_SCARAB = v("PJ_KEPHRI_SCARAB_2147(KephriDetector)", 2147);
	private static final int PJ_WARDENS_SPECIAL_MELEE = v("PJ_WARDENS_SPECIAL_MELEE_2204(WardensP1P2Detector)", 2204);
	private static final int PJ_WARDENS_SPECIAL_RANGED = v("PJ_WARDENS_SPECIAL_RANGED_2206(WardensP1P2Detector)", 2206);
	private static final int PJ_WARDENS_SPECIAL_MAGIC = v("PJ_WARDENS_SPECIAL_MAGIC_2208(WardensP1P2Detector)", 2208);
	private static final int PJ_WARDENS_CORE_SKULL = v("PJ_WARDENS_CORE_SKULL_2237(WardensP1P2Detector)", 2237);
	private static final int PJ_P3_AKKHA_GHOST_MAGIC = v("PJ_P3_AKKHA_GHOST_MAGIC_2253(WardensP3Detector)", 2253);
	private static final int PJ_P3_AKKHA_GHOST_RANGED = v("PJ_P3_AKKHA_GHOST_RANGED_2255(WardensP3Detector)", 2255);
	private static final int PJ_P3_ZEBAK_GHOST_MAGIC = v("PJ_P3_ZEBAK_GHOST_MAGIC_2181(WardensP3Detector)", 2181);
	private static final int PJ_P3_ZEBAK_GHOST_RANGED = v("PJ_P3_ZEBAK_GHOST_RANGED_2187(WardensP3Detector)", 2187);

	private static final Map<Integer, MechanicTag> BY_SOURCE_NPC = buildNpcMap();

	private ToaMechanics()
	{
	}

	private static int v(String note, int value)
	{
		return UnverifiedRegistry.note("TOA:" + note, value);
	}

	/** NPC-id keyed tags — these use gameval constants, so they're compile-verified. */
	private static Map<Integer, MechanicTag> buildNpcMap()
	{
		Map<Integer, MechanicTag> map = new HashMap<>();
		// Akkha elemental trail orbs: touching them is always a mistake.
		MechanicTag akkhaOrb = new MechanicTag("AKKHA_ELEMENTAL_ORB", true);
		map.put(NpcID.AKKHA_TRAIL_ORB_LIGHTNING, akkhaOrb);
		map.put(NpcID.AKKHA_TRAIL_ORB_DARKNESS, akkhaOrb);
		map.put(NpcID.AKKHA_TRAIL_ORB_BURN, akkhaOrb);
		map.put(NpcID.AKKHA_TRAIL_ORB_FREEZE, akkhaOrb);
		map.put(NpcID.AKKHA_ENRAGE_ORB, akkhaOrb);
		map.put(NpcID.AKKHA_SHADOW, new MechanicTag("AKKHA_SHADOW_ATTACK", false));
		map.put(NpcID.AKKHA_SHADOW_ENRAGE, new MechanicTag("AKKHA_SHADOW_ATTACK", false));

		MechanicTag rollingBoulder = new MechanicTag("BABA_ROLLING_BOULDER", true);
		map.put(NpcID.TOA_BABA_BOULDER, rollingBoulder);
		map.put(NpcID.TOA_BABA_BOULDER_WEAK, rollingBoulder);

		MechanicTag zebakWave = new MechanicTag("ZEBAK_WAVE", true);
		map.put(NpcID.TOA_ZEBAK_WAVE, zebakWave);
		map.put(NpcID.TOA_ZEBAK_WAVE_BLOODY, zebakWave);
		MechanicTag bloodCloud = new MechanicTag("ZEBAK_BLOOD_CLOUD", true);
		map.put(NpcID.TOA_ZEBAK_BLOOD_CLOUD, bloodCloud);
		map.put(NpcID.TOA_ZEBAK_BLOOD_CLOUD_SMALL, bloodCloud);

		map.put(NpcID.TOA_KEPHRI_SCARAB_RANGEKITE, new MechanicTag("KEPHRI_EXPLODING_SCARAB", true));
		map.put(NpcID.TOA_KEPHRI_GUARDIAN_MELEE, new MechanicTag("KEPHRI_GUARDIAN", false));
		map.put(NpcID.TOA_KEPHRI_GUARDIAN_RANGED, new MechanicTag("KEPHRI_GUARDIAN", false));
		map.put(NpcID.TOA_KEPHRI_GUARDIAN_MAGE, new MechanicTag("KEPHRI_GUARDIAN", false));

		map.put(NpcID.TOA_CRONDIS_CROCODILE, new MechanicTag("CRONDIS_CROCODILE", false));
		map.put(NpcID.TOA_WARDEN_TUMEKEN_CORE, new MechanicTag("WARDENS_CORE_CONTACT", true));
		map.put(NpcID.TOA_WARDEN_ELIDINIS_CORE, new MechanicTag("WARDENS_CORE_CONTACT", true));
		return map;
	}

	/**
	 * Classification order: tile graphics object (ground hazards) → targeted projectile
	 * (prayer-dodgeable specials) → source NPC id → hitsplat type → room default. First match wins;
	 * ground hazards win because standing in them is the clearest player-attributable mistake.
	 */
	static MechanicTag classify(MechanicContext ctx)
	{
		MechanicTag byTile = classifyTileGraphics(ctx.getTileGraphicsObjectId(), ctx.getRoom());
		if (byTile != null)
		{
			return byTile;
		}

		MechanicTag byProjectile = classifyProjectile(ctx.getPlayerTargetedProjectileId(), ctx.getRoom());
		if (byProjectile != null)
		{
			return byProjectile;
		}

		MechanicTag byNpc = BY_SOURCE_NPC.get(ctx.getSourceNpcId());
		if (byNpc != null)
		{
			return byNpc;
		}

		if (ctx.getHitsplatType() == HitsplatID.POISON || ctx.getHitsplatType() == HitsplatID.VENOM)
		{
			// In ToA poison is overwhelmingly Zebak's acid pools / poison spit — dodgeable.
			return new MechanicTag("POISON", ToaRooms.ZEBAK.equals(ctx.getRoom()));
		}
		if (ctx.getHitsplatType() == HitsplatID.BURN)
		{
			return new MechanicTag("BURN", false);
		}

		// Attributed to a boss's regular attack: not avoidable (prayer correctness is not observable
		// enough to claim otherwise honestly).
		if (ctx.getSourceNpcId() > 0 && ctx.getRoom() != null)
		{
			return new MechanicTag(ctx.getRoom() + "_ATTACK", false);
		}
		return MechanicTag.UNKNOWN;
	}

	private static MechanicTag classifyTileGraphics(int id, String room)
	{
		if (id < 0)
		{
			return null;
		}
		if (id == GO_HET_BEAM_VERTICAL || id == GO_HET_BEAM_HORIZONTAL || id == GO_HET_BEAM_CRASH)
		{
			return roomTag(room, ToaRooms.PUZZLE_HET, "HET_BEAM", true);
		}
		if (id == GO_HET_ORB_OF_DARKNESS)
		{
			return roomTag(room, ToaRooms.PUZZLE_HET, "HET_ORB_OF_DARKNESS", true);
		}
		if (id == GO_APMEKEN_VOLATILE_EXPLOSION)
		{
			return roomTag(room, ToaRooms.PUZZLE_APMEKEN, "APMEKEN_VOLATILE_EXPLOSION", true);
		}
		if (id >= GO_AKKHA_QUADRANT_MIN && id <= GO_AKKHA_QUADRANT_MAX)
		{
			return roomTag(room, ToaRooms.AKKHA, "AKKHA_QUADRANT_BOMB", true);
		}
		if (id == GO_AKKHA_UNSTABLE_ORB)
		{
			return roomTag(room, ToaRooms.AKKHA, "AKKHA_UNSTABLE_ORB", true);
		}
		if (id == GO_BABA_BOULDER_SHADOW_LONG || id == GO_BABA_BOULDER_SHADOW_SHORT)
		{
			return roomTag(room, ToaRooms.BABA, "BABA_FALLING_BOULDER", true);
		}
		if (id == GO_BABA_SLAM)
		{
			return roomTag(room, ToaRooms.BABA, "BABA_SLAM", true);
		}
		if (id == GO_BABA_RUBBLE_EXPLOSION)
		{
			return roomTag(room, ToaRooms.BABA, "BABA_RUBBLE_EXPLOSION", true);
		}
		if (id == GO_KEPHRI_BOMB_SHADOW_1 || id == GO_KEPHRI_BOMB_SHADOW_2 || id == GO_KEPHRI_BOMB_SHADOW_3
			|| (id >= GO_KEPHRI_EXPLOSION_MIN && id <= GO_KEPHRI_EXPLOSION_MAX))
		{
			return roomTag(room, ToaRooms.KEPHRI, "KEPHRI_BOMB", true);
		}
		if (id == GO_ZEBAK_EARTHQUAKE)
		{
			return roomTag(room, ToaRooms.ZEBAK, "ZEBAK_EARTHQUAKE", true);
		}
		if (id == GO_WARDENS_DDR)
		{
			return roomTag(room, ToaRooms.WARDENS_P1_P2, "WARDENS_OBELISK_DDR", true);
		}
		if (id == GO_WARDENS_WINDMILL_SHADOW || id == GO_WARDENS_WINDMILL_HIT)
		{
			return roomTag(room, ToaRooms.WARDENS_P1_P2, "WARDENS_OBELISK_WINDMILL", true);
		}
		if (id == GO_WARDENS_LIGHTNING_1 || id == GO_WARDENS_LIGHTNING_2)
		{
			return roomTag(room, ToaRooms.WARDENS_P1_P2, "WARDENS_LIGHTNING", true);
		}
		if (id == GO_WARDENS_BOMB)
		{
			return roomTag(room, ToaRooms.WARDENS_P1_P2, "WARDENS_BOMB", true);
		}
		if (id == GO_WARDENS_P3_LIGHTNING)
		{
			return roomTag(room, ToaRooms.WARDENS_P3, "WARDENS_P3_LIGHTNING", true);
		}
		if (id == GO_CRONDIS_WATER_HAZARD)
		{
			return roomTag(room, ToaRooms.PUZZLE_CRONDIS, "CRONDIS_WATER_HAZARD", true);
		}
		return null;
	}

	private static MechanicTag classifyProjectile(int id, String room)
	{
		if (id < 0)
		{
			return null;
		}
		if (id == PJ_KEPHRI_FIREBALL)
		{
			return roomTag(room, ToaRooms.KEPHRI, "KEPHRI_FIREBALL", true);
		}
		if (id == PJ_KEPHRI_EXPLODING_SCARAB)
		{
			return roomTag(room, ToaRooms.KEPHRI, "KEPHRI_EXPLODING_SCARAB", true);
		}
		if (id == PJ_WARDENS_SPECIAL_MELEE || id == PJ_WARDENS_SPECIAL_RANGED || id == PJ_WARDENS_SPECIAL_MAGIC)
		{
			return roomTag(room, ToaRooms.WARDENS_P1_P2, "WARDENS_PRAYER_SPECIAL", true);
		}
		if (id == PJ_WARDENS_CORE_SKULL)
		{
			return roomTag(room, ToaRooms.WARDENS_P1_P2, "WARDENS_CORE_SKULL", true);
		}
		if (id == PJ_P3_AKKHA_GHOST_MAGIC || id == PJ_P3_AKKHA_GHOST_RANGED
			|| id == PJ_P3_ZEBAK_GHOST_MAGIC || id == PJ_P3_ZEBAK_GHOST_RANGED)
		{
			return roomTag(room, ToaRooms.WARDENS_P3, "WARDENS_P3_GHOST_ATTACK", true);
		}
		return null;
	}

	/**
	 * Gates a graphics-object / projectile mechanic to the room that actually owns it. These raw ids
	 * are not globally unique across ToA: during a live raid on 2026-07-12 the Wardens P3 ghost
	 * projectiles fired their ids inside the Zebak and Akkha fights and Kephri's bomb graphics fired
	 * inside Ba-Ba and Wardens P3, which mislabeled ordinary boss damage as avoidable and corrupted
	 * the coach's findings. Returning null on a room mismatch lets {@link #classify} fall through to
	 * the NPC-id / hitsplat / room-default steps instead of trusting a cross-room id collision.
	 */
	private static MechanicTag roomTag(String actualRoom, String expectedRoom, String mechanic, boolean avoidable)
	{
		return expectedRoom.equals(actualRoom) ? new MechanicTag(mechanic, avoidable) : null;
	}
}
