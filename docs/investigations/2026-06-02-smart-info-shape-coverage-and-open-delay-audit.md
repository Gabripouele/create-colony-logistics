# Smart Info Shape Coverage And Open Delay Audit

Date: 2026-06-02

## Scope

This audit investigated two post-fallback issues:

1. Some Domum Ornamentum / Architects Cutter rows now show Smart Info and Shape but no `Can learn`.
2. Smart Colony Clipboard right-click opening now feels delayed, with the hand-use animation appearing to complete or replay when the UI finally opens.

The audit is static source inspection only. No code, UI, assets, language keys, version metadata, release jar, or backup folder changes were made.

## Non-Goals

- Do not change locked Smart Info tooltip formatting.
- Do not modify tooltip text, order, colors, spacing, wrapping, or language keys.
- Do not change requester/resolver UI, accordion behavior, delivery arrow, scrolling, slots, tabs, filtering, sorting, or unrelated systems.
- Do not bump version metadata.
- Do not build or copy a release jar.
- Do not implement a fix in this pass.

## Part A Summary

Stripped Dark Oak Wood Panel shows Shape but no `Can learn` because it is resolving through the global DO production fallback, not through an authoritative report entry or exact request-analysis path.

The current rc.21 precision guard in `SmartInfoResolver.productionFallback(...)` deliberately clears `canLearn` for Domum Ornamentum fallback entries:

```java
List<String> canLearn = domumFallback ? List.of() : production.canLearn();
```

That prevents broad generic lists such as Sawmill, Stonemason, Fletcher, Mechanic, and Glassblower from appearing when the only match is generic Architects Cutter production data. It also means any DO row that reaches only fallback will show Shape-only unless it has exact `knownBy` context.

This is probably safer than the previous broad list, but it is also likely over-correcting for shapes whose exact generated recipe could be validated if the server-side exact production lookup were reused for fallback rows.

## Shape Coverage Table

Runtime recipe ids and per-module compatibility were not captured in this pass because no Minecraft session was run and no Domum Ornamentum recipe jar was available in the local Gradle-cache search. The table below reflects current source-path coverage for the requested shape categories.

| Shape/category | Expected item id pattern | Shape resolves? | Cutter recipe resolves? | Exact generated output validates? | knownBy resolves? | canLearn resolves? | Accepted modules/buildings | Current omission reason | Correct or likely bug? |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Panel | `domum_ornamentum:*panel*` | Yes when fallback matches production by recipe id or DO metadata | Likely yes for observed Panel, because Shape appears | Not proven in fallback; exact path did not win | Only if exact production match is not recipe-id-only | No in fallback | Not serialized for exact fallback; generic modules intentionally suppressed | rc.21 clears all DO fallback `canLearn` | Likely over-correcting if exact recipe is valid |
| Trapdoor | `domum_ornamentum:*trapdoor*` | Yes after global fallback | Likely yes when recipe id is available | Not proven in fallback | Only exact-ish knownBy | No in fallback | Generic modules previously broad; now hidden | Generic recipe-id can show Shape but not `Can learn` | Safer than broad list; exact path still missing |
| Pillar | `domum_ornamentum:*pillar*` | Expected yes if recipe id/fallback key matches | Unknown per runtime stack | Not proven in fallback | Only exact-ish knownBy | No in fallback | Not available from static inspection | Same fallback suppression | Likely needs exact validation reuse |
| Fence | `domum_ornamentum:*fence*` | Expected yes if recipe id/fallback key matches | Unknown per runtime stack | Not proven in fallback | Only exact-ish knownBy | No in fallback | Not available from static inspection | Same fallback suppression | Likely needs exact validation reuse |
| Wall | `domum_ornamentum:*wall*` | Expected yes if recipe id/fallback key matches | Unknown per runtime stack | Not proven in fallback | Only exact-ish knownBy | No in fallback | Not available from static inspection | Same fallback suppression | Likely needs exact validation reuse |
| Framed | `domum_ornamentum:*framed*` | Often yes through indexed/report/semantic paths | More likely than single-material rows when metadata is populated | More likely if material data is complete | Yes when report entry or exact known recipe path wins | Yes only from report-entry exact analysis; no in fallback | Exact list only in request-analysis entries | If fallback only, `canLearn` hidden | Correct for fallback, but exact route should win |
| Shingle | `domum_ornamentum:*shingle*` | Expected yes when recipe id/fallback key matches | Unknown per runtime stack | Not proven in fallback | Only exact-ish knownBy | No in fallback | Not available from static inspection | Same fallback suppression | Likely needs exact validation reuse |
| Pane | `domum_ornamentum:*pane*` | Expected yes when recipe id/fallback key matches | Unknown per runtime stack | Can fail if pane/framed/glass representation differs | Only exact-ish knownBy | No in fallback | Not available from static inspection | Same fallback suppression or exact mismatch | Needs runtime confirmation |
| Stairs | `domum_ornamentum:*stair*` | Known working in earlier control cases | Often direct/index/semantic, not fallback-only | Likely yes when exact path wins | Yes if exact report path finds taught recipe | Yes if exact report path finds learnable module | Exact modules from `ColonyProductionInspector.inspect(...)` | No omission when report path wins | Current behavior likely correct |
| Slab | `domum_ornamentum:*slab*` | Expected yes if recipe id/fallback key matches | Unknown per runtime stack | Not proven in fallback | Only exact-ish knownBy | No in fallback | Not available from static inspection | Same fallback suppression | Likely needs exact validation reuse |
| Other Architects Cutter shapes | DO recipe result item id from runtime recipe list | Depends on stack keys, recipe id, and DO metadata | Depends on `findArchitectsCutterMatch(...)` | Only proven in report-entry path | Only exact-ish knownBy in fallback | No in fallback | Not available from static inspection | DO fallback strips generic `canLearn` | Needs runtime dump per shape |

