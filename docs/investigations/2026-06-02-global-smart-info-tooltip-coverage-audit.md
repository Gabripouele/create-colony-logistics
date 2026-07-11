# Global Smart Info Tooltip Coverage Audit

Date: 2026-06-02

## Scope

This audit investigated whether Domum Ornamentum / Architects Cutter Smart Info is applied consistently across Smart Colony Clipboard and Smart Resource Scroll hover paths.

No gameplay, UI, tooltip, language, version, delivery-indicator, release, or backup-folder changes were made in this pass.

## Non-Goals

- Do not change the locked Smart Info tooltip golden reference.
- Do not modify tooltip text, ordering, colors, spacing, wrapping, or translation keys.
- Do not implement Smart Info propagation or matching changes in this pass.
- Do not change Resource Scroll delivery / warehouse indicator rendering in this pass.
- Do not bump the mod version, create a release jar, or copy anything to a release backup folder.

## Classes And Methods Inspected

MineColonies reference paths:

- `com.minecolonies.core.client.gui.WindowResourceList`
  - `onOpened()`
  - `pullResourcesFromHut()`
  - `addDeliveryRequestsToList(List<Delivery>, ImmutableCollection<IToken<?>>)`
  - row update callback from `onOpened()`
- `com.minecolonies.core.colony.buildings.moduleviews.BuildingResourcesModuleView`
  - `deserialize(RegistryFriendlyByteBuf)`
  - `getResources()`
  - `getProgress()`
- `com.minecolonies.core.colony.buildings.modules.BuildingResourcesModule`
  - `serializeToView(RegistryFriendlyByteBuf)`
  - `updateAvailableResources()`
  - `addNeededResource(ItemStack, int)`
  - `getNeededResources()`
  - `getRequiredResources()`
- `com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource`
  - `getItemStack()`
  - `getAmount()`
  - `getAvailable()`
  - `getAmountInDelivery()`
  - `getAvailabilityStatus()`
- `com.minecolonies.api.util.ItemStackUtils`
  - `compareItemStacksIgnoreStackSize(...)`
  - `compareItemStackListIgnoreStackSize(...)`
- `com.minecolonies.api.colony.requestsystem.request.IRequest`
  - `getRequest()`
  - `getDisplayStacks()`
  - `getChildren()`
  - `getRequestOfType(Class<T>)`
- `com.minecolonies.core.colony.requestsystem.requests.AbstractRequest`
  - `getDisplayStacks()`
- `com.minecolonies.api.colony.requestsystem.requestable.IDeliverable`
  - `matches(ItemStack)`
  - `getResult()`
  - `getCount()`
  - `getMinimumCount()`
- `com.minecolonies.api.colony.requestsystem.requestable.Stack`
  - `matches(ItemStack)`
  - `getStack()`
  - `getRequestedItems()`
- `com.minecolonies.api.colony.requestsystem.requestable.StackList`
  - `matches(ItemStack)`
  - `getStacks()`
  - `getRequestedItems()`

Create-Colony-Logistics paths:

- `com.createcolonylogistics.client.SmartClipboardScreen`
  - `render(...)`
  - `renderHoveredTabTooltip(...)`
  - `renderHoveredItemTooltip(...)`
  - `renderHoveredScrollTooltip(...)`
  - `renderHoveredScrollResourceTooltip(...)`
  - `renderHoveredTreeItemTooltip(...)`
  - `renderHoveredOverflowTooltip(...)`
  - `renderSmartInfoOrItemTooltip(...)`
  - `smartInfoEntryForStack(ItemStack)`
  - `buildApprovedSmartInfoTooltip(...)`
  - `displayStack(SmartClipboardReport.Entry)`
  - `isArchitectsCutterEntry(SmartClipboardReport.Entry)`
  - `ResourceScrollContent.fromClientSelectedStack(ItemStack)`
  - `ResourceLine.from(BuildingBuilderResource, Map<String, Integer>)`
