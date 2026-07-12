package tech.jaredezz.raidrecorder.capture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import lombok.Getter;
import lombok.Setter;
import tech.jaredezz.raidrecorder.model.RoomRecord;

/**
 * Mutable in-progress state for the room the player is currently in. Mutated only on the client
 * thread; frozen into an immutable {@link RoomRecord} when the room is exited.
 */
@Getter
public class RoomCapture
{
	private final String roomKey;
	private final int entryTick;
	private final long entryEpochMs;

	@Setter
	private int officialTicks = -1;

	private int firstHitTick = -1;
	/** Distinct ticks on which at least one of our hitsplats landed on a target. */
	private final Set<Integer> hitTicks = new TreeSet<>();
	/** Distinct ticks on which an attackable target existed (the DPS-uptime denominator). */
	private final Set<Integer> attackableTicks = new TreeSet<>();

	private int damageTotal;
	private final Map<String, Integer> damageByTarget = new LinkedHashMap<>();
	private final List<int[]> damageByTick = new ArrayList<>();
	private final List<RoomRecord.DamageTakenEvent> damageTaken = new ArrayList<>();
	private final List<RoomRecord.EquipmentSnapshot> equipmentTimeline = new ArrayList<>();
	private final Map<String, Integer> suppliesUsed = new LinkedHashMap<>();

	@Setter
	private int deaths;
	@Setter
	private int specsUsed;
	@Setter
	private int prayerSwitches;

	public RoomCapture(String roomKey, int entryTick, long entryEpochMs)
	{
		this.roomKey = roomKey;
		this.entryTick = entryTick;
		this.entryEpochMs = entryEpochMs;
	}

	public void recordDamageDealt(int tick, String targetLabel, int amount)
	{
		if (firstHitTick < 0)
		{
			firstHitTick = tick;
		}
		hitTicks.add(tick);
		damageTotal += amount;
		damageByTarget.merge(targetLabel, amount, Integer::sum);
		damageByTick.add(new int[]{tick - entryTick, amount});
	}

	public void recordAttackableTick(int tick)
	{
		attackableTicks.add(tick);
	}

	public void recordSupplyUsed(String supplyKey, int count)
	{
		suppliesUsed.merge(supplyKey, count, Integer::sum);
	}

	/** Freeze into the exported record. */
	public RoomRecord freeze(int order, int exitTick, long exitEpochMs, int downtimeWindowMinTicks)
	{
		RoomRecord record = new RoomRecord();
		record.setRoom(roomKey);
		record.setOrder(order);
		record.setEntryTick(entryTick);
		record.setExitTick(exitTick);
		record.setEntryEpochMs(entryEpochMs);
		record.setExitEpochMs(exitEpochMs);
		record.setOfficialTicks(officialTicks);
		record.setTimeToFirstHitTicks(firstHitTick >= 0 ? firstHitTick - entryTick : -1);
		record.setDpsUptimePct(computeUptimePct());
		record.setDowntimeWindows(computeDowntimeWindows(downtimeWindowMinTicks));

		RoomRecord.DamageDealt dealt = new RoomRecord.DamageDealt();
		dealt.setTotal(damageTotal);
		dealt.setByTarget(damageByTarget);
		dealt.setByTick(damageByTick);
		record.setDamageDealt(dealt);

		record.setDamageTaken(damageTaken);
		record.setEquipmentTimeline(equipmentTimeline);
		record.setSuppliesUsed(suppliesUsed);
		record.setDeaths(deaths);
		record.setSpecsUsed(specsUsed);
		record.setPrayerSwitches(prayerSwitches);
		return record;
	}

	private double computeUptimePct()
	{
		if (attackableTicks.isEmpty())
		{
			return 0.0;
		}
		long hitTicksInWindow = hitTicks.stream().filter(attackableTicks::contains).count();
		return Math.round(10000.0 * hitTicksInWindow / attackableTicks.size()) / 100.0;
	}

	/**
	 * Gaps of >= minTicks inside the attackable window with no hit from us — [startOffset, length]
	 * pairs relative to room entry. These are the "you were doing nothing" windows.
	 */
	private List<int[]> computeDowntimeWindows(int minTicks)
	{
		List<int[]> windows = new ArrayList<>();
		Integer gapStart = null;
		Integer previous = null;
		for (int tick : attackableTicks)
		{
			if (hitTicks.contains(tick))
			{
				if (gapStart != null && previous != null && previous - gapStart + 1 >= minTicks)
				{
					windows.add(new int[]{gapStart - entryTick, previous - gapStart + 1});
				}
				gapStart = null;
			}
			else if (gapStart == null)
			{
				gapStart = tick;
			}
			previous = tick;
		}
		if (gapStart != null && previous != null && previous - gapStart + 1 >= minTicks)
		{
			windows.add(new int[]{gapStart - entryTick, previous - gapStart + 1});
		}
		return windows;
	}
}
