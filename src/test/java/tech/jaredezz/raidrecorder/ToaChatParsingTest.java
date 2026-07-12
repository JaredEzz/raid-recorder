package tech.jaredezz.raidrecorder;

import java.util.Arrays;
import org.junit.Test;
import tech.jaredezz.raidrecorder.raid.RaidModule;
import tech.jaredezz.raidrecorder.raid.toa.ToaModule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ToaChatParsingTest
{
	private final ToaModule module = newModule();

	private static ToaModule newModule()
	{
		try
		{
			java.lang.reflect.Constructor<ToaModule> ctor =
				ToaModule.class.getDeclaredConstructor(
					tech.jaredezz.raidrecorder.raid.toa.ToaInvocationReader.class,
					RaidRecorderConfig.class);
			ctor.setAccessible(true);
			return ctor.newInstance(new Object[]{null, null});
		}
		catch (ReflectiveOperationException e)
		{
			throw new AssertionError(e);
		}
	}

	@Test
	public void parsesRoomCompletion()
	{
		RaidModule.ChatEvent event = module.onChatMessage(
			"Challenge complete: Akkha. Duration: 3:44.40");
		assertNotNull(event);
		assertEquals(RaidModule.ChatEvent.Type.ROOM_COMPLETED, event.type);
		assertEquals("AKKHA", event.roomKey);
		// 3:44.4 = 224.4 s / 0.6 = 374 ticks
		assertEquals(374, event.officialTicks);
	}

	@Test
	public void parsesPathCompletion()
	{
		RaidModule.ChatEvent event = module.onChatMessage(
			"Challenge complete: Path of Het. Duration: 2:29");
		assertNotNull(event);
		assertEquals("PUZZLE_HET", event.roomKey);
	}

	@Test
	public void parsesBaBa()
	{
		RaidModule.ChatEvent event = module.onChatMessage(
			"Challenge complete: Path of Apmeken. Duration: 1:01");
		assertNotNull(event);
		assertEquals("PUZZLE_APMEKEN", event.roomKey);

		event = module.onChatMessage("Challenge complete: Ba-Ba. Duration: 4:10.20");
		assertNotNull(event);
		assertEquals("BABA", event.roomKey);
	}

	@Test
	public void parsesWardens()
	{
		RaidModule.ChatEvent event = module.onChatMessage(
			"Challenge complete: The Wardens. Tombs of Amascut challenge completion time: 24:19.80. Personal best: 22:01");
		assertNotNull(event);
		assertEquals(RaidModule.ChatEvent.Type.ROOM_COMPLETED, event.type);
		assertEquals("WARDENS_P3", event.roomKey);
	}

	@Test
	public void parsesKc()
	{
		RaidModule.ChatEvent event = module.onChatMessage(
			"Your completed Tombs of Amascut count is: 123.");
		assertNotNull(event);
		assertEquals(RaidModule.ChatEvent.Type.KC_LEARNED, event.type);
		assertEquals(123, event.kc);
	}

	@Test
	public void parsesRaidStartAndWipe()
	{
		assertEquals(RaidModule.ChatEvent.Type.RAID_STARTED,
			module.onChatMessage("You enter the Tombs of Amascut... good luck!").type);
		assertEquals(RaidModule.ChatEvent.Type.RAID_WIPED,
			module.onChatMessage("Your party failed to complete the challenge in time.").type);
	}

	@Test
	public void ignoresUnrelatedChat()
	{
		assertNull(module.onChatMessage("Welcome to Old School RuneScape."));
		assertNull(module.onChatMessage("Challenge complete: Some Unknown Room. Duration: 1:00"));
	}

	// Wiki-sourced recommended styles (verified 2026-07-12; see KNOWN_UNKNOWNS.md #13). These lock
	// in the fact-check: Ba-Ba is melee-only, Zebak ranged/magic, Akkha magic/ranged, and Kephri +
	// the Wardens carry NO single-style claim because their styles are genuinely phase-split.

	@Test
	public void baBaIsMeleeOnly()
	{
		// Was wrongly "MELEE, RANGED" — Ba-Ba is a melee (stab) DPS fight.
		assertEquals(Arrays.asList("MELEE"), module.recommendedStyles("BABA"));
	}

	@Test
	public void zebakIsRangedThenMagic()
	{
		assertEquals(Arrays.asList("RANGED", "MAGIC"), module.recommendedStyles("ZEBAK"));
	}

	@Test
	public void akkhaIsMagicThenRanged()
	{
		// Was "no opinion" — Akkha's magic defence (+10) makes magic/ranged the recommended styles.
		assertEquals(Arrays.asList("MAGIC", "RANGED"), module.recommendedStyles("AKKHA"));
	}

	@Test
	public void kephriHasNoSingleStyle()
	{
		// The headline fix: Kephri is two-phase (RANGED swarms, MELEE boss), so any single-style
		// claim is wrong. Empty = "no opinion" disables the wrong-style rule for the room.
		assertTrue(module.recommendedStyles("KEPHRI").isEmpty());
	}

	@Test
	public void wardensHaveNoSingleStyle()
	{
		// Phase-gated (P2 ranged then melee-on-core, P3 mixed) — no single dominant style.
		assertTrue(module.recommendedStyles("WARDENS_P1_P2").isEmpty());
		assertTrue(module.recommendedStyles("WARDENS_P3").isEmpty());
	}

	@Test
	public void parsesTotalCompletion()
	{
		RaidModule.ChatEvent event = module.onChatMessage(
			"Tombs of Amascut total completion time: 27:01.20. Personal best: 25:23");
		assertNotNull(event);
		assertEquals(RaidModule.ChatEvent.Type.RAID_COMPLETED, event.type);
		assertEquals(2702, event.officialTicks);
	}
}
