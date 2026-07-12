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

/** Calls out the single largest idle window in each boss room when it exceeds the threshold. */
public class DowntimeRule implements CoachRule
{
	@Override
	public String id()
	{
		return "downtime";
	}

	@Override
	public List<CoachFinding> evaluate(RaidRecord record, CoachContext context)
	{
		List<CoachFinding> findings = new ArrayList<>();
		int warnAt = context.getThresholds().getDowntimeWindowWarnTicks();

		for (RoomRecord room : record.getRooms())
		{
			if (!context.getModule().isBossRoom(room.getRoom()))
			{
				continue;
			}
			int[] longest = null;
			for (int[] window : room.getDowntimeWindows())
			{
				if (longest == null || window[1] > longest[1])
				{
					longest = window;
				}
			}
			if (longest != null && longest[1] >= warnAt)
			{
				findings.add(new CoachFinding(room.getRoom(), Severity.INFO, "long_idle_window",
					String.format("Longest no-damage stretch in %s: %d ticks (%.0fs), starting %d ticks in. "
							+ "Some of this is mechanics; the rest is free DPS.",
						room.getRoom(), longest[1], longest[1] * 0.6, longest[0]),
					ImmutableMap.of("startTickOffset", longest[0], "lengthTicks", longest[1],
						"windowCount", room.getDowntimeWindows().size())));
			}
		}
		return findings;
	}
}
