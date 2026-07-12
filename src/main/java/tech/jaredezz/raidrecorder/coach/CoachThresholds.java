package tech.jaredezz.raidrecorder.coach;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Editable coach thresholds, loaded from coach-thresholds.json (a default is written to the
 * export directory on first run — edit it there). All values are the *base* expectation for an
 * experienced player; KC bands relax them for learners.
 */
@Getter
@Setter
public class CoachThresholds
{
	private double dpsUptimeWarnPct = 55;
	private double dpsUptimeGoodPct = 75;
	private int timeToFirstHitWarnTicks = 12;
	private int avoidableDamageWarnPerRoom = 30;
	private int avoidableDamageCriticalPerRoom = 70;
	private int downtimeWindowWarnTicks = 25;
	/** A room where supplies consumed (in doses) exceed this while most damage taken was avoidable gets flagged. */
	private int supplyHeavyDosesPerRoom = 6;
	/** Share of damage dealt on a non-recommended style before the wrong-style rule fires (0..1). */
	private double wrongStyleDamageShareWarn = 0.3;

	private List<KcBand> kcBands = defaultKcBands();

	@Getter
	@Setter
	public static class KcBand
	{
		/** Band applies while kc <= maxKc. */
		private int maxKc;
		/** Subtracted from dpsUptimeWarnPct. */
		private double uptimeSlackPct;
		/** Multiplies the avoidable-damage thresholds. */
		private double avoidableMultiplier;
		/** Added to timeToFirstHitWarnTicks. */
		private int firstHitSlackTicks;

		public KcBand()
		{
		}

		public KcBand(int maxKc, double uptimeSlackPct, double avoidableMultiplier, int firstHitSlackTicks)
		{
			this.maxKc = maxKc;
			this.uptimeSlackPct = uptimeSlackPct;
			this.avoidableMultiplier = avoidableMultiplier;
			this.firstHitSlackTicks = firstHitSlackTicks;
		}
	}

	public static List<KcBand> defaultKcBands()
	{
		List<KcBand> bands = new ArrayList<>();
		bands.add(new KcBand(10, 20, 2.5, 10));
		bands.add(new KcBand(50, 12, 1.8, 6));
		bands.add(new KcBand(150, 6, 1.3, 3));
		bands.add(new KcBand(Integer.MAX_VALUE, 0, 1.0, 0));
		return bands;
	}

	/** The band for a kill count; unknown kc (-1) is treated as the most lenient band. */
	public KcBand bandFor(int kc)
	{
		if (kcBands == null || kcBands.isEmpty())
		{
			return new KcBand(Integer.MAX_VALUE, 0, 1.0, 0);
		}
		if (kc < 0)
		{
			return kcBands.get(0);
		}
		for (KcBand band : kcBands)
		{
			if (kc <= band.getMaxKc())
			{
				return band;
			}
		}
		return kcBands.get(kcBands.size() - 1);
	}
}
