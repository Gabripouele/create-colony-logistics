# 2026-05-30 Factory Panel Cache Copy Bad-Tick Audit

## Executive Summary

This was an audit-only investigation. No source code, version metadata, jar artifacts, UI behavior, tooltip formatting, or release files were changed.

The recurring Spark hot path is real in rc.11. The rc.11 cache-ownership hardening correctly stopped returning the mutable cached Create `InventorySummary` object directly, but it did so by returning `CachedSummary.copySummary()` on every cache hit. `copySummary()` calls `CreateInventorySummaryAdapter.safeCopy()`, and `safeCopy()` rebuilds a fresh Create `InventorySummary` by iterating every cached `BigItemStack`, copying its `ItemStack`, and calling `InventorySummary.add(...)`.

That means rc.11 reduced full MineColonies handler scans in normal captures, but in bad-tick-only captures the remaining hot path is now the defensive cache-hit copy. Factory panel storage monitoring is a read-only `getCountOf(...)` consumer, yet it currently pays for a full mutable `InventorySummary` clone per panel/read/caller.

Final recommendation: do not revert rc.11 ownership hardening globally. Add a factory-panel-specific read-only stock path backed by a CCL-owned immutable/pre-grouped cache snapshot, so factory panel monitoring can answer `getCountOf(filter)` without constructing or copying Create `InventorySummary`. Keep defensive `InventorySummary` copies for active package dispatch/routing until a separate mutable-summary safety proof exists.

## Exact Hot Path

Spark profile `tv3J9vonFk` bad-tick-only path:

```text
Create SmartBlockEntityTicker
-> FactoryPanelBehaviour.tickStorageMonitor
-> FactoryPanelBehaviour.getLevelInStorage
-> FactoryPanelBehaviour.getRelevantSummary
-> PackagerBlockEntity.getAvailableItems
-> PackagerBlockEntityMixin HEAD injection
-> ColonyStockCache.getIfFresh
-> CachedSummary.copySummary
-> CreateInventorySummaryAdapter.safeCopy
-> CreateInventorySummaryAdapter.addCopiedStack
-> InventorySummary.add
-> ItemStack.isSameItemSameComponents
-> PatchedDataComponentMap.equals
```

Approximate bad-tick profile costs supplied:

| Frame | Cost |
| --- | ---: |
| Create `SmartBlockEntityTicker` | 37.45% |
| Factory panel storage monitor | 17.06% |
| `PackagerBlockEntity.getAvailableItems` | 16.04% |
| CCL cached handler | 15.68% |
| `ColonyStockCache.getIfFresh` | 15.62% |
| `CreateInventorySummaryAdapter.safeCopy` | 15.61% |
| `InventorySummary.add` | 12.56% |
| `ItemStack.isSameItemSameComponents` | 11.30% |

## Code Files And Classes Inspected

Local CCL files:

- `src/main/java/com/createcolonylogistics/mixin/PackagerBlockEntityMixin.java`
- `src/main/java/com/createcolonylogistics/cache/ColonyStockCache.java`
- `src/main/java/com/createcolonylogistics/cache/CachedSummary.java`
- `src/main/java/com/createcolonylogistics/cache/CreateInventorySummaryAdapter.java`
- `src/main/java/com/createcolonylogistics/config/ColonyLogisticsConfig.java`
- prior reports in `docs/investigations/`

Create 6.0.6 bytecode inspected from local Gradle cache:

- `com.simibubi.create.content.logistics.packager.PackagerBlockEntity`
- `com.simibubi.create.content.logistics.packager.InventorySummary`
- `com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour`
- `com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity`
- `com.simibubi.create.content.logistics.packagerLink.LogisticsManager`
- `com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour`

Dependency context:

- Minecraft `1.21.1`
- NeoForge `21.1.172`
- Create `6.0.6`
- MineColonies `1.1.1041-1.21.1`
- CCL `0.3.0-rc.11`

## Exact Current Behavior

`PackagerBlockEntityMixin` injects into `PackagerBlockEntity.getAvailableItems()` at HEAD and RETURN.

HEAD behavior:

1. Resolve server level and `targetInventory.getInventory()`.
2. Call `ColonyStockCache.INSTANCE.getIfFresh(handler, level)`.
3. If present, return that `InventorySummary` immediately and mark `create_colony_logistics$returnedCachedSummary = true`.

