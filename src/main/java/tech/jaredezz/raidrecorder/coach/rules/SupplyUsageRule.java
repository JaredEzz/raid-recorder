package tech.jaredezz.raidrecorder.coach.rules;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tech.jaredezz.raidrecorder.coach.CoachContext;
import tech.jaredezz.raidrecorder.coach.CoachRule;
import tech.jaredezz.raidrecorder.model.CoachFinding;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.model.RoomRecord;
import tech.jaredezz.raidrecorder.model.Severity;

/**
 * Relates supplies consumed to damage taken: heavy supply burn in a room where most damage was
 * avoidable means supplies are subsidizing dodgeable mistakes. Raid totals go out as INFO.
 */
public class SupplyUsageRule implements CoachRule
{
	@Override
	public String id()
	{
		return "supply_usage";
	}

	@Override
	public List<CoachFinding> evaluate(RaidRecord record, CoachContext context)
	{
		List<CoachFinding> findings = new ArrayList<>();
		int heavyAt = context.getThresholds().getSupplyHeavyDosesPerRoom();
		Map<String, Integer> raidTotals = new LinkedHashMap<>();

		for (RoomRecord room : record.getRooms())
		{
			int doses = room.getSuppliesUsed().values().stream().mapToInt(Integer::intValue).sum();
			room.getSuppliesUsed().forEach((k, v) -> raidTotals.merge(k, v, Integer::sum));

			int taken = room.getDamageTaken().stream()
				.mapToInt(RoomRecord.DamageTakenEvent::getAmount).sum();
			int avoidable = room.getDamageTaken().stream()
				.filter(RoomRecord.DamageTakenEvent::isAvoidable)
				.mapToInt(RoomRecord.DamageTakenEvent::getAmount).sum();

			if (doses >= heavyAt && taken > 0 && avoidable * 2 >= taken)
			{
				findings.add(new CoachFinding(room.getRoom(), Severity.WARN, "supplies_on_avoidable",
					String.format("Used %d doses of supplies in %s, but %d%% of the damage there was "
							+ "avoidable — dodge the mechanics and this food stays in your inventory.",
						doses, room.getRoom(), 100 * avoidable / taken),
					ImmutableMap.of("doses", doses, "damageTaken", taken, "avoidableDamage", avoidable,
						"supplies", room.getSuppliesUsed())));
			}
		}

		if (!raidTotals.isEmpty())
		{
			int total = raidTotals.values().stream().mapToInt(Integer::intValue).sum();
			findings.add(new CoachFinding("RAID", Severity.INFO, "supplies_total",
				String.format("Supplies used this raid: %d doses across %d types.", total, raidTotals.size()),
				ImmutableMap.of("totalDoses", total, "byType", raidTotals)));
		}
		return findings;
	}
}
