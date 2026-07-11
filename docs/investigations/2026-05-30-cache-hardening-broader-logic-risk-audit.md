# 2026-05-30 Cache Hardening Broader Logic Risk Audit

## Executive Summary

This was a broader pre-coding logic audit of the current modified Create: Colony Logistics cache/mixin path. No source code, version metadata, dependency declarations, jars, release artifacts, or backups were changed.

The narrow cache-hit copy patch is still recommended as the safest next coding step. The current modified logic stores copied `InventorySummary` objects, but `ColonyStockCache.getIfFresh()` returns the stored mutable cached `InventorySummary` directly. That is the clearest proven local bug.

The broader audit found additional risks, but none are stronger immediate candidates than restoring cache-hit ownership. The most important deferred design risk is that returning from the `PackagerBlockEntity.getAvailableItems()` HEAD injection bypasses Create's own method body on cache hits, including Create's `availableItems` field update, `submitNewArrivals(...)`, and `VersionedInventoryTrackerBehaviour.awaitNewVersion(...)`. That behavior already exists in the current optimization design and should be reviewed separately after the ownership bug is fixed.

This patch is still not expected to directly fix the Create stockpile/threshold switch crash. The stockpile crash path remains Create's own `ThresholdSwitchBlockEntity.updateCurrentLevel()` inventory scan and threshold compatibility stream. No local mixin targets threshold switches.

## Dirty Files Reviewed

Initial dirty status:

```text
 M gradle.properties
 M src/main/java/com/createcolonylogistics/cache/ColonyStockCache.java
 M src/main/java/com/createcolonylogistics/cache/CreateInventorySummaryAdapter.java
 M src/main/java/com/createcolonylogistics/mixin/PackagerBlockEntityMixin.java
?? docs/investigations/2026-05-30-create-stockpile-switch-stream-crash-audit.md
?? docs/investigations/2026-05-30-inventorysummary-cache-hardening-task-audit.md
```

Reviewed dirty files:

- `gradle.properties`
- `src/main/java/com/createcolonylogistics/cache/ColonyStockCache.java`
- `src/main/java/com/createcolonylogistics/cache/CreateInventorySummaryAdapter.java`
- `src/main/java/com/createcolonylogistics/mixin/PackagerBlockEntityMixin.java`
- `docs/investigations/2026-05-30-create-stockpile-switch-stream-crash-audit.md`
- `docs/investigations/2026-05-30-inventorysummary-cache-hardening-task-audit.md`

Reviewed related clean files:

- `src/main/java/com/createcolonylogistics/cache/CachedSummary.java`
- `src/main/java/com/createcolonylogistics/config/ColonyLogisticsConfig.java`
- `src/main/java/com/createcolonylogistics/CreateColonyLogistics.java`
- `src/main/resources/create_colony_logistics.mixins.json`
- `docs/investigations/2026-05-29-stockpile-switch-stream-crash.md`

Dependency bytecode spot-checked:

- Create 6.0.6 `PackagerBlockEntity`
- Create 6.0.6 `InventorySummary`
- MineColonies 1.1.1041 `CombinedItemHandler`

## Current Cache And Mixin Behavior

`PackagerBlockEntityMixin`:

- Injects at the HEAD of `PackagerBlockEntity.getAvailableItems()`.
- Resolves the packager target inventory through `targetInventory.getInventory()`.
- Uses the cache only when the handler is a MineColonies `CombinedItemHandler`.
- If `ColonyStockCache.getIfFresh(...)` returns a value, it sets the method return value and records `create_colony_logistics$returnedCachedSummary = true`.
- Injects at RETURN to store raw Create results, unless the call returned a cached summary.

`ColonyStockCache`:

- Uses a singleton cache keyed by weak identity of `IItemHandler`.
- Enables only when `ENABLE_MINECOLONIES_SUMMARY_CACHE` is true and the handler is a `CombinedItemHandler`.
- Validates cached entries by source slot count and TTL.
- Stores a defensive copy of raw Create summaries.
- Currently returns the stored cached summary directly on hit.
- Logs aggregate cache stats only when debug logging is enabled.

