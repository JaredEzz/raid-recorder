package tech.jaredezz.raidrecorder.raid.toa;

import net.runelite.api.HitsplatID;
import net.runelite.api.gameval.NpcID;
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

	@Test
	public void akkhaShadowSlamIsAvoidable()
	{
		// Wiki-corrected 2026-07-12: Akkha's Shadow's quadrant element slam is dodgeable (kill the shadow
		// before its bar fills, or step out of the quadrant). Previously wrongly tagged unavoidable.
		MechanicTag tag = ToaMechanics.classify(base(ToaRooms.AKKHA).sourceNpcId(NpcID.AKKHA_SHADOW).build());
		assertEquals("AKKHA_SHADOW_ATTACK", tag.getMechanic());
		assertEquals(true, tag.isAvoidable());
	}

	@Test
	public void akkhaTrailOrbIsAvoidableButEnrageOrbIsNot()
	{
		// The elemental trail orbs are a step-on-them positioning mistake (avoidable)...
		MechanicTag trail = ToaMechanics.classify(
			base(ToaRooms.AKKHA).sourceNpcId(NpcID.AKKHA_TRAIL_ORB_LIGHTNING).build());
		assertEquals("AKKHA_ELEMENTAL_ORB", trail.getMechanic());
		assertEquals(true, trail.isAvoidable());

		// ...but the enrage-phase white orbs are capped chip that hits regardless of positioning.
		MechanicTag enrage = ToaMechanics.classify(
			base(ToaRooms.AKKHA).sourceNpcId(NpcID.AKKHA_ENRAGE_ORB).build());
		assertEquals("AKKHA_ENRAGE_ORB", enrage.getMechanic());
		assertEquals(false, enrage.isAvoidable());
	}

	@Test
	public void poisonIsAvoidableInZebakAndCrondisButNotKephri()
	{
		// Zebak acid pools and Crondis waterfall poison clouds are dodgeable ground hazards.
		assertEquals(true, ToaMechanics.classify(
			base(ToaRooms.ZEBAK).hitsplatType(HitsplatID.POISON).build()).isAvoidable());
		assertEquals(true, ToaMechanics.classify(
			base(ToaRooms.PUZZLE_CRONDIS).hitsplatType(HitsplatID.POISON).build()).isAvoidable());
		// Kephri's Spitting-scarab poison "can hit through prayers" — not a clean dodge, stays unavoidable.
		MechanicTag kephri = ToaMechanics.classify(
			base(ToaRooms.KEPHRI).hitsplatType(HitsplatID.POISON).build());
		assertEquals("POISON", kephri.getMechanic());
		assertEquals(false, kephri.isAvoidable());
	}
}
