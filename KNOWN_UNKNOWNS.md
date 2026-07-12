# KNOWN_UNKNOWNS

Everything in this plugin that rests on data I could **not** compile-verify against
`runelite-api 1.12.32`, with exactly where to check each. Every entry here is also registered in
`UnverifiedRegistry` at runtime, so each exported JSON lists (in its `unverified` array) which of
these actually influenced that record.

**Verified and NOT listed here** (for contrast): all NPC ids (from
`net.runelite.api.gameval.NpcID`), varbits/varps (`VarbitID`/`VarPlayerID`: `TOA_CLIENT_RAID_LEVEL`
14380, `TOA_CLIENT_P0-P7` 14346-53, `IRONMAN` 1777, `SA_ENERGY` 300), interface group ids
(`InterfaceID.TOA_*`), container ids (`InventoryID` INV 93 / WORN 94 / BANK 95),
`Hitsplat.isMine()`, `HitsplatID` values, and the Party API usage (mirrors the working
toa-raid-log plugin built against this same API version). Region ids are cross-confirmed by four
independent Plugin Hub plugins (LlemonDuck/tombs-of-amascut, ToaMistakeTracker,
TombsOfAmascutStats, AdvancedRaidTracker) and treated as verified.

## 1. Account type varbit **value ordering** — `AccountType.fromVarbit` — **RESOLVED, verified live 2026-07-12**

Varbit 1777 (`VarbitID.IRONMAN` = legacy `Varbits.ACCOUNT_TYPE`) confirmed live during a full ToA
raid on boomball (a GROUP_IRONMAN account): the export correctly showed `GROUP_IRONMAN`. The
assumed enum ordering (0 NORMAL, 1 IRONMAN, 2 ULTIMATE, 3 HARDCORE, 4 GIM, 5 HC-GIM, 6
UNRANKED-GIM) is confirmed correct for value 4 at minimum; values 2/3/5/6 remain unverified but
the ordering pattern is now trusted.

## 2. GraphicsObject / Projectile ids in `ToaMechanics` (the big one)

No gameval class covers spotanim/graphics-object or projectile ids, so all of these are sourced
from **QuestingPet/ToaMistakeTracker** (files in parentheses) with two from
**LlemonDuck/tombs-of-amascut**. They ship on the Plugin Hub and are community-battle-tested, but
a game update can shift them and the compiler can't catch it.

| Constant | Value | Source file |
|---|---|---|
| Het beam (vertical / horizontal / crash) | 2064 / 2114 / 2120 | HetPuzzleDetector; crash from tombs-of-amascut BeamTimerTracker |
| Het orb of darkness | 379 | HetPuzzleDetector |
| Apmeken volatile explosion | 131 | ApmekenPuzzleDetector |
| Akkha quadrant/memory bombs | 2256–2259 | AkkhaDetector |
| Akkha unstable orb pop | 2260 | AkkhaDetector |
| Ba-Ba boulder shadows | 2250, 2251 | BabaDetector |
| Ba-Ba slam / rubble explosion | 1103 / 1463 | BabaDetector |
| Kephri bomb shadows / explosions | 1447, 1446, 2111 / 2156–2159 | KephriDetector |
| Zebak earthquake | 2184 | ZebakDetector |
| Wardens DDR / windmill (shadow, hit) / lightning / bomb | 2235 / 2236, 2234 / 2199, 2200 / 2198 | WardensP1P2Detector |
| Wardens P3 lightning | 2197 | WardensP3Detector |
| Kephri fireball / exploding scarab projectile | 2266 / 2147 | KephriDetector |
| Wardens prayer specials (melee/ranged/magic) | 2204 / 2206 / 2208 | WardensP1P2Detector |
| Wardens core skull | 2237 | WardensP1P2Detector |
| P3 ghost projectiles (Akkha mage/range, Zebak mage/range) | 2253 / 2255, 2181 / 2187 | WardensP3Detector |

**Check:** turn on **verbose logging** in the plugin config and run a raid; every damage hit that
fails classification logs its tile-graphics id, projectile id, source NPC and animation — compare
against this table. Known gap in the sources: the **Kephri ghost at Wardens P3** is unmodeled in
every hub plugin (tagged as WARDENS_P3_ATTACK here).
**Failure mode:** a hit degrades to `<ROOM>_ATTACK` / `UNKNOWN` (unavoidable) — under-counts
avoidable damage, never crashes.

## 3. Raider names from VarcStr 1099–1106 — `ToaModule.populateContext`

