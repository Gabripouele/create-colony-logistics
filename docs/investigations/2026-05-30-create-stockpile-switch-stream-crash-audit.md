# 2026-05-30 Create Stockpile Switch Stream Crash Audit

## Executive Summary

This was an audit-only pass. No gameplay code, UI code, version metadata, build output, release jar, or backup was changed by this investigation.

The two supplied crashes are both Create `create:stockpile_switch` ticking block entity crashes inside `ThresholdSwitchBlockEntity.updateCurrentLevel()`, while Create is scanning an adjacent inventory. The failing stream is Create's own threshold-switch compatibility stream over its static `COMPAT` list, not a stream produced by Create: Colony Logistics.

Create: Colony Logistics is not directly involved in the crash stacks and does not mix into Create threshold/stockpile switch logic. Based on the inspected code, direct responsibility for causing `ThresholdSwitchBlockEntity.updateCurrentLevel()` to receive a reused Java `Stream` is not supported.

There is, however, a real local cache ownership risk in the current working tree: `ColonyStockCache.getIfFresh()` returns the cached `InventorySummary` object directly. That can expose the same mutable `InventorySummary` instance to multiple `PackagerBlockEntity.getAvailableItems()` callers. This is relevant hardening work for Create packager/stock summary paths, but it is only indirectly plausible for general Create inventory monitoring and is not proven to explain the stockpile switch stream-internal NPEs.

## Exact Crash Signatures

Crash 1:

- File: `crash-2026-05-30_00.47.30-server.txt`
- Description: `Ticking block entity`
- Block entity: `create:stockpile_switch`
- Location: overworld `(2488, 78, -461)`
- Exception: `java.lang.NullPointerException: Cannot read field "combinedFlags" because "p.previousStage" is null`
- Stack frames supplied:
  - `java.util.stream.AbstractPipeline.wrapSink`
  - `java.util.stream.ReferencePipeline.findFirst`
  - `com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity.updateCurrentLevel(ThresholdSwitchBlockEntity.java:172)`
  - `com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity.lazyTick(ThresholdSwitchBlockEntity.java:297)`

Crash 2:

- File: `crash-2026-05-30_05.13.16-server.txt`
- Description: `Ticking block entity`
- Block entity: `create:stockpile_switch`
- Location: overworld `(280, 71, -193)`
- Exception: `java.lang.NullPointerException: Cannot read field "parallel" because "this.sourceStage" is null`
- Stack frames supplied:
  - `java.util.stream.AbstractPipeline.isParallel`
  - `java.util.stream.AbstractPipeline.evaluate`
  - `java.util.stream.ReferencePipeline.findFirst`
  - `com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity.updateCurrentLevel(ThresholdSwitchBlockEntity.java:172)`
  - `com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity.lazyTick(ThresholdSwitchBlockEntity.java:297)`

## Confirmed Affected Blocks And Coordinates

- `create:stockpile_switch` at overworld `(2488, 78, -461)`
- `create:stockpile_switch` at overworld `(280, 71, -193)`

No Create: Colony Logistics stack frame was reported in either crash.

## Code Paths Inspected

Local files inspected:

- `src/main/java/com/createcolonylogistics/cache/ColonyStockCache.java`
- `src/main/java/com/createcolonylogistics/cache/CachedSummary.java`
- `src/main/java/com/createcolonylogistics/cache/CreateInventorySummaryAdapter.java`
- `src/main/java/com/createcolonylogistics/mixin/PackagerBlockEntityMixin.java`
- `src/main/java/com/createcolonylogistics/mixin/TileEntityColonyBuildingMixin.java`
- `src/main/resources/create_colony_logistics.mixins.json`
- `src/main/java/com/createcolonylogistics/config/ColonyLogisticsConfig.java`
- `src/main/java/com/createcolonylogistics/CreateColonyLogistics.java`
- `src/main/java/com/createcolonylogistics/clipboard/RequestAnalysisService.java`
- `src/main/java/com/createcolonylogistics/clipboard/SmartClipboardReport.java`
- `src/main/java/com/createcolonylogistics/clipboard/DomumOrnamentumRequestInspector.java`
- `src/main/java/com/createcolonylogistics/clipboard/ColonyProductionInspector.java`
- `src/main/java/com/createcolonylogistics/clipboard/SmartClipboardScrollStorage.java`
- `src/main/java/com/createcolonylogistics/network/*SmartClipboard*Packet.java`
- `src/main/java/com/createcolonylogistics/client/SmartClipboardScreen.java`
- `build.gradle`
- `gradle.properties`

