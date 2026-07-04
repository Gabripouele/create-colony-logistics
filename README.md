# Create: Colony Logistics

Create: Colony Logistics is a NeoForge addon for Minecraft 1.21.1 that improves the performance of Create logistics monitoring when it reads MineColonies warehouse, rack, and building inventory handlers.

It targets one expensive integration point: repeated Create stock-summary scans over MineColonies `CombinedItemHandler`. The mod keeps Create's monitoring behavior intact, but returns a short-lived cached `InventorySummary` for repeated read-only summary calls within the configured TTL window.

## What It Does

- Caches Create `InventorySummary` results only when the backing inventory handler is MineColonies `CombinedItemHandler`.
- Uses weak handler keys so unloaded inventories can be garbage-collected.
- Uses identity-based handler keys so MineColonies handler equality cannot merge unrelated inventories.
- Keeps cache entries separated per handler instance to avoid cross-colony contamination.
- Falls back immediately to Create's original logic for every non-MineColonies handler.
- Falls back immediately if cache lookup, copying, or storage fails.

## What It Does Not Do

- Does not reduce Create monitoring.
- Does not remove factory gauges, stock links, packagers, or stock ticker behavior.
- Does not cache insertion, extraction, crafting, package creation, or item movement.
- Does not mutate MineColonies inventories from cached data.
- Does not alter Create gameplay or MineColonies gameplay.

## Why This Exists

Create stock monitoring can ask packagers for available items frequently. With ordinary inventories this is usually cheap. MineColonies warehouse-style storage can expose a virtual combined inventory spanning many racks and building handlers, so each `CombinedItemHandler.getStackInSlot` pass can become expensive at colony scale.

Spark profiling for the target case showed the hot path:

```text
FactoryPanelBehaviour.tickStorageMonitor
-> getLevelInStorage
-> PackagerBlockEntity.getAvailableItems
-> MineColonies CombinedItemHandler.getStackInSlot
```

For display and monitoring, a short-lived summary is safe because Create is asking what appears to be available. Actual insertions and extractions still go through the real MineColonies handlers, so item movement remains authoritative. Create 6.0.10 exposes `InventorySummary.copy()`, and this addon uses it when returning cached summaries.

## Supported Versions

- Minecraft 1.21.1
- NeoForge 21.1.219
- Java 21
- Create 6.0.10
- MineColonies 1.1.1041-1.21.1

## Unsupported Versions

- Forge builds
- Fabric builds
- Minecraft versions other than 1.21.1
- Create versions outside the 6.0.10 target line unless explicitly tested
- MineColonies versions outside 1.1.1041-1.21.1 unless explicitly tested

## Configuration

Server config defaults:

```toml
enableMineColoniesSummaryCache = true
cacheTtlTicks = 40
debugLogging = false
logCacheStatsIntervalTicks = 1200
```

Lower `cacheTtlTicks` for fresher display data. Raise it only if you understand the tradeoff: the cache affects monitoring summaries, not real movement, but longer TTLs can make displayed stock levels lag behind the colony inventory for longer.

## Smart Colony Clipboard

The Smart Colony Clipboard is a read-only diagnostic item for MineColonies colonies using Domum Ornamentum Architect's Cutter outputs. Use it on a MineColonies hut/building, or use it while standing in a colony, to scan active colony requests and report relevant Domum Ornamentum requests in chat.

It currently reports requested item/count, warehouse stock when safely readable, the Domum Ornamentum output id, matching cutter recipe source when extractable, the exact stack/component fingerprint, and which compatible colony crafting modules already know or can likely learn the exact combo.

It does not teach recipes yet, add keybinds, open a custom GUI, alter worker AI, change Create logistics, or mutate MineColonies request/crafting data. A future version is planned to help teach exact Domum Ornamentum Architect's Cutter combos to compatible huts.

## Spark Testing

1. Run the server without this addon.
2. Start profiling:
   ```text
   /spark profiler start
   ```
3. Let normal colony and factory operation run.
4. Stop profiling:
   ```text
   /spark profiler stop
   ```
5. Install this addon and repeat the same test.
6. Compare time spent in:
   - `FactoryPanelBehaviour.tickStorageMonitor`
   - `PackagerBlockEntity.getAvailableItems`
   - `CombinedItemHandler.getStackInSlot`

Expected result: repeated Create stock-summary reads against MineColonies `CombinedItemHandler` collapse into cached reads within the TTL window, reducing server-thread time while preserving automation behavior.

## Compatibility Reports

When reporting issues, include:

- Minecraft, NeoForge, Create, and MineColonies versions
- Full latest.log or crash report
- Spark profiler links when reporting performance
- A description of the MineColonies storage setup
- Number and type of Create logistics monitors involved

## Distribution Description

Create: Colony Logistics is a performance-focused Create and MineColonies integration addon. It preserves Create logistics monitoring while reducing repeated expensive scans of MineColonies virtual combined inventories by caching read-only stock summaries for a short configurable TTL. Insertion, extraction, package creation, and MineColonies storage behavior remain untouched.