## Why Panel Lacks Can Learn

High confidence path:

1. The Panel row is visible in Scrolls, so `ResourceLine` has a valid `BuildingBuilderResource` stack.
2. `SmartInfoResolver.resolveResource(...)` fails to find an indexed/report entry or exact semantic entry.
3. `resourceRowKeys(...)` can still call `findArchitectsCutterMatch(...)` client-side and add `recipe:<id>`.
4. `productionFallback(...)` finds a global `ProductionInfo` by that fallback key.
5. Because the hovered stack is Domum Ornamentum, rc.21 sets `canLearn` to an empty list.
6. The fallback entry still has `doBlockId` or `recipeId`, so `hasSmartInfoTooltip(...)` allows the locked Smart Info block and Shape line.
7. The tooltip has no teaching line because `teachingFeedbackBuildings(entry)` is empty.

This means Panel is not missing Shape resolution. It is missing an authoritative exact `canLearnCombo` source.

## Is Exact Validation Too Strict?

There are two separate strict points:

1. `DomumOrnamentumRequestInspector.findArchitectsCutterMatch(...)` is strict. It requires material data, textured block components, recipe assembly, and `isSameMaterializedCutterOutput(...)` to agree. Single-material or implicit-material shapes can fail this before exact production lookup is possible.
2. `SmartInfoResolver.productionFallback(...)` is now intentionally strict for `canLearn`: every DO fallback entry gets no `canLearn`, even if generic production metadata says some modules can learn the recipe.

The second point is probably too blunt for final behavior. It fixes broad generic lists, but it does not attempt exact server-side module validation for fallback rows.

## Are Shapes Missing From MineColonies Exact Learnability?

Not proven. Current static evidence does not show that MineColonies lacks panel/trapdoor/pillar/fence/wall recipes. The problem is that the fallback path does not run the exact server-side learnability check for the materialized hovered stack.

The exact report-entry path does this:

- `ColonyProductionInspector.inspect(colony, level, requestedStack)`
- `findArchitectsCutterMatch(level, requestedStack)`
- `CutterRecipeMatch.genericRecipe()`
- `module.canLearn(ModCraftingTypes.ARCHITECTS_CUTTER.get())`
- `module.isRecipeCompatible(exactGeneratedRecipe)`

The global fallback path does not do that exact check for the hovered Scroll row.

## Are Generic Recipes Compatible When Exact Recipes Are Not?

Likely yes for some cases.

`ColonyProductionInspector.inspectGlobal(...)` calls each supported crafting type's `findRecipes(...)`. For Architects Cutter, MineColonies generates generic material-empty recipes with valid skin/material options. Modules can accept those generic recipes through `module.isRecipeCompatible(recipe)`.

That proves generic shape compatibility, not exact materialized output compatibility. This explains why broad lists appeared before rc.21 and why they disappeared after rc.21.

## Is The Current Fix Over-Correcting?

Yes, probably.

It is correct to reject recipe-id-only generic `canLearn` for DO fallback rows. It is probably too strict to reject all DO fallback `canLearn` forever. A better future fix would compute exact DO fallback learnability server-side and serialize only exact module names.