- `com.createcolonylogistics.clipboard.RequestAnalysisService`
  - `analyze(ServerLevel, IColony, int)`
  - `clipboardRootRequests(IColony)`
  - `requestedStack(IRequest<?>)`
  - `inspectRequest(...)`
  - `displayStacks(IRequest<?>, ItemStack)`
  - `requestTree(IRequestManager, IRequest<?>, int, int)`
  - `requestDisplayStack(IRequest<?>)`
  - `requestCount(IRequest<?>)`
  - `quantityDisplay(IRequest<?>, ItemStack)`
  - `deliverable(IRequest<?>)`
- `com.createcolonylogistics.clipboard.SmartClipboardReport`
  - `fromAnalysis(...)`
  - `Entry`
  - `Entry.encode(...)`
  - `Entry.decode(...)`
- `com.createcolonylogistics.clipboard.DomumOrnamentumRequestInspector`
  - `isDomumOrnamentumStack(ItemStack)`
  - `materializedRequestedStack(IRequest<?>)`
  - `findArchitectsCutterMatch(Level, ItemStack)`
  - `findCutterRecipeHolder(...)`
  - `findCutterRequirements(...)`
  - `isMaterializedArchitectsCutterOutput(ItemStack)`
  - `exactComboFingerprint(ItemStack)`
- `com.createcolonylogistics.clipboard.ColonyProductionInspector`
  - `inspect(IColony, Level, ItemStack)`
  - `supportsArchitectsCutter(ICraftingBuildingModule)`
  - `safeGetFirstRecipe(ICraftingBuildingModule, ItemStack)`
  - `safeCanLearn(ICraftingBuildingModule)`
- `com.createcolonylogistics.clipboard.RequestReportFormatter`
  - `format(RequestAnalysisService.AnalysisResult)`
- `com.createcolonylogistics.clipboard.SmartClipboardRecipeTeachingService`
  - `teachBestAvailable(...)`
  - `canTeach(...)`
  - `supportsArchitectsCutter(...)`

## Tooltip Paths

Smart Info is not globally attached to every item hover. There are several separate tooltip paths:

- Requests tab, main request icon:
  `SmartClipboardScreen.renderHoveredItemTooltip(...)` calls `buildApprovedSmartInfoTooltip(entry, displayStack(entry))` directly for the hovered `SmartClipboardReport.Entry`.
  This is the strongest path. If the entry is an Architects Cutter entry, Smart Info appears.

- Requests tab, request-tree child icon:
  `renderHoveredTreeItemTooltip(...)` calls `renderSmartInfoOrItemTooltip(node.stack())`.
  This is a reverse lookup path. The tree node must match an Architects Cutter report entry through `smartInfoEntryForStack(...)`.

- Scrolls tab, stored Resource Scroll slot:
  `renderHoveredScrollTooltip(...)` calls vanilla `graphics.renderTooltip(font, stack, ...)` on the stored Resource Scroll item.
  This bypasses Smart Info by design because the hovered item is the scroll itself.

- Scrolls tab, resource row icon:
  `renderHoveredScrollResourceTooltip(...)` calls `renderSmartInfoOrItemTooltip(resource.stack())`.
  This is also a reverse lookup path. The resource row must match an Architects Cutter report entry through `smartInfoEntryForStack(...)`.

- Tab labels and overflow text:
  `renderHoveredTabTooltip(...)` and `renderHoveredOverflowTooltip(...)` render text-only tooltips.
  These are not item Smart Info paths.

- Smart Colony Clipboard item report text:
  `RequestReportFormatter.format(...)` formats report/debug-style clipboard lines with DO metadata, recipe, taught, known-by, can-learn, request token, and fingerprint. This is separate from the approved on-screen Smart Info hover tooltip in `SmartClipboardScreen.buildApprovedSmartInfoTooltip(...)`.

## Detection And Metadata Path

`RequestAnalysisService.analyze(...)` builds `SmartClipboardReport.Entry` objects from MineColonies clipboard root requests only.

For each root request:

