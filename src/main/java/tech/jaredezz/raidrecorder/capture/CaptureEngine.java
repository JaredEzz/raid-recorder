package tech.jaredezz.raidrecorder.capture;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GraphicsObject;
import net.runelite.api.HeadIcon;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.Text;
import tech.jaredezz.raidrecorder.RaidRecorderConfig;
import tech.jaredezz.raidrecorder.model.AccountType;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.model.RoomRecord;
import tech.jaredezz.raidrecorder.raid.MechanicContext;
import tech.jaredezz.raidrecorder.raid.MechanicTag;
import tech.jaredezz.raidrecorder.raid.RaidModule;
import tech.jaredezz.raidrecorder.raid.RaidModuleRegistry;

/**
 * The raid-agnostic black-box recorder. Subscribes to game events (registered on the EventBus by
 * the plugin), buffers everything in memory on the client thread, and hands a frozen
 * {@link RaidRecord} to {@link #onRaidFinished} when the raid ends. No I/O happens here — the
 * plugin routes finished records to the exporter's background executor.
 */
@Slf4j
@Singleton
public class CaptureEngine
{
	/** Ticks the player must be outside raid regions before we call the raid abandoned. */
	private static final int OUT_OF_RAID_GRACE_TICKS = 12;
	/** Ticks to keep collecting KC / final room-time chat lines after raid completion. */
	private static final int COMPLETION_GRACE_TICKS = 6;
	/** Minimum idle gap (ticks with a target but no hit) recorded as a downtime window. */
	private static final int DOWNTIME_WINDOW_MIN_TICKS = 10;
	/** How long a graphics object stays relevant for damage attribution. */
	private static final int GRAPHICS_RELEVANCE_TICKS = 6;
	/**
	 * After a raid finishes, region membership alone will not start a new session for this many
	 * ticks. Raid detection is region-based, but right after {@link #finishRaid} the player is still
	 * standing in the boss region (looting the chest, walking out), so an unguarded region check
	 * would immediately spawn a phantom session on the tail of the raid that just ended. A genuine
	 * re-entry — a fresh RAID_STARTED chat line fired after the last finish — bypasses this instantly.
	 */
	private static final int POST_FINISH_COOLDOWN_TICKS = 30;
	/** How far back {@link #probableSource} trusts a "this NPC was interacting with me" sample. */
	private static final int INTERACTION_LOOKBACK_TICKS = 2;
	/** Chebyshev tile radius for the nearest-animating-NPC last-resort damage-source guess. */
	private static final int NEAREST_SOURCE_MAX_TILES = 2;

	private static final Skill[] COMBAT_SKILLS = {
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC,
		Skill.HITPOINTS, Skill.PRAYER,
	};

	private final Client client;
	private final RaidRecorderConfig config;
	private final RaidModuleRegistry registry;
	private final BankTracker bankTracker;
	private final ItemManager itemManager;

	/** Invoked on the client thread with each finished raid record. */
	@Setter
	private Consumer<RaidRecord> onRaidFinished;

	/** Invoked on the client thread with each frozen room (for party broadcasting). */
	@Setter
	private Consumer<RoomRecord> onRoomCompleted;

	/** Invoked on the client thread with each of your damage-dealt hits (for the live feed). */
	@Setter
	private Consumer<LiveHitEvent> onLiveHit;

	/** Invoked on the client thread when a new raid session starts (to reset the live feed). */
	@Setter
	private Runnable onRaidStarted;

	private RaidSession session;
	private int outOfRaidTicks;
	private int completionCountdown = -1;
	private boolean manualActive;
	/** Tick of the most recent {@link #finishRaid}, so a new session can't start on its tail. */
	private int lastFinishTick = Integer.MIN_VALUE;
	/** Tick a genuine RAID_STARTED chat line last fired — the trustworthy "player entered" signal. */
	private int raidStartedSignalTick = Integer.MIN_VALUE;