## Part B Summary

The Smart Colony Clipboard opens only after server-side report generation completes. The current right-click path performs a full synchronous analysis before sending the client packet that opens the screen.

The newest expensive addition is global production fallback indexing:

```java
RequestAnalysisService.analyze(...)
  -> ColonyProductionInspector.inspectGlobal(colony, level)
```

That scan runs on every clipboard open, and also on report refresh paths such as scroll-slot mutations and cancel refreshes.

## Exact Open Path

Right-click item use:

1. `SmartColonyClipboardItem.use(Level, Player, InteractionHand)`
2. Server side calls `runReport(serverPlayer, stack)`.
3. `runReport(...)` resolves the linked colony.
4. It synchronously calls `RequestAnalysisService.analyze(player.serverLevel(), colony, MAX_REPORT_REQUESTS)`.
5. `SmartClipboardReport.fromAnalysis(...)` serializes entries, production index, Smart Info index, and scroll stacks.
6. `PacketDistributor.sendToPlayer(...)` sends `ClientboundSmartClipboardReportPacket`.
7. Client packet handler calls `SmartClipboardClientBridge.open(...)`.
8. `SmartClipboardClient.open(...)` finally calls `minecraft.setScreen(new SmartClipboardScreen(report))`.

The screen is not opened optimistically. It waits for the server analysis and packet round trip.

## Expensive Work On Every Open

`RequestAnalysisService.analyze(...)` currently does:

- `lessImportantRequests(colony)`
- `WorkerGroups.from(colony)`
- `clipboardRootRequests(colony)`
- sorting root requests by requester name
- up to 250 `inspectRequest(...)` calls
- per DO request, `findArchitectsCutterMatch(...)`
- per DO request, `ColonyProductionInspector.inspect(...)`
- request-tree recursion through child request tokens
- synthetic cutter requirement fallback when needed
- global `ColonyProductionInspector.inspectGlobal(...)`

`inspectGlobal(...)` does:

- all buildings in `colony.getBuildingManager().getBuildings().values()`
- all `ICraftingBuildingModule` modules in each building
- all known recipe tokens for each module
- global recipe-manager storage lookup for each token
- all supported crafting types for each module
- `CraftingType.findRecipes(level.getRecipeManager(), level)` for each learnable type
- `module.getAdditionalRecipesForDisplayPurposesOnly(level)`
- `module.isRecipeCompatible(recipe)` for every candidate recipe
- output/key accumulation for primary, multi, and additional outputs

For Architects Cutter-capable modules, `findRecipes(...)` can scan the full Architects Cutter recipe list and generate generic recipes. If several modules support the same crafting type, this can repeat the same recipe-list scan per module.

## Estimated Cost

No runtime timing was captured in this audit. Static complexity is approximately:

```text
open cost =
  O(root requests + request-tree edges + DO request recipe scans)
  +
  O(buildings * crafting modules * (known recipe tokens + supported crafting types * recipes per type))
```

The new global production scan is the main likely contributor to the observed roughly one-second delay because it is broad, synchronous, uncached, and runs before the open packet is sent.

## Specific Delay Questions

- Is `inspectGlobal(...)` running on every clipboard open?
  Yes. It is called unconditionally in `RequestAnalysisService.analyze(...)`, and `analyze(...)` is called by `SmartColonyClipboardItem.runReport(...)`.

- Is the fallback scanning every MineColonies building/module synchronously?
  Yes. `inspectGlobal(...)` walks every building and each `ICraftingBuildingModule` synchronously on the server thread.

- Is the Architects Cutter recipe list scanned repeatedly?
  Likely yes. For each module and each supported crafting type, `learnableRecipes(...)` calls `type.findRecipes(...)`. For Architects Cutter crafting type, that can enumerate Architects Cutter recipes. There is no shared per-open recipe cache in the current code.

- Is the UI waiting for server-side analysis before opening?
  Yes. The client screen is opened only when `ClientboundSmartClipboardReportPacket` is received and handled.

- Why does the hand item-use animation appear to play again when the UI opens?
  Static code does not show a deliberate second item-use packet. The most likely explanation is that client-side item use succeeds immediately, while the screen opens later after the server finishes analysis and sends the report. The delayed UI transition makes the use animation or server hand-sync feel like it completes/replays when the packet arrives. Runtime packet/tick logging would be needed to distinguish late completion from an actual resend.

- Is the second animation caused by delayed server response, packet resend, or normal item use finishing late?
  Most likely delayed server response or late normal use completion. No resend path was found in the Smart Clipboard open code.

