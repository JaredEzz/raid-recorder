package tech.jaredezz.raidrecorder.raid.toa;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import tech.jaredezz.raidrecorder.RaidRecorderConfig;
import tech.jaredezz.raidrecorder.capture.UnverifiedRegistry;
import tech.jaredezz.raidrecorder.model.AccountType;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.raid.MechanicContext;
import tech.jaredezz.raidrecorder.raid.MechanicTag;
import tech.jaredezz.raidrecorder.raid.RaidModule;

/**
 * Tombs of Amascut. Room detection is by instance-template region ({@link ToaRegions}); lifecycle
 * by chat lines matching the patterns the Plugin Hub ToA plugins parse; context (raid level,
 * invocations, team) from varbits plus the lobby invocation interface.
 */
@Slf4j
@Singleton
public class ToaModule implements RaidModule
{
	private static final Pattern CHALLENGE_COMPLETE = Pattern.compile(
		"^Challenge complete: (?:Path of )?(The )?([A-Za-z\\- ]+?)\\.(.*)$");
	private static final Pattern DURATION = Pattern.compile(
		"(?:Duration|completion time):\\s*(\\d+):(\\d+)(?:\\.(\\d+))?");
	private static final Pattern KC = Pattern.compile(
		"Your completed (?:Expert Mode )?Tombs of Amascut(?:: Expert Mode)? count is:? ([\\d,]+)");
	private static final Pattern TOTAL_TIME = Pattern.compile(
		"^Tombs of Amascut(?::? Expert Mode)? total completion time:");
	private static final String RAID_START = "You enter the Tombs of Amascut";
	private static final String WIPE = "Your party failed to complete the challenge";

	/** Chat challenge name → room key. Path names are puzzle completions; boss names are bosses. */
	private static final Map<String, String> ROOM_BY_CHALLENGE_NAME = ImmutableMap.<String, String>builder()
		.put("Het", ToaRooms.PUZZLE_HET)
		.put("Crondis", ToaRooms.PUZZLE_CRONDIS)
		.put("Scabaras", ToaRooms.PUZZLE_SCABARAS)
		.put("Apmeken", ToaRooms.PUZZLE_APMEKEN)
		.put("Akkha", ToaRooms.AKKHA)
		.put("Ba-Ba", ToaRooms.BABA)
		.put("Kephri", ToaRooms.KEPHRI)
		.put("Zebak", ToaRooms.ZEBAK)
		.put("Wardens", ToaRooms.WARDENS_P3)
		.build();

	private static final int[] PARTY_SLOT_VARBITS = {
		VarbitID.TOA_CLIENT_P0, VarbitID.TOA_CLIENT_P1, VarbitID.TOA_CLIENT_P2, VarbitID.TOA_CLIENT_P3,
		VarbitID.TOA_CLIENT_P4, VarbitID.TOA_CLIENT_P5, VarbitID.TOA_CLIENT_P6, VarbitID.TOA_CLIENT_P7,
	};

	/**
	 * VarcStr ids 1099-1106 carry the raider display names in-raid.
	 * TODO(verify): sourced from ToaMistakeTracker RaidState.java (reads them after script 6585);
	 * confirm the names appear during a live raid via verbose logging.
	 */
	private static final int RAIDER_NAME_VARCSTR_BASE = UnverifiedRegistry.note(
		"TOA:VARCSTR_RAIDER_NAMES_1099_1106(ToaMistakeTracker RaidState)", 1099);
	private static final int RAIDER_NAME_VARCSTR_COUNT = 8;

