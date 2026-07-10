# AI and Entity Audit

| Component | Tick/path behavior and scaling | Configuration / risk | Evidence / confidence | Validation |
|---|---|---|---|---|
| MineColonies citizens, builders, couriers, guards, visitors, raiders | Per-entity state machines, asynchronous path searches with main-thread world interaction, request/inventory resolution; scales with active citizens, path length, work orders and colonies | Cap 250; one additional path thread; raid max 80 | config plus JAR class-name inspection; high subsystem, medium timing | Spark entity/AI/path/request stacks by scenario |
| Vanilla villagers/animals plus Naturalist, Friends & Foes, Goblin Traders, Ribbits, Guard Villagers, Illager Invasion, Takes a Pillage | Goal selectors/brains, sensing, target scans, navigation and spawning scale with loaded density | AI Improvements leaves goals enabled but replaces compatible look controller | metadata/config; medium | Entity counts and per-type tick time |
| Create contraptions, trains, actors and projectiles/items | Entity/block-entity ticks, collision, graph/schedule work and sync scale with active machinery | Contraption size ceiling effectively removed | config/JAR; high setting, medium cost | Largest machines idle and active |
| TACZ/RPG combat/spells/projectiles | Player-triggered entities, hit scans, animation/equipment sync and effects scale with combatants/rate | Exact packet/tick cadence unverified | metadata/class names; medium-low | Raid/combat packet and entity profile |

Tick intervals, search radii and synchronous boundaries not exposed in configuration remain implementation details and must not be invented from mod names.

