package tech.jaredezz.raidrecorder.export;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import tech.jaredezz.raidrecorder.model.CoachFinding;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.model.RoomRecord;
import tech.jaredezz.raidrecorder.raid.RaidModule;

/** Renders the Obsidian-friendly human summary (raid-&lt;ts&gt;-summary.md). */
final class SummaryWriter
{
	private SummaryWriter()
	{
	}

	static String render(RaidRecord record, RaidModule module)
	{
		StringBuilder md = new StringBuilder();
		RaidRecord.Context ctx = record.getContext();
		RaidRecord.Timing timing = record.getTiming();

		md.append("# ").append(module.raidName()).append(" — ")
			.append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(timing.getStartEpochMs())))
			.append("\n\n");

		md.append("| | |\n|---|---|\n");
		row(md, "Player", record.getAccount().getRsn() + " (" + record.getAccount().getType() + ")");
		row(md, "KC after this raid", ctx.getKc() >= 0 ? String.valueOf(ctx.getKc()) : "unknown");
		row(md, "Points", ctx.getPoints() >= 0 ? String.valueOf(ctx.getPoints()) : "unknown");
		row(md, "Purple", ctx.getPurple() == null ? "unknown" : (ctx.getPurple() ? "yes" : "no"));
		row(md, "Raid level", ctx.getRaidLevel() >= 0 ? String.valueOf(ctx.getRaidLevel()) : "unknown");
		row(md, "Team size", String.valueOf(ctx.getTeamSize()));
		row(md, "Invocations", ctx.getInvocations().getParsed().isEmpty()
			? "not captured" : String.join(", ", ctx.getInvocations().getParsed()));
		int ownTicks = timing.getEndTick() - timing.getStartTick();
		row(md, "Duration", ticksToClock(timing.getOfficialTotalTicks() > 0
			? timing.getOfficialTotalTicks() : ownTicks)
			+ (timing.getOfficialTotalTicks() > 0 ? " (official)" : " (measured)"));
		md.append('\n');

		md.append("## Rooms\n\n");
		md.append("| # | Room | Time | DPS uptime | Dmg dealt | Dmg taken | Avoidable | Deaths | Supplies |\n");
		md.append("|---|------|------|-----------|-----------|-----------|-----------|--------|----------|\n");
		for (RoomRecord room : record.getRooms())
		{
			int taken = room.getDamageTaken().stream().mapToInt(RoomRecord.DamageTakenEvent::getAmount).sum();
			int avoidable = room.getDamageTaken().stream()
				.filter(RoomRecord.DamageTakenEvent::isAvoidable)
				.mapToInt(RoomRecord.DamageTakenEvent::getAmount).sum();
			int doses = room.getSuppliesUsed().values().stream().mapToInt(Integer::intValue).sum();
			int ticks = room.getOfficialTicks() > 0
				? room.getOfficialTicks() : room.getExitTick() - room.getEntryTick();
			md.append(String.format("| %d | %s | %s | %.0f%% | %d | %d | %d | %d | %d doses |%n",
				room.getOrder(), room.getRoom(), ticksToClock(ticks), room.getDpsUptimePct(),
				room.getDamageDealt().getTotal(), taken, avoidable, room.getDeaths(), doses));
		}
		md.append('\n');

		if (!record.getCoachFindings().isEmpty())
		{
			md.append("## Coach\n\n");
			for (CoachFinding finding : record.getCoachFindings())
			{
				md.append("- **[").append(finding.getSeverity()).append("]** ")
					.append(finding.getRoom()).append(" — ").append(finding.getMessage()).append('\n');
			}
			md.append('\n');
		}

		md.append("## Damage taken by mechanic\n\n");
		for (RoomRecord room : record.getRooms())
		{
			if (room.getDamageTaken().isEmpty())
			{
				continue;
			}
			md.append("### ").append(room.getRoom()).append('\n');
			Map<String, int[]> byMechanic = new java.util.LinkedHashMap<>();
			for (RoomRecord.DamageTakenEvent event : room.getDamageTaken())
			{
				int[] entry = byMechanic.computeIfAbsent(event.getMechanic(), k -> new int[3]);
				entry[0] += event.getAmount();
				entry[1]++;
				entry[2] |= event.isAvoidable() ? 1 : 0;
			}
			byMechanic.forEach((mechanic, sums) ->
				md.append(String.format("- %s: %d damage over %d hits%s%n",
					mechanic, sums[0], sums[1], sums[2] == 1 ? " *(avoidable)*" : "")));
			md.append('\n');
		}

		if (!record.getUnverified().isEmpty())
		{
			md.append("---\n*Some tags rest on unverified ids (see KNOWN_UNKNOWNS.md): ")
				.append(record.getUnverified().size()).append(" entries.*\n");
		}
		return md.toString();
	}

	private static void row(StringBuilder md, String key, String value)
	{
		md.append("| **").append(key).append("** | ").append(value).append(" |\n");
	}

	static String ticksToClock(int ticks)
	{
		if (ticks < 0)
		{
			return "?";
		}
		int totalSeconds = (int) Math.round(ticks * 0.6);
		return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
	}
}