	// Cross-cutting per-tick state
	private WorldPoint lastPlayerLocation;
	private HeadIcon lastOverhead;
	private int lastSpecEnergy = -1;
	private final Map<WorldPoint, int[]> recentTileGraphics = new HashMap<>();
	/**
	 * NPCs recently seen interacting with the local player, mapped to the tick last seen. Mirrors the
	 * {@link #recentTileGraphics} idiom: a hit's true source often clears its interaction between the
	 * tick it commits an attack and the tick the hitsplat lands, so a short lookback recovers it.
	 */
	private final Map<NPC, Integer> recentInteractors = new HashMap<>();
	private Map<String, Integer> lastInventoryDoses;
	private int lastTargetedProjectileId = -1;
	private int lastTargetedProjectileTick = -1;
	private final LiveHitTracker liveHitTracker = new LiveHitTracker();
	private final LiveDpsTracker liveDpsTracker = new LiveDpsTracker();
	/** Weapon currently worn, tracked independently of room capture so the live feed works even
	 *  with equipment capture disabled, and is correct the instant a raid starts. */
	private String currentWeaponName = "Unarmed";

	@Inject
	CaptureEngine(Client client, RaidRecorderConfig config, RaidModuleRegistry registry,
		BankTracker bankTracker, ItemManager itemManager)
	{
		this.client = client;
		this.config = config;
		this.registry = registry;
		this.bankTracker = bankTracker;
		this.itemManager = itemManager;
	}

	public boolean isRecording()
	{
		return session != null;
	}

	/** Manual override entry points (wired to ::rrstart / ::rrstop developer commands). */
	public void manualStart()
	{
		manualActive = true;
		log.info("[raid-recorder] manual recording armed — session starts when a raid module matches");
	}

	public void manualStop()
	{
		manualActive = false;
		if (session != null)
		{
			finishRaid("manual stop");
		}
	}

	/**
	 * External completion signal (e.g. the reward chest interface loading) — starts the same
	 * grace countdown as the completion chat line, whichever arrives first.
	 */
	public void notifyRaidCompleted()
	{
		if (session != null && completionCountdown < 0)
		{
			completionCountdown = COMPLETION_GRACE_TICKS;
		}
	}

