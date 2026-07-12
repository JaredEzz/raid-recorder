package tech.jaredezz.raidrecorder.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * The complete record of one raid — the top-level object serialized to
 * {@code raid-<timestamp>.json}. Field names are the schema (see SCHEMA.md).
 */
@Getter
@Setter
public class RaidRecord
{
	public static final String SCHEMA_VERSION = "1.0.0";

	private String schemaVersion = SCHEMA_VERSION;
	/** Raid key from the module: "TOA", "COX", "TOB". */
	private String raid;

	private Account account = new Account();
	private Context context = new Context();
	private Timing timing = new Timing();
	private List<RoomRecord> rooms = new ArrayList<>();
	private BankSnapshot bankSnapshot;
	private List<CoachFinding> coachFindings = new ArrayList<>();
	/** Per-member room summaries received over the party socket (recorder mode only). */
	private Map<String, List<TeamRoomSummary>> teamReport;
	/** Names of TODO(verify) constants that influenced this record — honesty over false precision. */
	private List<String> unverified = new ArrayList<>();

	@Getter
	@Setter
	public static class Account
	{
		private String rsn;
		private AccountType type = AccountType.UNKNOWN;
		private int world;
		/** Skill name to levels for combat-relevant skills. */
		private Map<String, SkillLevels> skills = new LinkedHashMap<>();
	}

	@Getter
	@Setter
	public static class SkillLevels
	{
		private int real;
		private int boosted;

		public SkillLevels()
		{
		}

		public SkillLevels(int real, int boosted)
		{
			this.real = real;
			this.boosted = boosted;
		}
	}

	@Getter
	@Setter
	public static class Context
	{
		/** Kill count scraped from the completion chat message; -1 if not seen. */
		private int kc = -1;
		private int raidLevel = -1;
		private Invocations invocations = new Invocations();
		private int teamSize = 1;
		/** RSNs of party members when known (party plugin members, else just self). */
		private List<String> party = new ArrayList<>();
		/**
		 * Personal contribution points, read from the authoritative end-of-raid varp.
		 * ToA-specific; -1 when not applicable (other raids) or not captured.
		 */
		private int points = -1;
		/** Whether this raid produced a unique ("purple"). ToA-specific; null when not applicable. */
		private Boolean purple;
	}

	@Getter
	@Setter
	public static class Invocations
	{
		/** Raw values as read: invocation bitmask + the raid level varbit. */
		private Map<String, Object> raw = new LinkedHashMap<>();
		/** Parsed invocation names, in interface order. */
		private List<String> parsed = new ArrayList<>();
	}

	@Getter
	@Setter
	public static class Timing
	{
		private int startTick;
		private int endTick;
		private long startEpochMs;
		private long endEpochMs;
		/** Official total raid ticks from the game, -1 when not scraped. */
		private int officialTotalTicks = -1;
	}

	@Getter
	@Setter
	public static class BankSnapshot
	{
		private long capturedEpochMs;
		/** Item id (as string, for JSON map keys) to quantity. */
		private Map<String, Integer> items = new LinkedHashMap<>();
		/** Item id to name, for the coach and for humans reading the JSON. */
		private Map<String, String> names = new LinkedHashMap<>();
	}

	/** Compact per-room summary broadcast by party members (see party.RoomSummaryMessage). */
	@Getter
	@Setter
	public static class TeamRoomSummary
	{
		private String room;
		private int damageDealt;
		private int damageTaken;
		private int deaths;
		private double dpsUptimePct;

		public TeamRoomSummary()
		{
		}

		public TeamRoomSummary(String room, int damageDealt, int damageTaken, int deaths, double dpsUptimePct)
		{
			this.room = room;
			this.damageDealt = damageDealt;
			this.damageTaken = damageTaken;
			this.deaths = deaths;
			this.dpsUptimePct = dpsUptimePct;
		}
	}
}
