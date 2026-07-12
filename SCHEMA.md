# raid-&lt;timestamp&gt;.json — schema 1.0.0

One file per raid, written to the export directory (default `<RuneLite home>/raid-recorder/`).
All ticks are game ticks (0.6 s). `-1` anywhere means "not captured / not applicable".

```jsonc
{
  "schemaVersion": "1.0.0",
  "raid": "TOA",                      // raid key from the module: TOA | COX | TOB

  "account": {
    "rsn": "boomball",
    "type": "GROUP_IRONMAN",          // NORMAL | IRONMAN | ULTIMATE_IRONMAN | HARDCORE_IRONMAN
                                      // | GROUP_IRONMAN | HARDCORE_GROUP_IRONMAN
                                      // | UNRANKED_GROUP_IRONMAN | UNKNOWN
    "world": 330,
    "skills": {                       // combat-relevant skills at raid end
      "ATTACK": { "real": 99, "boosted": 118 },
      "STRENGTH": { "real": 99, "boosted": 118 },
      "DEFENCE": { "real": 99, "boosted": 118 },
      "RANGED": { "real": 99, "boosted": 112 },
      "MAGIC": { "real": 99, "boosted": 112 },
      "HITPOINTS": { "real": 99, "boosted": 121 },
      "PRAYER": { "real": 99, "boosted": 99 }
    }
  },

  "context": {
    "kc": 123,                        // scraped from the completion chat message; -1 if unseen
    "raidLevel": 305,                 // varbit TOA_CLIENT_RAID_LEVEL
    "invocations": {
      "raw": {                        // exactly as read, for reprocessing
        "bitmask": 123456789,         // bit i = ToaInvocation.values()[i] active
        "raidLevelVarbit": 305,
        "lastReadRaidLevel": 305      // raid level at the moment the interface was last readable
      },
      "parsed": ["Walk The Path", "Pathfinder", "..."]  // display names, interface order
    },
    "teamSize": 2,                    // occupied TOA_CLIENT_P0..P7 slots (min 1)
    "party": ["boomball", "GIMArcher"] // raider names (VarcStr 1099-1106; unverified — see KNOWN_UNKNOWNS)
  },

  "timing": {
    "startTick": 1000, "endTick": 4200,          // client tick counter (client-relative!)
    "startEpochMs": 0, "endEpochMs": 0,          // wall clock
    "officialTotalTicks": 2702                   // from "total completion time" chat; -1 if unseen
  },

  "rooms": [
    {
      "room": "AKKHA",               // PUZZLE_HET | AKKHA | PUZZLE_CRONDIS | ZEBAK | PUZZLE_SCABARAS
                                     // | KEPHRI | PUZZLE_APMEKEN | BABA | WARDENS_P1_P2 | WARDENS_P3
      "order": 3,                    // 1-based order the player actually did rooms in
      "entryTick": 0, "exitTick": 0,
      "entryEpochMs": 0, "exitEpochMs": 0,
      "officialTicks": 374,          // from "Challenge complete ... Duration" chat; -1 if unseen
      "timeToFirstHitTicks": 4,      // entry → first own hitsplat; -1 = never hit anything
      "dpsUptimePct": 71.4,          // ticks-with-own-hit ÷ ticks-with-attackable-target × 100
      "downtimeWindows": [[120, 14]],// [startOffsetTicks, lengthTicks] idle gaps ≥10 ticks

      "damageDealt": {
        "total": 8140,
        "byTarget": { "Akkha": 7200, "Akkha's Shadow": 940 },
        "byTick": [[4, 43], [8, 51]] // [tickOffsetFromEntry, amount] per hitsplat
      },

      "damageTaken": [
        {
          "tick": 1234,              // absolute client tick (subtract entryTick for offset)
          "amount": 22,
          "hitsplatType": 16,        // net.runelite.api.HitsplatID
          "sourceNpc": "Akkha",      // probable source (NPC targeting you) or "UNKNOWN"
          "mechanic": "AKKHA_QUADRANT_BOMB",  // module taxonomy key, "<ROOM>_ATTACK", or "UNKNOWN"
          "avoidable": true,
          "worldPoint": { "x": 3680, "y": 5405, "plane": 0 }  // instance-template coords
        }
      ],

      "equipmentTimeline": [         // snapshot on entry + every change; dedup'd
        { "tick": 0, "items": { "WEAPON": { "id": 27246, "name": "Osmumten's fang" },
                                 "HEAD": { "id": 26382, "name": "Torva full helm" } } }
      ],

      "suppliesUsed": { "Saradomin brew": 3, "Shark": 2, "Nectar": 4 },  // dose-normalized counts
      "deaths": 0,
      "specsUsed": 2,                // SA_ENERGY decreases
      "prayerSwitches": 41           // overhead-icon changes observed in the room
    }
  ],

  "bankSnapshot": {                  // latest snapshot from any bank visit this session; null if none
    "capturedEpochMs": 0,
    "items": { "27246": 1, "385": 214 },   // itemId → quantity
    "names": { "27246": "Osmumten's fang", "385": "Shark" }
  },

  "coachFindings": [                 // deterministic rules engine output, CRITICAL→WARN→INFO→GOOD
    {
      "room": "AKKHA",               // room key or "RAID"
      "severity": "WARN",            // GOOD | INFO | WARN | CRITICAL
      "rule": "dps_uptime_low",      // stable rule id
      "message": "one-line human explanation",
      "evidence": { "uptimePct": 48.2, "warnThresholdPct": 55.0 }   // rule-specific numbers
    }
  ],

  "teamReport": {                    // only when "act as recorder" + party mode; else absent
    "GIMArcher": [
      { "room": "AKKHA", "damageDealt": 6100, "damageTaken": 88, "deaths": 0, "dpsUptimePct": 64.0 }
    ]
  },

  "unverified": [                    // which KNOWN_UNKNOWNS entries influenced this record
    "TOA:GO_AKKHA_QUADRANT_2256_2259(AkkhaDetector)"
  ]
}
```

## Rule ids the coach can emit

| rule | severity | meaning |
|---|---|---|
| `death` | CRITICAL | died in a room |
| `deathless` | GOOD | zero deaths raid-wide |
| `avoidable_damage_high` | CRITICAL | avoidable damage ≥ critical threshold (KC-scaled) |
| `avoidable_damage` | WARN | avoidable damage ≥ warn threshold |
| `avoidable_damage_clean` | GOOD | boss room with zero avoidable damage |
| `dps_uptime_low` | WARN | uptime below KC-adjusted benchmark |
| `dps_uptime_good` | GOOD | uptime ≥ good threshold |
| `slow_first_hit` | WARN | slow ramp-up entering a boss room |
| `long_idle_window` | INFO | largest no-damage stretch in a boss room |
| `wrong_gear_style` | WARN | meaningful damage share on a non-recommended style |
| `supplies_on_avoidable` | WARN | heavy supply burn where most damage was avoidable |
| `supplies_total` | INFO | raid-wide supply totals |
| `owned_but_unused_upgrade` | INFO | bank has a better weapon (per style ladder) than what was brought |

Thresholds live in `coach-thresholds.json` in the export directory (created with defaults on first
run; edit freely — reread every raid). KC bands relax expectations at low kill counts; account
type gates what the coach may suggest.
