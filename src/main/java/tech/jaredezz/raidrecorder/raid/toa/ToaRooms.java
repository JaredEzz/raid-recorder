package tech.jaredezz.raidrecorder.raid.toa;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;

/**
 * Stable room keys for ToA exports. Path→boss mapping (unanimous across hub plugins):
 * Path of Het→Akkha, Path of Crondis→Zebak, Path of Scabaras→Kephri, Path of Apmeken→Ba-Ba.
 * The Wardens fight spans two instanced regions, recorded as two rooms (P1/P2 obelisk, P3).
 */
public final class ToaRooms
{
	public static final String PUZZLE_HET = "PUZZLE_HET";
	public static final String AKKHA = "AKKHA";
	public static final String PUZZLE_CRONDIS = "PUZZLE_CRONDIS";
	public static final String ZEBAK = "ZEBAK";
	public static final String PUZZLE_SCABARAS = "PUZZLE_SCABARAS";
	public static final String KEPHRI = "KEPHRI";
	public static final String PUZZLE_APMEKEN = "PUZZLE_APMEKEN";
	public static final String BABA = "BABA";
	public static final String WARDENS_P1_P2 = "WARDENS_P1_P2";
	public static final String WARDENS_P3 = "WARDENS_P3";

	public static final List<String> ALL = ImmutableList.of(
		PUZZLE_HET, AKKHA, PUZZLE_CRONDIS, ZEBAK,
		PUZZLE_SCABARAS, KEPHRI, PUZZLE_APMEKEN, BABA,
		WARDENS_P1_P2, WARDENS_P3);

	public static final Set<String> BOSSES = ImmutableSet.of(
		AKKHA, ZEBAK, KEPHRI, BABA, WARDENS_P1_P2, WARDENS_P3);

	private ToaRooms()
	{
	}
}
