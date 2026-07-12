package tech.jaredezz.raidrecorder.raid.toa;

import org.junit.Test;
import tech.jaredezz.raidrecorder.raid.MechanicContext;
import tech.jaredezz.raidrecorder.raid.MechanicTag;

import static org.junit.Assert.assertEquals;

public class ToaMechanicsTest
{
	private static MechanicContext.MechanicContextBuilder base(String room)
	{
		return MechanicContext.builder()
			.room(room)
			.sourceNpcId(-1)
			.sourceAnimation(-1)
			.playerGraphic(-1)
			.hitsplatType(-1)
			.tileGraphicsObjectId(-1)
			.playerTargetedProjectileId(-1);
	}

	@Test
	public void tagsGraphicsHazardInItsOwningRoom()
	{
		// Kephri bomb shadow (1447) in the Kephri fight is the real thing.
		MechanicTag tag = ToaMechanics.classify(base(ToaRooms.KEPHRI).tileGraphicsObjectId(1447).build());
		assertEquals("KEPHRI_BOMB", tag.getMechanic());
	}

	@Test
	public void ignoresGraphicsHazardFromWrongRoom()
	{
		// Same Kephri bomb id observed firing inside Ba-Ba on 2026-07-12 must NOT tag as KEPHRI_BOMB;
		// with no NPC/hitsplat context it falls through to UNKNOWN rather than a cross-room mislabel.
		MechanicTag tag = ToaMechanics.classify(base(ToaRooms.BABA).tileGraphicsObjectId(1447).build());
		assertEquals("UNKNOWN", tag.getMechanic());
	}

	@Test
	public void tagsProjectileInItsOwningRoom()
	{
		// Wardens P3 ghost projectile (2253) during the P3 fight is legitimate.
		MechanicTag tag = ToaMechanics.classify(base(ToaRooms.WARDENS_P3).playerTargetedProjectileId(2253).build());
		assertEquals("WARDENS_P3_GHOST_ATTACK", tag.getMechanic());
	}

	@Test
	public void ignoresProjectileFromWrongRoom()
	{
		// The bug: WARDENS_P3_GHOST_ATTACK id (2253) fired during Zebak, mislabeling all of Zebak's
		// damage as an avoidable Wardens mechanic. It must not classify as the Wardens ghost attack.
		MechanicTag tag = ToaMechanics.classify(base(ToaRooms.ZEBAK).playerTargetedProjectileId(2253).build());
		assertEquals("UNKNOWN", tag.getMechanic());
	}

	@Test
	public void tagsNewCrondisWaterHazard()
	{
		MechanicTag tag = ToaMechanics.classify(base(ToaRooms.PUZZLE_CRONDIS).tileGraphicsObjectId(2129).build());
		assertEquals("CRONDIS_WATER_HAZARD", tag.getMechanic());
		assertEquals(true, tag.isAvoidable());
	}
}
