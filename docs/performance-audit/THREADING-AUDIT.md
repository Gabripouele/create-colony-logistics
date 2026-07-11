# Threading and Executor Audit

| Executor | Current/default | Purpose and contention | Side | Confidence / validation |
|---|---|---|---|---|
| MineColonies pathfinding | `pathfindingmaxthreadcount=1`, default 1, range 1-10 | Additional path searches; results still interact with main-thread world/entity state | Server | High config; profile queue latency and CPU before changing |
| Distant Horizons | 16 threads, runtime ratio 1.0, priority 5 | LOD import/generation/processing and I/O; may contend with client render, integrated server or chunk workers | Client/integrated; server capability unclear | High config; inspect named threads and mode |
| Flywheel | `workerThreads=-1` automatic; 0 disables parallelism | Instance update/render preparation | Client | High config; inspect resolved count and frametime |
| Sodium | `chunk_builder_threads=0` automatic | Client chunk mesh building | Client | High config; inspect resolved workers |
| Vanilla/NeoForge | chunk generation, region I/O, resource reload, network compression | Automatic pools depend on JVM/CPU; exact sizing not captured statically | Both/server | Medium; capture thread dump and Spark threads |
| ModernFix | dedicated reload executor and thread-priority mixins default active | Resource/datapack loading and contention control | Both | High properties; validate loaded mixins |
| Spark | profiler executors | Spark not found as a top-level installed JAR; may be server-side or bundled, unconfirmed | Server | Low; verify server inventory before Phase 2 |

