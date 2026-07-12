package tech.jaredezz.raidrecorder;

import org.junit.Test;
import tech.jaredezz.raidrecorder.capture.LiveDpsTracker;

import static org.junit.Assert.assertEquals;

public class LiveDpsTrackerTest
{
	@Test
	public void sessionDpsIsTotalOverElapsedTime()
	{
		LiveDpsTracker tracker = new LiveDpsTracker();
		long start = 1_000_000L;
		tracker.start(start);
		tracker.record(start + 1000, 50);
		tracker.record(start + 2000, 50);
		// 100 damage over 2 seconds elapsed = 50 dps
		assertEquals(50.0, tracker.sessionDps(start + 2000), 0.01);
	}

	@Test
	public void rollingDpsPurgesOldEntries()
	{
		LiveDpsTracker tracker = new LiveDpsTracker();
		long start = 1_000_000L;
		tracker.start(start);
		tracker.record(start, 100);
		// 15s later, the window (10s) should have purged the old hit entirely.
		assertEquals(0.0, tracker.rollingDps(start + 15_000), 0.01);
	}

	@Test
	public void rollingDpsReflectsRecentWindow()
	{
		LiveDpsTracker tracker = new LiveDpsTracker();
		long start = 1_000_000L;
		tracker.start(start);
		tracker.record(start, 100);
		tracker.record(start + 2000, 100);
		double dps = tracker.rollingDps(start + 2000);
		// 200 damage over a 2s window = 100 dps
		assertEquals(100.0, dps, 0.01);
	}
}
