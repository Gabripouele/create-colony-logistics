# Phase 1 Static Performance Audit

## Scope and Evidence

Read-only audit dated 2026-07-09. The authoritative client inventory is the local client instance `mods` directory; installed client configuration is the sibling `config` directory. No confirmed dedicated-server directory, `server.properties`, world `serverconfig`, JVM command line, or Spark configuration was found in the supplied inputs. Consequently, server settings discussed here are **expected dedicated-server configuration copied from the client instance**, not confirmed production settings. The instance was not modified, launched, built, or benchmarked.

The complete 182-artifact inventory (178 enabled, four disabled) is in [MOD-INVENTORY.md](MOD-INVENTORY.md). Evidence labels used throughout are: **configuration**, **JAR**, **metadata**, **implementation inference**, and **profiling hypothesis**.

## Executive Findings

| Priority | Component / installed JAR | Environment / category | Current and default configuration | Impact and scaling | Risk | Confidence / evidence | Runtime validation |
|---|---|---|---|---|---|---|---|
| A | MineColonies `minecolonies-1.1.1041-1.21.1.jar` | Server; colony AI, requests, pathfinding, loading | `maxcitizenpercolony=250` (default 250); `forceloadcolony=true` (true); `loadtime=10` min (10); strictness `3` (3); path threads `1` (1) | Citizen AI, inventory/request resolution and pathfinding scale with active population and colony footprint; force-loading extends activity after departure | High when large colonies overlap other loaders | High; installed config comments and JAR metadata | Spark by entity/tick/worker and chunk-ticket inspection in copied QA world |
| A | Create `create-1.21.1-6.0.10.jar` | Server/shared; machinery and contraptions | `maxBlocksMoved=999999999` vs default `2048`; `maxChainConveyorLength=1000` vs `32`; belt length `40` vs `20` | Removes practical contraption bound and greatly enlarges possible conveyor graph; cost depends on constructed machines | High abuse/accidental-complexity ceiling, not proof of current lag | High; installed config | Profile representative largest contraptions and chain graphs |
| A | Distant Horizons `DistantHorizons-3.0.1-b-1.21.1.jar` | Primarily client plus integrated/server protocol; LOD generation, I/O, rendering | `numberOfThreads=16`, ratio `1.0`, LOD radius `256`; advanced debug `enableRendering=false`; generation mode must be verified in full config | Can contend for CPU, disk and generation resources; client rendering is distinct from server TPS | High on client/integrated worlds; unknown on dedicated server | High values, medium behavioral interpretation; config | Client frametime, CPU, disk queue and new-vs-existing terrain scenarios |
| A | Worldgen stack: Repurposed Structures, Create Structures Arise, seven YUNG structure mods, Naturalist/Friends & Foes content | Shared/server; structures/features/entities | Mostly datapack/JSON-driven; no global density conclusion from names | Exploration cost combines structure placement, template loading, feature placement and mob initialization | High during new-chunk exploration | Medium; inventory, metadata, config presence | Worldgen-only Spark profile in disposable copied world |
| B | FTB Chunks `ftb-chunks-neoforge-2101.1.9.jar` | Server/shared; claims and tickets | pack config permits `500` claimed and `25` force-loaded chunks per team; online/offline policy and world overrides require server-world verification | Entity/block ticks persist in force-loaded chunks according to ticket level | High if allocations are used or overlap colonies/Create loaders | High values, medium active state; config | `/ftbchunks`/ticket state plus Spark with no players nearby |
| B | Create Power Loader `create_power_loader-2.0.5-mc1.21.1.jar` | Server/shared; chunk tickets | 10-tick checks; radius 2 (9 chunks). Andesite train/contraption disabled; Brass enabled for trains and contraptions | Moving ticket footprint and periodic checks scale with loader count | Medium-high | High; installed config | Ticket inventory by loader type and moving-train scenario |
| B | Entity stack: MineColonies, Naturalist, Illager Invasion, Friends & Foes, Guard Villagers, Goblin Traders, Ribbits, RPG combat/projectiles | Server/shared; AI/entities/network | Per-mod configs vary; AI Improvements keeps behavior goals and replaces compatible look controllers | Goal selectors, target scans, pathfinding, spawn counts, projectiles and animation sync scale with loaded entities/players | Medium-high | Medium; metadata/config/class-name inference | Spark entity type, AI goal/path time, packet rates |
| B | Create factory/logistics plus `create_colony_logistics-0.3.6.jar` | Server/shared; block entities, inventories, networks | Create normal tick controls mostly default; local integration config has no broad throttle exposed | Belts, funnels, arms, packagers, stock/request links and inventory scans scale with devices and transfers | Medium-high | Medium-high; installed JARs/config and repository implementation | Isolate idle vs active factory, package burst and colony warehouse linkage |
| C | ModernFix 5.24.1, FerriteCore 7.0.2, AI Improvements 0.5.3, Clumps 19.0.0.1, Smoothchunk 4.1 | Mixed server/client optimization | ModernFix defaults active; FerriteCore dedup options active; Smoothchunk unload limit 20, save delay 300s; AI look-controller replacement active | Reduces memory/allocation/save or AI overhead; overlaps require observation, not removal assumptions | Low-medium compatibility risk | High config; medium net benefit | Baseline with mixin lists recorded; do not toggle until profiles exist |

## Priorities

### Priority A - Likely major contributors

MineColonies active population/pathfinding/loading; unbounded Create contraption limit and enlarged chain graphs; combined new-chunk structure generation; Distant Horizons CPU/I/O when generation or import is active.

### Priority B - Conditional contributors

FTB and Create Power Loader tickets; active Create factories/trains/package logistics; mob/RPG entity density; maps/voice/projectile traffic; save activity in colonies, maps, claims, DH and region files.

### Priority C - Low-risk tuning candidates

Thread counts, save/unload smoothing, render distances/culling, and bounded gameplay limits. These are candidates for controlled testing, not recommended changes.

## Limits

Static inspection cannot establish actual entity counts, used FTB allocations, `/forceload`, spawn-chunk radius, ticket levels, packet rates, executor saturation, worldgen timings, save latency, TPS, MSPT, or production JVM sizing. Those are explicitly deferred to [RUNTIME-PROFILING-PLAN.md](RUNTIME-PROFILING-PLAN.md).