	// Attackable-target NPC ids per room (gameval constants — compile-verified).
	private static final Set<Integer> AKKHA_TARGETS = ImmutableSet.of(
		NpcID.AKKHA_SPAWN, NpcID.AKKHA_MELEE, NpcID.AKKHA_RANGE, NpcID.AKKHA_MAGE,
		NpcID.AKKHA_ENRAGE_SPAWN, NpcID.AKKHA_ENRAGE_INITIAL, NpcID.AKKHA_ENRAGE,
		NpcID.AKKHA_SHADOW, NpcID.AKKHA_SHADOW_ENRAGE);
	private static final Set<Integer> BABA_TARGETS = ImmutableSet.of(
		NpcID.TOA_BABA, NpcID.TOA_BABA_COFFIN, NpcID.TOA_BABA_DIGGING, NpcID.TOA_BABA_BABOON);
	private static final Set<Integer> KEPHRI_TARGETS = ImmutableSet.of(
		NpcID.TOA_KEPHRI_BOSS_SHIELDED, NpcID.TOA_KEPHRI_BOSS_WEAK, NpcID.TOA_KEPHRI_BOSS_ENRAGE,
		NpcID.TOA_KEPHRI_SHIELD_SCARAB, NpcID.TOA_KEPHRI_GUARDIAN_MELEE,
		NpcID.TOA_KEPHRI_GUARDIAN_RANGED, NpcID.TOA_KEPHRI_GUARDIAN_MAGE,
		NpcID.TOA_KEPHRI_SCARAB_RANGEKITE);
	private static final Set<Integer> ZEBAK_TARGETS = ImmutableSet.of(
		NpcID.TOA_ZEBAK, NpcID.TOA_ZEBAK_ENRAGED);
	private static final Set<Integer> WARDENS_P1_P2_TARGETS = ImmutableSet.of(
		NpcID.TOA_WARDEN_ELIDINIS_PHASE1, NpcID.TOA_WARDEN_TUMEKEN_PHASE1,
		NpcID.TOA_WARDENS_P1_OBELISK_NPC, NpcID.TOA_WARDENS_P2_OBELISK_NPC,
		NpcID.TOA_WARDEN_ELIDINIS_PHASE2_MAGE, NpcID.TOA_WARDEN_ELIDINIS_PHASE2_RANGE,
		NpcID.TOA_WARDEN_ELIDINIS_PHASE2_EXPOSED, NpcID.TOA_WARDEN_TUMEKEN_PHASE2_MAGE,
		NpcID.TOA_WARDEN_TUMEKEN_PHASE2_RANGE, NpcID.TOA_WARDEN_TUMEKEN_PHASE2_EXPOSED,
		NpcID.TOA_WARDEN_TUMEKEN_CORE, NpcID.TOA_WARDEN_ELIDINIS_CORE, NpcID.TOA_WARDENS_ENERGY);
	private static final Set<Integer> WARDENS_P3_TARGETS = ImmutableSet.of(
		NpcID.TOA_WARDEN_ELIDINIS_PHASE3, NpcID.TOA_WARDEN_TUMEKEN_PHASE3,
		NpcID.TOA_WARDEN_ELIDINIS_PHASE3_CHARGING, NpcID.TOA_WARDEN_TUMEKEN_PHASE3_CHARGING,
		NpcID.TOA_WARDENS_ZEBAK, NpcID.TOA_WARDENS_BABA, NpcID.TOA_WARDENS_AKKHA,
		NpcID.TOA_WARDEN_TUMEKEN_CORE, NpcID.TOA_WARDEN_ELIDINIS_CORE);
	private static final Set<Integer> APMEKEN_TARGETS = ImmutableSet.of(
		NpcID.TOA_PATH_APMEKEN_BABOON_MELEE_1, NpcID.TOA_PATH_APMEKEN_BABOON_RANGED_1,
		NpcID.TOA_PATH_APMEKEN_BABOON_MAGIC_1, NpcID.TOA_PATH_APMEKEN_BABOON_MELEE_2,
		NpcID.TOA_PATH_APMEKEN_BABOON_RANGED_2, NpcID.TOA_PATH_APMEKEN_BABOON_MAGIC_2,
		NpcID.TOA_PATH_APMEKEN_BABOON_SHAMAN, NpcID.TOA_PATH_APMEKEN_BABOON_ZOMBIE,
		NpcID.TOA_PATH_APMEKEN_BABOON_CURSED, NpcID.TOA_PATH_APMEKEN_BABOON_THRALL);
	private static final Set<Integer> HET_TARGETS = ImmutableSet.of(
		NpcID.TOA_HET_GOAL_VULNERABLE);
	private static final Set<Integer> SCABARAS_TARGETS = ImmutableSet.of(
		NpcID.TOA_SCABARAS_SCARAB);