	// ------------------------------------------------------------------ //
	//  Tick loop: session lifecycle, room transitions, per-tick sampling  //
	// ------------------------------------------------------------------ //

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}
		lastPlayerLocation = WorldPoint.fromLocalInstance(client, local.getLocalLocation());

		for (RaidModule polled : registry.all())
		{
			polled.onGameTick(client);
		}

		if (session == null)
		{
			maybeStartSession();
			return;
		}

		RaidModule module = session.getModule();
		boolean inRaid = module.isInRaid(client);

		if (completionCountdown >= 0)
		{
			if (--completionCountdown < 0 || !inRaid)
			{
				finishRaid("completed");
			}
			return;
		}

		if (!inRaid)
		{
			if (++outOfRaidTicks >= OUT_OF_RAID_GRACE_TICKS)
			{
				finishRaid(session.isWiped() ? "wiped" : "left raid");
			}
			return;
		}
		outOfRaidTicks = 0;

		trackRoomTransition(module);
		sampleTick(module, local);
		expireTileGraphics();
		expireRecentInteractors();
	}

	private void maybeStartSession()
	{
		if (config.manualRecording() && !manualActive)
		{
			return;
		}
		RaidModule module = registry.activeModule(client);
		if (module == null)
		{
			return;
		}
		int now = client.getTickCount();
		// Region membership alone is not a reliable raid-entry signal: right after a raid finishes the
		// player lingers in the boss region, which would otherwise spawn a phantom session that
		// immediately "finishes" again and exports junk. Require either a genuine entry chat line that
		// fired after our last finish (correct re-entry), or that enough ticks have elapsed since the
		// finish to rule out that tail. Manual arming bypasses the gate — it's an explicit request.
		boolean freshEntrySignal = raidStartedSignalTick > lastFinishTick;
		boolean cooldownElapsed = lastFinishTick == Integer.MIN_VALUE
			|| now - lastFinishTick >= POST_FINISH_COOLDOWN_TICKS;
		if (!manualActive && !freshEntrySignal && !cooldownElapsed)
		{
			return;
		}
		session = new RaidSession(module, now, System.currentTimeMillis());
		outOfRaidTicks = 0;
		completionCountdown = -1;
		recentInteractors.clear();
		lastInventoryDoses = null;
		lastSpecEnergy = client.getVarpValue(VarPlayerID.SA_ENERGY);
		liveHitTracker.reset();
		liveDpsTracker.start(session.getStartEpochMs());
		if (onRaidStarted != null)
		{
			onRaidStarted.run();
		}
		log.info("[raid-recorder] recording started: {} at tick {}", module.raidKey(), session.getStartTick());
	}

	private void trackRoomTransition(RaidModule module)
	{
		String room = module.classifyCurrentRoom(client);
		RoomCapture current = session.getCurrentRoom();
		String currentKey = current != null ? current.getRoomKey() : null;

		if (java.util.Objects.equals(room, currentKey))
		{
			return;
		}

		int tick = client.getTickCount();
		long now = System.currentTimeMillis();
		if (current != null)
		{
			RoomRecord frozen = session.completeRoom(tick, now, DOWNTIME_WINDOW_MIN_TICKS);
			if (frozen != null && onRoomCompleted != null)
			{
				onRoomCompleted.accept(frozen);
			}
			if (config.verboseLog())
			{
				log.info("[raid-recorder] exited room {} at tick {}", currentKey, tick);
			}
		}
		if (room != null)
		{
			RoomCapture next = new RoomCapture(room, tick, now);
			session.setCurrentRoom(next);
			snapshotEquipment(next, tick);
			if (config.verboseLog())
			{
				log.info("[raid-recorder] entered room {} at tick {}", room, tick);
			}
		}
	}

	private void sampleTick(RaidModule module, Player local)
	{
		RoomCapture room = session.getCurrentRoom();
		if (room == null)
		{
			return;
		}

		int tick = client.getTickCount();
		boolean sawAttackable = false;
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc == null)
			{
				continue;
			}
			if (!sawAttackable && module.isAttackableTarget(npc, room.getRoomKey()))
			{
				room.recordAttackableTick(tick);
				sawAttackable = true;
			}
			// Feed the recent-interactors cache used by probableSource() to recover a hit's source
			// when the attacker briefly clears its interaction between committing an attack and the
			// hitsplat landing a tick or two later.
			if (npc.getInteracting() == local)
			{
				recentInteractors.put(npc, tick);
			}
		}

		HeadIcon overhead = local.getOverheadIcon();
		if (overhead != lastOverhead && lastOverhead != null && overhead != null)
		{
			room.setPrayerSwitches(room.getPrayerSwitches() + 1);
		}
		lastOverhead = overhead;
	}

	// ------------------------------------------------------------------ //
	//                              Damage                                 //
	// ------------------------------------------------------------------ //

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (session == null || !config.captureDamage())
		{
			return;
		}
		RoomCapture room = session.getCurrentRoom();
		if (room == null)
		{
			return;
		}
		RaidModule module = session.getModule();
		int tick = client.getTickCount();
		Actor target = event.getActor();

		if (target instanceof NPC && event.getHitsplat().isMine())
		{
			// Investigation note (Bug 4, raid-20260712-103049): WARDENS_P1_P2's ~10k damageDealt total
			// was verified legitimate, not thrall or double-count corruption. byTarget sums exactly to
			// the total across distinct (transforming) warden ids, and it is dominated by real P2 burst
			// damage — a single ~1700 obelisk-detonation hitsplat plus repeated large mechanic hits on
			// the wardens, all of which isMine()==true. Summoned-thrall-range hits (<=3) summed to only
			// ~107, so thrall misattribution is not the cause and no per-source split is applied here.
			// Known limitation: those same giant mechanic hitsplats (and any thrall hits that do land)
			// still feed the live per-weapon running-max/luck tracker in recordLiveHit, which can skew
			// it; left as-is since it does not affect the persisted damage totals.
			NPC npc = (NPC) target;
			int amount = event.getHitsplat().getAmount();
			String targetLabel = module.npcLabel(npc);
			room.recordDamageDealt(tick, targetLabel, amount);
			recordLiveHit(targetLabel, amount);
			return;
		}

		if (target == client.getLocalPlayer())
		{
			recordDamageTaken(event, room, module, tick);
		}
	}

	/** Packages a damage-dealt hit for the live feed: running per-weapon max + rolling/session DPS. */
	private void recordLiveHit(String targetLabel, int amount)
	{
		long now = System.currentTimeMillis();
		LiveHitTracker.Result result = liveHitTracker.record(currentWeaponName, amount);
		liveDpsTracker.record(now, amount);
		if (onLiveHit != null)
		{
			onLiveHit.accept(new LiveHitEvent(now, currentWeaponName, targetLabel, amount,
				result.priorMax, result.isFirst, result.newMax, result.luckPct,
				liveDpsTracker.rollingDps(now), liveDpsTracker.sessionDps(now)));
		}
	}

	private void recordDamageTaken(HitsplatApplied event, RoomCapture room, RaidModule module, int tick)
	{
		Player local = client.getLocalPlayer();
		NPC source = probableSource(local, tick);

		int tileGraphic = -1;
		if (lastPlayerLocation != null)
		{
			int[] entry = recentTileGraphics.get(lastPlayerLocation);
			if (entry != null && tick - entry[1] <= GRAPHICS_RELEVANCE_TICKS)
			{
				tileGraphic = entry[0];
			}
		}

		MechanicContext context = MechanicContext.builder()
			.room(room.getRoomKey())
			.sourceNpcId(source != null ? source.getId() : -1)
			.sourceAnimation(source != null ? source.getAnimation() : -1)
			.playerGraphic(local != null ? local.getGraphic() : -1)
			.hitsplatType(event.getHitsplat().getHitsplatType())
			.playerLocation(lastPlayerLocation)
			.tileGraphicsObjectId(tileGraphic)
			.playerTargetedProjectileId(
				tick - lastTargetedProjectileTick <= GRAPHICS_RELEVANCE_TICKS ? lastTargetedProjectileId : -1)
			.build();
		MechanicTag tag = module.classifyDamageTaken(context);

		RoomRecord.DamageTakenEvent taken = new RoomRecord.DamageTakenEvent();
		taken.setTick(tick);
		taken.setAmount(event.getHitsplat().getAmount());
		taken.setHitsplatType(event.getHitsplat().getHitsplatType());
		taken.setSourceNpc(source != null ? module.npcLabel(source) : "UNKNOWN");
		taken.setMechanic(tag.getMechanic());
		taken.setAvoidable(tag.isAvoidable());
		if (lastPlayerLocation != null)
		{
			taken.setWorldPoint(new RoomRecord.Point(
				lastPlayerLocation.getX(), lastPlayerLocation.getY(), lastPlayerLocation.getPlane()));
		}
		room.getDamageTaken().add(taken);

		if (config.verboseLog() && "UNKNOWN".equals(tag.getMechanic()))
		{
			log.info("[raid-recorder] unclassified damage: room={} src={} anim={} gfx={} tileGfx={} splat={} amt={}",
				room.getRoomKey(), context.getSourceNpcId(), context.getSourceAnimation(),
				context.getPlayerGraphic(), context.getTileGraphicsObjectId(),
				context.getHitsplatType(), event.getHitsplat().getAmount());
		}
	}

	/**
	 * Best guess at what dealt a hit to the local player. Prefers an NPC currently pointing at us,
	 * then one that pointed at us within the last {@link #INTERACTION_LOOKBACK_TICKS} ticks (attackers
	 * routinely clear their interaction between committing an attack and its hitsplat landing), then a
	 * nearby NPC mid-animation whose interaction we simply never sampled. All heuristic: a hit from an
	 * off-screen or purely-AoE source that never showed interaction still resolves to null (UNKNOWN).
	 */
	private NPC probableSource(Player local, int tick)
	{
		if (local == null)
		{
			return null;
		}
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc != null && npc.getInteracting() == local)
			{
				return npc;
			}
		}
		NPC recent = null;
		int recentTick = Integer.MIN_VALUE;
		for (Map.Entry<NPC, Integer> entry : recentInteractors.entrySet())
		{
			if (tick - entry.getValue() <= INTERACTION_LOOKBACK_TICKS && entry.getValue() > recentTick)
			{
				recent = entry.getKey();
				recentTick = entry.getValue();
			}
		}
		if (recent != null)
		{
			return recent;
		}
		return nearestAnimatingNpc(local);
	}

	/**
	 * Nearest NPC within melee-ish range that is mid-animation — a last-resort source guess for a hit
	 * whose attacker we never caught interacting. Kept tight (Chebyshev &lt;= 2 tiles) to avoid grabbing
	 * a bystander. Distance is measured in local (instance) coordinates so it works inside raid
	 * instances, where world coordinates are template-relative and wouldn't compare correctly.
	 */
	private NPC nearestAnimatingNpc(Player local)
	{
		net.runelite.api.coords.LocalPoint self = local.getLocalLocation();
		if (self == null)
		{
			return null;
		}
		int selfPlane = local.getWorldLocation() != null ? local.getWorldLocation().getPlane() : -1;
		NPC best = null;
		int bestDist = Integer.MAX_VALUE;
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc == null || npc.getAnimation() == -1)
			{
				continue;
			}
			WorldPoint npcWp = npc.getWorldLocation();
			if (npcWp != null && selfPlane >= 0 && npcWp.getPlane() != selfPlane)
			{
				continue;
			}
			net.runelite.api.coords.LocalPoint lp = npc.getLocalLocation();
			if (lp == null)
			{
				continue;
			}
			// Local coordinates are 128 units per tile; MAX_TILES tiles = MAX_TILES * 128 units.
			int cheb = Math.max(Math.abs(self.getX() - lp.getX()), Math.abs(self.getY() - lp.getY()));
			if (cheb <= NEAREST_SOURCE_MAX_TILES * 128 && cheb < bestDist)
			{
				best = npc;
				bestDist = cheb;
			}
		}
		return best;
	}

	private void expireRecentInteractors()
	{
		int tick = client.getTickCount();
		recentInteractors.entrySet().removeIf(e -> tick - e.getValue() > INTERACTION_LOOKBACK_TICKS);
	}

	@Subscribe
	public void onProjectileMoved(net.runelite.api.events.ProjectileMoved event)
	{
		if (session == null)
		{
			return;
		}
		if (event.getProjectile().getTargetActor() == client.getLocalPlayer())
		{
			lastTargetedProjectileId = event.getProjectile().getId();
			lastTargetedProjectileTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		if (session == null)
		{
			return;
		}
		GraphicsObject object = event.getGraphicsObject();
		WorldPoint point = WorldPoint.fromLocalInstance(client, object.getLocation());
		if (point != null)
		{
			recentTileGraphics.put(point, new int[]{object.getId(), client.getTickCount()});
		}
	}

	private void expireTileGraphics()
	{
		int tick = client.getTickCount();
		recentTileGraphics.values().removeIf(entry -> tick - entry[1] > GRAPHICS_RELEVANCE_TICKS);
	}

	// ------------------------------------------------------------------ //
	//                    Deaths, specs, chat lifecycle                     //
	// ------------------------------------------------------------------ //

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (session == null || event.getActor() != client.getLocalPlayer())
		{
			return;
		}
		RoomCapture room = session.getCurrentRoom();
		if (room != null)
		{
			room.setDeaths(room.getDeaths() + 1);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (session == null)
		{
			return;
		}
		if (event.getVarpId() == VarPlayerID.SA_ENERGY)
		{
			int energy = event.getValue();
			if (lastSpecEnergy > 0 && energy < lastSpecEnergy)
			{
				RoomCapture room = session.getCurrentRoom();
				if (room != null)
				{
					room.setSpecsUsed(room.getSpecsUsed() + 1);
				}
			}
			lastSpecEnergy = energy;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		String message = Text.removeTags(event.getMessage());

		// Modules see chat even before a session exists — raid start is often announced by chat.
		RaidModule module = session != null ? session.getModule() : registry.activeModule(client);
		if (module == null)
		{
			return;
		}
		RaidModule.ChatEvent chatEvent = module.onChatMessage(message);
		if (chatEvent == null)
		{
			return;
		}
		if (config.verboseLog())
		{
			log.info("[raid-recorder] chat event {} room={} ticks={} kc={} from \"{}\"",
				chatEvent.type, chatEvent.roomKey, chatEvent.officialTicks, chatEvent.kc, message);
		}

		switch (chatEvent.type)
		{
			case RAID_STARTED:
				// The trustworthy "player genuinely entered a raid" signal. Recorded unconditionally so
				// maybeStartSession() can tell a real (re-)entry apart from the tail of the raid that
				// just finished. Region detection usually still starts the session first.
				raidStartedSignalTick = client.getTickCount();
				if (session == null)
				{
					maybeStartSession();
				}
				break;
			case ROOM_COMPLETED:
				if (session != null)
				{
					session.recordOfficialRoomTicks(chatEvent.roomKey, chatEvent.officialTicks);
					RoomCapture current = session.getCurrentRoom();
					if (current != null && current.getRoomKey().equals(chatEvent.roomKey))
					{
						current.setOfficialTicks(chatEvent.officialTicks);
					}
				}
				break;
			case RAID_COMPLETED:
				// Guard against a residual completion line from the raid that just ended leaking into a
				// freshly-started session (which then wrongly finishes as "completed"). A genuine
				// completion always has room progress behind it; a new session still sitting in the
				// nexus/loot area with no rooms cannot have just been completed.
				if (session != null
					&& (session.getCurrentRoom() != null || !session.getCompletedRooms().isEmpty()))
				{
					session.setOfficialTotalTicks(chatEvent.officialTicks);
					completionCountdown = COMPLETION_GRACE_TICKS;
				}
				break;
			case RAID_WIPED:
				if (session != null)
				{
					session.setWiped(true);
				}
				break;
			case KC_LEARNED:
				if (session != null)
				{
					session.setKc(chatEvent.kc);
				}
				break;
		}
	}

	// ------------------------------------------------------------------ //
	//                 Containers: bank, equipment, supplies               //
	// ------------------------------------------------------------------ //

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (config.captureBank())
		{
			bankTracker.onItemContainerChanged(event);
			if (config.verboseLog() && event.getContainerId() == InventoryID.BANK
				&& bankTracker.getLatestSnapshot() != null)
			{
				log.info("[raid-recorder] bank snapshot captured: {} distinct items",
					bankTracker.getLatestSnapshot().getItems().size());
			}
		}
		if (config.verboseLog() && (event.getContainerId() == InventoryID.INV
			|| event.getContainerId() == InventoryID.WORN))
		{
			logContainerDiagnostic(event.getContainerId() == InventoryID.INV ? "inventory" : "equipment",
				event.getItemContainer());
		}
		if (event.getContainerId() == InventoryID.WORN)
		{
			// Tracked independently of capture toggles/session state so the live feed always has
			// the right weapon the instant a raid starts, even with equipment capture disabled.
			updateCurrentWeaponName(event.getItemContainer());
		}
		if (session == null)
		{
			return;
		}

		if (event.getContainerId() == InventoryID.WORN && config.captureEquipment())
		{
			RoomCapture room = session.getCurrentRoom();
			if (room != null)
			{
				snapshotEquipment(room, client.getTickCount());
			}
		}
		else if (event.getContainerId() == InventoryID.INV && config.captureSupplies())
		{
			trackSupplyUsage(event.getItemContainer());
		}
	}

	/**
	 * Verbose-only proof-of-read: logs a container's contents whenever it changes, independent of
	 * whether a raid session is active. Not used for capture itself — purely so a debug session can
	 * confirm container reads are wired correctly before entering a raid.
	 */
	private void logContainerDiagnostic(String label, ItemContainer container)
	{
		if (container == null)
		{
			log.info("[raid-recorder] {} changed: container is null", label);
			return;
		}
		StringBuilder items = new StringBuilder();
		int count = 0;
		boolean truncated = false;
		for (Item item : container.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			count++;
			if (items.length() >= 200)
			{
				truncated = true;
				continue;
			}
			if (items.length() > 0)
			{
				items.append(", ");
			}
			items.append(itemManager.getItemComposition(item.getId()).getName())
				.append(" x").append(item.getQuantity());
		}
		log.info("[raid-recorder] {} read: {} items — {}{}", label, count, items, truncated ? ", ..." : "");
	}

	private void updateCurrentWeaponName(ItemContainer worn)
	{
		if (worn == null)
		{
			return;
		}
		Item weapon = worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		currentWeaponName = weapon != null && weapon.getId() > 0
			? itemManager.getItemComposition(weapon.getId()).getName() : "Unarmed";
	}

	private void snapshotEquipment(RoomCapture room, int tick)
	{
		if (!config.captureEquipment())
		{
			return;
		}
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null)
		{
			return;
		}
		RoomRecord.EquipmentSnapshot snapshot = new RoomRecord.EquipmentSnapshot();
		snapshot.setTick(tick);
		for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
		{
			Item item = worn.getItem(slot.getSlotIdx());
			if (item != null && item.getId() > 0)
			{
				snapshot.getItems().put(slot.name(),
					new RoomRecord.Item(item.getId(), itemManager.getItemComposition(item.getId()).getName()));
			}
		}
		// Skip consecutive identical snapshots (container fires on quantity-only changes too).
		java.util.List<RoomRecord.EquipmentSnapshot> timeline = room.getEquipmentTimeline();
		if (!timeline.isEmpty() && sameItems(timeline.get(timeline.size() - 1), snapshot))
		{
			return;
		}
		timeline.add(snapshot);
	}

	private static boolean sameItems(RoomRecord.EquipmentSnapshot a, RoomRecord.EquipmentSnapshot b)
	{
		if (a.getItems().size() != b.getItems().size())
		{
			return false;
		}
		for (Map.Entry<String, RoomRecord.Item> entry : a.getItems().entrySet())
		{
			RoomRecord.Item other = b.getItems().get(entry.getKey());
			if (other == null || other.getId() != entry.getValue().getId())
			{
				return false;
			}
		}
		return true;
	}

	/**
	 * Supply usage from inventory deltas. Everything is normalized to "doses": a Shark is 1 dose,
	 * a Saradomin brew(3) is 3. A sip turns (n) into (n-1) — net -1 dose for the base name; eating
	 * food removes the item — net -1. Positive deltas (looting supplies, ToA supply chests) are
	 * ignored. Coarse but robust; corroborating animation checks are documented as a limitation.
	 */
	private void trackSupplyUsage(ItemContainer inventory)
	{
		if (inventory == null)
		{
			return;
		}
		Map<String, Integer> doses = new LinkedHashMap<>();
		for (Item item : inventory.getItems())
		{
			if (item.getId() <= 0)
			{
				continue;
			}
			String name = itemManager.getItemComposition(item.getId()).getName();
			String key = ConsumableClassifier.supplyKey(name);
			if (key == null)
			{
				continue;
			}
			int dosesPerItem = doseCount(name);
			doses.merge(key, dosesPerItem * Math.max(1, item.getQuantity()), Integer::sum);
		}

		if (lastInventoryDoses != null)
		{
			RoomCapture room = session.getCurrentRoom();
			if (room != null)
			{
				for (Map.Entry<String, Integer> entry : lastInventoryDoses.entrySet())
				{
					int before = entry.getValue();
					int after = doses.getOrDefault(entry.getKey(), 0);
					if (after < before)
					{
						room.recordSupplyUsed(entry.getKey(), before - after);
					}
				}
			}
		}
		lastInventoryDoses = doses;
	}

	private static int doseCount(String itemName)
	{
		int open = itemName.lastIndexOf('(');
		if (open >= 0 && itemName.endsWith(")"))
		{
			try
			{
				return Math.max(1, Integer.parseInt(itemName.substring(open + 1, itemName.length() - 1)));
			}
			catch (NumberFormatException ignored)
			{
				// "(g)", "(or)" etc. — not a dose
			}
		}
		return 1;
	}

	// ------------------------------------------------------------------ //
	//                            Finalization                             //
	// ------------------------------------------------------------------ //

	/** Freeze the session into a record and hand it off. Client thread only. */
	public void finishRaid(String reason)
	{
		if (session == null)
		{
			return;
		}
		RaidSession finished = this.session;
		this.session = null;
		completionCountdown = -1;
		outOfRaidTicks = 0;
		recentInteractors.clear();

		int tick = client.getTickCount();
		lastFinishTick = tick;
		long now = System.currentTimeMillis();
		RoomRecord lastRoom = finished.completeRoom(tick, now, DOWNTIME_WINDOW_MIN_TICKS);
		if (lastRoom != null && onRoomCompleted != null)
		{
			onRoomCompleted.accept(lastRoom);
		}

		RaidRecord record = new RaidRecord();
		record.setRaid(finished.getModule().raidKey());
		populateAccount(record.getAccount());

		RaidRecord.Context context = record.getContext();
		context.setKc(finished.getKc());
		finished.getModule().populateContext(client, context);

		RaidRecord.Timing timing = record.getTiming();
		timing.setStartTick(finished.getStartTick());
		timing.setEndTick(tick);
		timing.setStartEpochMs(finished.getStartEpochMs());
		timing.setEndEpochMs(now);
		timing.setOfficialTotalTicks(finished.getOfficialTotalTicks());

		record.setRooms(finished.getCompletedRooms());
		record.setBankSnapshot(bankTracker.getLatestSnapshot());
		record.getUnverified().addAll(UnverifiedRegistry.all());

		log.info("[raid-recorder] recording finished ({}): {} rooms, {} ticks",
			reason, record.getRooms().size(), tick - finished.getStartTick());

		// A session that completed no rooms is a phantom/degenerate entry (e.g. entered and left the
		// lobby, or a residual detection blip): exporting it produces junk JSON/summary/prompt files
		// and pollutes raid history. Log it for visibility but don't propagate a useless record —
		// onRaidFinished drives both export and history-append in the plugin.
		if (record.getRooms().isEmpty())
		{
			log.info("[raid-recorder] discarding empty session ({}): no completed rooms, nothing exported",
				reason);
			return;
		}

		if (onRaidFinished != null)
		{
			onRaidFinished.accept(record);
		}
	}

	private void populateAccount(RaidRecord.Account account)
	{
		Player local = client.getLocalPlayer();
		account.setRsn(local != null && local.getName() != null ? local.getName() : "unknown");
		account.setWorld(client.getWorld());
		// Varbit 1777 — gameval VarbitID.IRONMAN, the same varbit legacy Varbits.ACCOUNT_TYPE names.
		account.setType(AccountType.fromVarbit(client.getVarbitValue(VarbitID.IRONMAN)));
		for (Skill skill : COMBAT_SKILLS)
		{
			account.getSkills().put(skill.name(), new RaidRecord.SkillLevels(
				client.getRealSkillLevel(skill), client.getBoostedSkillLevel(skill)));
		}
	}
}