`CachedSummary`:

- Stores a mutable Create `InventorySummary`.
- Tracks build tick, source slot count, hit count, and miss count.
- Exposes the stored mutable summary through package-private `summary()`.

`CreateInventorySummaryAdapter`:

- Creates new `InventorySummary` instances.
- Copies stacks with `ItemStack.copy()`.
- Copies `contributingLinks`.
- Returns a new partial summary, not the original source, if copying throws.
- Tracks safe-copy and copied-stack counts for debug stats.

`ColonyLogisticsConfig`:

- Cache is enabled by default.
- Default TTL is 40 ticks.
- Debug logging defaults to false.
- Cache stats log interval defaults to 1200 ticks when debug logging is enabled.

## Risks Found, Ranked

### 1. Shared mutable cached InventorySummary on cache hits

Severity: high.

Evidence:

- `ColonyStockCache.getIfFresh()` returns `Optional.of(cached.summary())`.
- `CachedSummary.summary()` returns the stored `InventorySummary`.
- Create `InventorySummary` is mutable and exposes mutable data.

Impact:

- A caller can mutate or retain the cached object.
- Later cache hits can observe corrupted or stale shared state.
- This can cause incorrect packager/stock summary behavior over time.

Immediate action:

- Fix now with copy-on-cache-hit and `CachedSummary` encapsulation.

### 2. Cache-hit short-circuit bypasses Create getAvailableItems side effects

Severity: medium.

Evidence from Create 6.0.6 bytecode:

- `PackagerBlockEntity.getAvailableItems()` normally uses its own `availableItems` field.
- It checks `VersionedInventoryTrackerBehaviour.stillWaiting(...)`.
- On a full scan, it calls `VersionedInventoryTrackerBehaviour.awaitNewVersion(...)`.
- It calls `submitNewArrivals(previousSummary, newSummary)`.
- It writes the new summary into `availableItems`.

The HEAD injection returns before these steps on cache hits.

Impact:

- Create's internal `availableItems` field may remain stale while CCL returns a fresh or cached value externally.
- Create's new-arrival tracking may not run on cache hits.
- Create's inventory-version tracker may not be advanced on cache hits.
- This could affect packager behavior or stock-report side effects independent of the mutable-summary ownership bug.

Immediate action:

- Defer. It is broader than the copy-on-hit patch and should be evaluated with runtime behavior in mind.
- Do not try to solve it by patching threshold switches.

### 3. TTL plus slot-count validation can serve stale counts

Severity: medium.

Evidence:

- Cache validity is `sourceSlotCount == slotCount && gameTime - lastBuildGameTime <= ttlTicks`.
- Default TTL is 40 ticks.
- MineColonies `CombinedItemHandler.getSlots()` returns a precomputed `totalSlots`; slot count can stay constant while contents change.

Impact:

- Item counts can be stale for up to the configured TTL.
- Stale results are expected for this optimization, but packagers may make decisions from stale summaries.
- Slot-count validation does not detect normal inventory content changes.

Immediate action:

- Defer. Changing TTL/invalidation policy changes behavior and should be a separate design decision.
- Keep in the report and validation notes.

### 4. Handler identity keying is narrow and may miss or over-retain logical identity

Severity: medium-low.

Evidence:

- Cache keys use weak identity of `IItemHandler`.
- MineColonies `CombinedItemHandler` contains a final handler array and precomputed total slot count.
- `CombinedItemHandler.equals(...)` compares underlying handler references, but CCL does not use equality.

Impact:

- If MineColonies rebuilds wrapper objects around the same underlying handlers, the cache will miss and rebuild, which is safe but less effective.
- If the same `CombinedItemHandler` object remains alive while underlying contents change, the cache can serve stale counts until TTL.
- Weak keys reduce long-term retention, but there is no explicit server/level clear.

Immediate action:

- Defer. Identity keying is acceptable for the narrow patch.

### 5. Partial summaries on copy failure can hide data loss

Severity: medium-low.

Evidence:

- `safeCopy()` returns the new partial summary if an exception occurs mid-copy.
- It logs only when debug logging is enabled and rate limit allows.

Impact:

- A copy failure could produce undercounted stock without visible non-debug logging.
- This is safer than returning the original mutable object, but it can still hide data loss.

Immediate action:

- Defer diagnostics. Do not expand scope unless copy failures are observed.
- Keep returning partial new summaries rather than original objects for safety.

### 6. safeCopy metadata and count semantics

Severity: low-medium.

Evidence:

- `safeCopy()` copies `contributingLinks`.
- It recreates entries through `InventorySummary.add(copiedStack, count)`.
- Create `add(ItemStack, int)` preserves item/component grouping and total count semantics, but may normalize stack count when the input stack count exceeds max stack size.

Impact:

- The helper likely preserves the important summary count semantics.
- It intentionally does not copy cached internal sort state such as `stacksByCount`; that should be recalculated by Create if needed.
- It might not preserve future Create metadata fields if Create adds them later.

Immediate action:

- No change now.
- Re-review if Create dependency changes.

### 7. Mixin flag edge cases

Severity: low.

Evidence:

- `create_colony_logistics$returnedCachedSummary` is an instance field.
- It is set to false when level/handler is missing or cache is absent.
- It is set to true when a cached value is returned.
- It is reset in RETURN injection when that RETURN injection runs.

Impact:

- Normal server-thread use is safe enough.
- If a HEAD cancellation prevents RETURN injection from running, the flag can remain true until the next call. The next HEAD path normally overwrites it before original method return.
- Re-entrant or concurrent calls could theoretically interleave, but Minecraft block entity logic is expected to run on the server thread.

Immediate action:

- No behavioral change needed for the copy-on-hit patch.
- Keep the mixin in review scope only.

### 8. Debug counters are non-atomic

Severity: low.

Evidence:

- `rawScanCount`, `safeCopyCount`, and `copiedStackCount` are plain static/instance longs.

Impact:

- Server-thread usage makes this acceptable.
- If called off-thread, stats could be inaccurate, but cache correctness should not depend on counters.

Immediate action:

- No change.

## Immediate Coding Pass Recommendations

Fix now:

- Return a defensive `InventorySummary` copy on every cache hit.
- Encapsulate `CachedSummary` so callers cannot obtain the stored mutable summary directly.
- Keep inbound store copying.

Do not fix now:

- Lifecycle cache clearing.
- TTL/invalidation redesign.
- Debug diagnostic expansion.
- Immutable DTO replacement.
- Changes to `PackagerBlockEntityMixin` behavior.
- Version bump.
- Release jar or backup.

## Deferred Risks

Defer these to separate passes:

- Evaluate whether the HEAD short-circuit should update Create's `availableItems` field or version tracker, or whether a different injection point would preserve Create side effects.
- Add explicit server-stop/level-unload cache clearing.
- Consider lowering TTL or adding version-aware invalidation if MineColonies exposes a reliable change signal.
- Add diagnostic logging for partial copy failures and cache decisions.
- Consider replacing cached `InventorySummary` with a CCL-owned immutable snapshot DTO.

## Lifecycle Cache Clearing

Recommendation: later.

Lifecycle clearing is good hygiene, especially because `ColonyStockCache.INSTANCE` is a singleton and entries are not partitioned by level or server instance. However, the cache uses weak handler keys and a short TTL. Adding lifecycle hooks touches event handling outside the immediate ownership bug and should remain a separate pass.

## Debug Diagnostics

Recommendation: later.

Debug diagnostics would help if copy failures or stale-summary issues recur, but adding new logging during the narrow patch risks scope creep. The current code already has debug-gated stats and copy-failure logging. Expand diagnostics only after the ownership boundary is fixed and there is a concrete validation question.

