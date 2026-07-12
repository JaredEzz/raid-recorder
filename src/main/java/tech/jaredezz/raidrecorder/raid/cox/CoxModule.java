package tech.jaredezz.raidrecorder.raid.cox;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import tech.jaredezz.raidrecorder.model.AccountType;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.raid.MechanicContext;
import tech.jaredezz.raidrecorder.raid.MechanicTag;
import tech.jaredezz.raidrecorder.raid.RaidModule;

/**
 * Chambers of Xeric — stub. Drop-in support means filling in the TODOs below; the capture engine,
 * coach and exporters need no changes.
 *
 * TODO(cox): region-id set for chambers (varbit Varbits.IN_RAID / raid party varbits are an
 *   alternative detection route); room classification per chamber layout; mechanic taxonomy
 *   (Olm hand attacks, crystal bombs, acid, Tekton spark, Vanguard, Vasa, Guardians, Ice Demon,
 *   Muttadile, Vespula, shamans, mystics, thieving/tightrope puzzles); points from varbits
 *   (TOTAL_POINTS / PERSONAL_POINTS); prep-phase exclusion from DPS uptime.
 */
@Singleton
public class CoxModule implements RaidModule
{
	@Inject
	CoxModule()
	{
	}

	@Override
	public String raidKey()
	{
		return "COX";
	}

	@Override
	public String raidName()
	{
		return "Chambers of Xeric";
	}

	@Override
	public boolean isInRaid(Client client)
	{
		// TODO(cox): detect via region ids or Varbits.IN_RAID. Returning false disables the module.
		return false;
	}

	@Override
	public String classifyCurrentRoom(Client client)
	{
		return null;
	}

	@Override
	public boolean isBossRoom(String roomKey)
	{
		return false;
	}

	@Override
	public boolean isAttackableTarget(NPC npc, String roomKey)
	{
		return false;
	}

	@Override
	public String npcLabel(NPC npc)
	{
		return npc.getName() != null ? npc.getName() : "NPC " + npc.getId();
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
	public void populateContext(Client client, RaidRecord.Context context)
	{
	}

	@Override
	public Map<String, Double> benchmarks(String roomKey, int kc, AccountType accountType)
	{
		return Collections.emptyMap();
	}

	@Override
	public List<String> allRoomKeys()
	{
		return Collections.emptyList();
	}
}
