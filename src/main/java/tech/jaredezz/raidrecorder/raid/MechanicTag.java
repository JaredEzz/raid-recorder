package tech.jaredezz.raidrecorder.raid;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** A classified damage-taken event: which named mechanic hit the player, and whether it was dodgeable. */
@AllArgsConstructor
@Getter
public class MechanicTag
{
	public static final MechanicTag UNKNOWN = new MechanicTag("UNKNOWN", false);

	/** Stable mechanic key from the module taxonomy, e.g. "AKKHA_QUADRANT_SPECIAL". */
	private final String mechanic;
	/** True when the mechanic is dodgeable with correct play — feeds the avoidable-damage rules. */
	private final boolean avoidable;
}