RETURN behavior:

1. If HEAD returned cached summary, skip storage.
2. Otherwise, call `ColonyStockCache.INSTANCE.store(handler, level, cir.getReturnValue())`.

`ColonyStockCache.getIfFresh(...)`:

1. Only applies when enabled and handler is a MineColonies `CombinedItemHandler`.
2. Looks up by weak identity key.
3. Validates `sourceSlotCount` and TTL.
4. Records a hit.
5. Returns `Optional.of(cached.copySummary())`.

`CachedSummary.copySummary()`:

```java
InventorySummary copySummary() {
    return CreateInventorySummaryAdapter.safeCopy(summary);
}
```

`ColonyStockCache.store(...)`:

1. Records a raw scan.
2. Calls `CreateInventorySummaryAdapter.safeCopy(summary)`.
3. Stores that defensive copy in `CachedSummary`.

`CreateInventorySummaryAdapter.safeCopy(...)`:

1. Increments `safeCopyCount`.
2. Creates a new `InventorySummary`.
3. Copies `contributingLinks`.
4. Iterates `source.getStacks()`.
5. For each non-empty stack, calls `stack.stack.copy()`.
6. Calls `target.add(ownedStack, stack.count)`.
7. Increments `copiedStackCount`.

Therefore, in rc.11 `safeCopy()` is called:

| Event | Frequency |
| --- | --- |
| Cache refresh/store after a real Create scan | Once per cache store/refresh per handler |
| Cache hit returned to any `getAvailableItems()` caller | Once per cache hit/caller |
| Factory panel bad-tick storage read | Once per panel/read that reaches cached `getAvailableItems()` |
| Per item group | No `safeCopy` per group, but one `InventorySummary.add(...)` and one `ItemStack.copy()` per group inside each copy |

This is the direct explanation for the Spark hot path.

## Why rc.11 Helped Normal Captures But Failed Bad-Tick Captures

Before rc.11's cache-hit copy hardening, cache hits could reuse the stored summary object. That reduced repeated full scans and avoided many rebuilds, but it exposed a shared mutable Create object.

rc.11 fixed the ownership risk by copying on every hit. That is safer for arbitrary Create callers, but it changes the hot work from:

```text
MineColonies handler scan avoided on cache hit
```

to:

```text
MineColonies handler scan avoided, but full InventorySummary clone still done on every cache hit
```

Normal captures likely improved because MineColonies inventory scanning is expensive and the cache reduces those scans. Bad-tick-only captures isolate the remaining pathological moments, where many factory panels query the same cached stock in the same tick. Those reads are cache hits, but each cache hit performs the full defensive clone.

The cache TTL default is `40` ticks. TTL controls refresh frequency, not hit-copy frequency. Extending TTL would reduce raw scans but would not reduce the number of `safeCopy()` calls on cache hits.

## Create InventorySummary Behavior

Create `InventorySummary` is mutable.

Relevant internals from bytecode:

- `items` is a private `Map<Item, List<BigItemStack>>`, specifically initialized as an `IdentityHashMap`.
- `stacksByCount` is a cached mutable `List<BigItemStack>`.
- `totalCount` is mutable.
- `contributingLinks` is public mutable int.

Mutation methods:

- `add(InventorySummary)`
- `add(ItemStack)`
- `add(BigItemStack)`
- `add(ItemStack, int)`
- `addAllItemStacks(...)`
- `addAllBigItemStacks(...)`
- `erase(ItemStack)`
- `getStacksByCount()` initializes and sorts `stacksByCount`
- `getItemMap()` exposes the internal mutable map directly

Read methods:

- `getCountOf(ItemStack)` scans the list for the matching raw `Item` and compares with `ItemStack.isSameItemSameComponents(...)`.
- `getTotalOfMatching(Predicate<ItemStack>)` iterates entries.
- `getStacks()` creates a new flat list when `stacksByCount` is null, otherwise returns `stacksByCount`.
- `getTotalCount()`
- `isEmpty()`

Important copy detail:

Create's own `InventorySummary.copy()` also rebuilds with `add(...)`. It does not solve the `InventorySummary.add`/component-equality storm.

Important accessor detail:

`getItemMap()` exposes the grouped map. Direct grouped-data copying is technically possible with an accessor mixin or public getter use, but the returned lists and `BigItemStack` objects are mutable. A safe direct clone must also handle `totalCount`, `contributingLinks`, `stacksByCount` invalidation, and `BigItemStack`/`ItemStack` ownership.

## Why InventorySummary.add Is Expensive Here

`InventorySummary.add(ItemStack, int)` groups first by raw `Item`, then linearly scans that item's existing `BigItemStack` list and calls:

```text
ItemStack.isSameItemSameComponents(existing.stack, incoming)
```

For many unique raw items, comparisons are low.

For many component-distinct variants of the same raw item, comparisons grow as:

```text
0 + 1 + 2 + ... + (variants - 1)
```

So 500 component-distinct variants of one raw item can produce about 124,750 component comparisons per single summary rebuild. With 32 same-tick panel reads, that model reaches about 3,992,000 component comparisons.

This matches the supplied hot path through `PatchedDataComponentMap.equals`. Domum Ornamentum, Copycats, shaped blocks, and other component-heavy items are plausible multipliers because many variants can share one raw `Item` while differing primarily by data components.

## Caller Analysis Of PackagerBlockEntity.getAvailableItems

Bytecode scan across Create logistics classes found these direct caller paths.

| Caller | Path | Use of returned summary | Mutation risk for returned object |
| --- | --- | --- | --- |
| `PackagerBlockEntity.triggerStockCheck()` | Calls `getAvailableItems()` and discards return | Refresh/side-effect trigger | Low direct mutation, but expects Create method side effects |
| `FactoryPanelBehaviour.getRelevantSummary()` | Restocker panel -> restocked packager -> `getAvailableItems()` | Returned to `getLevelInStorage()` -> `getCountOf(filter)` | Read-only in this path |
| `PackagerLinkBlockEntity.fetchSummaryFromPackager(...)` | Stock link summary delegation | Returns packager summary to logistics aggregation | Aggregator reads/adds into separate aggregate |
| `PackagerLinkBlockEntity.processRequest(...)` | Package request routing | Calls `getCountOf(requestStack)` then creates `PackagingRequest` | Read-only in this method |

Indirect paths:

- `LogisticallyLinkedBehaviour.getSummary(...)` delegates to `PackagerLinkBlockEntity.fetchSummaryFromPackager(...)`.
- `LogisticsManager.getSummaryOfNetwork(...)` builds a new aggregate `InventorySummary` and calls `aggregate.add(summary)` for each linked summary. This mutates the aggregate, not the returned packager summary.
- `LogisticsManager.getStockOf(...)` reads `getCountOf(...)` from each linked summary.
- `FactoryPanelBehaviour.tickRequests()` uses `LogisticsManager.getSummaryOfNetwork(...)` and `getCountOf(...)` during crafting/request decisions.
- `FactoryPanelBehaviour.tryRestock()` calls `getLevelInStorage()` again, so a restocker panel can perform another factory-panel read during restock logic.
- `LogisticsManager.performPackageRequests(...)` calls `PackagerBlockEntity.triggerStockCheck()` after dispatch.

## Mutation Safety Analysis

Factory panel storage monitor:

- `tickStorageMonitor()` calls `getLevelInStorage()`.
- `getLevelInStorage()` calls `getRelevantSummary()`.
- `getRelevantSummary()` returns `packager.getAvailableItems()` for restocker panels.
- `getLevelInStorage()` then calls `summary.getCountOf(filter)`.
- No mutation of the returned summary was found in this path.

Package routing and dispatch:

- `PackagerLinkBlockEntity.processRequest(...)` calls `getAvailableItems()`, then `getCountOf(...)`, then creates a request. It does not mutate the returned summary.
- `LogisticsManager.findPackagersForRequest(...)` asks linked behaviours to process requests; it does not mutate returned summaries directly.
- `LogisticsManager.performPackageRequests(...)` triggers package sending and then `triggerStockCheck()`.

Other Create mutations:

- `LogisticsManager.getSummaryOfNetwork(...)` mutates a new aggregate `InventorySummary`, not the individual packager summaries it reads.
- `LogisticallyLinkedBehaviour.deductFromAccurateSummary(...)` mutates the network `ACCURATE_SUMMARIES` cache using negative `add(...)` calls after items leave.
- `PackagerBlockEntity.submitNewArrivals(oldSummary, newSummary)` mutates the old `availableItems` summary by adding negative counts before informing promise queues.

