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

## 1. Account type varbit **value ordering** — `AccountType.fromVarbit`

Varbit 1777 itself is verified (`VarbitID.IRONMAN` = legacy `Varbits.ACCOUNT_TYPE`), but the
**meaning of values 2-6** follows RuneLite's removed `AccountType` enum ordering
(0 NORMAL, 1 IRONMAN, 2 ULTIMATE, 3 HARDCORE, 4 GIM, 5 HC-GIM, 6 UNRANKED-GIM).
**Check:** log the raw value once in a live session (boomball is GIM → expect 4), or read
`net.runelite.client.game.chatbox`-era AccountType history in the runelite repo.
**Failure mode:** wrong label in exports; coach still never suggests buying (any non-zero value is
treated as an ironman variant except UNKNOWN).

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

## 8a. `TOA_CLIENT_RAID_LEVEL` reads 0 in the lobby (confirmed live, 2026-07-10)

Live-tested: with 21-22 invocations actively toggled in the lobby (including large-point ones —
Insanity, Walk The Path, Overclocked II), the varbit read consistently `0`. The invocation names
themselves read correctly and updated live tick-to-tick as toggles changed. **Fix applied**:
`ToaInvocationReader` now uses the varbit only when it's `>0` (in-raid, presumably
server-confirmed), falling back to `ToaInvocation.sumRaidLevel(activeInvocations)` — summing each
invocation's known point value — whenever the varbit is 0 (lobby preview). Both the raw varbit and
the computed sum are recorded in the export (`invocations.raw.raidLevelVarbit` /
`.raidLevelComputed`) for transparency. **Still unverified:** whether the varbit reliably populates
once you actually enter the raid (untested — no completed raid yet), and whether the point-sum
formula has an undocumented cap/rounding rule beyond the per-invocation values already in
`ToaInvocation`. **Check:** compare `raidLevelVarbit` to `raidLevelComputed` in a completed raid's
export; they should match once genuinely in-raid.

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