	private static final Map<String, Set<Integer>> TARGETS_BY_ROOM = ImmutableMap.<String, Set<Integer>>builder()
		.put(ToaRooms.AKKHA, AKKHA_TARGETS)
		.put(ToaRooms.BABA, BABA_TARGETS)
		.put(ToaRooms.KEPHRI, KEPHRI_TARGETS)
		.put(ToaRooms.ZEBAK, ZEBAK_TARGETS)
		.put(ToaRooms.WARDENS_P1_P2, WARDENS_P1_P2_TARGETS)
		.put(ToaRooms.WARDENS_P3, WARDENS_P3_TARGETS)
		.put(ToaRooms.PUZZLE_APMEKEN, APMEKEN_TARGETS)
		.put(ToaRooms.PUZZLE_HET, HET_TARGETS)
		.put(ToaRooms.PUZZLE_SCABARAS, SCABARAS_TARGETS)
		.build();

	private final ToaInvocationReader invocationReader;
	private final RaidRecorderConfig config;

	private List<String> lastLoggedParty = Collections.emptyList();
	private int lastLoggedTeamSize = -1;

	@Inject
	ToaModule(ToaInvocationReader invocationReader, RaidRecorderConfig config)
	{
		this.invocationReader = invocationReader;
		this.config = config;
	}

	@Override
	public String raidKey()
	{
		return "TOA";
	}

	@Override
	public String raidName()
	{
		return "Tombs of Amascut";
	}

	@Override
	public void onGameTick(Client client)
	{
		// Lobby-only interface: refresh the cached invocation set whenever it's readable.
		invocationReader.poll();
		if (config.verboseLog())
		{
			logPartyDiagnostic(client);
		}
	}

	/**
	 * Verbose-only proof-of-read for the team-size varbits and raider-name VarcStrs (the latter is
	 * TODO(verify) — see KNOWN_UNKNOWNS.md #3). Logs only when the reading changes, and is safe to
	 * poll every tick in or out of the raid since it never mutates capture state.
	 */
	private void logPartyDiagnostic(Client client)
	{
		int teamSize = 0;
		for (int varbit : PARTY_SLOT_VARBITS)
		{
			if (client.getVarbitValue(varbit) != 0)
			{
				teamSize++;
			}
		}

		List<String> party = new ArrayList<>();
		for (int i = 0; i < RAIDER_NAME_VARCSTR_COUNT; i++)
		{
			String name = client.getVarcStrValue(RAIDER_NAME_VARCSTR_BASE + i);
			if (name != null && !name.isEmpty())
			{
				party.add(name);
			}
		}

		if (teamSize != lastLoggedTeamSize || !party.equals(lastLoggedParty))
		{
			log.info("[raid-recorder] party read: teamSizeVarbits={} raiderNameVarcStrs={}", teamSize, party);
			lastLoggedTeamSize = teamSize;
			lastLoggedParty = party;
		}
	}

	@Override
	public boolean isInRaid(Client client)
	{
		Integer region = currentRegion(client);
		return region != null && ToaRegions.IN_RAID.contains(region);
	}

	@Override
	public String classifyCurrentRoom(Client client)
	{
		Integer region = currentRegion(client);
		return region != null ? ToaRegions.ROOM_BY_REGION.get(region) : null;
	}

