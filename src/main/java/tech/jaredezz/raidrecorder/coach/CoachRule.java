package tech.jaredezz.raidrecorder.coach;

import java.util.List;
import tech.jaredezz.raidrecorder.model.CoachFinding;
import tech.jaredezz.raidrecorder.model.RaidRecord;

/**
 * One deterministic coaching rule. Rules are pure functions of (record, context) — no client
 * access, no I/O — so they can run on the background executor and be unit-tested with synthetic
 * records.
 */
public interface CoachRule
{
	/** Stable rule id prefix used in findings, e.g. "dps_uptime". */
	String id();

	List<CoachFinding> evaluate(RaidRecord record, CoachContext context);
}