1. `requestedStack(IRequest<?>)` chooses the stack from `IStackBasedTask`, then `IDeliverable.getResult()`, then first `request.getDisplayStacks()` entry.
2. `inspectRequest(...)` checks `DomumOrnamentumRequestInspector.isDomumOrnamentumStack(requestedStack)`.
3. If the stack is Domum Ornamentum, it calls `ColonyProductionInspector.inspect(...)`.
4. `ColonyProductionInspector.inspect(...)` calls `DomumOrnamentumRequestInspector.findArchitectsCutterMatch(...)` to find an Architects Cutter recipe for that exact requested stack.
5. The entry records `knownBy`, `canLearnCombo`, `cutterRecipeId`, `exactComboFingerprint`, and `exactComboAlreadyTaught`.
6. `SmartClipboardReport.fromAnalysis(...)` serializes the entry and its request tree to the client.

The Architects Cutter entry check used by the screen is:

- `entry.doBlockId().startsWith("domum_ornamentum:")`
- and either `entry.cutterRecipeId().isPresent()` or `DomumOrnamentumRequestInspector.isMaterializedArchitectsCutterOutput(entry.requestedStack())`

This is entry-level metadata. It is not a global registry of every stack that should show Smart Info.

## Already-Taught Recipe Behavior

Already-taught recipes are not filtered out during detection.

`ColonyProductionInspector.inspect(...)` adds buildings to `knownBy` when `module.getFirstRecipe(requestedStack)` returns a recipe whose primary output is exactly the requested stack. `RequestAnalysisService.inspectRequest(...)` then sets `exactComboAlreadyTaught` from `!knowledge.knownBy().isEmpty()`.

The locked tooltip builder still shows Smart Info for already-taught Architects Cutter entries. It only suppresses the "Can learn" line when `entry.exactComboAlreadyTaught()` is true or `entry.canLearnCombo()` is empty. The base Smart Info header and Shape line remain available.

Therefore, already-taught status is not the expected reason for missing Smart Info on a valid Architects Cutter entry. If Smart Info is absent entirely, the hovered stack usually failed to resolve to an Architects Cutter report entry before formatting.

## Current Coverage

Expected current behavior:

- A top-level Smart Clipboard request row that is itself a detected Domum Ornamentum / Architects Cutter entry should show Smart Info when hovering its main icon.
- The same entry can be already taught and still show Smart Info, with the can-learn feedback omitted when appropriate.
- Vanilla items and non-DO items should fall back to normal item tooltip unless they are deliberately mapped to a parent DO request in a future implementation.

Current gaps:

- Request-tree child item hovers do not use request-token or parent-entry context. They reverse-match the child `ItemStack` against all report entries.
- Resource Scroll resource row hovers do not use `BuildingBuilderResource`, MineColonies resource keys, request tokens, parent request tokens, `Stack`, `StackList`, or MineColonies predicate semantics.
- `smartInfoEntryForStack(...)` checks only:
  - exact `ItemStack.isSameItemSameComponents(entry.requestedStack(), hoveredStack)`
  - then same item id fallback, only for Architects Cutter entries
- It does not search `entry.displayStacks()`, `entry.requestTree()`, StackList alternatives, materialized DO helper output, or exact combo fingerprints.
- It has no ambiguity guard for same-item variants. If multiple Architects Cutter entries share an item id with different components, the first same-item match can be wrong.

## Example Classification

These screenshot items are useful coverage cases, not proof that the names themselves are special:

- Polished Diorite Fence:
  Likely a Domum Ornamentum / Architects Cutter output or derived output case. It should show Smart Info when the hovered stack maps to the exact DO requested stack or a uniquely identified materialized DO output. If it does not, the likely failure is reverse matching from a resource/tree stack to the report entry.

- Mossy Stone Brick Wall:
  Could be a vanilla resource row, a material input, or part of a parent DO build. If it is not itself a DO stack, current code should not show Smart Info unless it accidentally matches a same-item DO entry. Parent inheritance is not implemented.

- Prismarine Bricks:
  Likely vanilla negative-control resource. Current expected behavior is vanilla tooltip unless it is part of an explicitly matched parent DO request in a future design.

