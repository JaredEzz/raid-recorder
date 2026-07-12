package tech.jaredezz.raidrecorder.coach.rules;

/**
 * Weapon combat-style classification by name keyword. Name-based on purpose (no item-id tables to
 * maintain); unknown weapons return null and the style rules skip them rather than guess.
 */
final class GearStyles
{
	static final String MELEE = "MELEE";
	static final String RANGED = "RANGED";
	static final String MAGIC = "MAGIC";

	private static final String[] MAGIC_KEYWORDS = {
		"staff", "sceptre", "trident", "sanguinesti", "shadow", "wand", "crozier", "salamander",
		"harmonised", "nightmare staff", "tome of",
	};
	private static final String[] RANGED_KEYWORDS = {
		"bow", "blowpipe", "chinchompa", "ballista", "thrownaxe", "knife", "dart", "javelin",
		"eclipse atlatl", "venator", "webweaver",
	};
	private static final String[] MELEE_KEYWORDS = {
		"scythe", "rapier", "fang", "whip", "tentacle", "blade", "sword", "scimitar", "mace",
		"hammer", "maul", "bludgeon", "claws", "halberd", "spear", "hasta", "dagger", "axe",
		"soulreaper", "keris", "swift blade", "saber", "machete", "bulwark", "flail", "cudgel",
	};

	private GearStyles()
	{
	}

	/** MELEE / RANGED / MAGIC, or null when unknown. Order matters: magic beats melee for "staff of the dead". */
	static String classifyWeapon(String weaponName)
	{
		if (weaponName == null)
		{
			return null;
		}
		String lower = weaponName.toLowerCase();
		for (String keyword : MAGIC_KEYWORDS)
		{
			if (lower.contains(keyword))
			{
				return MAGIC;
			}
		}
		for (String keyword : RANGED_KEYWORDS)
		{
			if (lower.contains(keyword))
			{
				// "Crossbow" contains "bow"; still RANGED, fine.
				return RANGED;
			}
		}
		for (String keyword : MELEE_KEYWORDS)
		{
			if (lower.contains(keyword))
			{
				return MELEE;
			}
		}
		return null;
	}
}
