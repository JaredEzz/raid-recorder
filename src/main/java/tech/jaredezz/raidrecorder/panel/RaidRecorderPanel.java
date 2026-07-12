package tech.jaredezz.raidrecorder.panel;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.GridLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import tech.jaredezz.raidrecorder.capture.LiveHitEvent;
import tech.jaredezz.raidrecorder.history.RaidHistoryEntry;
import tech.jaredezz.raidrecorder.history.RaidHistoryStats;
import tech.jaredezz.raidrecorder.raid.toa.ToaInvocation;

/**
 * Side panel: a live per-hit feed with pixel-art hitsplat badges and DPS during a raid, cross-raid
 * history &amp; stats (from {@link tech.jaredezz.raidrecorder.history}), plus the ToA party
 * invocation-sync checklist. The history/sync parts are ported from the earlier toa-raid-log
 * plugin, which this plugin supersedes. The per-room black-box capture and coach otherwise have
 * no UI of their own by design (they're file exports, meant to be read in Obsidian or pasted into
 * an AI) — the live feed is the one place this plugin shows anything in real time.
 */
public class RaidRecorderPanel extends PluginPanel
{
	private static final DateTimeFormatter TIME_FMT =
		DateTimeFormatter.ofPattern("MMM d HH:mm").withZone(ZoneId.systemDefault());
	private static final int RECENT_RAIDS_SHOWN = 8;
	private static final int INVOCATION_FREQUENCIES_SHOWN = 10;
	private static final int LIVE_FEED_ROWS_SHOWN = 12;

	private final JLabel dpsLabel = new JLabel("DPS: —");
	private final JPanel liveFeedSection = section();
	private final JLabel statusLabel = new JLabel();
	private final JPanel summarySection = section();
	private final JPanel frequencySection = section();
	private final JPanel recentSection = section();
	private final JButton setTargetButton = new JButton("Set target from my invocations");
	private final JButton clearTargetButton = new JButton("Clear");
	private final JPanel targetSection = section();
	private final JPanel partySection = section();

	// State, mutated on the EDT only.
	private List<RaidHistoryEntry> history = Collections.emptyList();
	private RaidHistoryStats stats = RaidHistoryStats.EMPTY;
	private Set<ToaInvocation> target = EnumSet.noneOf(ToaInvocation.class);
	private int targetLevel = 0;
	private Set<ToaInvocation> localActive = EnumSet.noneOf(ToaInvocation.class);
	private final Map<String, Long> memberMasks = new TreeMap<>();

	private Runnable onSetTarget = () -> { };
	private Runnable onClearTarget = () -> { };

