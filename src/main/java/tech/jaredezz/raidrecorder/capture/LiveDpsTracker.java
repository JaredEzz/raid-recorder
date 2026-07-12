package tech.jaredezz.raidrecorder.capture;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Rolling + session DPS from damage-dealt events, for the live feed. Rolling window gives a
 * "right now" feel; session average is total damage over total elapsed time since the raid
 * started. Updates on each hit rather than ticking down between hits — good enough for a feed
 * that's driven by hit events, without needing a repeating UI timer.
 */
public class LiveDpsTracker
{
	private static final long ROLLING_WINDOW_MS = 10_000;

	private final Deque<long[]> recent = new ArrayDeque<>();
	private long sessionStartMs;
	private long sessionTotal;

	public void start(long epochMs)
	{
		recent.clear();
		sessionStartMs = epochMs;
		sessionTotal = 0;
	}

	public void record(long epochMs, int amount)
	{
		recent.addLast(new long[]{epochMs, amount});
		sessionTotal += amount;
		purge(epochMs);
	}

	private void purge(long nowMs)
	{
		while (!recent.isEmpty() && nowMs - recent.peekFirst()[0] > ROLLING_WINDOW_MS)
		{
			recent.removeFirst();
		}
	}

	/** Damage-per-second over the trailing window ending at {@code nowMs}. */
	public double rollingDps(long nowMs)
	{
		purge(nowMs);
		if (recent.isEmpty())
		{
			return 0;
		}
		long sum = 0;
		for (long[] entry : recent)
		{
			sum += entry[1];
		}
		long windowMs = Math.max(1000, nowMs - recent.peekFirst()[0]);
		return 1000.0 * sum / windowMs;
	}

	public double sessionDps(long nowMs)
	{
		long elapsed = Math.max(1000, nowMs - sessionStartMs);
		return 1000.0 * sessionTotal / elapsed;
	}
}
