package tech.jaredezz.raidrecorder;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import tech.jaredezz.raidrecorder.coach.CoachContext;
import tech.jaredezz.raidrecorder.coach.CoachThresholds;
import tech.jaredezz.raidrecorder.coach.rules.AvoidableDamageRule;
import tech.jaredezz.raidrecorder.coach.rules.DeathsRule;
import tech.jaredezz.raidrecorder.coach.rules.DpsUptimeRule;
import tech.jaredezz.raidrecorder.coach.rules.OwnedButUnusedUpgradeRule;
import tech.jaredezz.raidrecorder.model.AccountType;
import tech.jaredezz.raidrecorder.model.CoachFinding;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.model.RoomRecord;
import tech.jaredezz.raidrecorder.model.Severity;
import tech.jaredezz.raidrecorder.raid.MechanicContext;
import tech.jaredezz.raidrecorder.raid.MechanicTag;
import tech.jaredezz.raidrecorder.raid.RaidModule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CoachEngineTest
{
	/** Minimal fake module: every room is a boss room, no benchmarks. */
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
		public java.util.Map<String, Double> benchmarks(String roomKey, int kc, AccountType accountType)
		{
			return ImmutableMap.of();
		}

		@Override
		public List<String> allRoomKeys()
		{
			return Arrays.asList("ROOM_A");
		}
	};

	private static CoachContext context(int kc)
	{
		CoachThresholds thresholds = new CoachThresholds();
		return new CoachContext(thresholds, thresholds.bandFor(kc), AccountType.GROUP_IRONMAN, null, MODULE);
	}

	private static RaidRecord recordWithRoom(RoomRecord room)
	{
		RaidRecord record = new RaidRecord();
		record.setRaid("TEST");
		record.getContext().setKc(200);
		record.getRooms().add(room);
		return record;
	}

	private static RoomRecord room(double uptime)
	{
		RoomRecord room = new RoomRecord();
		room.setRoom("ROOM_A");
		room.setOrder(1);
		room.setDpsUptimePct(uptime);
		return room;
	}

	@Test
	public void lowUptimeWarns()
	{
		List<CoachFinding> findings = new DpsUptimeRule().evaluate(recordWithRoom(room(30)), context(200));
		assertEquals(1, findings.size());
		assertEquals(Severity.WARN, findings.get(0).getSeverity());
		assertEquals("dps_uptime_low", findings.get(0).getRule());
	}

	@Test
	public void highUptimePraised()
	{
		List<CoachFinding> findings = new DpsUptimeRule().evaluate(recordWithRoom(room(85)), context(200));
		assertEquals(1, findings.size());
		assertEquals(Severity.GOOD, findings.get(0).getSeverity());
	}

	@Test
	public void lowKcGetsSlack()
	{
		// 45% uptime: fails the 55% bar at high KC, passes with the 20-point low-KC slack.
		assertEquals(1, new DpsUptimeRule().evaluate(recordWithRoom(room(45)), context(500)).size());
		assertTrue(new DpsUptimeRule().evaluate(recordWithRoom(room(45)), context(5)).isEmpty());
	}

	@Test
	public void uptimeThatRoundsToTheThresholdIsNotFlagged()
	{
		// 54.83% is fractionally under the 55% warn bar, but both render as "55%" in the summary.
		// The coach must not fire a WARN that reads as "55% (expected ≥55%)".
		assertTrue(new DpsUptimeRule().evaluate(recordWithRoom(room(54.83)), context(200)).isEmpty());
	}

	@Test
	public void avoidableDamageEscalates()
	{
		RoomRecord room = room(60);
		RoomRecord.DamageTakenEvent hit = new RoomRecord.DamageTakenEvent();
		hit.setAmount(80);
		hit.setAvoidable(true);
		hit.setMechanic("TEST_MECHANIC");
		room.getDamageTaken().add(hit);

		List<CoachFinding> findings = new AvoidableDamageRule()
			.evaluate(recordWithRoom(room), context(500));
		assertEquals(1, findings.size());
		assertEquals(Severity.CRITICAL, findings.get(0).getSeverity());
	}

	@Test
	public void deathlessRaidGetsGood()
	{
		List<CoachFinding> findings = new DeathsRule().evaluate(recordWithRoom(room(60)), context(200));
		assertEquals(1, findings.size());
		assertEquals(Severity.GOOD, findings.get(0).getSeverity());
		assertEquals("deathless", findings.get(0).getRule());
	}

	private static RaidRecord recordWithUsedWeapon(String weaponName)
	{
		RoomRecord room = room(60);
		RoomRecord.EquipmentSnapshot snapshot = new RoomRecord.EquipmentSnapshot();
		snapshot.getItems().put("WEAPON", new RoomRecord.Item(0, weaponName));
		room.getEquipmentTimeline().add(snapshot);
		return recordWithRoom(room);
	}

	private static CoachContext contextWithBank(String... bankItemNames)
	{
		CoachThresholds thresholds = new CoachThresholds();
		RaidRecord.BankSnapshot bank = new RaidRecord.BankSnapshot();
		int id = 1;
		for (String name : bankItemNames)
		{
			bank.getNames().put(String.valueOf(id++), name);
		}
		return new CoachContext(thresholds, thresholds.bandFor(200), AccountType.GROUP_IRONMAN, bank, MODULE);
	}

	@Test
	public void ownedUpgradeFiresWhenBankBeatsUsedWeapon()
	{
		// Raided with a Ghrazi rapier, but a Scythe of Vitur is sitting in the bank (higher rung).
		List<CoachFinding> findings = new OwnedButUnusedUpgradeRule()
			.evaluate(recordWithUsedWeapon("Ghrazi rapier"), contextWithBank("Scythe of Vitur"));
		assertEquals(1, findings.size());
		assertEquals("owned_but_unused_upgrade", findings.get(0).getRule());
		assertEquals(Severity.INFO, findings.get(0).getSeverity());
	}

	@Test
	public void soulreaperAxeIsRankedAboveFang()
	{
		// Locks in the added melee rung: an owned Soulreaper axe outranks a used Osmumten's fang.
		List<CoachFinding> findings = new OwnedButUnusedUpgradeRule()
			.evaluate(recordWithUsedWeapon("Osmumten's fang"), contextWithBank("Soulreaper axe"));
		assertEquals(1, findings.size());
		assertEquals("owned_but_unused_upgrade", findings.get(0).getRule());
	}

	@Test
	public void noUpgradeWhenUsedWeaponIsTopRung()
	{
		// Raided with the Scythe (top rung); a banked fang is NOT flagged as an upgrade.
		List<CoachFinding> findings = new OwnedButUnusedUpgradeRule()
			.evaluate(recordWithUsedWeapon("Scythe of Vitur"), contextWithBank("Osmumten's fang"));
		assertTrue(findings.isEmpty());
	}
}