Sourced from ToaMistakeTracker `RaidState.java` (it reads them after `ScriptPostFired 6585`; we
read lazily at export time instead).
**Check:** verbose-log the `party` array after a group raid; compare with actual members.
**Failure mode:** empty/stale party list; falls back to local RSN only.

## 4. Outside-lobby region: 13454 vs 13455

Three plugins say 13454, AdvancedRaidTracker says 13455. Both are treated as **outside** the raid
(neither is in `ToaRegions.IN_RAID`), so nothing depends on resolving this; listed for honesty.

## 5. Chat-line wording variants

- Rooms: `Challenge complete: <name>. Duration: m:ss(.cs)` — parsed. The Wardens line uses
  `...completion time:` instead of `Duration:` (per LlemonDuck's SplitsTracker regexes); both
  keywords are accepted.
- Raid total: `Tombs of Amascut total completion time: ...`.
- KC: `Your completed Tombs of Amascut count is: N`. **Expert-mode wording variants for both are
  guesses** (`Expert Mode` infix handled two plausible ways in the regex).
- **Check:** run one normal and (eventually) one expert raid with verbose logging; the raw line is
  logged whenever a chat event parses — and unparsed `Challenge complete` lines simply don't match,
  so also grep the client log for `Challenge complete` to catch misses.
- **Failure mode:** missing `officialTicks` (own tick measurements still recorded), missing KC.

## 6. Deaths vs "downs" in group ToA

`ActorDeath` on the local player is counted as a death. In group raids ToA "downs" you (ghost)
rather than fully killing you unless the whole party wipes; whether `ActorDeath` fires on a down
is **unverified**.
**Check:** get downed once in a duo raid with verbose logging on.
**Failure mode:** deaths may under- or double-count in teams; solo is unaffected.

## 7. Supply-usage heuristic limits (by design, not a bug to fix)

Inventory-delta + name-based dose counting. Known blind spots: an item **dropped** counts as
consumed (rare mid-raid); ambrosia auto-revive consumption timing; ToA supply-chest purchases are
positive deltas (correctly ignored). Charged jewellery `(n)` suffixes are excluded by keyword —
new charged item names could leak through.
**Check:** compare a raid's `suppliesUsed` against what you actually ate/drank.

## 8a. `TOA_CLIENT_RAID_LEVEL` reads 0 in the lobby, populates in-raid — **RESOLVED, verified live 2026-07-12**

Live-tested 2026-07-10: with 21-22 invocations toggled in the lobby, the varbit read consistently
`0` while the invocation names themselves read correctly and updated live. **Fix applied**:
`ToaInvocationReader` uses the varbit only when it's `>0`, falling back to
`ToaInvocation.sumRaidLevel(activeInvocations)` when it's 0 (lobby preview); both values are
recorded in the export for transparency. **Confirmed 2026-07-12** on a full completed raid:
`raidLevelVarbit` and `raidLevelComputed` both read `280` — an exact match, confirming both (a) the
varbit does populate once genuinely in-raid, and (b) the point-sum formula has no hidden
cap/rounding at this invocation set. The point-sum formula is now trusted.

## 8. Invocation interface layout — `ToaInvocationReader`

Child 52 of `TOA_PARTYDETAILS`, toggle at `ordinal()*3`, active = 4th on-op listener arg == 1.
Validated live in July 2026 (toa-raid-log, this machine) and matching LlemonDuck + two other
plugins, but it's a scraped interface layout Jagex can rearrange.
**Failure mode:** empty invocation list in exports; raid level (varbit) unaffected.

## 9. DPS-uptime denominator semantics

"Attackable ticks" = ticks where an NPC from the room's target set exists. This intentionally
counts phase transitions where the boss is present but briefly invulnerable (e.g. Kephri between
phases, Wardens obelisk pauses are *mostly* excluded because those NPC forms aren't in the target
set). It's a consistent, comparable metric, not a perfect one; the Wardens P1/P2 benchmark is
lowered to 40% to compensate.

## 10. Hitsplat type 11 overload

Type 11 (CYAN_UP) is both Kephri's shield-heal and the Crondis-puzzle water meter. We don't use
type 11 for anything today; if you extend the recorder to track heals, disambiguate by room.

## 11. Live-feed "luck" is empirical, not a theoretical max-hit calculator (by design)

`LiveHitTracker` computes "how lucky was this hit" as a percentage of the highest hit you've
*actually landed* with the current weapon so far this raid — not a calculated theoretical maximum
from gear/prayers/style/boosts/target defense. A real combat-formula calculator would need to
correctly model every ToA invocation that changes damage output (Overclocked/Overclocked
II/Insanity, etc.) and would be a meaningful secondary project prone to subtle errors; the
empirical approach is always correct by construction, at the cost of the first hit with any
weapon having no ceiling to compare against yet (shown as "first hit," not scored). If a true
theoretical calculator is wanted later, it's a separate, larger addition — not a bug fix to this.

## 12. First full live-raid validation (2026-07-12) — findings and fixes

Ran a complete solo ToA raid (level 280, 21 invocations, GROUP_IRONMAN, KC 66→66, deathless,
10/10 rooms) end to end for the first time. Everything in the capture pipeline worked correctly:
region-based room detection through all 10 rooms, chat-parsed lifecycle (start/room-complete/
raid-complete/KC, including a fused multi-line completion message), points (19372) and purple
(false) captured at the chest, invocation/party/account-type reads, and the export pipeline.
Three real bugs were found and fixed same-day:

- **Cross-room mechanic misclassification (the significant one).** `ToaMechanics.classify()` had
  no per-room gating on its GraphicsObject/Projectile id lookups, so ids that collide with
  something in an unrelated boss's kit misfired outside their real room —
  `WARDENS_P3_GHOST_ATTACK` fired during Zebak and Akkha; `KEPHRI_BOMB` fired during Ba-Ba and
  Wardens P3. This corrupted the deterministic coach's avoidable-damage findings for those rooms
  (Zebak's entire damage-taken total was mislabeled as an avoidable Wardens mechanic). **Fixed**:
  every graphics-object/projectile mechanic tag now only fires when `ctx.getRoom()` matches its
  real owning room; a mismatch falls through to the NPC-id → hitsplat-type → generic
  `<ROOM>_ATTACK`/UNKNOWN chain instead. Covered by `ToaMechanicsTest`, including the exact
  reproduction case (P3 ghost projectile id in Zebak's room → no longer misfires).
  **Caveat**: raids captured *before* this fix (including `raid-20260712-103049`, the one full raid
  from today) still have the old mislabeled tags baked into their stored JSON — the room string was
  already resolved and persisted at capture time, so this can't be retroactively corrected without
  the raw graphics/projectile id, which isn't stored per-hit. Treat any `KEPHRI_BOMB` in Ba-Ba/
  Wardens P3 or `WARDENS_P3_GHOST_ATTACK` in Zebak/Akkha in that specific export as
  known-mislabeled: the damage amount is real, the mechanic name is not.
- **Phantom sessions right after a real raid finishes.** Raid detection is purely region-based, and
  the player is still standing in the boss region for a while after `finishRaid()` runs (looting,
  walking out), so an unguarded region check immediately started a brand-new session on the tail of
  the one that just ended — producing junk exports. **Fixed**: a 30-tick cooldown after
  `finishRaid()` during which region membership alone won't start a new session, bypassed instantly
  by a genuine `RAID_STARTED` chat line (a real re-entry). Also hardened `RAID_COMPLETED`/
  `ROOM_COMPLETED` chat handling to require the session already has real progress, so a residual
  chat event from the just-ended raid can't flip a brand-new empty session to "(completed)". Sessions
  with zero completed rooms no longer export (skip `onRaidFinished` entirely) — previously even a
  0-room session wrote three junk files.
- **Real damage falling through with no source NPC.** `probableSource()` only checked
  `npc.getInteracting() == player` at the exact hit tick, missing sources that briefly clear
  interaction state between committing an attack and the hitsplat landing (6 real, non-zero-damage
  events this raid: a 29-dmg hit in Ba-Ba, five 3-dmg hits in Wardens P1/P2). **Improved**: now
  checks (1) current-tick interaction, (2) an NPC that interacted with the player within the last 2
  ticks (same idiom as the existing tile-graphics lookback), (3) last resort, the nearest
  mid-animation NPC within 2 tiles. Still a heuristic — a pure off-screen/AoE hit can still resolve
  to UNKNOWN — but recovers real cases the old exact-tick check missed.

Also investigated and **ruled out** as a bug: Wardens P1/P2 showed 10,022 total damage dealt (5-10x
every other room), suspected as Necromancy-thrall damage leaking into the player's own totals (a
thrall was active during part of the raid). The raw JSON showed this is legitimate — dominated by a
single 1700-damage obelisk-detonation hitsplat plus repeated large mechanic hits — and thrall-range
hits (≤3 dmg) summed to only ~107 damage, negligible against the total. No thrall-attribution code
was added since there was no real bug to fix; the live-feed's per-weapon max/DPS tracking can still
be skewed by large non-weapon mechanic hitsplats landing as "mine," which remains an open item if
the live feed's accuracy during heavy-mechanic rooms ever needs tightening.

Also fixed: the coach's DPS-uptime rule could WARN a room whose displayed uptime exactly equals its
displayed threshold (e.g. "34% (expected ≥34%)") — root cause was comparing at full floating-point
precision while displaying rounded whole percents (33.83 vs a 34.0 threshold genuinely compares
below, but both round to "34%"). Fixed by rounding both sides to the same precision before
comparing. And: `SummaryWriter` never rendered the `points`/`purple` fields in the human-readable
summary despite capturing them correctly in the JSON — added as two more rows in the context table.

## 13. `recommendedStyles()` was an unverified gameplay-strategy claim — **RESOLVED, wiki-sourced 2026-07-12**

This is a genuine **process gap**, not just a code bug. `ToaModule.recommendedStyles()` — which
drives the coach's `WrongStyleRule` (WARN when a room's damage-by-style doesn't match the list) and
fed an LLM report — hardcoded per-boss "recommended styles" written from assumption during initial
development. It had **no citation, no wiki check, and no KNOWN_UNKNOWNS entry**, so it never went
through this project's "flag every unverified claim" discipline. It surfaced only when a real player
was told "Kephri's a ranged fight," which is wrong. The old table was `BABA → MELEE, RANGED`,
`ZEBAK/KEPHRI → RANGED, MAGIC`, everything else empty.

Fact-checked against the OSRS Wiki (`oldschool.runescape.wiki`) 2026-07-12:

| Room | Old (assumed) | Corrected (wiki-sourced) | Why |
|---|---|---|---|
| Ba-Ba | MELEE, RANGED | **MELEE** | Melee (stab) DPS fight; boulders/baboons are avoidance, not a 2nd style. `/w/Ba-Ba`, `/w/Tombs_of_Amascut/Strategies` |
| Kephri | RANGED, MAGIC (**wrong**) | **empty (no opinion)** | Two-phase: RANGED clears swarms, **MELEE (stab, fang)** kills the exposed boss (shield defences slash/ranged +300, magic +200, stab +60); fire magic a niche ~40% weakness. No single dominant style. `/w/Kephri` |
| Zebak | RANGED, MAGIC | **RANGED, MAGIC** (kept) | Ranged-primary (Twisted bow strongest), magic secondary. `/w/Tombs_of_Amascut/Strategies` |
| Akkha | empty | **MAGIC, RANGED** (added) | Magic defence +10 (vs ranged +60, melee +60/120); Tumeken's shadow strongest. `/w/Akkha`, strategies |
| Wardens P1/P2, P3 | empty | **empty** (kept, now cited) | Phase-gated: P2 forces ranged then melee-on-core; P3 mixed. `/w/Tumeken's_Warden` |

The headline correction is **Kephri**: the plugin's whole "one recommended style per room" model is
unsound for it, so the honest fix is an empty list (= disable the wrong-style rule there) rather than
force a single answer. Same reasoning omits the Wardens. Only rooms with a genuine single dominant
style now carry a claim, each cited in a comment in `recommendedStyles()`.
**Check:** re-confirm against the wiki after any ToA-affecting balance update (Kephri's fire
weakness, Ba-Ba's Jun-2025 melee-block change, and shadow's raid buff have all moved historically).
**Failure mode:** a stale style list mis-fires or suppresses `WrongStyleRule`; never crashes.

## Assumptions (explicit)

1. Raid detection is purely region-based (`WorldPoint.fromLocalInstance`); no varbit fallback for ToA.
2. One session at a time; leaving raid regions for 12+ ticks ends the recording (grace covers the
   short Nexus/corridor hops because those regions count as in-raid).
3. `Hitsplat.isMine()` correctly attributes party-member damage in ToA (it's the same API the DPS
   Counter uses).
4. Bank can never be read in-raid (true for ToA), so the "latest snapshot while open" model is safe.
5. The party "shared code" = RuneLite Party passphrase; no custom networking. Party messages are
   one compact summary per member per room (~8 messages per member per raid) — far below the party
   service's rate limits.
6. Special-attack usage is inferred from `SA_ENERGY` (varp 300) decreasing.
7. Prayer flicking cadence is NOT recorded (only overhead-icon switches per room) — true flick
   detection needs sub-tick data the API doesn't expose reliably.
