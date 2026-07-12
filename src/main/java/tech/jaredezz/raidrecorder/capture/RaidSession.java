package tech.jaredezz.raidrecorder.capture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.model.RoomRecord;
import tech.jaredezz.raidrecorder.raid.RaidModule;

/**
 * Mutable state for the raid currently being recorded. Mutated only on the client thread;
 * frozen into a {@link RaidRecord} at raid end.
 */
@Getter
public class RaidSession
{
	private final RaidModule module;
	private final int startTick;
	private final long startEpochMs;

	private final List<RoomRecord> completedRooms = new ArrayList<>();
	/**
	 * Official times parsed from chat before/after the engine's own room transition — chat and
	 * region changes race each other, so completions are matched to rooms by key, whenever seen.
	 */
	private final Map<String, Integer> officialRoomTicks = new LinkedHashMap<>();

	@Setter
	private RoomCapture currentRoom;
	@Setter
	private int kc = -1;
	@Setter
	private int officialTotalTicks = -1;
	@Setter
	private boolean wiped;

	public RaidSession(RaidModule module, int startTick, long startEpochMs)
	{
		this.module = module;
		this.startTick = startTick;
		this.startEpochMs = startEpochMs;
	}

	/** Freeze the current room into the completed list; returns it (null when no room active). */
	public RoomRecord completeRoom(int exitTick, long exitEpochMs, int downtimeWindowMinTicks)
	{
		if (currentRoom == null)
		{
			return null;
		}
		RoomRecord record = currentRoom.freeze(completedRooms.size() + 1, exitTick, exitEpochMs,
			downtimeWindowMinTicks);
		Integer official = officialRoomTicks.get(record.getRoom());
		if (official != null)
		{
			record.setOfficialTicks(official);
		}
		completedRooms.add(record);
		currentRoom = null;
		return record;
	}

	public void recordOfficialRoomTicks(String roomKey, int ticks)
	{
		if (roomKey == null)
		{
			return;
		}
		officialRoomTicks.put(roomKey, ticks);
		// Back-fill a room that already exited before its chat line arrived.
		for (RoomRecord room : completedRooms)
		{
			if (room.getRoom().equals(roomKey) && room.getOfficialTicks() < 0)
			{
				room.setOfficialTicks(ticks);
			}
		}
	}
}
