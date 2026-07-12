package tech.jaredezz.raidrecorder.raid;

import lombok.Builder;
import lombok.Getter;
import net.runelite.api.coords.WorldPoint;

/**
 * Everything the capture engine knows about a damage-taken hitsplat at the moment it lands,
 * handed to the raid module for mechanic classification. All fields are nullable/-1 when unknown.
 */
@Builder
@Getter
public class MechanicContext
{
	/** Room key the player is in. */
	private final String room;
	/** NPC id of the probable source (the NPC interacting with the player), -1 if none. */
	private final int sourceNpcId;
	/** Animation the source NPC was playing on the damage tick, -1 if unknown. */
	private final int sourceAnimation;
	/** Graphic (spotanim) active on the local player, -1 if none. */
	private final int playerGraphic;
	/** Hitsplat type id (net.runelite.api.HitsplatID). */
	private final int hitsplatType;
	/** Where the player was standing. */
	private final WorldPoint playerLocation;
	/** Id of a graphics object on the player's tile this tick, -1 if none. */
	private final int tileGraphicsObjectId;
	/** Id of the most recent projectile targeting the player (within a few ticks), -1 if none. */
	private final int playerTargetedProjectileId;
}
