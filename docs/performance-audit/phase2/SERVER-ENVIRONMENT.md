# Server Environment

| Field | Confirmed production value | Evidence |
|---|---|---|
| Minecraft / NeoForge | 1.21.1 / 21.1.226 | launch path and Spark metadata |
| Java | Eclipse Temurin OpenJDK 21.0.8+9 HotSpot | `java -version`, production launch PATH |
| JVM arguments | No active user JVM flags | `user_jvm_args.txt`; all heap examples commented |
| Heap / GC | Ergonomic max 8,514,437,120 bytes; G1 | Spark metadata from identical QA launch |
| CPU | Intel Family 6 Model 183; 32 logical processors | environment; exact marketing model unverified |
| OS | 64-bit Windows, kernel 10.0.26200 | environment |
| Storage | Local storage volume, 1.8 TB total / 1.6 TB used reported by Spark | health output; filesystem/media type unverified |
| World | `world`; copied as `world-qa` | `server.properties` / QA isolation |
| Seed | blank property; actual `level.dat` seed pending console `/seed` capture | production config |
| View / simulation distance | 10 / 10 | production `server.properties` |
| Chunk writes / compression | synchronous / deflate | production `server.properties` |
| Server mods | 159 artifacts including three disabled filenames | authoritative server `mods` directory |
| Spark | 1.10.124 NeoForge; background profiler enabled | JAR/config/log |
| Smoothchunk | delay 300s; unload 20/tick; proto-save disabled; debug off | server config |
| Active datapacks | six world ZIPs, none identified as terrain/structure generators | world datapack inventory |

Production `world/serverconfig` contains only a readme; active server settings are therefore sourced from root `config` unless a mod stores world state elsewhere.
