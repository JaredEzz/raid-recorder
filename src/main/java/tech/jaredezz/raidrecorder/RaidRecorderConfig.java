package tech.jaredezz.raidrecorder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(RaidRecorderConfig.GROUP)
public interface RaidRecorderConfig extends Config
{
	String GROUP = "raidrecorder";

	@ConfigSection(name = "Capture", description = "What the recorder captures", position = 0)
	String captureSection = "capture";

	@ConfigSection(name = "Party", description = "Group aggregation over the RuneLite party", position = 1)
	String partySection = "party";

	@ConfigSection(name = "Export", description = "What gets written when a raid ends", position = 2)
	String exportSection = "export";

	@ConfigSection(name = "Coach", description = "Deterministic coaching rules", position = 3)
	String coachSection = "coach";

	@ConfigSection(name = "History", description = "Cross-raid history and invocation stats", position = 4)
	String historySection = "history";

	// ---- Capture ----

	@ConfigItem(keyName = "captureDamage", name = "Damage dealt/taken", section = captureSection,
		description = "Record every hitsplat you deal and receive, with source and mechanic tagging", position = 0)
	default boolean captureDamage()
	{
		return true;
	}

	@ConfigItem(keyName = "captureEquipment", name = "Equipment timeline", section = captureSection,
		description = "Snapshot worn equipment on every change so hits are tagged with the active gear set", position = 1)
	default boolean captureEquipment()
	{
		return true;
	}

	@ConfigItem(keyName = "captureSupplies", name = "Supply usage", section = captureSection,
		description = "Count food and potion doses consumed per room from inventory changes", position = 2)
	default boolean captureSupplies()
	{
		return true;
	}

	@ConfigItem(keyName = "capturePositions", name = "Positioning", section = captureSection,
		description = "Sample your tile each tick so damage events are geolocated within the room", position = 3)
	default boolean capturePositions()
	{
		return true;
	}

	@ConfigItem(keyName = "captureBank", name = "Bank snapshots", section = captureSection,
		description = "Snapshot your bank whenever it is open — feeds the owned-but-unused-upgrade coach rule", position = 4)
	default boolean captureBank()
	{
		return true;
	}

	@ConfigItem(keyName = "manualRecording", name = "Manual start/stop only", section = captureSection,
		description = "Ignore automatic raid detection; record only between the Start/Stop menu actions on the plugin panel icon", position = 5)
	default boolean manualRecording()
	{
		return false;
	}

	// ---- Party ----

	@ConfigItem(keyName = "partyEnabled", name = "Share room summaries", section = partySection,
		description = "Broadcast compact per-room summaries to your RuneLite party (requires everyone to run this plugin)", position = 0)
	default boolean partyEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "actAsRecorder", name = "Act as recorder", section = partySection,
		description = "Assemble the full team report from party members' summaries into this client's export", position = 1)
	default boolean actAsRecorder()
	{
		return false;
	}

	@ConfigItem(keyName = "invocationSync", name = "Invocation sync checklist", section = partySection,
		description = "Broadcast a target invocation list to your party and show who matches it (ToA only)", position = 2)
	default boolean invocationSync()
	{
		return true;
	}

	@Range(min = 2, max = 60)
	@ConfigItem(keyName = "broadcastThrottle", name = "Broadcast throttle (s)", section = partySection,
		description = "Minimum seconds between invocation-sync broadcasts, to avoid spamming the party relay", position = 3)
	default int broadcastThrottle()
	{
		return 5;
	}

	// ---- History ----

	@ConfigItem(keyName = "logHistory", name = "Log raid history", section = historySection,
		description = "Keep a permanent per-account log of every completed raid's invocations, level, points and loot outcome, with invocation frequency stats", position = 0)
	default boolean logHistory()
	{
		return true;
	}

	// ---- Export ----

	@ConfigItem(keyName = "exportJson", name = "Export JSON record", section = exportSection,
		description = "Write raid-<timestamp>.json (the complete structured record)", position = 0)
	default boolean exportJson()
	{
		return true;
	}

	@ConfigItem(keyName = "exportSummary", name = "Export Markdown summary", section = exportSection,
		description = "Write raid-<timestamp>-summary.md (Obsidian-friendly, includes coach findings)", position = 1)
	default boolean exportSummary()
	{
		return true;
	}

	@ConfigItem(keyName = "exportPrompt", name = "Export AI coaching prompt", section = exportSection,
		description = "Write raid-<timestamp>-prompt.md, ready to paste into any AI chat. The plugin itself never calls an AI.", position = 2)
	default boolean exportPrompt()
	{
		return true;
	}

	@ConfigItem(keyName = "exportDir", name = "Export directory", section = exportSection,
		description = "Directory for exports; empty = <RuneLite home>/raid-recorder", position = 3)
	default String exportDir()
	{
		return "";
	}

	@ConfigItem(keyName = "autoExport", name = "Auto-export on completion", section = exportSection,
		description = "Export automatically when the raid completes (or wipes)", position = 4)
	default boolean autoExport()
	{
		return true;
	}

	// ---- Coach ----

	@ConfigItem(keyName = "coachEnabled", name = "Run deterministic coach", section = coachSection,
		description = "Evaluate the rules engine on each completed raid and embed findings in exports", position = 0)
	default boolean coachEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "thresholdsPath", name = "Thresholds JSON path", section = coachSection,
		description = "Path to a coach-thresholds.json to override the defaults; empty = <export dir>/coach-thresholds.json (created on first run)", position = 1)
	default String thresholdsPath()
	{
		return "";
	}

	@ConfigItem(keyName = "verboseLog", name = "Verbose logging", section = coachSection,
		description = "Log room transitions, mechanic tags and unclassified damage to the client log — use during a debug session to verify tracking", position = 2)
	default boolean verboseLog()
	{
		return false;
	}
}
