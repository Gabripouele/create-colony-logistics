# Performance Mods Audit

| Mod | Purpose / side | Active configuration and overlap | Confidence |
|---|---|---|---|
| ModernFix 5.24.1 | memory, loading, allocation and bug fixes; both | default mixin set active; async JEI blacklist contains `jepb:jei_plugin` | High config |
| FerriteCore 7.0.2 | blockstate/model memory dedup; both/client loading | major dedup/replacement toggles true; compact map and small threading detector false | High config |
| AI Improvements 0.5.3 | entity goal/controller optimization; server | compatible look-controller replacement true; behavior-removal toggles false | High config |
| Sodium 0.6.13 / ImmediatelyFast 1.6.10 / Entity Culling 1.8.0 | rendering/meshing/batching/culling; client | active configs; Create contraptions excluded from Entity Culling | High |
| Clumps 19.0.0.1 | merges XP orbs; server/shared | active by installation; config not found | Medium |
| Smoothchunk 4.1 | spreads saves/unloads; server | save delay 300s, unload cap 20/tick | High config |
| Immersive Optimization 0.1.5 | entity tracking/culling; server/shared | entities enabled, block entities and force-loaded optimization false; Create excluded | High config |
| GPU Memory Leak Fix 1.8 | client GPU resource cleanup | tiny client artifact; config absent | Medium |
| Connectivity 7.1 | connection/network robustness | active config present; not assumed to improve packet throughput | Medium |

Chunk Sending and Packet Fixer are disabled. Spark is not in the authoritative client inventory. Missing categories to verify on the real server include a dedicated profiler, explicit region-I/O replacement, network compression optimizer and server chunk-generation optimizer; this is a gap inventory, not an installation recommendation.

