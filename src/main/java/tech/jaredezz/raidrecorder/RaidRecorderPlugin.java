package tech.jaredezz.raidrecorder;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PartyChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import tech.jaredezz.raidrecorder.capture.CaptureEngine;
import tech.jaredezz.raidrecorder.capture.LiveHitEvent;
import tech.jaredezz.raidrecorder.export.RaidExporter;
import tech.jaredezz.raidrecorder.history.RaidHistoryEntry;
import tech.jaredezz.raidrecorder.history.RaidHistoryStats;
import tech.jaredezz.raidrecorder.history.RaidHistoryStore;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.model.RoomRecord;
import tech.jaredezz.raidrecorder.panel.RaidRecorderPanel;
import tech.jaredezz.raidrecorder.party.CurrentInvocationsMessage;
import tech.jaredezz.raidrecorder.party.PartyAggregator;
import tech.jaredezz.raidrecorder.party.RoomSummaryMessage;
import tech.jaredezz.raidrecorder.party.TargetInvocationsMessage;
import tech.jaredezz.raidrecorder.raid.RaidModuleRegistry;
import tech.jaredezz.raidrecorder.raid.toa.ToaInvocation;
import tech.jaredezz.raidrecorder.raid.toa.ToaInvocationReader;
import tech.jaredezz.raidrecorder.raid.toa.ToaModule;

/**
 * Raid Recorder — a black-box flight recorder for OSRS raids (ToA first) with a deterministic
 * coach, plus (merged from the earlier toa-raid-log plugin, which this supersedes) a permanent
 * cross-raid history and a live party invocation-sync checklist. See README.md for the
 * architecture; the short version:
 *
 * <ul>
 * <li>{@link CaptureEngine} buffers everything in memory on the client thread;</li>
 * <li>{@link RaidModuleRegistry} owns the per-raid knowledge ({@link ToaModule} + CoX/ToB stubs);</li>
 * <li>{@link RaidExporter} runs the coach and writes JSON/summary/prompt on a background executor;</li>
 * <li>{@link PartyAggregator} shares per-room summaries over the RuneLite party;</li>
 * <li>{@link RaidHistoryStore} persists a compact per-raid summary forever, for the panel's
 * history/stats view;</li>
 * <li>{@link RaidRecorderPanel} shows history/stats and the invocation-sync checklist (ToA only —
 * the per-room capture and coach are file exports with no UI of their own).</li>
 * </ul>
 */
@Slf4j
@PluginDescriptor(
	name = "Raid Recorder",
	description = "Records every measurable dimension of a raid (ToA) and coaches you with "
		+ "deterministic rules; exports JSON, a Markdown summary and a paste-ready AI prompt; "
		+ "keeps a permanent raid history with a party invocation-sync checklist",
	tags = {"toa", "tombs", "amascut", "raid", "recorder", "coach", "dps", "stats", "party", "pvm",
		"invocation", "history"}
)
public class RaidRecorderPlugin extends Plugin
{
	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private EventBus eventBus;
	@Inject
	private WSClient wsClient;
	@Inject
	private PartyService partyService;
	@Inject
	private ScheduledExecutorService executor;
	@Inject
	private CaptureEngine captureEngine;
	@Inject
	private PartyAggregator partyAggregator;
	@Inject
	private RaidExporter exporter;
	@Inject
	private RaidHistoryStore historyStore;
	@Inject
	private ToaInvocationReader invocationReader;
	@Inject
	private RaidModuleRegistry registry;
	@Inject
	private RaidRecorderConfig config;

	private RaidRecorderPanel panel;
	private NavigationButton navButton;

	private Set<ToaInvocation> syncTarget = EnumSet.noneOf(ToaInvocation.class);
	private long lastBroadcastMask = -1L;
	private long lastBroadcastMs;
	private boolean historyLoaded;

