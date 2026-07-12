package tech.jaredezz.raidrecorder.coach.rules;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tech.jaredezz.raidrecorder.coach.CoachContext;
import tech.jaredezz.raidrecorder.coach.CoachRule;
import tech.jaredezz.raidrecorder.model.CoachFinding;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.model.RoomRecord;
import tech.jaredezz.raidrecorder.model.Severity;

/** Flags boss rooms where DPS uptime fell below the KC-adjusted expectation; praises high uptime. */
public class DpsUptimeRule implements CoachRule
{
	@Override
	public String id()
	{
		return "dps_uptime";
	}

	@Override
	public List<CoachFinding> evaluate(RaidRecord record, CoachContext context)
	{
		List<CoachFinding> findings = new ArrayList<>();
		for (RoomRecord room : record.getRooms())
		{
			if (!context.getModule().isBossRoom(room.getRoom()))
			{
				continue;
			}
			Map<String, Double> benchmarks = context.getModule()
				.benchmarks(room.getRoom(), record.getContext().getKc(), context.getAccountType());
			double warnAt = benchmarks.getOrDefault("dpsUptimeWarnPct",
				context.getThresholds().getDpsUptimeWarnPct())
				- context.getKcBand().getUptimeSlackPct();
			double goodAt = context.getThresholds().getDpsUptimeGoodPct();
			double uptime = room.getDpsUptimePct();

			// Findings report whole-percent figures (%.0f), so every boundary decision is made at
			// that same precision. Otherwise a room whose raw uptime is fractionally under the bar
			// (33.83 vs an expected 34.0) fires a WARN that reads as self-contradictory once both
			// sides round to "34%". Comparing the rounded values keeps the verdict consistent with
			// what the player is actually shown.
			long uptimeShown = Math.round(uptime);

			if (uptimeShown < Math.round(warnAt))
			{
				findings.add(new CoachFinding(room.getRoom(), Severity.WARN, "dps_uptime_low",
					String.format("DPS uptime in %s was %.0f%% (expected ≥%.0f%% at your KC) — "
						+ "look at the downtime windows for where attacks stopped.",
						room.getRoom(), uptime, warnAt),
					ImmutableMap.of("uptimePct", uptime, "warnThresholdPct", warnAt,
						"downtimeWindows", room.getDowntimeWindows().size())));
			}
			else if (uptimeShown >= Math.round(goodAt))
			{
				findings.add(new CoachFinding(room.getRoom(), Severity.GOOD, "dps_uptime_good",
					String.format("Strong DPS uptime in %s: %.0f%%.", room.getRoom(), uptime),
					ImmutableMap.of("uptimePct", uptime, "goodThresholdPct", goodAt)));
			}
		}
		return findings;
	}
}
