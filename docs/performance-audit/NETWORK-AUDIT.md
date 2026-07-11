# Network Audit

| Rank | Producer | Direction / trigger / scaling | Controls and evidence |
|---|---|---|---|
| High | MineColonies | Server-to-client colony/citizen/request/building GUI state; client actions to server; scales with tracked citizens, GUI data and colonies | No broad packet throttle found; JAR/config inference; profile bytes/packets |
| High | Create | Block-entity, contraption, train, inventory and logistics synchronization; scales with watched active machinery | tickrate sync 20 ticks; schematic chunks max 1024 bytes; config verified |
| Medium | TACZ, RPG combat/spells/animations | Actions/projectiles/equipment/effects, player count and fire rate | JAR inference; runtime packet capture required |
| Medium | Simple Voice Chat | Bidirectional UDP audio; speaking players/listener proximity | port 24454, distance 48, MTU 1024, keepalive 1000 ms; config verified |
| Medium | DH/Xaero/Watut | LOD/map/player-action state; depends on enabled server protocols and views | deployment unclear; verify server mod set |
| Low/unknown | Inventory UIs, Jade/EMI/JEI | Mostly request/response or client computation | runtime profiling if large payload symptom exists |

