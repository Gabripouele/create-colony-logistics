# 2026-05-29 Stockpile Switch Stream Crash Investigation

## Scope

Investigated a delayed server crash reported at 2026-05-29 02:28:40 and a follow-up watchdog report at 2026-05-29 02:29:41 in a Minecraft 1.21.1 / NeoForge 21.1.172 environment with Create 6.0.6, MineColonies 1.1.1041-1.21.1, and Create: Colony Logistics 0.3.0-rc.3.

The crash report files were not present in this checkout or under `run/`, so this report uses the stack/frame details supplied in the prompt plus local source and dependency bytecode inspection.

## Root Crash Summary

The root crash is:

- Description: `Ticking block entity`
- Exception: `java.lang.IllegalStateException: stream has already been operated upon or closed`
- Ticking block entity: `create:stockpile_switch`
- Block entity class: `com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity`
- Location: `(280,71,-193)`
- Stack path: `ReferencePipeline.filter(...)` -> `ThresholdSwitchBlockEntity.updateCurrentLevel(...)` -> `ThresholdSwitchBlockEntity.lazyTick(...)` -> `SmartBlockEntity.tick(...)`

Bytecode inspection of Create 6.0.6 shows `ThresholdSwitchBlockEntity.updateCurrentLevel()` creates a fresh stream from Create's static `COMPAT` list for each observed inventory slot:

```java
COMPAT.stream()
    .filter(compat -> compat.isFromThisMod(blockEntity))
    .map(compat -> compat.getSpaceInSlot(handler, slot))
    .findFirst()
```

The failing `filter` frame corresponds to this Create-side compat stream, not to a stream produced by Create: Colony Logistics.

## Follow-Up Watchdog Report

The 2026-05-29 02:29:41 watchdog report is most likely a symptom/follow-up, not the root cause. It happened about one minute after the ticking block entity crash and the described environment was heavily loaded, including 25,115 overworld block entities. No local code path points to a separate Create: Colony Logistics server tick loop that would independently explain the watchdog.

## Files and Classes Inspected

- `src/main/java/com/createcolonylogistics/cache/ColonyStockCache.java`
- `src/main/java/com/createcolonylogistics/cache/CachedSummary.java`
- `src/main/java/com/createcolonylogistics/cache/CreateInventorySummaryAdapter.java`
- `src/main/java/com/createcolonylogistics/mixin/PackagerBlockEntityMixin.java`
- `src/main/java/com/createcolonylogistics/mixin/TileEntityColonyBuildingMixin.java`
- `src/main/java/com/createcolonylogistics/config/ColonyLogisticsConfig.java`
- `src/main/java/com/createcolonylogistics/CreateColonyLogistics.java`
- `src/main/java/com/createcolonylogistics/clipboard/RequestAnalysisService.java`
- `src/main/java/com/createcolonylogistics/clipboard/SmartClipboardReport.java`
- `src/main/java/com/createcolonylogistics/clipboard/DomumOrnamentumRequestInspector.java`
- `src/main/java/com/createcolonylogistics/clipboard/ColonyProductionInspector.java`
- `src/main/java/com/createcolonylogistics/clipboard/SmartClipboardScrollStorage.java`
- `src/main/java/com/createcolonylogistics/network/*SmartClipboard*Packet.java`
- `src/main/java/com/createcolonylogistics/client/SmartClipboardScreen.java`
- Create 6.0.6 bytecode:
  - `com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity`
  - `com.simibubi.create.content.logistics.packager.PackagerBlockEntity`
  - `com.simibubi.create.content.logistics.packager.InventorySummary`
  - `com.simibubi.create.foundation.blockEntity.behaviour.inventory.VersionedInventoryTrackerBehaviour`
- MineColonies bytecode:
  - `com.minecolonies.api.inventory.api.CombinedItemHandler`

## Relevant Local Flow

### Create packager / factory gauge style summary path

