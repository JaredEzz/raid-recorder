# Raid Recorder

A [RuneLite](https://runelite.net) plugin that is a **black-box flight recorder for OSRS raids** —
Tombs of Amascut first — plus a **deterministic coach**. It supersedes the earlier standalone
[toa-raid-log](https://github.com/JaredEzz/toa-raid-log) plugin: that plugin's permanent raid
history, invocation-frequency stats and party invocation-sync checklist are merged in here (side
panel), alongside the much larger black-box capture + coach this plugin adds. Every raid produces
three artifacts in `<RuneLite home>/raid-recorder/` (configurable):

| File | What it is |
|---|---|
| `raid-<ts>.json` | The complete structured record — see [SCHEMA.md](SCHEMA.md) |
| `raid-<ts>-summary.md` | Obsidian-friendly human summary with coach findings |
| `raid-<ts>-prompt.md` | Paste-ready AI coaching prompt (system instruction + embedded JSON + your-goals placeholders). **The plugin never calls an AI** — you paste it wherever you like |

## What gets captured

- **Per room** (each puzzle and boss): entry/exit tick + wall clock, the game's *official*
  "Challenge complete" time alongside our own tick count, time-to-first-hit, **DPS uptime**
  (ticks-with-your-hit ÷ ticks-a-target-existed), idle windows, damage dealt (by target, by tick),
  every damage-taken event tagged with probable source NPC, **named mechanic and whether it was
  avoidable**, and where you were standing.
- **Gear at time of DPS**: worn-equipment snapshots on every change, so every hit is attributable
  to the switch you were on.
- **Supplies**: dose-normalized consumption per room from inventory deltas.
- **Session context**: RSN, world, account type, combat levels (real + boosted), raid level, the
  full invocation list (scraped in the lobby, cached through the raid), team size and member names,
  KC from the completion message, deaths, special attacks, prayer-switch counts.
- **Bank snapshots** whenever your bank is open — feeds the "you own an upgrade you didn't bring"
  coach rule.

## The coach

A pluggable rules engine (`coach/rules/`) runs over the finished record and emits findings —
`GOOD / INFO / WARN / CRITICAL`, each with a one-line explanation and the numbers behind it. It is
**KC-band aware** (a 5 KC learner is not held to 150 KC standards), **account-type aware** (never
tells an ironman to buy anything; the owned-but-unused rule only surfaces gear provably in *your*
bank), and raid-aware (benchmarks come from the raid module). Thresholds live in an editable
`coach-thresholds.json` next to the exports. It reports what went *well*, not just what went wrong.

## Side panel: raid history &amp; invocation sync (ToA)

The per-room capture and coach are file exports with no UI of their own by design (they're meant
to be read in Obsidian or pasted into an AI). The panel instead covers what toa-raid-log did:

- **Permanent raid history** — every completed ToA raid logged with its invocations, raid level,
  points (the real end-of-raid `TOA_PERSONAL_CONTRIBUTION` value, not a damage-based estimate),
  deaths, duration, party size and purple flag. Stored at
  `RUNELITE_DIR/raid-recorder/history/<accountHash>.json`, unbounded (config: `logHistory`).
- **Per-invocation frequency stats** — what fraction of your logged raids included each invocation.
- **Party invocation-sync checklist** — hit "Set target" to capture your current toggles and
  broadcast them to your RuneLite party; every member running this plugin shows green/red against
  the target, with a per-member match count (config: `invocationSync`).

## Group play

Uses RuneLite's built-in **Party** system — the party passphrase is the "shared code". Everyone
runs this plugin and joins the same party; each member broadcasts one compact summary per finished
room (well under party rate limits). A client with **"act as recorder"** enabled assembles the
per-member team report into its export. Solo play is fully functional with zero party dependency.

## Architecture (30 seconds)

```
RaidRecorderPlugin          wiring only: eventbus registration, callbacks, dev commands
 ├─ capture/CaptureEngine   raid-agnostic recorder; buffers in memory on the client thread
 │   ├─ RaidSession / RoomCapture   mutable in-progress state → frozen model objects
 │   ├─ BankTracker / ConsumableClassifier
 │   └─ UnverifiedRegistry  every TODO(verify) constant self-registers → exports stay honest
 ├─ raid/RaidModule (SPI)   everything raid-specific lives behind this interface
 │   ├─ toa/ToaModule       regions, rooms, mechanic taxonomy, chat parsing, invocations
 │   ├─ cox/CoxModule       stub with TODOs — drop-in later
 │   └─ tob/TobModule       stub with TODOs — drop-in later
 ├─ coach/CoachEngine       rules engine, runs off-thread; thresholds from editable JSON
 ├─ export/RaidExporter     JSON + summary.md + prompt.md on the background executor
 ├─ party/PartyAggregator   room-summary Party messages; RSN-keyed team report
 ├─ history/RaidHistoryStore  permanent per-account raid log + frequency stats (ported from toa-raid-log)
 └─ panel/RaidRecorderPanel   history/stats + invocation-sync checklist (ToA only)
```

Adding CoX/ToB = implement `RaidModule`, register it in `RaidModuleRegistry`. The engine, coach,
exporters and party layer need no changes. Raid selection is purely by region detection.

**Ground rules baked into the design:** no game-thread I/O (everything buffers in memory;
serialization happens on RuneLite's background executor), no invented IDs (every game-data
constant is either a compile-checked `gameval` reference or a cited, runtime-registered
TODO(verify) — see [KNOWN_UNKNOWNS.md](KNOWN_UNKNOWNS.md)), no secrets, no network egress beyond
the built-in party socket.

## Building

Requires JDK 11 (RuneLite's build target).

```sh
./gradlew build        # compile + unit tests
./gradlew run          # dev-mode RuneLite with the plugin loaded
```

On this machine: `./dev-run.sh` launches a detached dev client reusing the Jagex session
(`./stop-dev.sh` to stop it).

## Sideloading (without the Plugin Hub)

1. `./gradlew shadowJar` → `build/libs/raid-recorder-*-all.jar` (dev/testing only), **or** build
   the plain jar and drop it in RuneLite's `sideloaded-plugins/` folder
   (`~/.runelite/sideloaded-plugins/` — create it if missing; with Bolt on this machine:
   `~/.local/share/bolt-launcher/.runelite/sideloaded-plugins/`).
2. Restart the client. Sideloaded plugins show up alongside normal ones.

Plugin Hub submission is a separate later step: push to a public repo, add a
`plugins/raid-recorder` manifest (`repository=`, `commit=`) in a fork of
[runelite/plugin-hub](https://github.com/runelite/plugin-hub), open a PR.

## Verifying the tracking (recommended first run)

Enable **verbose logging** in the config, run `./dev-run.sh`, do a raid, then check
`/tmp/raid-recorder-dev-run.log` for:
- `entered room X / exited room X` lines matching where you actually were,
- `chat event ROOM_COMPLETED ...` lines with sane tick counts,
- `unclassified damage ...` lines — each is a mechanic id to add/fix in `ToaMechanics`,
- the three export files appearing when the raid ends.

## Docs

- [SCHEMA.md](SCHEMA.md) — the JSON record format + coach rule ids
- [KNOWN_UNKNOWNS.md](KNOWN_UNKNOWNS.md) — every unverified constant, how to verify each, and the
  explicit assumptions

## Config reference

Capture toggles (damage / equipment / supplies / positions / bank), manual start/stop mode
(`::rrstart` / `::rrstop` in the chat box), party sharing + act-as-recorder, invocation-sync
checklist + broadcast throttle, raid history logging, per-artifact export toggles, export
directory, thresholds path, auto-export, verbose logging.

## License

BSD 2-Clause. Region ids, interface layout and mechanic id research credit the Plugin Hub plugins
by LlemonDuck, QuestingPet, sreilly64 and capslock13 (all BSD 2-Clause) — see KNOWN_UNKNOWNS.md
for exact citations.
