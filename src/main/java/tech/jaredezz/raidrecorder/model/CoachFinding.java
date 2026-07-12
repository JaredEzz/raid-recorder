package tech.jaredezz.raidrecorder.model;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** One deterministic coach finding: a rule verdict plus the numbers that justify it. */
@AllArgsConstructor
@Getter
public class CoachFinding
{
	/** Room key the finding applies to, or "RAID" for raid-wide findings. */
	private final String room;
	private final Severity severity;
	/** Stable machine-readable rule id, e.g. "dps_uptime_low". */
	private final String rule;
	/** One-line human explanation. */
	private final String message;
	/** The numeric evidence behind the finding (threshold, observed value, etc.). */
	private final Map<String, Object> evidence;
}