	public RaidRecorderPanel()
	{
		super(true);
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		final JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		final JLabel title = new JLabel("Raid Recorder");
		title.setForeground(Color.WHITE);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setBorder(new EmptyBorder(0, 0, 6, 0));

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setBorder(new EmptyBorder(0, 0, 6, 0));

		setTargetButton.setFocusPainted(false);
		setTargetButton.addActionListener(e -> onSetTarget.run());
		clearTargetButton.setFocusPainted(false);
		clearTargetButton.addActionListener(e -> onClearTarget.run());
		final JPanel buttons = new JPanel(new BorderLayout(4, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.setBorder(new EmptyBorder(0, 0, 6, 0));
		buttons.add(setTargetButton, BorderLayout.CENTER);
		buttons.add(clearTargetButton, BorderLayout.EAST);

		dpsLabel.setFont(FontManager.getRunescapeSmallFont());
		dpsLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		content.add(title);
		content.add(statusLabel);
		content.add(heading("Live feed"));
		content.add(dpsLabel);
		content.add(liveFeedSection);
		content.add(heading("Summary"));
		content.add(summarySection);
		content.add(heading("Invocation frequency"));
		content.add(frequencySection);
		content.add(heading("Recent raids"));
		content.add(recentSection);
		content.add(heading("Party invocation sync"));
		content.add(buttons);
		content.add(heading("Target invocations"));
		content.add(targetSection);
		content.add(heading("Party"));
		content.add(partySection);

		add(content, BorderLayout.NORTH);
		rebuild();
	}

	public void setCallbacks(Runnable onSetTarget, Runnable onClearTarget)
	{
		this.onSetTarget = onSetTarget;
		this.onClearTarget = onClearTarget;
	}

	/** Clear the feed for a new raid. Call on the EDT. */
	public void clearLiveFeed()
	{
		liveFeedSection.removeAll();
		dpsLabel.setText("DPS: —");
		revalidate();
		repaint();
	}

	/** Insert one hit at the top of the feed, trimming to the last {@link #LIVE_FEED_ROWS_SHOWN}. */
	public void pushLiveHit(LiveHitEvent event)
	{
		PixelIcon.Tier tier;
		String subtitle;
		if (event.isFirst)
		{
			tier = PixelIcon.Tier.FIRST;
			subtitle = "first hit this raid";
		}
		else if (event.newMax)
		{
			tier = PixelIcon.Tier.NEW_MAX;
			subtitle = "NEW MAX! (prev " + event.priorMax + ")";
		}
		else
		{
			tier = PixelIcon.tierFor(event.luckPct);
			subtitle = String.format("%.0f%% of your max (%d)", event.luckPct, event.priorMax);
		}

		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.add(new JLabel(PixelIcon.hitBadge(event.amount, tier)), BorderLayout.WEST);

		JLabel text = new JLabel("<html>" + event.target + " &mdash; " + event.amount
			+ "<br><span style='color:#" + hex(tier.color) + "'>" + subtitle + "</span></html>");
		text.setFont(FontManager.getRunescapeSmallFont());
		text.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(text, BorderLayout.CENTER);

		liveFeedSection.add(row, 0);
		while (liveFeedSection.getComponentCount() > LIVE_FEED_ROWS_SHOWN)
		{
			liveFeedSection.remove(liveFeedSection.getComponentCount() - 1);
		}

		dpsLabel.setText(String.format("DPS: %.1f  (session avg %.1f)", event.rollingDps, event.sessionDps));

		revalidate();
		repaint();
	}

	private static String hex(Color c)
	{
		return String.format("%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	/** Replace the raid history and its computed stats. Call on the EDT. */
	public void setHistory(List<RaidHistoryEntry> history, RaidHistoryStats stats)
	{
		this.history = history == null ? Collections.emptyList() : history;
		this.stats = stats == null ? RaidHistoryStats.EMPTY : stats;
		rebuild();
	}

	public void setTarget(Set<ToaInvocation> target, int level)
	{
		this.target = target == null ? EnumSet.noneOf(ToaInvocation.class) : EnumSet.copyOf(target.isEmpty()
			? EnumSet.noneOf(ToaInvocation.class) : target);
		this.targetLevel = level;
		rebuild();
	}

	public void setLocalActive(Set<ToaInvocation> active)
	{
		this.localActive = active == null ? EnumSet.noneOf(ToaInvocation.class)
			: (active.isEmpty() ? EnumSet.noneOf(ToaInvocation.class) : EnumSet.copyOf(active));
		rebuild();
	}

	public void setMemberMask(String name, long mask)
	{
		memberMasks.put(name, mask);
		rebuild();
	}

	public void removeMember(String name)
	{
		memberMasks.remove(name);
		rebuild();
	}

	public void clearMembers()
	{
		memberMasks.clear();
		rebuild();
	}

	private void rebuild()
	{
		rebuildStatus();
		rebuildSummary();
		rebuildFrequency();
		rebuildRecent();
		rebuildTarget();
		rebuildParty();
		revalidate();
		repaint();
	}

	private void rebuildStatus()
	{
		if (target.isEmpty())
		{
			statusLabel.setText("No target set. Open the invocations and click \"Set target\".");
		}
		else
		{
			statusLabel.setText("Target: raid level " + targetLevel + " (" + target.size() + " invocations)");
		}
	}

	private void rebuildSummary()
	{
		summarySection.removeAll();
		if (stats.totalRaids == 0)
		{
			summarySection.add(muted("No raids logged yet."));
			return;
		}
		summarySection.add(row("Raids logged: " + stats.totalRaids, Color.WHITE));
		summarySection.add(row(String.format("Purple rate: %.1f%% (%d)", stats.purpleRatePct, stats.purpleCount),
			ColorScheme.BRAND_ORANGE));
		summarySection.add(row(String.format("Avg points: %.0f", stats.avgPoints), ColorScheme.LIGHT_GRAY_COLOR));
		summarySection.add(row(String.format("Avg deaths: %.2f", stats.avgDeaths),
			stats.avgDeaths > 0 ? ColorScheme.PROGRESS_ERROR_COLOR : ColorScheme.LIGHT_GRAY_COLOR));
		summarySection.add(row("Avg time: " + formatDuration(stats.avgDurationMs), ColorScheme.LIGHT_GRAY_COLOR));
	}

	private void rebuildFrequency()
	{
		frequencySection.removeAll();
		if (stats.invocationFrequencies.isEmpty())
		{
			frequencySection.add(muted("—"));
			return;
		}
		int shown = 0;
		for (RaidHistoryStats.InvocationFrequency f : stats.invocationFrequencies)
		{
			if (shown >= INVOCATION_FREQUENCIES_SHOWN)
			{
				break;
			}
			String text = String.format("%s  %.0f%% (%d/%d)", f.invocation.getDisplayName(), f.percentage,
				f.raidCount, stats.totalRaids);
			frequencySection.add(row(text, ColorScheme.LIGHT_GRAY_COLOR));
			shown++;
		}
	}

	private void rebuildRecent()
	{
		recentSection.removeAll();
		if (history.isEmpty())
		{
			recentSection.add(muted("No raids logged yet."));
			return;
		}
		int shown = 0;
		for (int i = history.size() - 1; i >= 0 && shown < RECENT_RAIDS_SHOWN; i--, shown++)
		{
			RaidHistoryEntry entry = history.get(i);
			int count = Long.bitCount(entry.getInvocationMask());
			String text = TIME_FMT.format(Instant.ofEpochMilli(entry.getTimestamp()))
				+ "  lvl " + entry.getRaidLevel() + "  " + count + " inv"
				+ "  " + entry.getPoints() + "pt"
				+ (entry.getDeaths() > 0 ? "  " + entry.getDeaths() + "☠" : "")
				+ "  " + formatDuration(entry.getDurationMs())
				+ (entry.isPurple() ? "  ★" : "");
			recentSection.add(row(text, entry.isPurple() ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR));
		}
	}

	private void rebuildTarget()
	{
		targetSection.removeAll();
		if (target.isEmpty())
		{
			targetSection.add(muted("—"));
		}
		else
		{
			for (ToaInvocation inv : ToaInvocation.values())
			{
				if (!target.contains(inv))
				{
					continue;
				}
				boolean have = localActive.contains(inv);
				targetSection.add(row((have ? "✔ " : "✘ ") + inv.getDisplayName(),
					have ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR));
			}
			for (ToaInvocation inv : localActive)
			{
				if (!target.contains(inv))
				{
					targetSection.add(row("+ " + inv.getDisplayName() + " (extra)", ColorScheme.BRAND_ORANGE));
				}
			}
		}
		clearTargetButton.setEnabled(!target.isEmpty());
	}

	private void rebuildParty()
	{
		partySection.removeAll();
		if (memberMasks.isEmpty())
		{
			partySection.add(muted("No party members sharing invocations."));
			return;
		}
		long targetMask = ToaInvocation.toBitmask(target);
		for (Map.Entry<String, Long> e : memberMasks.entrySet())
		{
			long mask = e.getValue();
			int matched = Long.bitCount(mask & targetMask);
			boolean exact = !target.isEmpty() && mask == targetMask;
			Color c = target.isEmpty() ? ColorScheme.LIGHT_GRAY_COLOR
				: exact ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR;
			String suffix = target.isEmpty() ? "" : "  " + matched + "/" + target.size() + (exact ? " ✔" : "");
			partySection.add(row(e.getKey() + suffix, c));
		}
	}

	private static String formatDuration(long ms)
	{
		if (ms <= 0)
		{
			return "—";
		}
		long totalSeconds = ms / 1000;
		return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
	}

	private static JPanel section()
	{
		JPanel p = new JPanel();
		p.setLayout(new GridLayout(0, 1, 0, 2));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(new EmptyBorder(6, 8, 6, 8));
		return p;
	}

	private static JLabel heading(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(Color.WHITE);
		l.setBorder(new EmptyBorder(8, 0, 2, 0));
		return l;
	}

	private static JLabel row(String text, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel muted(String text)
	{
		return row(text, ColorScheme.LIGHT_GRAY_COLOR);
	}
}
