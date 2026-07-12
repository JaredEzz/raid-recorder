package tech.jaredezz.raidrecorder.raid.tob;

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
 * Theatre of Blood — stub. Drop-in support means filling in the TODOs below; the capture engine,
 * coach and exporters need no changes.
 *
 * TODO(tob): region ids per room (Maiden, Bloat, Nylocas, Sotetseg, Xarpus, Verzik);
 *   Varbits.THEATRE_OF_BLOOD as an in-raid signal; mechanic taxonomy (Maiden blood spawns,
 *   Bloat hands, Nylo explosions, Sote balls/maze, Xarpus poison, Verzik phases);
 *   room-time chat lines ("Wave 'The Maiden of Sugadinti' complete! Duration: ...").
 */
@Singleton
public class TobModule implements RaidModule
{
	@Inject
	TobModule()
	{
	}

	@Override
	public String raidKey()
	{
		return "TOB";
	}

	@Override
	public String raidName()
	{
		return "Theatre of Blood";
	}

	@Override
	public boolean isInRaid(Client client)
	{
		// TODO(tob): detect via region ids or Varbits.THEATRE_OF_BLOOD. Returning false disables the module.
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
