# Runtime Profiling Plan

Use a copied QA world for any scenario that can generate terrain, trigger raids, move contraptions, alter tickets, or change inventories. Record mod list/config hashes, JVM arguments, CPU/RAM/storage, player count, view/simulation distance, TPS/MSPT percentiles, GC, entity/block-entity/chunk counts, network rates, disk latency and client frametimes. Verify/install Spark on the server before this phase.

| Scenario | Isolates / participants | Spark and metrics | State risk / copy |
|---|---|---|---|
| Empty server idle | Baseline; no players | tickmonitor, profiler, health, GC, loaded chunks | Low; copy preferred |
| Main city | Dense non-colony build; one player | block entities, entities, chunks, packets; client FPS/frametime | Low |
| Active colony | Colony manager/citizens; one player | entity type, AI/path/request stacks, tickets | Medium; copy |
| Many citizens | Population scaling; one observer | MSPT percentiles, path queues/threads, packets | Medium; copy |
| Multiple builders | Work orders/Structurize | builder stacks, block checks/edits, disk | High; copy required |
| Warehouse/couriers | Request/inventory logistics | resolver/inventory stacks, packets | Medium; copy |
| Guard/raid | Combat AI/path/projectiles; players as needed | entities, goals, path, packets | High; copy required |
| Large Create factory | kinetic/item/fluid block entities | block-entity stacks, networks, transfers | Medium; copy |
| Active trains | graph/schedule/entities/tickets | train stacks, chunks/tickets, packets | High; copy |
| Chunk-loaded, no players | FTB/MineColonies/Create loaders | ticket dump, ticking chunks/entities | Medium; copy |
| Existing terrain travel | loading/I/O without generation | chunk load/I/O, server MSPT, client frametime | Low |
| New terrain travel | world generation/structures | chunk stage/structure stacks, CPU/disk | High; disposable copy required |
| Client terrain rendering with stable TPS | Sodium/Flywheel/DH/Iris | server TPS plus client CPU/GPU/VRAM/frametime/build queues | Low; avoid generation |

Run warm-up and repeated fixed-duration captures, change one variable at a time, and preserve raw Spark links/exports with scenario timestamps. Do not compare client FPS changes to server TPS as though they were the same bottleneck.
