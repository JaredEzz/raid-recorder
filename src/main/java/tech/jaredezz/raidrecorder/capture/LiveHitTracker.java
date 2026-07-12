package tech.jaredezz.raidrecorder.capture;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks, per weapon, the highest hit dealt so far this raid — an empirical running "max hit"
 * rather than one computed from combat formulas. Simpler and more robust than a full max-hit
 * calculator would be (which would need to model every invocation's effect on damage output), at
 * the cost of needing a first hit per weapon before "luck" is meaningful.
 */
public class LiveHitTracker
{
	private final Map<String, Integer> maxByWeapon = new LinkedHashMap<>();

	public Result record(String weapon, int amount)
	{
		Integer priorMax = maxByWeapon.get(weapon);
		boolean isFirst = priorMax == null;
		boolean newMax = !isFirst && amount > priorMax;
		double luckPct = isFirst || newMax ? 100.0 : Math.min(100.0, 100.0 * amount / priorMax);
		maxByWeapon.merge(weapon, amount, Math::max);
		return new Result(amount, isFirst ? 0 : priorMax, isFirst, newMax, luckPct);
	}

	public void reset()
	{
		maxByWeapon.clear();
	}

	public static class Result
	{
		public final int amount;
		/** The running max before this hit; 0 when {@link #isFirst}. */
		public final int priorMax;
		/** True when this is the first tracked hit with this weapon this raid. */
		public final boolean isFirst;
		/** True when this hit set a new running max (and wasn't the first hit). */
		public final boolean newMax;
		/** 0-100; against the prior max, or 100 for a first hit / new max. */
		public final double luckPct;

		Result(int amount, int priorMax, boolean isFirst, boolean newMax, double luckPct)
		{
			this.amount = amount;
			this.priorMax = priorMax;
			this.isFirst = isFirst;
			this.newMax = newMax;
			this.luckPct = luckPct;
		}
	}
}
