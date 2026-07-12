package tech.jaredezz.raidrecorder.export;

import com.google.gson.Gson;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.raid.RaidModule;

/**
 * Renders the paste-ready AI coaching prompt (raid-&lt;ts&gt;-prompt.md). The plugin never calls
 * an AI; the user pastes this into whatever assistant they like.
 */
final class PromptWriter
{
	private PromptWriter()
	{
	}

	static String render(RaidRecord record, RaidModule module, Gson gson)
	{
		StringBuilder md = new StringBuilder();
		md.append("# ").append(module.raidName()).append(" coaching request\n\n");
		md.append("Copy this whole file into an AI assistant.\n\n---\n\n");

		md.append("You are an expert Old School RuneScape PvM coach specializing in ")
			.append(module.raidName()).append(". Below is a complete machine-captured record of one ")
			.append("of my raids: per-room timings, every damage event I dealt and took (with the ")
			.append("mechanic that caused it and whether it was avoidable), my gear at each moment, ")
			.append("supplies used, DPS uptime, idle windows, and my account context. A deterministic ")
			.append("rules engine already produced the `coachFindings` — treat those as starting ")
			.append("points, then go deeper.\n\n");

		md.append("Coach me:\n");
		md.append("1. Rank the three changes that would most improve my next raid, with expected impact.\n");
		md.append("2. For each avoidable-damage mechanic I got hit by, explain how the mechanic works and the positioning/timing that dodges it.\n");
		md.append("3. Review my room order, gear switches and supply usage against my raid level and invocations.\n");
		md.append("4. Tell me what I did well and should keep doing.\n\n");

		md.append("Important context about me:\n");
		md.append("- Account type: ").append(record.getAccount().getType())
			.append(" — only suggest gear I can realistically obtain; if it's an ironman variant, never say \"buy X\".\n");
		md.append("- KC: ").append(record.getContext().getKc() >= 0
			? String.valueOf(record.getContext().getKc()) : "(fill in)").append('\n');
		md.append("- My goals: (fill in — e.g. \"comfortable 300s\", \"first 400\", \"faster solos\")\n");
		md.append("- Anything unusual about this raid: (fill in, or delete)\n\n");

		md.append("Notes on the data: ticks are 0.6s. `officialTicks` is the in-game room timer; ")
			.append("`dpsUptimePct` = ticks with one of my hits landing ÷ ticks a target was attackable. ")
			.append("`mechanic: \"UNKNOWN\"` means the recorder couldn't classify the hit — don't over-read those. ")
			.append("Values listed in `unverified` rest on community-sourced ids and may be mistagged.\n\n");

		md.append("```json\n").append(gson.toJson(record)).append("\n```\n");
		return md.toString();
	}
}