## PackagerBlockEntityMixin Change Need

The immediate copy-on-hit patch should not require `PackagerBlockEntityMixin` changes.

The mixin remains relevant because cached HEAD returns bypass Create's own side effects, but changing injection behavior would be a broader behavioral patch. For the next pass, re-read the mixin and verify no accidental changes, but keep code edits inside cache classes if possible.

## CreateInventorySummaryAdapter Change Need

No immediate change is required for `CreateInventorySummaryAdapter.safeCopy()`.

Current behavior is acceptable for the narrow ownership patch:

- creates a new summary
- copies stacks
- copies `contributingLinks`
- returns a new partial summary instead of the original on failure

Possible later improvements:

- Add a clearer method name if `CachedSummary.copySummary()` needs it.
- Add diagnostics for partial-copy failures.
- Add tests or a validation harness for count and component preservation.

## CachedSummary Encapsulation

Encapsulation is enough for the immediate patch.

Recommended shape:

- Remove or stop using `summary()` as an exposed raw getter.
- Add `copySummary()` that returns `CreateInventorySummaryAdapter.safeCopy(summary)`.
- Keep `replace(...)`, validity, hit/miss tracking as-is.

This directly fixes the proven shared mutable return without redesigning cache policy.

## Behavioral Impact

Packager behavior:

- Expected impact from copy-on-hit is low and positive.
- Consumers still receive an `InventorySummary` with the same counts, but it is no longer the cache's stored object.
- There may be more copy work per cache hit; this is the intended tradeoff for ownership safety.
- The patch does not address the existing broader issue that cached HEAD returns bypass Create's internal `availableItems` and tracker side effects.

Smart Clipboard behavior:

- No expected impact.
- Smart Clipboard code is not in the cache/mixin path and should not be touched.

Create stockpile/threshold switch behavior:

- No direct expected impact.
- Stockpile/threshold switches do not call `PackagerBlockEntity.getAvailableItems()` based on inspected Create behavior.
- The patch should not add threshold switch mixins or alter Create dependency versions.

## Recommended Exact Next Coding Prompt

Use this prompt for the coding pass:

```text
Implement the minimal InventorySummary cache ownership hardening only.

Change the cache-hit path so ColonyStockCache never returns the mutable InventorySummary stored inside CachedSummary. Prefer adding CachedSummary.copySummary() that calls CreateInventorySummaryAdapter.safeCopy(summary), and have ColonyStockCache.getIfFresh() return that copy after recording a hit.

Keep ColonyStockCache.store() copying inbound summaries before storing.
Do not change PackagerBlockEntityMixin behavior unless required by the cache method rename.
Do not change CreateInventorySummaryAdapter.safeCopy() unless a compile issue requires it.
Do not touch Smart Clipboard UI, tooltip formatting, packets, scroll storage, cancel behavior, assets/lang/models, item registration, dependency versions, Create threshold/stockpile switch mixins, gradle.properties, release jars, or backups.

Run compile validation only after the patch.
```

## Files Likely To Change Next

Likely:

- `src/main/java/com/createcolonylogistics/cache/ColonyStockCache.java`
- `src/main/java/com/createcolonylogistics/cache/CachedSummary.java`

Possibly only if needed:

- `src/main/java/com/createcolonylogistics/cache/CreateInventorySummaryAdapter.java`

Review-only, not expected to change:

- `src/main/java/com/createcolonylogistics/mixin/PackagerBlockEntityMixin.java`

## Files Explicitly Not To Touch

- `gradle.properties`
- `build.gradle`
- `settings.gradle`
- `src/main/resources/META-INF/neoforge.mods.toml`
- `src/main/resources/assets/**`
- `src/main/resources/data/**`
- `src/main/java/com/createcolonylogistics/client/**`
- `src/main/java/com/createcolonylogistics/clipboard/SmartClipboardScrollStorage.java`
- `src/main/java/com/createcolonylogistics/clipboard/RequestReportFormatter.java`
- `src/main/java/com/createcolonylogistics/network/**`
- `src/main/java/com/createcolonylogistics/item/**`
- `src/main/java/com/createcolonylogistics/registry/**`
- Create dependency versions
- MineColonies dependency versions
- Create stockpile/threshold switch mixins
- Release jar or backup folders