Create reader -> `PackagerBlockEntity.getAvailableItems()` -> `PackagerBlockEntityMixin` -> `ColonyStockCache` -> cached `InventorySummary` -> returned copy.

Object safety:

- `IItemHandler` target: live MineColonies `CombinedItemHandler`; mutable live object, used only as a weak identity cache key and scan source.
- Cache key: weak identity wrapper around handler; not an `ItemStack` key.
- Cached value: Create `InventorySummary`; materialized map/list structure, but mutable.
- Returned value: `CreateInventorySummaryAdapter.safeCopy(cached.summary())`; intended to be a copy.
- Streams/iterators in this path: no stored `Stream`, `Iterator`, `Iterable`, or `Supplier<Stream>` found.

### Create stockpile / threshold switch path

Create stockpile switch -> `ThresholdSwitchBlockEntity.updateCurrentLevel()` -> target `IItemHandler.getSlots()` / `getStackInSlot()` / `getSlotLimit()` -> MineColonies `CombinedItemHandler`.

Object safety:

- The stockpile switch does not call `PackagerBlockEntity.getAvailableItems()`.
- The Create: Colony Logistics cache does not intercept `ThresholdSwitchBlockEntity`.
- The stream in the crash is Create's static threshold compat list stream, not a CCL cached object.

### Smart clipboard request and warehouse path

Smart clipboard -> `RequestAnalysisService.analyze()` -> MineColonies request/building/warehouse APIs -> `SmartClipboardReport`.

Object safety:

- Request roots are materialized into a `LinkedHashMap` and returned as `roots.values()`, then immediately streamed and converted with `toList()`.
- Open building requests are materialized into `ArrayList`.
- Display/request stacks are copied before report serialization.
- Warehouse stock uses reflected `getMatchingItemStacksInWarehouse(Predicate)` result only locally and does not cache or expose it to Create.
- No shared cache between clipboard reports and Create stock/gauge readers was found.

## Findings Against Questions A-J

A. No local field/static/cache/record/DTO stores a `Stream`, `Iterator`, `Iterable`, or `Supplier<Stream>`.

B. No code path returns the same `Stream` or `Iterator` instance more than once.

C. No cached MineColonies live collection is exposed to Create. `ColonyStockCache` caches Create `InventorySummary`, not MineColonies collections.

D. Risk found: Create `InventorySummary.copy()` is not a deep `ItemStack` copy in all cases. Create's `InventorySummary.add(ItemStack, int)` stores a `BigItemStack` with the original `ItemStack` unless the input count exceeds max stack size. Therefore CCL's cache can preserve `ItemStack` references originating from `IItemHandler.getStackInSlot()`. This is mutable snapshot risk, but it does not explain the reported `ReferencePipeline.filter` stream reuse exception by itself.

E. No local use of `ItemStack` as a `Map` key was found in the cache. Other maps use strings, resource locations, tokens, or items.

F. The cache can be shared between multiple `PackagerBlockEntity.getAvailableItems()` readers targeting the same MineColonies `CombinedItemHandler` identity. No evidence found that the `ThresholdSwitchBlockEntity` reads this cached summary.

G. No 20-tick or lazy poll in CCL returns a one-shot source to Create. The Create threshold switch lazy tick directly scans the live item handler.

H. Cache invalidation is TTL + handler identity weak-reference pruning + slot-count check. It does not observe building/rack/worker inventory mutations directly. This can produce stale counts for up to `cacheTtlTicks` (currently 40), but should not produce stream reuse.

I. `ColonyStockCache.INSTANCE` is a mod singleton. Entries are weakly keyed by handler identity, but there is no explicit server/level/dimension partition. A live handler reference keeps the entry reachable through the weak key until GC/pruning. This is acceptable for short TTL behavior, but a safer model would include level identity and explicit clear on server/level unload.

