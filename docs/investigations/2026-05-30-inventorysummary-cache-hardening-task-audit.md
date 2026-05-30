# 2026-05-30 InventorySummary Cache Hardening Task Audit

## Executive Summary

This was an audit-only review of the proposed `InventorySummary` cache hardening task. No source code, version metadata, jars, backups, dependency versions, release artifacts, or gameplay/UI behavior were changed.

The proposed hardening task is valid and correctly scoped. The current cache-hit path in `ColonyStockCache.getIfFresh()` returns `Optional.of(cached.summary())`, which exposes the stored mutable Create `InventorySummary` instance directly. Create 6.0.6 `InventorySummary` is mutable, backed by mutable map/list state, and exposes mutable data through methods such as `getItemMap()` and stack-list accessors.

This hardening is likely to reduce local cache/shared-state risk in the Create packager summary path. It is not proven, and should not be presented, as a direct fix for the Create `create:stockpile_switch` crash in `ThresholdSwitchBlockEntity.updateCurrentLevel()`. The stockpile switch crash still appears to occur in Create's own threshold-switch compatibility stream path, not in CCL's cached `InventorySummary` path.

## Proposed Task Validity

The task is valid.

Reasons:

- The existing investigation found a real local ownership problem in `ColonyStockCache.getIfFresh()`.
- The current implementation confirms that cache hits return the cached `InventorySummary` object directly.
- `CachedSummary` stores an `InventorySummary` mutable object.
- Create 6.0.6 `InventorySummary` is mutable and exposes mutable internals.
- The proposed task can be fixed without touching Create threshold switch logic, MineColonies logic, UI, packets, scroll storage, cancel behavior, assets, dependency versions, or release files.

The task is correctly scoped as cache hardening, not as a stockpile-switch crash fix.

## Expected Impact

Expected to reduce local cache/shared-state risk: yes.

Returning a defensive copy on every cache hit should prevent external Create callers from mutating or retaining the same `InventorySummary` object stored in `ColonyStockCache`.

Expected to directly fix the Create stockpile switch crash: not proven.

Clear answer: no direct evidence. The task may reduce one local unsafe-sharing risk, but the supplied stockpile switch crash signatures occur inside `ThresholdSwitchBlockEntity.updateCurrentLevel()` while Create is evaluating its own threshold compatibility stream. Stockpile switches do not call `PackagerBlockEntity.getAvailableItems()` based on the inspected Create path.

## Current Code Behavior Summary

`ColonyStockCache.store()`:

- Only runs when caching is enabled and the handler is a MineColonies `CombinedItemHandler`.
- Calls `CreateInventorySummaryAdapter.safeCopy(summary)`.
- Stores that copied `InventorySummary` in a `CachedSummary`.
- Replaces existing cached summary objects on refresh.

`ColonyStockCache.getIfFresh()`:

- Only runs when caching is enabled and the handler is a MineColonies `CombinedItemHandler`.
- Looks up a `CachedSummary` by weak handler identity.
- Verifies slot count and TTL.
- Records a hit.
- Currently returns `Optional.of(cached.summary())`.

`CachedSummary`:

- Stores a mutable `InventorySummary` field.
- Exposes that exact object through package-private `summary()`.
- Tracks build time, source slot count, hits, and misses.

`CreateInventorySummaryAdapter.safeCopy()`:

- Creates a new `InventorySummary`.
- Returns the new empty summary when source is `null`.
- Copies `contributingLinks`.
- Iterates `source.getStacks()`.
- Copies each non-empty `BigItemStack.stack` via `ItemStack.copy()`.
- Adds the copied stack/count to the target summary.
- On `RuntimeException`, logs at most once per interval when debug logging is enabled and returns the partial new summary.
- Does not return the original source object on failure.

`PackagerBlockEntityMixin`:

- Injects only into `PackagerBlockEntity.getAvailableItems()`.
- Uses `ColonyStockCache.getIfFresh()` at method head.
- Stores raw Create results on method return unless the head injection returned a cached summary.
- Does not touch Create stockpile/threshold switch classes.

## Exact Risk In ColonyStockCache.getIfFresh()

The exact risk is this cache-hit return:

```java
return Optional.of(cached.summary());
```

That returns the same `InventorySummary` instance held by `CachedSummary`. Because `InventorySummary` is mutable, any downstream caller that mutates the returned summary, mutates a returned internal collection, or retains it across ticks can affect the cache's stored object and later readers.

Risk classification:

- Proven: the same object reference is returned on cache hits.
- Proven: the object type is mutable.
- Likely: returning a copy would reduce cache/shared-state risk for packager summary callers.
- Possible: this can cause stale or cross-reader summary behavior in Create logistics consumers.
- Not proven: this corrupts Java Stream pipeline internals in Create threshold switches.

## Create InventorySummary Mutability

Create 6.0.6 local bytecode inspection confirms mutability:

- Constructor initializes a mutable `IdentityHashMap`.
- `add(InventorySummary)`, `add(ItemStack)`, `add(BigItemStack)`, `add(ItemStack, int)`, `addAllItemStacks(...)`, and `addAllBigItemStacks(...)` mutate summary contents.
- `erase(ItemStack)` mutates summary contents.
- `getItemMap()` returns the backing map.
- `getStacksByCount()` can initialize and cache a mutable list in the summary instance.
- `contributingLinks` is public mutable state.

The `javap` command emitted the needed bytecode output and then ended with an internal `AccessDeniedException` against the Gradle-cached Create jar. The inspected output was still sufficient to confirm mutability.

## Current safeCopy Behavior

`CreateInventorySummaryAdapter.safeCopy()` is currently sufficient for the intended copy boundary in these respects:

- It creates a new `InventorySummary`.
- It copies each `ItemStack` with `ItemStack.copy()` before adding it.
- It does not return the original source object when copying fails.
- It returns either a full copy or a partial new summary.

Remaining caveat:

- The copy result is still a mutable Create `InventorySummary`. That is fine if the copy is returned to a caller and not retained as shared cache state, but it means the cached object must not be returned directly.

## CachedSummary Encapsulation

`CachedSummary` should be encapsulated further in the later coding pass.

Minimal improvement:

- Replace `summary()` with a method that returns `CreateInventorySummaryAdapter.safeCopy(summary)`, such as `copySummary()`.
- Keep the stored `summary` field private.
- Ensure `ColonyStockCache.getIfFresh()` cannot accidentally return the stored mutable instance.

This keeps ownership rules local and makes future accidental shared returns less likely.

## Recommended Minimal Coding Plan

1. In the cache hit path, return a defensive copy instead of the cached object.
2. Prefer moving that copy boundary into `CachedSummary`, for example `cached.copySummary()`, so `ColonyStockCache` cannot reach the stored object directly.
3. Keep `ColonyStockCache.store()` copying inbound summaries before storage.
4. Keep `CreateInventorySummaryAdapter.safeCopy()` as the single copy helper.
5. Do not change `PackagerBlockEntityMixin` unless the later implementation needs a method-name adjustment after encapsulating `CachedSummary`.
6. Do not add stockpile/threshold switch mixins or Create-side patches.

Most conservative code shape:

```java
cached.recordHit();
return Optional.of(cached.copySummary());
```

with `CachedSummary.copySummary()` internally calling `CreateInventorySummaryAdapter.safeCopy(summary)`.

## PackagerBlockEntityMixin Scope

`PackagerBlockEntityMixin` probably does not need behavioral changes.

It should be re-read during the coding pass to ensure the cache-hit flag still behaves correctly, but the ownership bug is inside cache return semantics. The mixin can continue to receive an `Optional<InventorySummary>` from `ColonyStockCache`.

## Files Likely To Change Later

Likely minimal coding-pass changes:

- `src/main/java/com/createcolonylogistics/cache/ColonyStockCache.java`
- `src/main/java/com/createcolonylogistics/cache/CachedSummary.java`

Possibly touched only if copy helper naming/visibility changes:

- `src/main/java/com/createcolonylogistics/cache/CreateInventorySummaryAdapter.java`

Likely review-only, not change-required:

- `src/main/java/com/createcolonylogistics/mixin/PackagerBlockEntityMixin.java`

## Files Explicitly Not To Touch

The later cache-hardening pass should not touch:

- `src/main/java/com/createcolonylogistics/client/SmartClipboardScreen.java`
- `src/main/java/com/createcolonylogistics/client/SmartColonyClipboardDecorator.java`
- `src/main/java/com/createcolonylogistics/client/SmartClipboardClient.java`
- `src/main/java/com/createcolonylogistics/clipboard/SmartClipboardScrollStorage.java`
- `src/main/java/com/createcolonylogistics/clipboard/RequestReportFormatter.java`
- `src/main/java/com/createcolonylogistics/network/*`
- `src/main/java/com/createcolonylogistics/item/*`
- `src/main/java/com/createcolonylogistics/registry/*`
- `src/main/resources/assets/**`
- `src/main/resources/data/**`
- `src/main/resources/META-INF/neoforge.mods.toml`
- `build.gradle`
- `settings.gradle`
- `gradle.properties`
- Create dependency declarations
- MineColonies dependency declarations
- Any Create threshold/stockpile switch class or mixin
- Any release jar or backup location

## Validation Practicality

Automated tests are not currently practical as a small follow-up unless a test harness is added.

Observed project state:

- No `src/test` tree exists.
- No JUnit/test dependency was found in `build.gradle`.
- `build.gradle` configures NeoForge client/server runs and GameTest namespace properties, but no local GameTest classes were found.

Recommended validation for the later coding pass:

1. Read-only/source review:
   - Verify no path returns `cached.summary()` or any stored mutable `InventorySummary`.
   - Verify cache hits call the defensive copy path.
   - Verify store still copies inbound summaries.

2. Compile/build validation:
   - `.\gradlew.bat compileJava`
   - Optionally `.\gradlew.bat build` if a full local verification artifact is desired.