Dependency bytecode/source inspected:

- Create 6.0.6 local jar:
  - `com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity`
  - `com.simibubi.create.content.logistics.packager.InventorySummary`
- MineColonies local jar presence:
  - `minecolonies-245506-6790456.jar`
- Upstream Create public sources/releases:
  - Create current `mc1.21.1/dev` `ThresholdSwitchBlockEntity.java`: https://raw.githubusercontent.com/Creators-of-Create/Create/mc1.21.1/dev/src/main/java/com/simibubi/create/content/redstone/thresholdSwitch/ThresholdSwitchBlockEntity.java
  - Create releases page: https://github.com/Creators-of-Create/Create/releases
  - Create changelog: https://github.com/Creators-of-Create/Create/blob/mc1.21.1/dev/changelog.md

## Relevant Local Integration Points

Packager summary cache path:

`PackagerBlockEntity.getAvailableItems()` -> `PackagerBlockEntityMixin` -> `ColonyStockCache` -> `CachedSummary` -> Create `InventorySummary`.

Important details:

- The mixin targets only `PackagerBlockEntity.getAvailableItems()`.
- The cache only enables for `handler instanceof CombinedItemHandler`.
- The handler is used as a weak identity key.
- Cached values are Create `InventorySummary` objects, not Java `Stream` objects.
- The current working tree stores defensive copies on cache store.
- The current working tree returns `cached.summary()` directly on cache hit, exposing a shared mutable summary.
- The current working tree has a `create_colony_logistics$returnedCachedSummary` flag to avoid re-storing a cached return value in the `RETURN` injection.

Threshold/stockpile switch path:

`ThresholdSwitchBlockEntity.lazyTick()` -> `updateCurrentLevel()` -> `observedInventory.getInventory()` -> target `IItemHandler.getSlots()` / `getStackInSlot()` / `getSlotLimit()` -> Create's `COMPAT.stream().filter(...).map(...).findFirst()`.

Important details:

- Create stockpile/threshold switches do not call `PackagerBlockEntity.getAvailableItems()`.
- Create: Colony Logistics does not mix into `ThresholdSwitchBlockEntity`.
- Create: Colony Logistics does not mix into `InvManipulationBehaviour`, `VersionedInventoryTrackerBehaviour`, `IItemHandler`, or Create threshold compat classes.
- The failing stream frames correspond to Create's internal static threshold compat list stream.

Smart clipboard / MineColonies request path:

Smart clipboard code reads MineColonies request/building/warehouse APIs and serializes reports. It materializes local `List`, `Map`, and report records for UI/network use. This path does not expose cached streams or Create inventory summaries to Create stockpile switches.

## Direct, Indirect, Or Unsupported Involvement

Direct involvement: not supported.

- No crash frame references Create: Colony Logistics.
- No local mixin targets `ThresholdSwitchBlockEntity`.
- No local code returns a `Stream` to Create threshold-switch code.
- No local code replaces Create's `COMPAT` list or threshold compat objects.

Indirect plausibility: possible but narrow.

- Both Create packagers and stockpile switches may scan the same live MineColonies `CombinedItemHandler`.
- CCL caches `InventorySummary` results for packager summary reads from `CombinedItemHandler`.
- The current cache-hit path returns a shared mutable `InventorySummary` instance to packager callers.
- This could cause stale or cross-reader summary state in packager/stock summary consumers.
- It does not explain why a freshly created `COMPAT.stream()` inside `ThresholdSwitchBlockEntity.updateCurrentLevel()` would have a null `sourceStage` or `previousStage`.

Unsupported by evidence:

- A CCL-owned cached stream being reused by Create.
- A CCL-returned collection view being streamed later by the stockpile switch.
- A CCL mixin changing the stockpile switch's return values or scan source.
- CCL modifying Create's static threshold compatibility list.

## Cached Stream, Iterable, Collection, And InventorySummary Risk

Stream risk:

- No local field, record, cache, or DTO stores `Stream`.
- No local method returns `Stream` to Create.
- No `parallelStream()` use was found.
- Local `.stream()` use in clipboard and UI code is immediate and terminally consumed in the same method.
- No lazy `Supplier<Stream>` or reusable iterator source was found.

Iterable/collection risk:

- No MineColonies live collection is cached and returned to Create inventory scanning.
- Clipboard collections are local/report-oriented and not part of Create stockpile switch scanning.
- `InventorySummary.getItemMap()`, `getStacks()`, and `getStacksByCount()` are mutable/view-like Create APIs, but CCL does not return those collections directly. It returns whole `InventorySummary` instances.

InventorySummary risk:

- Proven: the current working tree returns the same cached `InventorySummary` object on cache hits.
- Proven: Create `InventorySummary` is mutable and exposes mutable structures through methods such as `getItemMap()` and stack lists.
- Proven: `CachedSummary` stores a mutable `InventorySummary`.
- Likely risk: a Create packager/stock-summary caller can mutate or retain a cached summary returned by CCL.
- Possible risk: stale or cross-reader summary state for packager-like Create logistics readers that target the same MineColonies `CombinedItemHandler`.
- Not supported: this mutability causes Create's `ThresholdSwitchBlockEntity.COMPAT.stream()` to have corrupt Java stream pipeline internals.

## InventorySummary Deep-Copy Hardening Relevance

The previous hardening target remains relevant, but the exact risk has shifted in the current working tree.

Current state:

- `CreateInventorySummaryAdapter.safeCopy()` builds a new `InventorySummary`.
- It iterates `source.getStacks()`.
- It copies each `BigItemStack.stack` with `ItemStack.copy()`.
- On exception, it returns the partial new summary, not the original source.
- This avoids the older high-risk fallback of returning the original summary.

Remaining issue:

- `ColonyStockCache.store()` copies before storing.
- `ColonyStockCache.getIfFresh()` currently returns `cached.summary()` directly.
- Therefore the cached mutable object is still shared externally on hits.

Assessment:

- Proven relevant for cache ownership and packager summary correctness.
- Possible hardening value for reducing unknown interactions with Create summary consumers.
- Not proven as a cause of the stockpile switch stream NPE, because threshold switches do not read `InventorySummary`.

## Upstream Create Comparison Notes

Create 6.0.6 local bytecode for `ThresholdSwitchBlockEntity.updateCurrentLevel()` shows the item scan loop creates a fresh stream from a static list for each slot:

```java
COMPAT.stream()
    .filter(...)
    .map(...)
    .findFirst()
```

The current upstream `mc1.21.1/dev` source still contains the same broad structure: a static `List` of threshold switch compat adapters and a per-slot `COMPAT.stream().filter(...).map(...).findFirst()` chain.

Create release notes identify threshold-switch fixes after 6.0.6:

- Create 6.0.8 for Minecraft 1.21.1 notes a fix for threshold switches incorrectly counting empty slots.
- Create 6.0.10 notes a fix for threshold switches detecting the wrong amount of items for chutes and smart chutes.

Those are relevant leads for stockpile/threshold behavior, but they do not by themselves prove the supplied `sourceStage` / `previousStage` stream-internal NPE is the same upstream issue.

## Ranked Patch Options, Safest To Riskiest

1. Always return a defensive `InventorySummary` copy on cache hits.

Safest local patch. It restores snapshot ownership at the cache boundary without changing when the cache is used.

2. Keep storing copied summaries, but make `CachedSummary.summary()` private/internal and expose `copy()` semantics.

Safer API shape. Reduces the chance future code accidentally returns the cached mutable instance.

3. Replace cached `InventorySummary` with a CCL-owned immutable snapshot DTO.

