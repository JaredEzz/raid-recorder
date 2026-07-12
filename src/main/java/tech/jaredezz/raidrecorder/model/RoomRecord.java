package tech.jaredezz.raidrecorder.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * The frozen record of one raid room (boss or puzzle). Serialized as-is into the export JSON;
 * field names are part of the schema (see SCHEMA.md).
 */
@Getter
@Setter
public class RoomRecord
{
	/** Stable room key from the raid module, e.g. "AKKHA", "PUZZLE_HET". */
	private String room;
	/** 1-based position in the order the player actually did the rooms. */
	private int order;

	private int entryTick;
	private int exitTick;
	private long entryEpochMs;
	private long exitEpochMs;
	/**
	 * Official per-room completion time in ticks, scraped from the game's
	 * "Challenge complete" chat line; -1 when unavailable (e.g. capture started mid-room).
	 */
	private int officialTicks = -1;

	/** Ticks from room entry until the player's first damage hitsplat landed; -1 if never hit. */
	private int timeToFirstHitTicks = -1;
	/** Ticks-with-at-least-one-of-my-hits divided by attackable ticks, 0..100. */
	private double dpsUptimePct;
	/** Number of ticks where an attackable NPC was present but no hit of ours landed for >= threshold. */
	private List<int[]> downtimeWindows = new ArrayList<>();

	private DamageDealt damageDealt = new DamageDealt();
	private List<DamageTakenEvent> damageTaken = new ArrayList<>();
	private List<EquipmentSnapshot> equipmentTimeline = new ArrayList<>();
	/** Supply name (dose-normalized, e.g. "Saradomin brew") to count consumed in this room. */
	private Map<String, Integer> suppliesUsed = new LinkedHashMap<>();
	private int deaths;
	private int specsUsed;
	private int prayerSwitches;

	@Getter
	@Setter
	public static class DamageDealt
	{
		private int total;
		/** Target NPC label to damage summed over the room. */
		private Map<String, Integer> byTarget = new LinkedHashMap<>();
		/** Compact [tickOffsetFromEntry, amount] pairs, one per hitsplat. */
		private List<int[]> byTick = new ArrayList<>();
	}

	@Getter
	@Setter
	public static class DamageTakenEvent
	{
		private int tick;
		private int amount;
		/** Hitsplat type id (net.runelite.api.HitsplatID). */
		private int hitsplatType;
		/** Probable source NPC label, or "UNKNOWN". */
		private String sourceNpc;
		/** Mechanic key from the raid module taxonomy, or "UNKNOWN". */
		private String mechanic;
		private boolean avoidable;
		private Point worldPoint;
	}

	@Getter
	@Setter
	public static class EquipmentSnapshot
	{
		private int tick;
		/** Slot name (HEAD, WEAPON, ...) to item. */
		private Map<String, Item> items = new LinkedHashMap<>();
	}

	@Getter
	@Setter
	public static class Item
	{
		private int id;
		private String name;

		public Item()
		{
		}

		public Item(int id, String name)
		{
			this.id = id;
			this.name = name;
		}
	}

	@Getter
	@Setter
	public static class Point
	{
		private int x;
		private int y;
		private int plane;

		public Point()
		{
		}

		public Point(int x, int y, int plane)
		{
			this.x = x;
			this.y = y;
			this.plane = plane;
		}
	}
}