## Per-Hover And Scroll Costs

Open delay is most likely server analysis, but there is another cost source after the UI opens:

- `renderSelectedScrollFromClientStack(...)` calls `buildClientResourceScrollRows(...)`.
- `ResourceLine.from(...)` calls `SmartInfoResolver.resourceRowKeys(...)`.
- For DO rows, `resourceRowKeys(...)` can call `findArchitectsCutterMatch(...)`.

Because `buildClientResourceScrollRows(...)` is used during Scrolls tab rendering and hover detection, DO recipe matching can also happen repeatedly on the client while the Scrolls tab is visible. That is separate from the initial right-click open delay.

## Recommended Future Implementation Plan

For `Can learn` coverage:

1. Keep direct indexed/report-entry Smart Info authoritative.
2. Add a server-side exact DO fallback learnability path for visible Scroll/resource rows or materialized fallback stacks.
3. Reuse `ColonyProductionInspector.inspect(colony, level, requestedStack)` or refactor its exact module-validation core.
4. Populate fallback `canLearnCombo` only from exact generated `CutterRecipeMatch.genericRecipe()` validation.
5. Keep recipe-id-only DO fallback for Shape, but not for teaching feedback.
6. Add diagnostic output for each shape: recipe id, assembled output key, exact match pass/fail, knownBy, canLearn, and ambiguity reason.

For open delay:

1. Do not run `inspectGlobal(...)` unconditionally on every open if no fallback data is needed immediately.
2. Cache global production index per colony for a short TTL or until recipe/building changes.
3. Cache `CraftingType.findRecipes(...)` results per server level and crafting type within a report generation pass.
4. Split initial open into a fast report and a later fallback-enrichment packet.
5. Lazy-load production fallback only when a tooltip needs it, or only when Scrolls tab is opened.
6. Cache client-side Resource Scroll rows and DO row keys per selected scroll/report instead of recomputing them every render/hover.

## Caching Options

- Per-open local cache:
  Safe and simple. Cache `findRecipes(...)` by `CraftingType` during one `inspectGlobal(...)` call.

- Short-lived server cache:
  Cache `productionIndex` by colony id and level for a few seconds. Invalidate on recipe teaching, building changes, or request refresh if detectable.

- Report-level lazy enrichment:
  Open immediately with request entries, then send production fallback index after analysis completes.

- Scroll-row targeted enrichment:
  Build exact fallback metadata only for visible linked Resource Scroll rows rather than every possible colony production output.

## Risks

- Caching production data can go stale after recipes are taught, buildings change jobs, or modules learn recipes.
- Lazy enrichment can make tooltips change after the UI opens unless the state transition is handled carefully.
- Exact DO fallback learnability may still fail for single-material items if `findArchitectsCutterMatch(...)` cannot reconstruct the materialized recipe.
- Moving work off the initial open path must not break cancel refresh, scroll-slot updates, or important-only filters.
- Per-hover server queries would avoid upfront cost but could introduce latency and packet spam.

## Future Validation Plan

Shape coverage:

1. Panel, trapdoor, pillar, fence, wall, framed, shingle, pane, stairs, slab, and any additional DO Architects Cutter recipe shapes found at runtime.
2. For each shape, record recipe id, assembled output, exact validation result, knownBy, canLearn, and final tooltip.
3. Confirm Shape remains visible for fallback-only DO rows.
4. Confirm `Can learn` appears only when exact generated recipe validation succeeds.
5. Confirm already-taught DO recipes still show known-by context.
6. Confirm vanilla teachable and non-teachable items keep current fallback behavior.

Open delay:

1. Add temporary timing around `runReport(...)`, `RequestAnalysisService.analyze(...)`, per-request inspection, and `inspectGlobal(...)`.
2. Log building count, module count, supported crafting type count, known recipe token count, and generated recipe count.
3. Measure open time before and after disabling/caching `inspectGlobal(...)`.
4. Test cancel refresh and scroll-slot mutations, because they also call `analyze(...)`.
5. Test Scrolls tab rendering with many DO resource rows for repeated client-side row-key cost.

## Validation

No Gradle build was run. This was an audit-only documentation pass with no implementation changes.

## No-Change Confirmation

No implementation code was changed.
No UI behavior was changed.
No assets were changed.
No Smart Info tooltip formatting was changed.
No tooltip text, order, colors, spacing, wrapping, or language keys were changed.
No version metadata was changed.
No release jar was built.
No backup copy was created.
