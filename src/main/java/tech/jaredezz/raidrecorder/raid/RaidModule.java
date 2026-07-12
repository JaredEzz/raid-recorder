package tech.jaredezz.raidrecorder.raid;

import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import tech.jaredezz.raidrecorder.model.AccountType;
import tech.jaredezz.raidrecorder.model.RaidRecord;

/**
 * Everything raid-specific lives behind this interface. The capture engine is raid-agnostic;
 * adding Chambers of Xeric or Theatre of Blood means implementing this and registering it in
 * {@link RaidModuleRegistry} — no engine changes.
 */
public interface RaidModule
{
	/** Stable raid key used in exports: "TOA", "COX", "TOB". */
	String raidKey();

	/** Human name for summaries: "Tombs of Amascut". */
	String raidName();

	/** True when the local player is inside this raid, decided by map region ids. */
	boolean isInRaid(Client client);

	/**
	 * Classify the room the player is currently in, or null when in no recognized room
	 * (corridors, nexus hub, loot room). Keys are stable per raid, e.g. "AKKHA", "PUZZLE_HET".
	 */
	String classifyCurrentRoom(Client client);

	/** True when the room key is a boss room (vs puzzle) — coaches weigh these differently. */
	boolean isBossRoom(String roomKey);

	/**
	 * True when this NPC counts as an attackable target for DPS-uptime accounting in the given
	 * room (bosses, adds worth hitting — not decorative or invulnerable-form NPCs).
	 */
	boolean isAttackableTarget(NPC npc, String roomKey);

	/** Display label for an NPC in damage tables (usually the composition name, disambiguated). */
	String npcLabel(NPC npc);

	/** Classify a damage-taken event against the raid's mechanic taxonomy. Never null. */
	MechanicTag classifyDamageTaken(MechanicContext context);

	/**
	 * Feed a game chat message (already stripped of tags). The module parses raid lifecycle out of
	 * it and returns an event, or null when the message is not raid-related.
	 */
	ChatEvent onChatMessage(String message);

	/** Read raid-specific context (raid level, invocations, team size) into the record. Null-safe. */
	void populateContext(Client client, RaidRecord.Context context);

	/**
	 * Per-room benchmark expectations for the coach, scaled by kill-count band and account type.
	 * Keys the coach understands: "dpsUptimeWarnPct", "timeToFirstHitWarnTicks",
	 * "avoidableDamageWarnPerRoom". Modules may add raid-specific keys.
	 */
	Map<String, Double> benchmarks(String roomKey, int kc, AccountType accountType);

	/** All room keys this raid can produce, in canonical order (for docs and aggregation). */
	List<String> allRoomKeys();

	/**
	 * Called every game tick regardless of raid state, so modules can poll interfaces that only
	 * exist outside the raid (e.g. the ToA lobby invocation board). Must be cheap when idle.
	 */
	default void onGameTick(Client client)
	{
	}

	/**
	 * Combat styles ("MELEE", "RANGED", "MAGIC") that are sensible primary styles in this room.
	 * Empty means "no opinion" and disables the wrong-gear-style rule for the room — which is the
	 * honest answer whenever the real game mechanics don't support a single dominant style (e.g. a
	 * boss whose damage is genuinely split across phases/styles). Implementations must source these
	 * from an authoritative reference (OSRS Wiki) and cite it, not from assumption — see
	 * KNOWN_UNKNOWNS.md #13 for why.
	 */
	default List<String> recommendedStyles(String roomKey)
	{
		return java.util.Collections.emptyList();
	}

	/** A raid-lifecycle event parsed from chat. */
	class ChatEvent
	{
		public enum Type
		{
			RAID_STARTED,
			/** A room/challenge finished; roomKey may be null if the message names no room. */
			ROOM_COMPLETED,
			RAID_COMPLETED,
			RAID_WIPED,
			/** Raid kill count learned. */
			KC_LEARNED
		}

		public final Type type;
		/** Room key for ROOM_COMPLETED, else null. */
		public final String roomKey;
		/** Official duration in ticks for ROOM_COMPLETED / RAID_COMPLETED, -1 when absent. */
		public final int officialTicks;
		/** Kill count for KC_LEARNED, else -1. */
		public final int kc;

		public ChatEvent(Type type, String roomKey, int officialTicks, int kc)
		{
			this.type = type;
			this.roomKey = roomKey;
			this.officialTicks = officialTicks;
			this.kc = kc;
		}

		public static ChatEvent raidStarted()
		{
			return new ChatEvent(Type.RAID_STARTED, null, -1, -1);
		}

		public static ChatEvent roomCompleted(String roomKey, int officialTicks)
		{
			return new ChatEvent(Type.ROOM_COMPLETED, roomKey, officialTicks, -1);
		}

		public static ChatEvent raidCompleted(int officialTicks)
		{
			return new ChatEvent(Type.RAID_COMPLETED, null, officialTicks, -1);
		}

		public static ChatEvent raidWiped()
		{
			return new ChatEvent(Type.RAID_WIPED, null, -1, -1);
		}

		public static ChatEvent kcLearned(int kc)
		{
			return new ChatEvent(Type.KC_LEARNED, null, -1, kc);
		}
	}
}
