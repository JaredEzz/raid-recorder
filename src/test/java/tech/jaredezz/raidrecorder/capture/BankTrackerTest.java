package tech.jaredezz.raidrecorder.capture;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the pure placeholder-filtering logic that decides whether a bank slot counts as a real
 * owned item. The client-thread {@code onItemContainerChanged} plumbing is not unit tested (it needs
 * a live client), but the decision that produced the confirmed-wrong "you own a twisted bow" advice
 * lives entirely in {@link BankTracker#isRealOwnedItem}.
 */
public class BankTrackerTest
{
	// getPlaceholderTemplateId() sentinels: 14401 == placeholder, -1 == real item.
	private static final int PLACEHOLDER = 14401;
	private static final int REAL = -1;

	@Test
	public void realItemWithStockCounts()
	{
		// A twisted bow the player actually owns.
		assertTrue(BankTracker.isRealOwnedItem(20997, 1, REAL));
		assertTrue(BankTracker.isRealOwnedItem(20997, 100, REAL));
	}

	@Test
	public void placeholderNeverCounts()
	{
		// The reported bug: a twisted bow placeholder must not count as owned, whatever quantity the
		// bank container reports for it.
		assertFalse(BankTracker.isRealOwnedItem(20997, 1, PLACEHOLDER));
		assertFalse(BankTracker.isRealOwnedItem(20997, 0, PLACEHOLDER));
	}

	@Test
	public void emptyOrInvalidSlotsNeverCount()
	{
		assertFalse(BankTracker.isRealOwnedItem(-1, 0, REAL));
		assertFalse(BankTracker.isRealOwnedItem(0, 0, REAL));
		assertFalse(BankTracker.isRealOwnedItem(20997, 0, REAL));
	}
}