Stronger correctness model. Store copied stack keys/counts in CCL-owned data and construct a fresh Create `InventorySummary` for every external read. More code, but less reliance on Create's mutable summary internals.

4. Add server/level lifecycle cache clearing and optional dimension identity in cache entries.

Good hygiene for stale references and world reloads. Useful but not sufficient for the stream NPE.

5. Add targeted diagnostics around MineColonies `CombinedItemHandler` summary reads.

Useful for recurrence evidence. Should be rate-limited and debug-gated.

6. Disable CCL cache for suspicious caller contexts if a threshold/stockpile switch context is detectable.

Likely low value for the confirmed stack because threshold switches do not call `getAvailableItems()`. Caller-context detection can be brittle.

7. Mix into Create `ThresholdSwitchBlockEntity` to guard stream failures.

Riskiest. This changes upstream Create ticking behavior and broadens blast radius. It should only be considered if transformed runtime evidence proves a Create-side workaround is required and no upstream upgrade is available.

## Recommended Next Patch Plan

Recommended next coding pass:

1. Change the cache-hit path so no cached `InventorySummary` instance is returned directly.
2. Encapsulate `CachedSummary` so callers cannot accidentally obtain the stored mutable object without copying.
3. Keep `CreateInventorySummaryAdapter.safeCopy()` as the single copy boundary and add focused validation for copied `ItemStack` ownership.
4. Consider a follow-up immutable cache DTO if tests show Create `InventorySummary` still leaks mutable internal state.
5. Add debug-only diagnostics for cache hits/misses and copy failures, without touching gameplay behavior outside the packager summary path.
6. If crashes continue, collect transformed runtime evidence for `ThresholdSwitchBlockEntity`, including loaded mixins affecting Create threshold switch classes.

## Files Likely To Change In Next Coding Pass

- `src/main/java/com/createcolonylogistics/cache/ColonyStockCache.java`
- `src/main/java/com/createcolonylogistics/cache/CachedSummary.java`
- `src/main/java/com/createcolonylogistics/cache/CreateInventorySummaryAdapter.java`
- `src/main/java/com/createcolonylogistics/mixin/PackagerBlockEntityMixin.java`

Possible only if lifecycle/debug work is chosen:

- `src/main/java/com/createcolonylogistics/CreateColonyLogistics.java`
- `src/main/java/com/createcolonylogistics/config/ColonyLogisticsConfig.java`

## Files Intentionally Not Touched

No files were modified except this report.

The following areas were intentionally not touched:

- Smart Colony Clipboard UI
- Tooltip formatting
- Packet behavior
- Scroll storage
- Cancel feature
- Item registration/assets/lang/model files
- `build.gradle`
- `gradle.properties`
- `src/main/resources/META-INF/neoforge.mods.toml`
- Create/MineColonies dependency versions
- Generated jars or release backup folders

## Validation Performed

Read-only commands/actions performed:

- Listed repository files with `rg --files`.
- Checked working tree state with `git status --short`.
- Searched source and docs for `ThresholdSwitch`, `stockpile`, `PackagerBlockEntity`, `getAvailableItems`, `InventorySummary`, `CombinedItemHandler`, `IItemHandler`, `Stream`, `Iterable`, `Collection`, `List`, `Map`, cache, and mixin references.
- Read the cache and mixin source files listed above.
- Read the existing `docs/investigations/2026-05-29-stockpile-switch-stream-crash.md`.
- Inspected local Create 6.0.6 bytecode with `javap` for `ThresholdSwitchBlockEntity` and `InventorySummary`.
- Checked local Gradle cache for Create 6.0.6 and 6.0.8 artifacts; only a local Create 6.0.6 jar was available.
- Compared against upstream Create public source/release notes via GitHub.

No compile, test, release build, or jar packaging was run. A build can be run later if the next coding pass needs compile verification, but this audit should not be treated as a release/build pass.

## Audit Closure Statement

This investigation made no code changes, no UI changes, no gameplay behavior changes, no dependency upgrades, no version bump, no release jar, and no release backup. The only added file is this investigation report under `docs/investigations/`.
