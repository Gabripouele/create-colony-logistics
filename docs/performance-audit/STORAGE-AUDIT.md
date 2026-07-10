# Storage and Disk I/O Audit

Vanilla region/entity/POI files save chunk, entity and village state; MineColonies adds colony/building/citizen/request persistence and backups; Create persists trains, contraptions, schedules and block entities; FTB persists teams/claims/tickets; Xaero and DH maintain map/LOD caches; player progression, skills, corpses/backpacks, voice caches, logs and crash reports add growth. The instance already contains `Distant_Horizons_server_data`, Xaero data, replay caches/recordings, logs and many JVM crash logs, proving client-side storage activity but not production-server save latency.

Smoothchunk sets a 300-second chunk save delay and unload limit 20/tick, intended to spread work; actual save-thread boundaries and spikes require measurement. Save frequency for mod capabilities normally follows dirty chunk/world saved-data cycles unless a mod implements independent I/O; this audit does not assume exact cadences. **Risk:** medium save spikes and long-term growth. **Validation:** disk latency/queue, Spark save stacks, world-size deltas and backup schedule on a copied world.

