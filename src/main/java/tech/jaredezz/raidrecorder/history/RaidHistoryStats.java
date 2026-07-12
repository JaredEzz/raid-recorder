package tech.jaredezz.raidrecorder.history;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import tech.jaredezz.raidrecorder.raid.toa.ToaInvocation;

/**
 * Aggregate stats computed from a raid history: totals/averages and, per invocation, how many
 * logged raids included it. Pure data + math (no client/Swing imports) so it's unit-testable.
 */
public final class RaidHistoryStats
{
	public final int totalRaids;
	public final int purpleCount;
	public final double purpleRatePct;
	public final double avgPoints;
	public final double avgDeaths;
	public final long avgDurationMs;
	/** Per invocation, how often it showed up across the log, sorted most-common first. */
	public final List<InvocationFrequency> invocationFrequencies;

	private RaidHistoryStats(int totalRaids, int purpleCount, double purpleRatePct, double avgPoints,
		double avgDeaths, long avgDurationMs, List<InvocationFrequency> invocationFrequencies)
	{
		this.totalRaids = totalRaids;
		this.purpleCount = purpleCount;
		this.purpleRatePct = purpleRatePct;
		this.avgPoints = avgPoints;
		this.avgDeaths = avgDeaths;
		this.avgDurationMs = avgDurationMs;
		this.invocationFrequencies = invocationFrequencies;
	}

	public static final class InvocationFrequency
	{
		public final ToaInvocation invocation;
		public final int raidCount;
		public final double percentage;

		InvocationFrequency(ToaInvocation invocation, int raidCount, double percentage)
		{
			this.invocation = invocation;
			this.raidCount = raidCount;
			this.percentage = percentage;
		}
	}

	public static final RaidHistoryStats EMPTY = new RaidHistoryStats(0, 0, 0, 0, 0, 0, new ArrayList<>());

	public static RaidHistoryStats compute(List<RaidHistoryEntry> log)
	{
		if (log == null || log.isEmpty())
		{
			return EMPTY;
		}

		int purple = 0;
		long pointsSum = 0;
		long deathsSum = 0;
		long durationSum = 0;
		int[] invocationCounts = new int[ToaInvocation.values().length];

		for (RaidHistoryEntry entry : log)
		{
			if (entry.isPurple())
			{
				purple++;
			}
			pointsSum += entry.getPoints();
			deathsSum += entry.getDeaths();
			durationSum += entry.getDurationMs();
			for (ToaInvocation inv : ToaInvocation.fromBitmask(entry.getInvocationMask()))
			{
				invocationCounts[inv.ordinal()]++;
			}
		}

		int total = log.size();
		List<InvocationFrequency> frequencies = new ArrayList<>();
		for (ToaInvocation inv : ToaInvocation.values())
		{
			int count = invocationCounts[inv.ordinal()];
			if (count == 0)
			{
				continue;
			}
			frequencies.add(new InvocationFrequency(inv, count, 100.0 * count / total));
		}
		frequencies.sort(Comparator.<InvocationFrequency>comparingInt(f -> f.raidCount).reversed()
			.thenComparing(f -> f.invocation.getDisplayName()));

		return new RaidHistoryStats(total, purple, 100.0 * purple / total, (double) pointsSum / total,
			(double) deathsSum / total, durationSum / total, frequencies);
	}
}