	private static Integer currentRegion(Client client)
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}
		WorldPoint point = WorldPoint.fromLocalInstance(client, local.getLocalLocation());
		return point != null ? point.getRegionID() : null;
	}

	@Override
	public boolean isBossRoom(String roomKey)
	{
		return ToaRooms.BOSSES.contains(roomKey);
	}

	@Override
	public boolean isAttackableTarget(NPC npc, String roomKey)
	{
		Set<Integer> targets = TARGETS_BY_ROOM.get(roomKey);
		return targets != null && targets.contains(npc.getId());
	}

	@Override
	public String npcLabel(NPC npc)
	{
		String name = npc.getName();
		if (name == null || name.isEmpty() || "null".equals(name))
		{
			return "NPC " + npc.getId();
		}
		// Disambiguate the two Wardens phases sharing display names.
		if ("Elidinis' Warden".equals(name) || "Tumeken's Warden".equals(name))
		{
			return name + " (" + npc.getId() + ")";
		}
		return name;
	}

	@Override
	public MechanicTag classifyDamageTaken(MechanicContext context)
	{
		return ToaMechanics.classify(context);
	}

	@Override
	public ChatEvent onChatMessage(String message)
	{
		if (message.startsWith(RAID_START))
		{
			return ChatEvent.raidStarted();
		}
		if (message.startsWith(WIPE))
		{
			return ChatEvent.raidWiped();
		}

		Matcher kcMatcher = KC.matcher(message);
		if (kcMatcher.find())
		{
			return ChatEvent.kcLearned(Integer.parseInt(kcMatcher.group(1).replace(",", "")));
		}

		if (TOTAL_TIME.matcher(message).find())
		{
			return ChatEvent.raidCompleted(parseDurationTicks(message));
		}

		Matcher challenge = CHALLENGE_COMPLETE.matcher(message);
		if (challenge.matches())
		{
			String roomKey = ROOM_BY_CHALLENGE_NAME.get(challenge.group(2).trim());
			if (roomKey != null)
			{
				return ChatEvent.roomCompleted(roomKey, parseDurationTicks(message));
			}
		}
		return null;
	}

	/** "Duration: 3:44.40" or "completion time: 24:19.80" → game ticks (0.6 s each), -1 if absent. */
	static int parseDurationTicks(String message)
	{
		Matcher matcher = DURATION.matcher(message);
		if (!matcher.find())
		{
			return -1;
		}
		int minutes = Integer.parseInt(matcher.group(1));
		int seconds = Integer.parseInt(matcher.group(2));
		double fraction = 0;
		if (matcher.group(3) != null)
		{
			fraction = Double.parseDouble("0." + matcher.group(3));
		}
		return (int) Math.round((minutes * 60 + seconds + fraction) / 0.6);
	}

	@Override
	public void populateContext(Client client, RaidRecord.Context context)
	{
		context.setRaidLevel(invocationReader.readRaidLevel());

		RaidRecord.Invocations invocations = context.getInvocations();
		long mask = ToaInvocation.toBitmask(invocationReader.getLastKnownInvocations());
		invocations.getRaw().put("bitmask", mask);
		invocations.getRaw().put("raidLevelVarbit", invocationReader.getLastKnownRaidLevelVarbit());
		invocations.getRaw().put("raidLevelComputed", ToaInvocation.sumRaidLevel(invocationReader.getLastKnownInvocations()));
		invocations.getRaw().put("lastReadRaidLevel", invocationReader.getLastKnownRaidLevel());
		List<String> parsed = new ArrayList<>();
		for (ToaInvocation invocation : invocationReader.getLastKnownInvocations())
		{
			parsed.add(invocation.getDisplayName());
		}
		invocations.setParsed(parsed);

		int teamSize = 0;
		for (int varbit : PARTY_SLOT_VARBITS)
		{
			if (client.getVarbitValue(varbit) != 0)
			{
				teamSize++;
			}
		}
		context.setTeamSize(Math.max(teamSize, 1));

		List<String> party = new ArrayList<>();
		for (int i = 0; i < RAIDER_NAME_VARCSTR_COUNT; i++)
		{
			String name = client.getVarcStrValue(RAIDER_NAME_VARCSTR_BASE + i);
			if (name != null && !name.isEmpty())
			{
				party.add(name);
			}
		}
		if (party.isEmpty() && client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null)
		{
			party.add(client.getLocalPlayer().getName());
		}
		context.setParty(party);

		// Read at raid end: the real, server-authoritative contribution score and purple flag
		// (mirrors the toa-raid-log plugin, validated live). Odd sarcophagus varbit = purple, per
		// the same source.
		context.setPoints(client.getVarpValue(VarPlayerID.TOA_PERSONAL_CONTRIBUTION));
		context.setPurple((client.getVarbitValue(VarbitID.TOA_VAULT_SARCOPHAGUS) % 2) != 0);
	}

	@Override
	public Map<String, Double> benchmarks(String roomKey, int kc, AccountType accountType)
	{
		Map<String, Double> benchmarks = new LinkedHashMap<>();
		// Wardens P1/P2 has long forced-downtime phases (obelisk transitions), so expect less uptime.
		// 40.0 is an informed estimate (not a sourced benchmark — see KNOWN_UNKNOWNS.md #15), lowered
		// from the 55 base to avoid false WARNs on a phase where the uptime denominator counts ticks
		// the boss is unavoidably invulnerable (see KNOWN_UNKNOWNS.md #9).
		if (ToaRooms.WARDENS_P1_P2.equals(roomKey))
		{
			benchmarks.put("dpsUptimeWarnPct", 40.0);
		}
		return benchmarks;
	}

	@Override
	public List<String> allRoomKeys()
	{
		return ToaRooms.ALL;
	}

	/**
	 * Wiki-sourced primary combat style(s) per boss room; empty = "no opinion" (disables the
	 * wrong-style rule), which is the honest answer for rooms with no single dominant style.
	 * Every entry (and every deliberate omission) is cited below and in KNOWN_UNKNOWNS.md #13.
	 * Verified against the OSRS Wiki 2026-07-12.
	 */
	@Override
	public List<String> recommendedStyles(String roomKey)
	{
		switch (roomKey)
		{
			case ToaRooms.BABA:
				// Ba-Ba is a melee (stab) DPS fight: lowest melee defence is stab (+80), highest is
				// crush (+240); magic (+280) and ranged (+200) defences make casting/shooting the boss
				// strictly worse. The boulder/baboon mechanics are damage-avoidance, not a second DPS
				// style. Source: oldschool.runescape.wiki/w/Ba-Ba and /w/Tombs_of_Amascut/Strategies
				// ("Melee is primarily used for Ba-Ba, Kephri, Akkha, and Warden's core").
				return ImmutableList.of("MELEE");
			case ToaRooms.AKKHA:
				// Akkha's magic defence (+10) is far lower than ranged (+60) or melee (stab +60,
				// slash/crush +120); Tumeken's shadow (magic, 4x in-raid) is the strongest weapon here,
				// with ranged the clear secondary. Melee is viable for melee-only setups but slower.
				// Source: /w/Akkha (defensive stats) and /w/Tombs_of_Amascut/Strategies
				// ("Magic is primarily used for Akkha ... the strongest weapon against Akkha").
				return ImmutableList.of("MAGIC", "RANGED");
			case ToaRooms.ZEBAK:
				// Ranged-primary (Twisted bow is the strongest weapon at Zebak, exploiting his high
				// magic bonus), magic secondary. Source: /w/Tombs_of_Amascut/Strategies ("Ranged is
				// primarily used for Zebak ... the Twisted bow ... is the strongest weapon at Zebak").
				return ImmutableList.of("RANGED", "MAGIC");
			case ToaRooms.KEPHRI:
				// Deliberately empty — Kephri has no single dominant style, so any single-style claim
				// is wrong (the old "RANGED/MAGIC" here produced the bogus "Kephri's a ranged fight"
				// coaching). It is genuinely two-phase: the shielded/swarm phase is cleared with RANGED
				// (chinchompas / fast weapon), and the exposed/dazed boss is killed with MELEE (stab;
				// fang BIS — shield-phase defences are slash/ranged +300, magic +200, but stab only
				// +60). Fire magic is a niche third option (~40% fire weakness since May 2024). A
				// legitimate kill mixes MELEE (boss) and RANGED (swarms), so "no opinion" is the only
				// honest answer. Source: /w/Kephri (defensive stats) and /w/Tombs_of_Amascut/Strategies.
				return Collections.emptyList();
			default:
				// Wardens (P1_P2, P3) also deliberately omitted: style is phase-gated, not single.
				// P2 forces RANGED first (simultaneous Protect from Melee + Magic), then the exposed
				// core takes max hits only from MELEE; P3 mixes all styles. No single dominant style,
				// so "no opinion". Source: /w/Tumeken's_Warden and /w/Tombs_of_Amascut/Strategies.
				return Collections.emptyList();
		}
	}
}
