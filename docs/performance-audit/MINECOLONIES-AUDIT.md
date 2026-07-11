# MineColonies Audit

Installed: `minecolonies-1.1.1041-1.21.1.jar`, BlockUI 1.0.205, Structurize 1.0.782 snapshot, Domum Ornamentum 1.0.220 snapshot and MultiPiston 1.2.57.

| Key | Current / default / range | Effect and trade-off | Status / evidence |
|---|---|---|---|
| `maxcitizenpercolony` | 250 / 250 / 100-500 | Upper population ceiling; AI, path, request and sync load grows with actual population | Gameplay decision; config verified |
| `pathfindingmaxthreadcount` | 1 / 1 / 1-10 | Additional pathfinding threads; more concurrency can reduce queueing or increase contention | Plausible candidate requiring profiling |
| `forceloadcolony` | true / true | Keeps eligible colony chunks loaded after owner/officer presence | Verified candidate for testing after ticket measurement |
| `loadtime` | 10 / 10 / 1-1440 minutes | Duration after departure; no restart persistence | Verified candidate for testing |
| `colonyloadstrictness` | 3 / 3 / 1-15 | Higher loads fewer claimed chunks; gameplay continuity trade-off | Gameplay decision requiring ticket map |
| `maxBarbarianSize` | 80 / 80 / 6-400 | Raid entity ceiling and combat/path load | Conditional gameplay decision |
| Structurize `maxOperationsPerTick` | 1000 / 1000 / 0-100000 | World edits per tick; higher can increase spikes | Leave unchanged pending builder profile |
| Structurize `maxBlocksChecked` | 1000 / 1000 / 0-100000 | Worker schematic search/check work | Leave unchanged pending profile |

Colony manager, citizens, work orders, builders, guards, visitors, raids, requests/resolvers, warehouse/courier scheduling, persistence, claims and synchronization are all server relevant. Exact internal tick intervals, node limits and queue types were not exposed by installed config and require focused class inspection or runtime stacks. No evidence supports claims about deleted/archived colony cleanup without production world data.

