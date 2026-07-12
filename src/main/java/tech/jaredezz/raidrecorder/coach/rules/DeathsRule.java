package tech.jaredezz.raidrecorder.coach.rules;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import tech.jaredezz.raidrecorder.coach.CoachContext;
import tech.jaredezz.raidrecorder.coach.CoachRule;
import tech.jaredezz.raidrecorder.model.CoachFinding;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.model.RoomRecord;
import tech.jaredezz.raidrecorder.model.Severity;

/** Deaths are always CRITICAL per room; a deathless raid earns a raid-wide GOOD. */
public class DeathsRule implements CoachRule
{
	@Override
	public String id()
	{
		return "deaths";
	}

	@Override
	public List<CoachFinding> evaluate(RaidRecord record, CoachContext context)
	{
		List<CoachFinding> findings = new ArrayList<>();
		int total = 0;
		for (RoomRecord room : record.getRooms())
		{
			total += room.getDeaths();
			if (room.getDeaths() > 0)
			{
				int avoidable = room.getDamageTaken().stream()
					.filter(RoomRecord.DamageTakenEvent::isAvoidable)
					.mapToInt(RoomRecord.DamageTakenEvent::getAmount).sum();
				findings.add(new CoachFinding(room.getRoom(), Severity.CRITICAL, "death",
					String.format("Died %d time%s in %s. %d of the damage taken there was avoidable — "
						+ "check the damageTaken timeline for what chunked you.",
						room.getDeaths(), room.getDeaths() > 1 ? "s" : "", room.getRoom(), avoidable),
					ImmutableMap.of("deaths", room.getDeaths(), "avoidableDamage", avoidable)));
			}
		}
		if (total == 0 && !record.getRooms().isEmpty())
		{
			findings.add(new CoachFinding("RAID", Severity.GOOD, "deathless",
				"Deathless raid.", ImmutableMap.of("rooms", record.getRooms().size())));
		}
		return findings;
	}
}