- White Bed, Lantern, Chain, Light Blue Carpet, Light Gray Carpet:
  Likely vanilla single-item builder resources. Current expected behavior is vanilla tooltip unless there is a top-level Architects Cutter report entry with the same requested stack, which is unlikely. If users expect these rows to explain the parent DO/Architects Cutter request they belong to, that is unsupported today.

- Glass panes, glass blocks, stained glass, framed/derived glass-like items:
  The observed correlation is plausible but not proven as glass-specific. Glass-like rows often involve variants, tag alternatives, panes vs blocks, or DO materialized outputs. Those increase the chance that MineColonies can render the resource through `BuildingBuilderResource` or `StackList.matches(...)` while our reverse lookup has only a different display stack, requested stack, or same-item fallback.

## Side-By-Side Explanation

MineColonies resource UI:

- The Resource Scroll UI renders from `BuildingResourcesModuleView.getResources()`.
- Each row is already a `BuildingBuilderResource` with stack, required amount, available amount, and delivery amount.
- Server grouping uses `stack.getDescriptionId() + "-" + stack.getComponentsPatch().hashCode()`.
- Resource availability and delivery matching use MineColonies item comparison helpers, including `ItemStackUtils.compareItemStacksIgnoreStackSize(...)`.
- `Stack` and `StackList` requests can match by MineColonies predicates, match flags, and alternatives.
- Rendering the row does not require that the item also be a top-level clipboard request.

Create-Colony-Logistics Smart Info:

- Smart Clipboard report entries are built from clipboard root requests.
- A Smart Info tooltip is attached reliably only when hovering a main request icon whose entry is already known.
- Tree child and Resource Scroll resource hovers are context-free reverse lookups from a hovered `ItemStack` back to `report.entries()`.
- The reverse lookup compares against `entry.requestedStack()` only, with a same-item fallback.
- It does not preserve enough MineColonies requestable metadata to reproduce `Stack` / `StackList` / display fallback matching for arbitrary child or resource rows.

## Most Likely Root Cause

High confidence: missing Smart Info is primarily a matching and fallback coverage issue, not a tooltip-rendering issue.

The locked Smart Info renderer works when it receives a valid Architects Cutter `SmartClipboardReport.Entry`. Missing cases occur earlier, where a hovered stack outside the main request row has to rediscover a Smart Info entry without the MineColonies request context that made the row visible.

Medium confidence: single-item and glass-like correlations come from representation differences, not from item names. Single-item builder resources often appear as child material requests or direct `BuildingBuilderResource` rows rather than top-level DO report entries. Glass-like items are more likely to involve variants, panes, tags, material components, or display-stack examples.

Low confidence: grouping alone is the primary cause. MineColonies grouping and our ResourceLine list can affect the join key, but the decisive failure is that Smart Info is attached from `report.entries()` instead of from the resource/request context that produced the hovered row.

## Classification

- Data collection issue: yes. `SmartClipboardReport` does not serialize a Smart Info index or enough requestable metadata for global hover resolution.
- Matching issue: yes, primary. Reverse lookup uses `entry.requestedStack()` only, then loose same-item fallback.
- Grouping issue: partial. MineColonies resource keys and Smart Clipboard entry stacks are not joined by a stable shared key.
- Rendering issue: no. Formatting renders correctly once a matching entry is selected.
- Fallback issue: yes. MineColonies can render rows from resource-module or display-stack fallbacks that our Smart Info lookup does not model.
- Taught-filtering issue: no. Already-taught entries still carry Smart Info metadata; only teaching feedback is suppressed.

## Minimal Future Implementation Plan

Keep `buildApprovedSmartInfoTooltip(...)` unchanged.

1. Add a compact Smart Info match index to `SmartClipboardReport`.
   Store keys that point to an existing `Entry`, not duplicate full tooltip data.

2. Populate the index in `RequestAnalysisService` for every Architects Cutter entry:
   - requested stack exact item/components key
   - MineColonies resource key: `descriptionId + "-" + componentsPatch.hashCode()`
   - exact DO combo fingerprint
   - display stack keys
   - request-tree node stack keys when the parent entry relationship is known
   - `Stack.getStack()` for direct stack requests
   - `StackList.getStacks()` / `getRequestedItems()` for alternative requests, marked lower priority
   - request token and parent token, where available