Conclusion:

Factory panel storage monitoring can safely consume a shared read-only or immutable cached stock view. Active routing appears read-only at the `getAvailableItems()` return boundary, but it is closer to dispatch state and Create's own summary side effects. It should keep defensive `InventorySummary` semantics until separately proven safe.

## Current Copy Granularity

In rc.11:

| Question | Answer |
| --- | --- |
| Copied once per cache refresh? | Yes, `ColonyStockCache.store(...)` copies once before storing. |
| Copied once per factory panel? | Yes, every panel read that hits `getIfFresh(...)` receives a new copy. |
| Copied once per packager? | Effectively yes per packager call/cache hit, not per underlying colony only. |
| Copied once per tick? | No. Multiple same-tick callers each copy. |
| Copied once per caller? | Yes. This is the bad-tick problem. |
| Copied once per item group? | `safeCopy()` is not called per group, but each copy calls `InventorySummary.add(...)` once per group. |

Factory panels can query the same colony/packager repeatedly in the same tick:

- Each active panel behaviour ticks independently.
- A factory gauge block can have multiple active panel slots.
- Multiple gauges can point at the same restocked packager.
- `tryRestock()` can call `getLevelInStorage()` after `tickStorageMonitor()` already did.
- All of those calls go through `getAvailableItems()` and currently trigger per-caller `safeCopy()` on cache hit.

## Simulation And Reasoned Model

A focused JVM benchmark was not practical in this pass because real `ItemStack`, data components, Create, NeoForge, and MineColonies classes require the modded runtime. Instead, I ran a small code-level count model based directly on the inspected bytecode:

- One cache hit equals one `safeCopy()`.
- One `safeCopy()` does one `ItemStack.copy()` and one `InventorySummary.add(...)` per cached `BigItemStack` group.
- Component comparisons in `InventorySummary.add(...)` are approximately `variants * (variants - 1) / 2` per raw item when variants share one raw `Item` and differ by components.
- No mutation occurs in the modeled factory-panel cache-hit path.

Results:

| Scenario | safeCopy calls | InventorySummary.add calls | ItemStack.copy calls | Approx component comparisons |
| --- | ---: | ---: | ---: | ---: |
| 1 panel, 1 colony, cache hit, 500 unique raw items | 1 | 500 | 500 | 0 |
| 32 panels, same colony, same tick, 500 unique raw items | 32 | 16,000 | 16,000 | 0 |
| 32 panels, same colony, same tick, 500 variants of same raw item | 32 | 16,000 | 16,000 | 3,992,000 |
| 8 packagers queried once, 500 groups each | 8 | 4,000 | 4,000 | 0 |
| Cache refresh plus hit, 500 variants of same raw item | 2 | 1,000 | 1,000 | 249,500 |
| 4 colonies, 8 panels each, 300 groups, 30 variants per item | 32 | 9,600 | 9,600 | 13,920 |

Interpretation:

- Simple unique-item inventories mostly pay allocation/copy/add overhead.
- Component-heavy same-raw-item inventories pay the nonlinear equality cost.
- Many same-tick factory panels multiply the whole cache-hit copy path.
- Cache refresh is expensive, but rc.11's bad-tick profile is dominated by cache-hit copying, not only refresh.

## Solution Permutation Matrix