## Validation Commands For Next Coding Pass

Suggested validation after coding:

```powershell
git diff -- src/main/java/com/createcolonylogistics/cache/ColonyStockCache.java src/main/java/com/createcolonylogistics/cache/CachedSummary.java src/main/java/com/createcolonylogistics/cache/CreateInventorySummaryAdapter.java src/main/java/com/createcolonylogistics/mixin/PackagerBlockEntityMixin.java
rg -n "cached\\.summary\\(|InventorySummary summary\\(\\)|copySummary\\(|safeCopy\\(" src/main/java/com/createcolonylogistics/cache src/main/java/com/createcolonylogistics/mixin/PackagerBlockEntityMixin.java
.\gradlew.bat compileJava
git status --short
```

Optional if a broader verification artifact is intentionally desired:

```powershell
.\gradlew.bat build
```

Treat any build jar as a local test artifact unless a separate release pass is requested.

## Version Bump Recommendation

Do not bump versions during the immediate coding pass.

The current dirty `gradle.properties` already contains a version change from `0.3.0-rc.10` to `0.3.0-rc.11`. This audit did not modify it. Version metadata should be handled in a separate release/build pass, not in the narrow cache-hardening code pass.

## Backup And Release Recommendation

Do not create backups or release jars during the immediate coding pass.

If a later build produces a jar only for compile validation, treat it as a local artifact. If a jar will be handed to a server or modpack for testing/release, do backup/release hygiene in a separate explicit pass.

## Implementation Note

Implemented after this audit:

- Cache hits now return a defensive `InventorySummary` copy through `CachedSummary.copySummary()`.
- `ColonyStockCache.getIfFresh()` no longer returns the mutable `InventorySummary` object stored inside `CachedSummary`.
- `ColonyStockCache.store()` continues to copy inbound summaries before storing them.
- This reduces local shared mutable cache risk in the Create packager summary path.
- This does not patch Create stockpile/threshold switch logic and is not a proven direct fix for the `ThresholdSwitchBlockEntity.updateCurrentLevel()` crash family.
- The broader HEAD-injection side-effect risks around Create's internal `availableItems`, `submitNewArrivals(...)`, and version tracker remain deferred for a separate audit/pass.

Implementation validation:

- Scoped diff confirmed the code change is limited to cache ownership in `CachedSummary` and `ColonyStockCache`; the diff also still shows pre-existing dirty debug-stat changes in nearby cache files.
- Search confirmed no remaining `cached.summary()` call or `InventorySummary summary()` raw getter in the cache/mixin path.
- Initial `.\gradlew.bat compileJava` failed before compilation because the ambient `JAVA_HOME` pointed at `...\bin\javaw.exe`.
- Retried with `JAVA_HOME` set to a local Java 21 JDK; the first retry needed network access to fetch the Gradle wrapper distribution.
- `compileJava` then completed successfully.
- No version bump, release jar, backup, UI, tooltip, packet, scroll, cancel, asset, dependency, or stockpile/threshold switch change was made.

## Commands Run

Read-only commands run for this audit:

