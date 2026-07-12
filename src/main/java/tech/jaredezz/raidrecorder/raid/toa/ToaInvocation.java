package tech.jaredezz.raidrecorder.raid.toa;

import java.util.EnumSet;
import java.util.Set;
import lombok.Getter;

/**
 * The Tombs of Amascut invocations, in the order they appear in the invocation interface.
 *
 * <p>The enum order, point values and {@link #getWidgetIx()} layout (each invocation occupies
 * three consecutive widget children, so the toggle sits at {@code ordinal() * 3}) mirror the
 * Plugin Hub "Tombs of Amascut" plugin by LlemonDuck (BSD 2-Clause), the authoritative source
 * for the interface layout, and were previously validated live in the toa-raid-log plugin.</p>
 */
@Getter
public enum ToaInvocation
{
	TRY_AGAIN(5),
	PERSISTENCE(10),
	SOFTCORE_RUN(15),
	HARDCORE_RUN(25),
	WALK_FOR_IT(10),
	JOG_FOR_IT(15),
	RUN_FOR_IT(20),
	SPRINT_FOR_IT(25),
	NEED_SOME_HELP(15),
	NEED_LESS_HELP(25),
	NO_HELP_NEEDED(40),
	WALK_THE_PATH(50),
	PATHSEEKER(15),
	PATHFINDER(40),
	PATHMASTER(50),
	QUIET_PRAYERS(20),
	DEADLY_PRAYERS(20),
	ON_A_DIET(15),
	DEHYDRATION(30),
	OVERLY_DRAINING(15),
	LIVELY_LARVAE(5),
	MORE_OVERLORDS(15),
	BLOWING_MUD(10),
	MEDIC(15),
	AERIAL_ASSAULT(10),
	NOT_JUST_A_HEAD(15),
	ARTERIAL_SPRAY(10),
	BLOOD_THINNERS(5),
	UPSET_STOMACH(15),
	DOUBLE_TROUBLE(20),
	KEEP_BACK(10),
	STAY_VIGILANT(15),
	FEELING_SPECIAL(20),
	MIND_THE_GAP(10),
	GOTTA_HAVE_FAITH(10),
	JUNGLE_JAPES(5),
	SHAKING_THINGS_UP(10),
	BOULDERDASH(10),
	ANCIENT_HASTE(10),
	ACCELERATION(10),
	PENETRATION(10),
	OVERCLOCKED(10),
	OVERCLOCKED_2(10),
	INSANITY(50);

	/** Raid-level points this invocation contributes when toggled on. */
	private final int raidLevel;
	/** Human-readable name, e.g. "Overclocked II". */
	private final String displayName;

	ToaInvocation(int raidLevel)
	{
		this.raidLevel = raidLevel;
		this.displayName = buildDisplayName(name());
	}

	/** Index of this invocation's toggle widget within the invocation list container. */
	public int getWidgetIx()
	{
		return ordinal() * 3;
	}

	private static String buildDisplayName(String enumName)
	{
		boolean tierTwo = enumName.endsWith("_2");
		String base = tierTwo ? enumName.substring(0, enumName.length() - 2) : enumName;
		StringBuilder sb = new StringBuilder();
		for (String word : base.split("_"))
		{
			if (word.isEmpty())
			{
				continue;
			}
			sb.append(Character.toUpperCase(word.charAt(0)))
				.append(word.substring(1).toLowerCase())
				.append(' ');
		}
		String out = sb.toString().trim();
		return tierTwo ? out + " II" : out;
	}

	/**
	 * Sum of point values for the active set — the raid level shown in the invocation UI.
	 * {@code VarbitID.TOA_CLIENT_RAID_LEVEL} only populates once actually in the raid (confirmed
	 * live: it reads 0 while still toggling invocations in the lobby), so this is the reliable
	 * source before entry; see {@link ToaInvocationReader}.
	 */
	public static int sumRaidLevel(Set<ToaInvocation> invocations)
	{
		int total = 0;
		for (ToaInvocation invocation : invocations)
		{
			total += invocation.raidLevel;
		}
		return total;
	}

	// ---- bitmask encoding (44 invocations fit in a long) ----

	public static long toBitmask(Set<ToaInvocation> invocations)
	{
		long mask = 0L;
		for (ToaInvocation inv : invocations)
		{
			mask |= (1L << inv.ordinal());
		}
		return mask;
	}

	public static Set<ToaInvocation> fromBitmask(long mask)
	{
		Set<ToaInvocation> set = EnumSet.noneOf(ToaInvocation.class);
		for (ToaInvocation inv : values())
		{
			if ((mask & (1L << inv.ordinal())) != 0)
			{
				set.add(inv);
			}
		}
		return set;
	}
}