| Option | Expected performance impact | Correctness and mutation safety | Complexity | Compatibility risk | Routing risk | Display/staleness risk | Avoids `InventorySummary.add` storms? | Recommendation |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| A. Return cached `InventorySummary` directly on safe read-only paths | Excellent for factory panel reads | Safe only if caller context is proven read-only; unsafe globally | Medium | Medium, caller detection can drift | Low if restricted | Low, same TTL | Yes for read path | Accept only with strong factory-panel-only guard |
| B. Store immutable/read-only cached summary and return shared instance for factory panels only | Excellent | Strong if CCL-owned immutable view is used; weak if just shared Create object | Medium | Low to medium | Low | Low | Yes | Recommended as part of final strategy |
| C. Copy once per tick per colony, share copied summary for same-tick panel reads | Good, caps multiplicative damage | Mutable shared per tick; caller isolation weaker | Medium | Medium | Medium if reused outside panels | Low | Partially, one storm per tick remains | Useful fallback, not best final fix |
| D. Copy once per cache TTL refresh instead of per hit | Excellent | Reintroduces shared mutable `InventorySummary` risk unless immutable | Low | Medium | Medium | TTL staleness unchanged | Yes on hits | Reject as direct rollback; okay only with immutable CCL snapshot |
| E. Accessor/reflection copy of `InventorySummary` internals | Good to excellent | Can be safe if deep-copying lists/stacks and total fields correctly | Medium to high | High against Create internals | Low if used only for copies | Low | Yes, if bypassing `add(...)` | Possible later optimization, not first choice |
| F. Custom pre-grouped cached stock structure, convert only when required | Excellent on factory panels | Strong, CCL owns representation | High | Low to medium | Low if routing keeps current path | Low | Yes for factory panel path | Best long-term foundation |
| G. Split behavior by caller context: factory monitoring vs routing | Excellent if factory path avoids copies | Strong if non-panel paths keep defensive copies | Medium | Medium, needs careful mixin point | Low | Low | Yes for panel path | Recommended |
| H. Secondary factory-panel-specific cache layer | Excellent | Strong if read-only/count-oriented | Medium | Low to medium | Low | Low | Yes | Recommended, especially as implementation shape for F/G |
| I. Reduce equality cost with stable item/component identity hashes | Good in heavy component inventories | Safety depends on exact component equivalence semantics | High | Medium to high | Medium if used for routing | Low | Yes if used throughout snapshot | Defer; useful inside CCL snapshot if carefully keyed |
| J. Shorten/extend cache TTL | Low for this issue | Safe but changes freshness | Low | Low | Low | Can return more/less stale stock | No, hit copies remain | Reject as primary fix |
| K. Revert part of rc.11 only where unsafe, preserve perf where safe | Good if limited to factory panel path | Safe if restricted; unsafe if broad | Medium | Medium | Medium if over-applied | Low | Yes on reverted path | Accept only as caller-split, not global revert |
| L. Add instrumentation counters per caller/context | Diagnostic impact only | Safe | Low | Low | None | None | No | Recommended before/with fix validation |

## Final Recommended Implementation Strategy

Recommended fix: split factory panel monitoring from general `getAvailableItems()` returns.

Implementation direction for the later patch:

1. Add a CCL-owned immutable stock snapshot for MineColonies `CombinedItemHandler` cache entries.
2. Store pre-grouped entries keyed for fast `getCountOf(filter)` semantics. The key must preserve Create/Minecraft `ItemStack.isSameItemSameComponents(...)` correctness. A conservative first version can group by raw `Item` and keep immutable copied representative stacks per component-distinct variant, then compare only inside that one item bucket on reads.
3. Add a factory-panel-specific mixin/read path around `FactoryPanelBehaviour.getLevelInStorage()` or `getRelevantSummary()` so restocker factory panels can ask CCL for the count directly.
4. On factory-panel cache hits, return the count without building or copying a Create `InventorySummary`.
5. Keep the current defensive `InventorySummary` copy behavior for normal `PackagerBlockEntity.getAvailableItems()` callers, especially package routing and stock-link request paths.
6. Optionally add a same-tick per-handler read-only count cache for factory panel filters if the same panel/filter repeats in `tickStorageMonitor()` and `tryRestock()`.
7. Add debug counters separating `factoryPanelSnapshotHits`, `factoryPanelSnapshotMisses`, `safeCopy` calls, copied stacks, and fallback `InventorySummary` builds.

Why this is safest:

- It preserves rc.11's mutation safety for general Create callers.
- It directly removes the profiled factory-panel `safeCopy()` path.
- It avoids relying on Create's mutable `InventorySummary` internals for display monitoring.
- It does not alter active package routing/dispatch semantics.
- It keeps tooltip/UI behavior untouched because only stock-count retrieval changes.
- It allows future optimization of the snapshot key without changing Create-facing routing behavior.

Secondary acceptable fallback:

If the factory-panel-specific mixin proves too invasive, implement a per-tick, per-handler factory-panel copy cache as a short-term mitigation. This would reduce N same-tick copies to one, but it still performs `InventorySummary.add(...)` at least once per tick and therefore is not the best long-term answer for component-heavy colonies.

## Why Not Patch getAvailableItems Directly To Return Shared Summary

Returning the cached `InventorySummary` directly would likely erase the bad-tick copy cost, but it partially reverts the rc.11 ownership fix.

Risks:

- `InventorySummary.getItemMap()` exposes mutable internals.
- `InventorySummary.getStacksByCount()` caches and returns mutable list state.
- Create mutates summaries in network aggregate and old-packager-summary paths.
- Future Create updates could add a mutating caller of `getAvailableItems()` without CCL noticing.

A direct return is only acceptable when the call site is proven factory-panel storage monitoring and the returned object cannot escape to arbitrary Create code. A direct count/snapshot API is cleaner than sharing a mutable Create object.

## Risks

Main risks for the recommended strategy:

- A mixin into `FactoryPanelBehaviour` can break if Create renames or restructures the method.
- A custom snapshot key must match `ItemStack.isSameItemSameComponents(...)` semantics exactly for displayed counts.
- If the snapshot is refreshed less often than the current cache, factory panels could show stale stock.
- If fallback handling is wrong, non-MineColonies inventories or unloaded packagers could display zero incorrectly.

Mitigations:

- Keep the existing `getAvailableItems()` cache path as fallback.
- Restrict the new path to server-side restocker panels with a MineColonies `CombinedItemHandler`.
- On any snapshot exception, fall back to Create's normal summary path.
- Add debug counters and Spark validation before release.

## Rollback Plan

If the future factory-panel snapshot patch causes display or routing regressions:

1. Disable the factory-panel-specific path behind config.
2. Keep rc.11 defensive `InventorySummary` copy behavior for `getAvailableItems()`.
3. Re-run the bad-tick Spark capture to confirm rollback returns to known rc.11 behavior.
4. Do not revert version metadata or unrelated cache hardening unless the patch changed it.

## Validation Plan With Spark

Use the same profile style as `tv3J9vonFk`:

- interval: `2 ms`
- only ticks over `75 ms`
- same world area and factory panel/packager setup
- CCL version after future fix
- Minecraft `1.21.1`
- NeoForge `21.1.172`
- Create `6.0.6`
- MineColonies `1.1.1041-1.21.1`

Validate:

1. `FactoryPanelBehaviour.tickStorageMonitor -> getLevelInStorage` no longer descends into `CreateInventorySummaryAdapter.safeCopy` on cache hits.
2. `InventorySummary.add` and `ItemStack.isSameItemSameComponents` no longer dominate factory panel bad ticks.
3. General package routing still works: package requests find stock, dispatch, deduct, and update stock checks.
4. Factory panel displayed counts match pre-fix counts for simple and component-heavy items.
5. Smart Info tooltip formatting and displayed content remain exactly unchanged.
6. Normal Gradle build passes.

## Proposed Acceptance Criteria For The Future Fix

- Factory panel bad ticks no longer call `safeCopy()` repeatedly.
- Cache-hit factory panel path does not repeatedly call `InventorySummary.add(...)` for every cached stack.
- `ItemStack.isSameItemSameComponents(...)` no longer dominates factory panel bad ticks.
- Active Create package routing/dispatch remains correct.
- Existing gameplay behavior remains unchanged except for performance.
- Smart Info tooltip formatting and displayed tooltip content remain exactly unchanged.
- No unrelated UI changes.
- No broad refactors.
- Normal Gradle build passes.
- If a release jar is built later, bump version from `0.3.0-rc.11` to the next appropriate version and back up the jar to `C:\Users\silfe\OneDrive\Desktop\Modding Minecraft\Backup\Release`.

## Commands And Evidence Notes

Commands used:

- `git status --short`
- `rg -n "PackagerBlockEntity|getAvailableItems|ColonyStockCache|CachedSummary|CreateInventorySummaryAdapter|InventorySummary|safeCopy" src docs build.gradle gradle.properties`
- `Get-Content` on CCL cache/mixin/config files
- `javap -classpath <Create jar> -c -p ...` on Create classes listed above
- A small PowerShell count model for safe-copy/add/copy/comparison estimates

The working tree was clean before this report file was added.
