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
	/**
	 * Ladders are worst→best; the coach flags (INFO only) when the bank rank beats the used rank.
	 *
	 * <p><b>KNOWN DESIGN LIMITATION (see KNOWN_UNKNOWNS.md #14).</b> A single flat "worst→best"
	 * order per style is a deliberately coarse model. Real best-in-slot in OSRS is
	 * <i>target-dependent</i> — it turns on the target's defence type, size, and magic level — so no
	 * flat ladder can be strictly correct for every boss. The lower rungs of each ladder are a sound
	 * progression (a whip is unambiguously below a rapier); the <b>top rungs are near-peers whose
	 * relative order flips by target</b>, e.g.:
	 * <ul>
	 *   <li>MELEE: Scythe of Vitur is BiS on large/multi-tile monsters (it hits up to 3x per swing),
	 *       but Osmumten's fang is BiS on high-defence single targets — which is <i>most ToA
	 *       bosses</i>, where the OSRS Wiki's own max-efficiency setups name the fang, not the scythe,
	 *       as primary melee. Soulreaper axe (at 5 stacks) can out-DPS the scythe on 1x1/2x2 monsters.
	 *       So a "you own a Scythe, bring it instead of your fang" hint is often <i>wrong for this
	 *       plugin's own domain</i>. The order below keeps the scythe nominally top only because it is
	 *       the general whole-game convention; treat the fang↔axe↔scythe ordering as "same tier", not
	 *       a real DPS claim.</li>
	 *   <li>RANGED: Twisted bow is BiS vs high-magic targets, Bow of faerdhinen vs low/mid-defence,
	 *       Zaryte crossbow for its spec / specific encounters, and Dragon hunter crossbow is
	 *       <i>anti-dragon only</i> (below blowpipe/bofa on everything else) — its fixed mid-ladder
	 *       rank is not a general truth.</li>
	 *   <li>MAGIC: Tumeken's shadow is clear BiS; Harmonised nightmare staff is a standard-spellbook
	 *       casting weapon, not directly comparable to the powered staffs below it, so its rank is
	 *       approximate.</li>
	 * </ul>
	 * Because the rule only fires INFO ("you own a higher-tier weapon"), a wrong intra-tier ordering
	 * degrades to a soft, ignorable nudge rather than a hard "do this" — but it is NOT a verified
	 * DPS ranking. Ordering reviewed against the OSRS Wiki 2026-07-12 (see KNOWN_UNKNOWNS.md #14).
	 */
	private static final Map<String, List<String>> LADDERS = ImmutableMap.of(
		GearStyles.MELEE, ImmutableList.of(
			"abyssal whip", "abyssal tentacle", "blade of saeldor", "ghrazi rapier",
			"osmumten's fang", "soulreaper axe", "scythe of vitur"),
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
