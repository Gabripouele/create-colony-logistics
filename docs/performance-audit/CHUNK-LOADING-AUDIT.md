# Chunk Loading Audit

| System | Mechanism / current state | Loaded vs ticking / duration | Overlap and impact | Evidence / confidence | Validation |
|---|---|---|---|---|---|
| MineColonies 1.1.1041 | Colony tickets when owner/officer loads part of colony; enabled, strictness 3 | Config says kept loaded for 10 minutes after departure, not across restart; precise ticket level needs runtime proof | Can overlap claims, spawn, player and Create loaders; cost scales with loaded colony AI/block entities | `config/minecolonies-server.toml`; high for settings | Inspect NeoForge tickets and entity-ticking chunks |
| FTB Chunks 2101.1.9 | Team force-load allocation, max 25/team; claims max 500 | Policy comments distinguish online/offline permission; actual server-world override and allocations absent | Potential persistent ticking footprint proportional to teams and allocation | `config/ftbchunks-world.snbt`; high values, low usage | Read production `world/serverconfig` and FTB data |
| Create Power Loader 2.0.5 | Radius-2 tickets (9 chunks), 10-tick checks; Brass train/contraption enabled, Andesite disabled for those forms | Ticket level and unload delay require full class/config verification | Moving trains can shift ticket sets; overlaps factories/colonies | installed config; high | Enumerate placed loaders and tickets |
| Vanilla | spawn chunks, `/forceload`, player, portal, entity and train movement tickets | Runtime/world dependent | Baseline overlap source | Vanilla subsystem; medium | `/forceload query`, spawn settings and ticket dump |
| Maps/DH | Xaero reads mapped chunks; DH may import/load/generate depending mode | No proof that current mode requests new server chunks | Client CPU/disk or server generation can be confused with rendering | config; medium-low | Verify DH generator mode and network requests |

No KubeJS files or top-level `defaultconfigs` files were found. Datapack functions and save-specific tickets remain a world-level verification item.