J. Exceptions during cache lookup/store are caught before storing/replacing, so no partially consumed lazy object is left behind. Logging is debug-only; if this area is hardened, diagnostics should be rate-limited and include handler class/slot count/target position when available.

## Highest-Risk Local Code Path

The highest-risk local path is `ColonyStockCache.store()` caching a copied Create `InventorySummary`, because `CreateInventorySummaryAdapter.safeCopy()` delegates to `InventorySummary.copy()` and falls back to returning the original summary if copy throws.

Risk details:

- `InventorySummary` is mutable.
- `InventorySummary.copy()` reconstructs entries through `add(BigItemStack.stack, count)`.
- Create's `add(ItemStack, int)` can retain the passed `ItemStack` reference.
- `safeCopy()` fallback can return the original summary, which could also be held by Create's own `PackagerBlockEntity.availableItems`.

This should be hardened, but it is not a confirmed route to the exact `stream has already been operated upon or closed` exception because neither `InventorySummary` nor MineColonies `CombinedItemHandler` stores or returns a Java `Stream`.

## Implication Assessment

Create: Colony Logistics is indirectly implicated in performance-sensitive MineColonies/Create inventory monitoring because it caches summaries for MineColonies `CombinedItemHandler` targets. However, based on the inspected code paths, it is not directly implicated in the specific stockpile switch stream crash.

The exact exception is more consistent with something making Create's `ThresholdSwitchBlockEntity.COMPAT.stream()` return or operate on a previously consumed stream, or with a transformed/mixed-in version of the threshold switch method differing from the inspected Create 6.0.6 bytecode. The supplied stack does not show CCL frames, and CCL does not modify threshold switch behavior.

No concrete evidence was found to blame Create: Enchantment Industry, ModernFix, MineColonies, or Create. The next evidence to gather is the transformed runtime class/mixin list around `ThresholdSwitchBlockEntity` and any mod that touches `java.util.stream`, Create threshold compat, or `SmartBlockEntity`.

## Recommended Fix Plan

1. Harden `CreateInventorySummaryAdapter.safeCopy()` so it always creates a fresh `InventorySummary` from copied `ItemStack`s and never returns the original summary.
2. Avoid caching `InventorySummary` directly if possible. Prefer a CCL-owned immutable DTO such as `List<StackCount>` with copied stacks or item/component keys and primitive counts, then build a fresh `InventorySummary` per external read.
3. Add explicit cache clearing on server stop/level unload if a suitable NeoForge lifecycle event is available.
4. Keep the TTL-based optimization only after snapshot ownership is guaranteed. Poll interval changes are mitigation only, not root-cause fixes.
5. Add controlled diagnostics for cache build failures: handler class, slot count, level dimension, game time, and caller path, rate-limited.
6. Capture a transformed stack/class dump for `ThresholdSwitchBlockEntity` if the crash recurs, including loaded mixins affecting Create threshold switches and `SmartBlockEntity`.

## Recommended Validation

1. Add a focused unit-style test for `CreateInventorySummaryAdapter` if the project test setup can load Create classes:
   - Build a summary from mutable `ItemStack`s.
   - Cache/copy it twice.
   - Mutate the original stacks.
   - Verify both returned summaries remain stable and independent.
2. Add a small integration harness or GameTest with two Create readers against the same MineColonies-like `CombinedItemHandler`:
   - Reader A calls `PackagerBlockEntity.getAvailableItems()` twice across ticks.
   - Reader B scans `getSlots()` / `getStackInSlot()` / `getSlotLimit()` like a threshold switch.
   - Verify no shared one-shot object is returned and no exception occurs.
3. Reproduce with debug logging enabled and a stockpile switch plus factory gauge/packager reading the same target.
4. If the stockpile crash recurs, inspect the transformed runtime `ThresholdSwitchBlockEntity` bytecode/mixin list before assigning blame.

## Build / Release

No gameplay code was changed in this investigation pass. No version bump or release jar backup is required.
