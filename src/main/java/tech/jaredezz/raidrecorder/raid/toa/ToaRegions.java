package tech.jaredezz.raidrecorder.raid.toa;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;

/**
 * ToA map-region ids (instance-template regions, i.e. what
 * {@code WorldPoint.fromLocalInstance(...).getRegionID()} returns).
 *
 * <p>Sources: cross-confirmed across four Plugin Hub plugins — LlemonDuck/tombs-of-amascut
 * {@code util/RaidRoom.java}, QuestingPet/ToaMistakeTracker {@code RaidRoom.java},
 * sreilly64/TombsOfAmascutStats and capslock13/AdvancedRaidTracker. All four agree on every room
 * region; the only disagreement is the outside lobby (13454 in three plugins, 13455 in ART) —
 * we accept both.</p>
 */
public final class ToaRegions
{
	public static final int LOBBY = 13454;
	public static final int LOBBY_ALT = 13455;
	public static final int NEXUS = 14160;

	public static final int PUZZLE_CRONDIS = 15698;
	public static final int BOSS_ZEBAK = 15700;
	public static final int PUZZLE_SCABARAS = 14162;
	public static final int BOSS_KEPHRI = 14164;
	public static final int PUZZLE_APMEKEN = 15186;
	public static final int BOSS_BABA = 15188;
	public static final int PUZZLE_HET = 14674;
	public static final int BOSS_AKKHA = 14676;

	public static final int WARDENS_P1_P2 = 15184;
	public static final int WARDENS_P3 = 15696;
	public static final int VAULT = 14672;

	/** Every region that counts as "inside the raid" (lobby deliberately excluded). */
	public static final Set<Integer> IN_RAID = ImmutableSet.of(
		NEXUS,
		PUZZLE_CRONDIS, BOSS_ZEBAK,
		PUZZLE_SCABARAS, BOSS_KEPHRI,
		PUZZLE_APMEKEN, BOSS_BABA,
		PUZZLE_HET, BOSS_AKKHA,
		WARDENS_P1_P2, WARDENS_P3,
		VAULT
	);

	/** Region to room key. Nexus and vault are corridors, not rooms — absent on purpose. */
	public static final Map<Integer, String> ROOM_BY_REGION = ImmutableMap.<Integer, String>builder()
		.put(PUZZLE_HET, ToaRooms.PUZZLE_HET)
		.put(BOSS_AKKHA, ToaRooms.AKKHA)
		.put(PUZZLE_CRONDIS, ToaRooms.PUZZLE_CRONDIS)
		.put(BOSS_ZEBAK, ToaRooms.ZEBAK)
		.put(PUZZLE_SCABARAS, ToaRooms.PUZZLE_SCABARAS)
		.put(BOSS_KEPHRI, ToaRooms.KEPHRI)
		.put(PUZZLE_APMEKEN, ToaRooms.PUZZLE_APMEKEN)
		.put(BOSS_BABA, ToaRooms.BABA)
		.put(WARDENS_P1_P2, ToaRooms.WARDENS_P1_P2)
		.put(WARDENS_P3, ToaRooms.WARDENS_P3)
		.build();

	private ToaRegions()
	{
	}
}