3. Change `SmartClipboardScreen.smartInfoEntryForStack(...)` to use priority-ordered lookup:
   - exact resource key / exact components
   - exact combo fingerprint for DO stacks
   - explicit parent/request-token relationship
   - display-stack match
   - conservative same-item fallback only when exactly one Architects Cutter candidate exists for that item id

4. For Resource Scroll rows, prefer a method that receives the `ResourceLine` or original `BuildingBuilderResource` key instead of only `ItemStack`.

5. For request-tree rows, prefer a method that receives the node's parent `Entry` or request token when available.

## Risks Of Future Fix

- Loose same-item fallback can attach the wrong Smart Info to DO variants that share an item id but differ by components.
- Indexing every `StackList` alternative can make generic ingredients inherit Smart Info too broadly.
- Parent inheritance for vanilla child resources changes semantics. Items like Chain, Carpet, Bed, or Lantern may begin showing parent DO Smart Info only if the request graph proves the relationship.
- Serializing too many full stacks can bloat packets. Prefer compact keys and entry indexes.
- Changing lookup behavior without tests can mask real vanilla negative controls.

## Test Plan

1. Top-level DO / Architects Cutter request that already shows Smart Info:
   Hover the main request icon. Expected: Smart Info remains visually unchanged.

2. Already-taught exact combo:
   Teach an Architects Cutter recipe, refresh the Smart Clipboard, and hover the top-level request. Expected: Smart Info still appears; can-learn feedback remains suppressed when already taught.

3. Untaught exact combo:
   Use a DO output that an eligible crafter can learn. Expected: Smart Info appears on the main request icon and includes the same existing can-learn behavior.

4. Request-tree child hover:
   Expand a DO request with child material nodes. Expected after a future fix: child rows show parent Smart Info only when linked by request token or parent context. Ambiguous unrelated child rows keep vanilla tooltip.

5. Resource Scroll row hover:
   Use a linked Resource Scroll containing Polished Diorite Fence or another DO materialized output. Expected after a future fix: exact DO row resolves by resource key or components and shows Smart Info.

6. Vanilla negative controls:
   Use White Bed, Lantern, Chain, Prismarine Bricks, Light Blue Carpet, and Light Gray Carpet as ordinary builder resources. Expected: vanilla tooltip unless a verified parent DO relationship exists.

7. Glass-like variants:
   Test glass block, glass pane, stained glass, framed glass, and any DO-derived glass output. Expected: exact variant matches correctly; panes and stained variants do not borrow Smart Info from a different variant.

8. Multiple same-item DO variants:
   Put two DO outputs with the same item id but different components in active requests. Expected: no cross-attachment; ambiguous same-item fallback refuses to attach.

9. Resource Scroll delivery / warehouse parity:
   Put a row in delivery and warehouse. Expected: existing quantity behavior remains unchanged unless a separate UI-indicator task explicitly changes it.

## Resource Scroll Delivery Indicator Notes

MineColonies' Resource Scroll row callback shows a right-side indicator:

- Delivery amount takes precedence.
- If `BuildingBuilderResource.getAmountInDelivery() > 0`, it shows the in-delivery icon and amount.
- Otherwise, if the warehouse snapshot has a value for the resource key, it shows the warehouse indicator and amount.

Create-Colony-Logistics currently computes a comparable `deliveryOrWarehouseAmount()` in `ResourceLine.from(...)`, also giving delivery precedence over warehouse. The current Smart Resource Scroll rendering draws the amount as text on the right side and does not reproduce MineColonies' icon treatment.

That is a UI parity note, not the root cause of missing Smart Info. No delivery indicator changes were made in this audit.

## Locked Tooltip Confirmation

The locked Smart Info tooltip formatting was not modified. No tooltip text, ordering, colors, spacing, wrapping, language keys, or formatting logic were changed.