3. Manual code review check:
   - Confirm no Smart Clipboard/UI/network/asset/version files changed.
   - Confirm no Create stockpile/threshold switch mixin was added.

4. Optional later test harness:
   - Add a focused test or GameTest only in a separate pass if the project chooses to introduce test infrastructure.
   - Test goal: cache a summary, retrieve it twice, mutate the first returned summary, and verify the cached summary and second returned summary remain independent.

## Version Bump Recommendation

For the later coding pass: yes, a version bump is reasonable if the change is intended to ship.

Recommended timing:

- Do not version bump during audit.
- Do not version bump during an experimental or review-only patch.
- If the hardening change is accepted and a release/test artifact is produced for pack testing, bump from the current working version to the next prerelease/patch version according to the project's existing versioning practice.

## Backup And Release Recommendation

For the later coding pass:

- Do not create a release jar or backup during implementation-only review.
- If `build` produces a jar only for compile validation, treat it as a local test artifact, not a release.
- If the jar will be handed to a server/modpack for testing or release, then create a backup according to the project's release hygiene before replacing any deployed artifact.

## Open Questions Or Blockers

- Should the next pass be minimal cache-boundary hardening only, or should it also introduce lifecycle cache clearing? Minimal cache-boundary hardening is recommended first.
- Should test infrastructure be added? Current repo shape suggests build-only validation plus focused code review is the practical default.
- Should the existing dirty `gradle.properties` version bump remain part of a future release pass? This audit did not touch it.
- If stockpile switch crashes continue after cache hardening, transformed runtime evidence for `ThresholdSwitchBlockEntity` and loaded mixins remains the next useful evidence, not a broader CCL patch.

## Commands Run

Initial git status:

```text
 M gradle.properties
 M src/main/java/com/createcolonylogistics/cache/ColonyStockCache.java
 M src/main/java/com/createcolonylogistics/cache/CreateInventorySummaryAdapter.java
 M src/main/java/com/createcolonylogistics/mixin/PackagerBlockEntityMixin.java
?? docs/investigations/2026-05-30-create-stockpile-switch-stream-crash-audit.md
```

Read-only commands run:

- `git status --short`
- `Get-Content docs\investigations\2026-05-30-create-stockpile-switch-stream-crash-audit.md`
- `Get-Content src\main\java\com\createcolonylogistics\cache\ColonyStockCache.java`
- `Get-Content src\main\java\com\createcolonylogistics\cache\CachedSummary.java`
- `Get-Content src\main\java\com\createcolonylogistics\cache\CreateInventorySummaryAdapter.java`
- `Get-Content src\main\java\com\createcolonylogistics\mixin\PackagerBlockEntityMixin.java`
- `rg -n "test|junit|gametest|sourceSets|dependencies|minecraft|runs" build.gradle settings.gradle src\test`
- `Get-ChildItem src -Recurse -Directory | Select-Object -ExpandProperty FullName`
- `javap -classpath $env:USERPROFILE\.gradle\caches\modules-2\files-2.1\maven.modrinth\create\tS7ygzAE\7453cffbd20bdc5acc8ea7afb472bf55538958b\create-tS7ygzAE.jar -c -p com.simibubi.create.content.logistics.packager.InventorySummary`
- `Get-Content build.gradle`
- `rg -n "junit|testImplementation|GameTest|gametest|src/test|sourceSets|test" .`
- `rg -n "class ColonyStockCache|Optional<InventorySummary> getIfFresh|return Optional\.of\(cached\.summary\(\)\)|InventorySummary summary\(\)|safeCopy|ItemStack ownedStack|return copy|create_colony_logistics\$returnedCachedSummary|storeMineColoniesSummary" src\main\java\com\createcolonylogistics\cache src\main\java\com\createcolonylogistics\mixin\PackagerBlockEntityMixin.java`

The first `rg ... src\test` command reported that `src\test` does not exist. The `javap` command printed the necessary `InventorySummary` bytecode and then reported an internal access-denied error against the Gradle-cached jar; the emitted bytecode was sufficient for this audit.

Final git status after this audit:

```text
 M gradle.properties
 M src/main/java/com/createcolonylogistics/cache/ColonyStockCache.java
 M src/main/java/com/createcolonylogistics/cache/CreateInventorySummaryAdapter.java
 M src/main/java/com/createcolonylogistics/mixin/PackagerBlockEntityMixin.java
?? docs/investigations/2026-05-30-create-stockpile-switch-stream-crash-audit.md
?? docs/investigations/2026-05-30-inventorysummary-cache-hardening-task-audit.md
```

This audit added only the `2026-05-30-inventorysummary-cache-hardening-task-audit.md` report. The previously dirty source/version files and the earlier stockpile-switch audit report were left untouched.

## Audit Closure Statement

No source code, version metadata, dependency declarations, jars, release artifacts, or backups were changed. Existing dirty files were left untouched. The only file added by this pass is `docs/investigations/2026-05-30-inventorysummary-cache-hardening-task-audit.md`.
