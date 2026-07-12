package tech.jaredezz.raidrecorder.coach;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tech.jaredezz.raidrecorder.model.AccountType;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.raid.RaidModule;

/** Everything a rule may condition on beyond the record itself. */
@AllArgsConstructor
@Getter
public class CoachContext
{
	private final CoachThresholds thresholds;
	private final CoachThresholds.KcBand kcBand;
	private final AccountType accountType;
	/** Latest bank snapshot, possibly null (bank never opened this session). */
	private final RaidRecord.BankSnapshot bank;
	/** The module that produced the record — source of per-room benchmarks. */
	private final RaidModule module;
}
