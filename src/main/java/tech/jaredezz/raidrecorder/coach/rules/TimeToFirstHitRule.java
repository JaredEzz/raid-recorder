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

/** Slow ramp-up: how long after entering a boss room the first hit landed. */
public class TimeToFirstHitRule implements CoachRule
{
	@Override
	public String id()
	{
		return "time_to_first_hit";
	}

	@Override
	public List<CoachFinding> evaluate(RaidRecord record, CoachContext context)
	{
		List<CoachFinding> findings = new ArrayList<>();
		int warnAt = context.getThresholds().getTimeToFirstHitWarnTicks()
			+ context.getKcBand().getFirstHitSlackTicks();

		for (RoomRecord room : record.getRooms())
		{
			if (!context.getModule().isBossRoom(room.getRoom()) || room.getTimeToFirstHitTicks() < 0)
			{
				continue;
			}
			if (room.getTimeToFirstHitTicks() > warnAt)
			{
				findings.add(new CoachFinding(room.getRoom(), Severity.WARN, "slow_first_hit",
					String.format("First hit in %s took %d ticks (%.1fs) — expected within %d ticks. "
						+ "Pre-pot, pre-position, and have your first attack queued on entry.",
						room.getRoom(), room.getTimeToFirstHitTicks(),
						room.getTimeToFirstHitTicks() * 0.6, warnAt),
					ImmutableMap.of("ticks", room.getTimeToFirstHitTicks(), "warnThresholdTicks", warnAt)));
			}
		}
		return findings;
	}
}
