package tech.jaredezz.raidrecorder.coach.rules;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tech.jaredezz.raidrecorder.coach.CoachContext;
import tech.jaredezz.raidrecorder.coach.CoachRule;
import tech.jaredezz.raidrecorder.model.CoachFinding;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.model.RoomRecord;
import tech.jaredezz.raidrecorder.model.Severity;

/**
 * "You own an upgrade you didn't bring": compares the best weapon per style seen in the equipment
 * timeline against the latest bank snapshot, using coarse best-in-slot ladders (worst → best).
 * Never tells anyone to buy anything — for ironmen that's impossible and for mains it's the AI
 * prompt's job; this rule only surfaces gear that is provably sitting in the player's own bank.
 */
public class OwnedButUnusedUpgradeRule implements CoachRule
{
	/** Ladders are worst→best; the coach flags when the bank rank beats the used rank. */
	private static final Map<String, List<String>> LADDERS = ImmutableMap.of(
		GearStyles.MELEE, ImmutableList.of(
			"abyssal whip", "abyssal tentacle", "blade of saeldor", "ghrazi rapier",
			"osmumten's fang", "scythe of vitur"),
		GearStyles.RANGED, ImmutableList.of(
			"rune crossbow", "dragon crossbow", "armadyl crossbow", "toxic blowpipe",
			"dragon hunter crossbow", "bow of faerdhinen", "zaryte crossbow", "twisted bow"),
		GearStyles.MAGIC, ImmutableList.of(
			"trident of the seas", "trident of the swamp", "sanguinesti staff",
			"harmonised nightmare staff", "tumeken's shadow")
	);

	@Override
	public String id()
	{
		return "owned_but_unused";
	}

	@Override
	public List<CoachFinding> evaluate(RaidRecord record, CoachContext context)
	{
		List<CoachFinding> findings = new ArrayList<>();
		if (context.getBank() == null || context.getBank().getNames() == null)
		{
			return findings;
		}

		Set<String> usedWeapons = usedWeaponNames(record);
		List<String> bankNames = new ArrayList<>(context.getBank().getNames().values());

		for (Map.Entry<String, List<String>> ladder : LADDERS.entrySet())
		{
			int bestUsed = bestRank(ladder.getValue(), usedWeapons);
			int bestBanked = bestRank(ladder.getValue(), bankNames);
			if (bestUsed >= 0 && bestBanked > bestUsed)
			{
				String banked = ladder.getValue().get(bestBanked);
				String used = ladder.getValue().get(bestUsed);
				findings.add(new CoachFinding("RAID", Severity.INFO, "owned_but_unused_upgrade",
					String.format("Your bank has a %s but you raided with a %s — bring the upgrade next time.",
						banked, used),
					ImmutableMap.of("style", ladder.getKey(), "banked", banked, "used", used)));
			}
		}
		return findings;
	}

	private static Set<String> usedWeaponNames(RaidRecord record)
	{
		Set<String> names = new LinkedHashSet<>();
		for (RoomRecord room : record.getRooms())
		{
			for (RoomRecord.EquipmentSnapshot snapshot : room.getEquipmentTimeline())
			{
				RoomRecord.Item weapon = snapshot.getItems().get("WEAPON");
				if (weapon != null && weapon.getName() != null)
				{
					names.add(weapon.getName());
				}
			}
		}
		return names;
	}

	/** Highest ladder index matched by any of the names (contains, case-insensitive), or -1. */
	private static int bestRank(List<String> ladder, Iterable<String> names)
	{
		int best = -1;
		for (String name : names)
		{
			String lower = name.toLowerCase(Locale.ROOT);
			for (int i = ladder.size() - 1; i > best; i--)
			{
				if (lower.contains(ladder.get(i)))
				{
					best = i;
					break;
				}
			}
		}
		return best;
	}
}
