package tech.jaredezz.raidrecorder.history;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One logged Tombs of Amascut raid: the compact cross-raid summary, distinct from the full
 * per-raid black-box record. Persisted as an unbounded JSON array (see {@link RaidHistoryStore})
 * rather than through {@code ConfigManager}, mirroring the earlier toa-raid-log plugin this
 * history feature was ported from.
 */
@AllArgsConstructor
@Getter
public class RaidHistoryEntry
{
	/** Epoch millis when the raid finished. */
	private final long timestamp;
	private final int raidLevel;
	/** Bitmask of {@code ToaInvocation} ordinals that were active. */
	private final long invocationMask;
	private final boolean purple;
	/** Personal contribution points, read from the authoritative end-of-raid varp. */
	private final int points;
	private final int kc;
	private final int deaths;
	private final long durationMs;
	private final int partySize;
}
