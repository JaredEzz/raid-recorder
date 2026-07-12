package tech.jaredezz.raidrecorder.model;

/**
 * OSRS account type, decoded from varbit 1777 ({@code VarbitID.IRONMAN}, the varbit the legacy
 * {@code Varbits.ACCOUNT_TYPE} constant points at).
 *
 * <p>Ordering follows RuneLite's historical {@code AccountType} enum (removed from the API), which
 * decoded this varbit by ordinal. TODO(verify): confirm values 2/3 (ULTIMATE vs HARDCORE order)
 * and 4-6 in a live session — log the raw value once and compare. See KNOWN_UNKNOWNS.md.</p>
 */
public enum AccountType
{
	NORMAL,
	IRONMAN,
	ULTIMATE_IRONMAN,
	HARDCORE_IRONMAN,
	GROUP_IRONMAN,
	HARDCORE_GROUP_IRONMAN,
	UNRANKED_GROUP_IRONMAN,
	UNKNOWN;

	public static AccountType fromVarbit(int value)
	{
		AccountType[] values = values();
		if (value < 0 || value >= values.length - 1)
		{
			return UNKNOWN;
		}
		return values[value];
	}

	/** True for any ironman variant — used by coach rules that must not suggest buying gear. */
	public boolean isIronman()
	{
		return this != NORMAL && this != UNKNOWN;
	}
}
