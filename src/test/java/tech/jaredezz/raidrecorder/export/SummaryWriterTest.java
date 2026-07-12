package tech.jaredezz.raidrecorder.export;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import tech.jaredezz.raidrecorder.model.AccountType;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.raid.MechanicContext;
import tech.jaredezz.raidrecorder.raid.MechanicTag;
import tech.jaredezz.raidrecorder.raid.RaidModule;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SummaryWriterTest
{
	/** Stub module; render() only reads raidName(). */
	private static final RaidModule MODULE = new RaidModule()
	{
		@Override
		public String raidKey()
		{
			return "TEST";
		}

		@Override
		public String raidName()
		{
			return "Test Raid";
		}

		@Override
		public boolean isInRaid(net.runelite.api.Client client)
		{
			return false;
		}

		@Override
		public String classifyCurrentRoom(net.runelite.api.Client client)
		{
			return null;
		}

		@Override
		public boolean isBossRoom(String roomKey)
		{
			return true;
		}

		@Override
		public boolean isAttackableTarget(net.runelite.api.NPC npc, String roomKey)
		{
			return false;
		}

		@Override
		public String npcLabel(net.runelite.api.NPC npc)
		{
			return "npc";
		}

		@Override
		public MechanicTag classifyDamageTaken(MechanicContext context)
		{
			return MechanicTag.UNKNOWN;
		}

		@Override
		public ChatEvent onChatMessage(String message)
		{
			return null;
		}

		@Override
		public void populateContext(net.runelite.api.Client client, RaidRecord.Context context)
		{
		}

		@Override
		public Map<String, Double> benchmarks(String roomKey, int kc, AccountType accountType)
		{
			return ImmutableMap.of();
		}

		@Override
		public List<String> allRoomKeys()
		{
			return Arrays.asList("ROOM_A");
		}
	};

	private static RaidRecord baseRecord()
	{
		RaidRecord record = new RaidRecord();
		record.setRaid("TEST");
		record.getAccount().setRsn("tester");
		return record;
	}

	@Test
	public void rendersPointsAndPurple()
	{
		RaidRecord record = baseRecord();
		record.getContext().setPoints(19372);
		record.getContext().setPurple(false);

		String md = SummaryWriter.render(record, MODULE);
		assertTrue(md.contains("Points"));
		assertTrue(md.contains("19372"));
		assertTrue(md.contains("Purple"));
		assertTrue(md.contains("| no |"));
	}

	@Test
	public void purpleRendersYesWhenTrue()
	{
		RaidRecord record = baseRecord();
		record.getContext().setPurple(true);

		assertTrue(SummaryWriter.render(record, MODULE).contains("| yes |"));
	}

	@Test
	public void unknownPointsAndPurpleAreLabelled()
	{
		// Defaults: points sentinel -1, purple null (not applicable / not captured).
		String md = SummaryWriter.render(baseRecord(), MODULE);
		assertTrue(md.contains("| **Points** | unknown |"));
		assertTrue(md.contains("| **Purple** | unknown |"));
		assertFalse(md.contains("| **Points** | -1 |"));
	}
}