	@Provides
	RaidRecorderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RaidRecorderConfig.class);
	}

	@Override
	protected void startUp()
	{
		wsClient.registerMessage(RoomSummaryMessage.class);
		wsClient.registerMessage(TargetInvocationsMessage.class);
		wsClient.registerMessage(CurrentInvocationsMessage.class);
		eventBus.register(captureEngine);
		eventBus.register(partyAggregator);

		panel = new RaidRecorderPanel();
		panel.setCallbacks(this::onSetTargetClicked, this::onClearTargetClicked);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Raid Recorder")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		captureEngine.setOnRoomCompleted(room ->
			partyAggregator.broadcastRoom(activeRaidKey(), localRsn(), room));
		captureEngine.setOnRaidFinished(this::handleFinishedRaid);
		captureEngine.setOnLiveHit(this::onLiveHit);
		captureEngine.setOnRaidStarted(() -> SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.clearLiveFeed();
			}
		}));
		invocationReader.setOnRead(this::onInvocationsRead);

		log.info("Raid Recorder started — exports go to {}", exporter.exportDir());
	}

	@Override
	protected void shutDown()
	{
		// Flush anything in progress rather than losing it.
		captureEngine.finishRaid("plugin shutdown");
		eventBus.unregister(captureEngine);
		eventBus.unregister(partyAggregator);
		wsClient.unregisterMessage(RoomSummaryMessage.class);
		wsClient.unregisterMessage(TargetInvocationsMessage.class);
		wsClient.unregisterMessage(CurrentInvocationsMessage.class);
		invocationReader.setOnRead(null);
		clientToolbar.removeNavigation(navButton);
		panel = null;
		historyLoaded = false;
	}

	private void handleFinishedRaid(RaidRecord record)
	{
		if (config.partyEnabled() && config.actAsRecorder())
		{
			record.setTeamReport(partyAggregator.drain());
		}
		if (config.autoExport())
		{
			exporter.export(record, registry.all().stream()
				.filter(m -> m.raidKey().equals(record.getRaid()))
				.findFirst().orElseThrow(IllegalStateException::new));
		}
		if (config.logHistory() && "TOA".equals(record.getRaid()))
		{
			appendHistoryAsync(record);
		}
	}

	// ====================== //
	//   RAID HISTORY (ToA)   //
	// ====================== //

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			loadHistory();
		}
	}

	private void loadHistory()
	{
		if (historyLoaded)
		{
			return;
		}
		historyLoaded = true;
		final long accountHash = client.getAccountHash();
		executor.execute(() ->
		{
			List<RaidHistoryEntry> loaded = historyStore.load(accountHash);
			pushHistoryToPanel(loaded);
		});
	}

	private void appendHistoryAsync(RaidRecord record)
	{
		int deaths = record.getRooms().stream().mapToInt(RoomRecord::getDeaths).sum();
		long durationMs = record.getTiming().getEndEpochMs() - record.getTiming().getStartEpochMs();
		RaidHistoryEntry entry = new RaidHistoryEntry(
			record.getTiming().getEndEpochMs(),
			record.getContext().getRaidLevel(),
			ToaInvocation.toBitmask(invocationReader.getLastKnownInvocations()),
			Boolean.TRUE.equals(record.getContext().getPurple()),
			record.getContext().getPoints(),
			record.getContext().getKc(),
			deaths,
			durationMs,
			record.getContext().getTeamSize());

		final long accountHash = client.getAccountHash();
		executor.execute(() -> pushHistoryToPanel(historyStore.append(accountHash, entry)));
	}

	private void pushHistoryToPanel(List<RaidHistoryEntry> entries)
	{
		RaidHistoryStats stats = RaidHistoryStats.compute(entries);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setHistory(entries, stats);
			}
		});
	}

	// ====================== //
	//        LIVE FEED        //
	// ====================== //

	private void onLiveHit(LiveHitEvent event)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.pushLiveHit(event);
			}
		});
	}

	// ====================== //
	//   INVOCATION SYNC (ToA) //
	// ====================== //

	private void onInvocationsRead(Set<ToaInvocation> active)
	{
		final Set<ToaInvocation> snapshot = active.isEmpty()
			? EnumSet.noneOf(ToaInvocation.class) : EnumSet.copyOf(active);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setLocalActive(snapshot);
			}
		});
		maybeBroadcastCurrent(active);
	}

	private void maybeBroadcastCurrent(Set<ToaInvocation> active)
	{
		if (!config.invocationSync() || !partyService.isInParty())
		{
			return;
		}
		long mask = ToaInvocation.toBitmask(active);
		long now = System.currentTimeMillis();
		if (mask == lastBroadcastMask || now - lastBroadcastMs < config.broadcastThrottle() * 1000L)
		{
			return;
		}
		lastBroadcastMask = mask;
		lastBroadcastMs = now;
		partyService.send(new CurrentInvocationsMessage(mask));
	}

	private void onSetTargetClicked()
	{
		// Button runs on the EDT; read the game on the client thread.
		clientThread.invoke(() ->
		{
			Set<ToaInvocation> active = invocationReader.getLastKnownInvocations();
			syncTarget = active.isEmpty() ? EnumSet.noneOf(ToaInvocation.class) : EnumSet.copyOf(active);
			int level = invocationReader.readRaidLevel();
			applyTarget(syncTarget, level);

			if (config.invocationSync() && partyService.isInParty())
			{
				partyService.send(new TargetInvocationsMessage(ToaInvocation.toBitmask(syncTarget), level));
			}
		});
	}

	private void onClearTargetClicked()
	{
		clientThread.invoke(() ->
		{
			syncTarget = EnumSet.noneOf(ToaInvocation.class);
			applyTarget(syncTarget, 0);
			if (config.invocationSync() && partyService.isInParty())
			{
				partyService.send(new TargetInvocationsMessage(0L, 0));
			}
		});
	}

	private void applyTarget(Set<ToaInvocation> newTarget, int level)
	{
		final Set<ToaInvocation> snapshot = newTarget.isEmpty()
			? EnumSet.noneOf(ToaInvocation.class) : EnumSet.copyOf(newTarget);
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setTarget(snapshot, level);
			}
		});
	}

	@Subscribe
	public void onTargetInvocationsMessage(TargetInvocationsMessage message)
	{
		if (!config.invocationSync())
		{
			return;
		}
		Set<ToaInvocation> received = ToaInvocation.fromBitmask(message.getTargetMask());
		syncTarget = received;
		applyTarget(received, message.getRaidLevel());
	}

	@Subscribe
	public void onCurrentInvocationsMessage(CurrentInvocationsMessage message)
	{
		if (!config.invocationSync())
		{
			return;
		}
		PartyMember local = partyService.getLocalMember();
		if (local != null && message.getMemberId() == local.getMemberId())
		{
			return; // our own loadout is shown via the target checklist
		}
		final String name = memberName(message.getMemberId());
		final long mask = message.getCurrentMask();
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setMemberMask(name, mask);
			}
		});
	}

	private String memberName(long memberId)
	{
		PartyMember m = partyService.getMemberById(memberId);
		if (m != null && m.getDisplayName() != null && !m.getDisplayName().isEmpty())
		{
			return m.getDisplayName();
		}
		return "Member " + Long.toString(memberId).substring(0, Math.min(4, Long.toString(memberId).length()));
	}

	@Subscribe
	public void onUserPart(UserPart event)
	{
		final String name = memberName(event.getMemberId());
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.removeMember(name);
			}
		});
	}

	@Subscribe
	public void onPartyChanged(PartyChanged event)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.clearMembers();
			}
		});
	}

	// ====================== //
	//       LIFECYCLE         //
	// ====================== //

	/**
	 * The reward chest interface loading is the most reliable completion signal (same trigger the
	 * earlier toa-raid-log plugin logged raids on); chat lines are the primary path, this the backup.
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.TOA_CHESTS)
		{
			captureEngine.notifyRaidCompleted();
		}
	}

	/** Developer-console commands: ::rrstart, ::rrstop (manual recording override). */
	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		switch (event.getCommand().toLowerCase())
		{
			case "rrstart":
				captureEngine.manualStart();
				break;
			case "rrstop":
				captureEngine.manualStop();
				break;
			default:
		}
	}

	private String activeRaidKey()
	{
		return registry.activeModule(client) != null ? registry.activeModule(client).raidKey() : "UNKNOWN";
	}

	private String localRsn()
	{
		return client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null
			? client.getLocalPlayer().getName() : "unknown";
	}
}
