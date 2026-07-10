# Configuration Candidates

| Status | Mod / file / key | Current / default | Expected effect / trade-off / risk | Profiling first? |
|---|---|---|---|---|
| Verified candidate for testing | MineColonies server: `forceloadcolony`, `loadtime`, `colonyloadstrictness` | true/10/3, all defaults | Changes unattended footprint vs colony continuity | Yes, ticket map first |
| Plausible candidate requiring profiling | MineColonies: `pathfindingmaxthreadcount` | 1 / 1, range 1-10 | Queue latency vs CPU contention | Yes |
| Gameplay decision | MineColonies: `maxcitizenpercolony` | 250 / 250 | Population ceiling, not a direct throttle on existing load | Yes |
| Verified candidate for testing | Create server: `maxBlocksMoved` | 999999999 / 2048 | Restores a safety ceiling but affects allowed builds | Inventory contraptions first |
| Verified candidate for testing | Create server: `maxChainConveyorLength` | 1000 / 32 | Bounds possible graph length; may invalidate intended builds | Profile/inspect builds first |
| Plausible candidate requiring profiling | DH: threads/radius/generation mode | 16 / unknown auto default; radius 256 | CPU/disk/client coverage trade-off | Yes |
| Plausible candidate requiring profiling | Flywheel/Sodium automatic workers | -1 / 0 automatic sentinels | Client CPU scheduling vs mesh/instance latency | Yes |
| Leave unchanged | FerriteCore/ModernFix defaults | active defaults | Known optimization baseline; toggles carry compatibility risk | Only if stack points there |
| Insufficient evidence | FTB force-load allocation | max 25/team | Limit differs from actual used tickets and offline policy | Obtain server data |

No value above is a final recommendation.

