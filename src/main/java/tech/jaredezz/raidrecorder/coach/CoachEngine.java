package tech.jaredezz.raidrecorder.coach;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import tech.jaredezz.raidrecorder.coach.rules.AvoidableDamageRule;
import tech.jaredezz.raidrecorder.coach.rules.DeathsRule;
import tech.jaredezz.raidrecorder.coach.rules.DowntimeRule;
import tech.jaredezz.raidrecorder.coach.rules.DpsUptimeRule;
import tech.jaredezz.raidrecorder.coach.rules.OwnedButUnusedUpgradeRule;
import tech.jaredezz.raidrecorder.coach.rules.SupplyUsageRule;
import tech.jaredezz.raidrecorder.coach.rules.TimeToFirstHitRule;
import tech.jaredezz.raidrecorder.coach.rules.WrongStyleRule;
import tech.jaredezz.raidrecorder.model.CoachFinding;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.raid.RaidModule;

/**
 * Runs every rule over a completed record. Pure computation plus one read of the thresholds file —
 * always call from the background executor, never the client thread.
 */
@Slf4j
@Singleton
public class CoachEngine
{
	private final Gson gson;

	private final List<CoachRule> rules = ImmutableList.of(
		new DeathsRule(),
		new AvoidableDamageRule(),
		new DpsUptimeRule(),
		new TimeToFirstHitRule(),
		new DowntimeRule(),
		new WrongStyleRule(),
		new SupplyUsageRule(),
		new OwnedButUnusedUpgradeRule()
	);

	@Inject
	CoachEngine(Gson gson)
	{
		this.gson = gson;
	}

	/**
	 * Evaluate all rules; findings are ordered CRITICAL→WARN→INFO→GOOD within record order.
	 * The thresholds file is (re)read each raid so edits apply without a client restart; a missing
	 * or unparsable file falls back to defaults and is written out for the user to edit.
	 */
	public List<CoachFinding> evaluate(RaidRecord record, RaidModule module, Path thresholdsFile)
	{
		CoachThresholds thresholds = loadOrCreateThresholds(thresholdsFile);
		CoachContext context = new CoachContext(
			thresholds,
			thresholds.bandFor(record.getContext().getKc()),
			record.getAccount().getType(),
			record.getBankSnapshot(),
			module);

		List<CoachFinding> findings = new ArrayList<>();
		for (CoachRule rule : rules)
		{
			try
			{
				findings.addAll(rule.evaluate(record, context));
			}
			catch (RuntimeException e)
			{
				log.warn("coach rule {} failed", rule.id(), e);
			}
		}
		// CRITICAL → WARN → INFO → GOOD (enum ordinal is GOOD..CRITICAL, so invert; GOOD sinks last).
		findings.sort(Comparator.comparingInt(f ->
			f.getSeverity() == tech.jaredezz.raidrecorder.model.Severity.GOOD
				? 3 : 3 - f.getSeverity().ordinal()));
		return findings;
	}

	private CoachThresholds loadOrCreateThresholds(Path file)
	{
		try
		{
			if (Files.exists(file))
			{
				String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
				CoachThresholds loaded = gson.fromJson(json, CoachThresholds.class);
				if (loaded != null)
				{
					return loaded;
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("could not read coach thresholds from {}, using defaults", file, e);
		}

		CoachThresholds defaults = new CoachThresholds();
		try
		{
			Files.createDirectories(file.getParent());
			Files.write(file, gson.toJson(defaults).getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			log.warn("could not write default coach thresholds to {}", file, e);
		}
		return defaults;
	}
}
