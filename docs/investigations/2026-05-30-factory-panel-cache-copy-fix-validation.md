# 2026-05-30 Factory Panel Cache Copy Fix Validation

## Summary

Implemented the factory-panel-specific MineColonies snapshot path recommended by the bad-tick audit.

The fix is intentionally narrow:

- Applies only when a factory panel storage count read is attached to a restocked packager.
- Applies only when that packager targets a MineColonies `CombinedItemHandler` accepted by `ColonyStockCache`.
- Applies only on a fresh CCL cache hit.
- Falls back to Create's original `FactoryPanelBehaviour.getLevelInStorage()` behavior for all misses, stale cache entries, non-MineColonies inventories, client-side reads, missing packagers, and empty filters.

## Files Changed

- `gradle.properties`
- `src/main/java/com/createcolonylogistics/cache/CachedSummary.java`
- `src/main/java/com/createcolonylogistics/cache/ColonyStockCache.java`
- `src/main/java/com/createcolonylogistics/cache/MineColoniesStockSnapshot.java`
- `src/main/java/com/createcolonylogistics/mixin/FactoryPanelBehaviourMixin.java`
- `src/main/resources/create_colony_logistics.mixins.json`

## Behavior

Factory panel MineColonies cache-hit reads now use a CCL-owned immutable snapshot to answer `getCountOf(filter)` without calling:

- `CreateInventorySummaryAdapter.safeCopy()`
- `InventorySummary.add(...)`
- full `InventorySummary` rebuild

The existing defensive `InventorySummary` copy behavior remains in place for `PackagerBlockEntity.getAvailableItems()` and therefore for routing, dispatch, stock-link request processing, and other Create callers.

## Diagnostics

Existing debug cache logging now includes:

- cache stores
- cache hits
- stale misses
- factory panel snapshot hits
- factory panel snapshot misses
- same-tick factory panel reads
- safeCopy calls
- copied stack count

Debug logging remains disabled by default.

## Risk Assessment

Risk is concentrated in the new `FactoryPanelBehaviour.getLevelInStorage()` mixin hook. If the snapshot path cannot prove a fresh MineColonies cache hit, it does not cancel and Create runs normally.

Displayed counts should remain equivalent because snapshot matching uses `ItemStack.isSameItemSameComponents(...)` against copied representative stacks, preserving Create's item/component equality semantics for the displayed filter count.

Package routing and dispatch are intentionally not optimized by this change.

## Spark Validation Plan

Repeat the bad-tick Spark capture:

- interval: `2 ms`
- only ticks over `75 ms`

Expected result:

- MineColonies-backed factory panel cache-hit reads no longer descend into `CreateInventorySummaryAdapter.safeCopy`.
- `InventorySummary.add` and component equality are no longer dominant under factory panel cache-hit monitoring.
- Any remaining `safeCopy` activity should come from cache stores/refreshes or non-factory-panel `getAvailableItems()` callers.

Spark was not run in this local coding pass because it requires an in-game server capture. The code-level validation target is that the optimized MineColonies factory panel path cancels before `FactoryPanelBehaviour.getLevelInStorage()` calls `getRelevantSummary()` and therefore before `PackagerBlockEntity.getAvailableItems()` can reach the cache-hit defensive copy.

## Build And Release Artifact

Build command:

```text
$env:JAVA_HOME='<local Java 21 JDK>'; .\gradlew.bat build
```

Result: passed.

Version: `0.3.0-rc.12`

Built jar:

```text
build\libs\create_colony_logistics-0.3.0-rc.12.jar
```

Backup jar:

```text
local release backup folder\create_colony_logistics-0.3.0-rc.12.jar
```

Backup folder already existed: yes.

The final same-version backup jar was refreshed after the final successful build.

## Acceptance Criteria

- Factory panel cache-hit path avoids repeated `safeCopy()`.
- Factory panel cache-hit path avoids repeated `InventorySummary.add(...)`.
- Create package routing remains on the defensive `InventorySummary` path.
- Non-MineColonies inventories fall back to Create behavior.
- Smart Colony Clipboard behavior is unchanged.
- Smart Info tooltip formatting is unchanged.
- No UI files changed.
- Gradle build must pass.
