# Create Colony Logistics

*A Goldstein Industries Endeavor*

Author: Gabripouele

Create Colony Logistics connects Create logistics monitoring with MineColonies inventory and request systems. Its primary player-facing tool is the Smart Colony Clipboard, while its server-side integration provides narrowly targeted caching and compatibility behavior for Create components reading MineColonies-managed inventories.

## Supported baseline

- Minecraft 1.21.1
- NeoForge 21.1.226 or newer compatible 21.1 releases
- Java 21
- Create 6.0.10 through the 6.0.x line (`[6.0.10,6.1.0)`)
- MineColonies 1.1.1041 for Minecraft 1.21.1 through the compatible 1.1 line (`[1.1.1041-1.21.1,1.2.0)`)
- Domum Ornamentum 1.0.220 through the compatible 1.0 line (`[1.0.220-snapshot,1.1.0)`)

The mod must be installed on both the client and server. It supports single-player/integrated servers and dedicated servers. Forge, Fabric, other Minecraft versions, and dependency versions outside the declared ranges are unsupported.

## Smart Colony Clipboard

The craftable Smart Colony Clipboard is separate from the normal MineColonies Clipboard. Shift-use it on a MineColonies Town Hall to link it explicitly to that colony.

Its custom screen provides:

- Up to 250 active colony requests with search and important-request filtering.
- Requester, worker, position, warehouse availability, and expandable dependency-tree information.
- Server-authorized request cancellation using MineColonies' `MANAGE_HUTS` permission.
- Domum Ornamentum Architect's Cutter shape, material, recipe, production, and recipe-teaching information when resolvable.
- Storage for up to 18 actual MineColonies Resource Scrolls, with linked builder-resource presentation.
- Storage for one actual MineColonies Colony Map, with access to the MineColonies map interface.

The colony link, filter setting, stored Resource Scrolls, and stored Colony Map persist on the item. The last selected screen tab is remembered only for the current client session.

## Create and MineColonies integration

The server-side integration is limited to established Create/MineColonies interaction points:

- Short-lived, copied Create inventory summaries for MineColonies combined inventory handlers.
- Fresh cached counts for compatible restocking Factory Panels.
- Warehouse threshold snapshots for Stockpile Switches aimed at MineColonies Warehouse huts.
- Packager and building-handler compatibility behavior.
- A targeted Funnel/inventory-extraction guard for malformed component data.

Actual insertion, extraction, inventory mutation, and request mutation remain server-authoritative. Ordinary non-MineColonies inventories fall back to Create's normal behavior.

## Installation

1. Install the supported NeoForge, Create, MineColonies, and Domum Ornamentum versions and MineColonies' required dependencies.
2. Place the same Create Colony Logistics JAR in the `mods` directory of every client and server using the world.
3. Start the game or server and verify that Create Colony Logistics appears in the mod list.

Back up existing worlds before adding or removing any mod. Removing Create Colony Logistics is not guaranteed to be consequence-free because worlds and player inventories may contain its registered item and persistent data components.

## Server configuration

NeoForge stores the per-world configuration in `serverconfig/create_colony_logistics-server.toml`.

| Key | Default | Range | Effect |
|---|---:|---:|---|
| `enableMineColoniesSummaryCache` | `true` | Boolean | Enables read-only Create summary caching for MineColonies combined handlers. |
| `cacheTtlTicks` | `40` | 1–12,000 | Maximum age of a cached inventory summary. |
| `enableWarehouseStockpileSwitchAdapter` | `true` | Boolean | Enables the MineColonies Warehouse Stockpile Switch adapter. |
| `warehouseStockpileCacheTtlTicks` | `40` | 1–12,000 | Maximum age of a warehouse threshold snapshot. |
| `smartClipboardProductionCacheTtlTicks` | `100` | 1–12,000 | Reuse period for Smart Clipboard production fallback data. |
| `debugLogging` | `false` | Boolean | Enables additional production cache diagnostics. |
| `logCacheStatsIntervalTicks` | `1200` | 0–72,000 | Cache-stat logging interval when debug logging is enabled; 0 disables it. |

Restart the world or server after changing configuration unless the active NeoForge environment has explicitly confirmed live reload behavior.

## Project links

- Source: https://github.com/Gabripouele/create-colony-logistics
- Issues: https://github.com/Gabripouele/create-colony-logistics/issues
- License: [MIT](LICENSE)

Create Colony Logistics is an independent addon. Create, MineColonies, and Domum Ornamentum are projects of their respective authors and contributors; their names are used only to identify compatibility and required dependencies, without implying ownership or endorsement.
