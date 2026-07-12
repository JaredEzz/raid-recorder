package tech.jaredezz.raidrecorder;

import org.junit.Test;
import tech.jaredezz.raidrecorder.capture.LiveHitTracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveHitTrackerTest
{
	@Test
	public void firstHitIsFlaggedAndFullLuck()
	{
		LiveHitTracker tracker = new LiveHitTracker();
		LiveHitTracker.Result result = tracker.record("Osmumten's fang", 42);
		assertTrue(result.isFirst);
		assertFalse(result.newMax);
		assertEquals(0, result.priorMax);
		assertEquals(100.0, result.luckPct, 0.001);
	}

	@Test
	public void higherHitIsNewMax()
	{
		LiveHitTracker tracker = new LiveHitTracker();
		tracker.record("Osmumten's fang", 42);
		LiveHitTracker.Result result = tracker.record("Osmumten's fang", 55);
		assertFalse(result.isFirst);
		assertTrue(result.newMax);
		assertEquals(42, result.priorMax);
		assertEquals(100.0, result.luckPct, 0.001);
	}

	@Test
	public void lowerHitComputesLuckAgainstPriorMax()
	{
		LiveHitTracker tracker = new LiveHitTracker();
		tracker.record("Osmumten's fang", 60);
		LiveHitTracker.Result result = tracker.record("Osmumten's fang", 30);
		assertFalse(result.isFirst);
		assertFalse(result.newMax);
		assertEquals(60, result.priorMax);
		assertEquals(50.0, result.luckPct, 0.001);
	}

	@Test
	public void tracksMaxSeparatelyPerWeapon()
	{
		LiveHitTracker tracker = new LiveHitTracker();
		tracker.record("Osmumten's fang", 60);
		LiveHitTracker.Result result = tracker.record("Twisted bow", 45);
		assertTrue(result.isFirst);
	}

	@Test
	public void resetClearsAllWeapons()
	{
		LiveHitTracker tracker = new LiveHitTracker();
		tracker.record("Osmumten's fang", 60);
		tracker.reset();
		LiveHitTracker.Result result = tracker.record("Osmumten's fang", 10);
		assertTrue(result.isFirst);
	}
}
