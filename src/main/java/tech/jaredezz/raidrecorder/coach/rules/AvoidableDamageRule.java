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
 * Sums damage tagged avoidable by the mechanic taxonomy per room, with a per-mechanic breakdown
 * in the evidence. Zero avoidable damage in a boss room earns a GOOD.
 */
public class AvoidableDamageRule implements CoachRule
{
	@Override
	public String id()
	{
		return "avoidable_damage";
	}

	@Override
	public List<CoachFinding> evaluate(RaidRecord record, CoachContext context)
	{
		List<CoachFinding> findings = new ArrayList<>();
		double multiplier = context.getKcBand().getAvoidableMultiplier();
		int warnAt = (int) (context.getThresholds().getAvoidableDamageWarnPerRoom() * multiplier);
		int criticalAt = (int) (context.getThresholds().getAvoidableDamageCriticalPerRoom() * multiplier);

		for (RoomRecord room : record.getRooms())
		{
			int avoidable = 0;
			Map<String, Integer> byMechanic = new LinkedHashMap<>();
			for (RoomRecord.DamageTakenEvent event : room.getDamageTaken())
			{
				if (event.isAvoidable())
				{
					avoidable += event.getAmount();
					byMechanic.merge(event.getMechanic(), event.getAmount(), Integer::sum);
				}
			}

			if (avoidable >= criticalAt)
			{
				findings.add(new CoachFinding(room.getRoom(), Severity.CRITICAL, "avoidable_damage_high",
					String.format("Took %d avoidable damage in %s — worst source: %s. These are dodgeable.",
						avoidable, room.getRoom(), worst(byMechanic)),
					ImmutableMap.of("avoidableDamage", avoidable, "criticalThreshold", criticalAt,
						"byMechanic", byMechanic)));
			}
			else if (avoidable >= warnAt)
			{
				findings.add(new CoachFinding(room.getRoom(), Severity.WARN, "avoidable_damage",
					String.format("Took %d avoidable damage in %s (mostly %s).",
						avoidable, room.getRoom(), worst(byMechanic)),
					ImmutableMap.of("avoidableDamage", avoidable, "warnThreshold", warnAt,
						"byMechanic", byMechanic)));
			}
			else if (avoidable == 0 && context.getModule().isBossRoom(room.getRoom())
				&& !room.getDamageTaken().isEmpty())
			{
				findings.add(new CoachFinding(room.getRoom(), Severity.GOOD, "avoidable_damage_clean",
					String.format("Clean %s — no avoidable damage taken.", room.getRoom()),
					ImmutableMap.of("totalDamageTaken",
						room.getDamageTaken().stream().mapToInt(RoomRecord.DamageTakenEvent::getAmount).sum())));
			}
		}
		return findings;
	}

	private static String worst(Map<String, Integer> byMechanic)
	{
		return byMechanic.entrySet().stream()
			.max(Map.Entry.comparingByValue())
			.map(Map.Entry::getKey)
			.orElse("UNKNOWN");
	}
}