- `git status --short`
- `git diff -- gradle.properties src/main/java/com/createcolonylogistics/cache/ColonyStockCache.java src/main/java/com/createcolonylogistics/cache/CreateInventorySummaryAdapter.java src/main/java/com/createcolonylogistics/mixin/PackagerBlockEntityMixin.java`
- `Get-Content docs\investigations\2026-05-30-create-stockpile-switch-stream-crash-audit.md`
- `Get-Content docs\investigations\2026-05-30-inventorysummary-cache-hardening-task-audit.md`
- `Get-Content src\main\java\com\createcolonylogistics\config\ColonyLogisticsConfig.java`
- `Get-Content src\main\resources\create_colony_logistics.mixins.json`
- `rg -n "ColonyStockCache|CachedSummary|CreateInventorySummaryAdapter|PackagerBlockEntityMixin|InventorySummary|CombinedItemHandler|getAvailableItems|cacheTtl|enableMineColoniesSummaryCache|returnedCachedSummary|safeCopy" src\main\java src\main\resources`
- `Get-ChildItem src\main\java\com\notjustcreate -Recurse -File | Select-Object -ExpandProperty FullName`
- `Get-Content src\main\java\com\createcolonylogistics\CreateColonyLogistics.java`
- `javap -classpath $env:USERPROFILE\.gradle\caches\modules-2\files-2.1\maven.modrinth\create\tS7ygzAE\7453cffbd20bdc5acc8ea7afb472bf55538958b\create-tS7ygzAE.jar -c -p com.simibubi.create.content.logistics.packager.PackagerBlockEntity`
- `javap -classpath $env:USERPROFILE\.gradle\caches\modules-2\files-2.1\curse.maven\minecolonies-245506\6790456\3b63a1bb1b8026eaceebd245c5f3f8bcafbb02c3\minecolonies-245506-6790456.jar -c -p com.minecolonies.api.inventory.api.CombinedItemHandler`
- `rg -n "version|cache|InventorySummary|PackagerBlockEntity|getAvailableItems|CombinedItemHandler|TTL|stale|copy|stockpile|threshold" docs\investigations`
- `javap -classpath $env:USERPROFILE\.gradle\caches\modules-2\files-2.1\maven.modrinth\create\tS7ygzAE\7453cffbd20bdc5acc8ea7afb472bf55538958b\create-tS7ygzAE.jar -c -p com.simibubi.create.content.logistics.packager.PackagerBlockEntity | Select-String -Pattern "getAvailableItems|availableItems|awaitNewVersion|stillWaiting|areturn" -Context 4,8`
- `javap -classpath $env:USERPROFILE\.gradle\caches\modules-2\files-2.1\maven.modrinth\create\tS7ygzAE\7453cffbd20bdc5acc8ea7afb472bf55538958b\create-tS7ygzAE.jar -c -p com.simibubi.create.content.logistics.packager.InventorySummary | Select-String -Pattern "getItemMap|add\(|erase|getStacks|getStacksByCount|contributingLinks|totalCount|copy\(" -Context 2,5`
- `javap -classpath $env:USERPROFILE\.gradle\caches\modules-2\files-2.1\curse.maven\minecolonies-245506\6790456\3b63a1bb1b8026eaceebd245c5f3f8bcafbb02c3\minecolonies-245506-6790456.jar -c -p com.minecolonies.api.inventory.api.CombinedItemHandler | Select-String -Pattern "getSlots|getStackInSlot|getSlotLimit|handlers|totalSlots|equals|hashCode" -Context 2,5`

The `javap` commands emitted useful bytecode output but ended with internal `AccessDeniedException` messages against Gradle-cached jars. The emitted output was sufficient for this audit.

## Final Git Status

Final status after creating this report:

```text
 M gradle.properties
 M src/main/java/com/createcolonylogistics/cache/ColonyStockCache.java
 M src/main/java/com/createcolonylogistics/cache/CreateInventorySummaryAdapter.java
 M src/main/java/com/createcolonylogistics/mixin/PackagerBlockEntityMixin.java
?? docs/investigations/2026-05-30-cache-hardening-broader-logic-risk-audit.md
?? docs/investigations/2026-05-30-create-stockpile-switch-stream-crash-audit.md
?? docs/investigations/2026-05-30-inventorysummary-cache-hardening-task-audit.md
```

## Audit Closure Statement

No source code, version metadata, dependency declarations, jars, release artifacts, or backups were changed. Existing dirty files were left untouched. The only file added by this pass is `docs/investigations/2026-05-30-cache-hardening-broader-logic-risk-audit.md`.
