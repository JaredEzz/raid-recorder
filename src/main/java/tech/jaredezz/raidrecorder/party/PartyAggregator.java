package tech.jaredezz.raidrecorder.party;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyService;
import tech.jaredezz.raidrecorder.RaidRecorderConfig;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.model.RoomRecord;

/**
 * Group aggregation over the RuneLite Party ("shared code" = the party passphrase; each member
 * joins the same party and runs this plugin). Members broadcast one compact summary per finished
 * room; a client with "act as recorder" enabled assembles them into the team report embedded in
 * its export. Solo play never touches any of this.
 */
@Slf4j
@Singleton
public class PartyAggregator
{
	private final RaidRecorderConfig config;
	private final PartyService partyService;

	/** RSN → room summaries received this raid. Mutated on the party/event thread only via EventBus. */
	private final Map<String, List<RaidRecord.TeamRoomSummary>> byMember = new LinkedHashMap<>();

	@Inject
	PartyAggregator(RaidRecorderConfig config, PartyService partyService)
	{
		this.config = config;
		this.partyService = partyService;
	}

	/** Broadcast our own finished room to the party. No-op when not in a party. */
	public void broadcastRoom(String raidKey, String rsn, RoomRecord room)
	{
		if (!config.partyEnabled() || !partyService.isInParty())
		{
			return;
		}
		int taken = room.getDamageTaken().stream()
			.mapToInt(RoomRecord.DamageTakenEvent::getAmount).sum();
		partyService.send(new RoomSummaryMessage(
			raidKey, room.getRoom(), rsn,
			room.getDamageDealt().getTotal(), taken, room.getDeaths(), room.getDpsUptimePct()));
	}

	@Subscribe
	public void onRoomSummaryMessage(RoomSummaryMessage message)
	{
		if (!config.partyEnabled() || !config.actAsRecorder())
		{
			return;
		}
		byMember.computeIfAbsent(message.getRsn(), k -> new ArrayList<>())
			.add(new RaidRecord.TeamRoomSummary(message.getRoom(), message.getDamageDealt(),
				message.getDamageTaken(), message.getDeaths(), message.getDpsUptimePct()));
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		byMember.clear();
	}

	/** Snapshot for the export, then reset for the next raid. Empty when not recording for a team. */
	public Map<String, List<RaidRecord.TeamRoomSummary>> drain()
	{
		if (byMember.isEmpty())
		{
			return null;
		}
		Map<String, List<RaidRecord.TeamRoomSummary>> snapshot = new LinkedHashMap<>();
		byMember.forEach((rsn, rooms) -> snapshot.put(rsn, new ArrayList<>(rooms)));
		byMember.clear();
		return snapshot;
	}
}
