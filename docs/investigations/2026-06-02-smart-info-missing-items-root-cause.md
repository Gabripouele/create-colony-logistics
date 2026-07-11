# Smart Info Missing Items Root-Cause Audit

Date: 2026-06-02

## Scope

This audit investigated why some items visible in MineColonies' Resource Scroll / clipboard resource UI do not receive the Create-Colony-Logistics Smart Info tooltip overlay in Smart Resource Scrolls / Smart Colony Clipboard.

No gameplay, UI, tooltip, language, or version code was changed in this pass.

## MineColonies Classes And Methods Inspected

- `com.minecolonies.core.client.gui.WindowResourceList`
  - Constructor `WindowResourceList(BuildingBuilder.View, Map<String, Integer>)`
  - `onOpened()`
  - `onUpdate()`
  - `pullResourcesFromHut()`
  - `addDeliveryRequestsToList(List<Delivery>, ImmutableCollection<IToken<?>>)`
  - row update callback registered from `onOpened()`
- `com.minecolonies.core.colony.buildings.moduleviews.BuildingResourcesModuleView`
  - `deserialize(RegistryFriendlyByteBuf)`
  - `getResources()`
  - `getProgress()`
- `com.minecolonies.core.colony.buildings.modules.BuildingResourcesModule`
  - `serializeToView(RegistryFriendlyByteBuf)`
  - `updateAvailableResources()`
  - `getNeededResources()`
  - `getRequiredResources()`
  - `getResourceFromIdentifier(String)`
  - `addNeededResource(ItemStack, int)`
  - `hasResourceInBucket(ItemStack)`
  - `checkOrRequestBucket(BuilderBucket, ICitizenData)`
- `com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource`
  - Constructors
  - `getItemStack()`
  - `getAmount()`
  - `getAvailable()`
  - `getAmountInDelivery()`
  - `getMissingFromPlayer()`
  - `getAvailabilityStatus()`
  - `getName()`
- `com.minecolonies.api.crafting.ItemStorage`
  - Constructors
  - `getItemStack()`
  - `getAmount()`
  - `equals(Object)`
  - `matchDefinitionEquals(ItemStorage)`
  - `getItemStackOfListMatchingPredicate(List<ItemStorage>, Predicate<ItemStack>)`
- `com.minecolonies.api.util.ItemStackUtils`
  - `compareItemStacksIgnoreStackSize(ItemStack, ItemStack)`
  - `compareItemStacksIgnoreStackSize(ItemStack, ItemStack, boolean, boolean)`
  - `compareItemStacksIgnoreStackSize(ItemStack, ItemStack, boolean, boolean, boolean, boolean)`
  - `compareItemStackListIgnoreStackSize(List<ItemStack>, ItemStack, boolean, boolean)`
  - `CHECKED_NBT_KEYS`
- `com.minecolonies.api.colony.requestsystem.request.IRequest`
  - `getRequest()`
  - `getDisplayStacks()`
  - `getChildren()`
  - `getRequestOfType(Class<T>)`
- `com.minecolonies.core.colony.requestsystem.requests.AbstractRequest`
  - `getDisplayStacks()`
- `com.minecolonies.api.colony.requestsystem.requestable.IDeliverable`
  - `matches(ItemStack)`
  - `getCount()`
  - `getMinimumCount()`
  - `getResult()`
- `com.minecolonies.api.colony.requestsystem.requestable.Stack`
  - `matches(ItemStack)`
  - `getStack()`
  - `getRequestedItems()`
  - `getCount()`
  - `getMinimumCount()`
  - `getResult()`
- `com.minecolonies.api.colony.requestsystem.requestable.StackList`
  - `matches(ItemStack)`
  - `getStacks()`
  - `getRequestedItems()`
  - `getCount()`
  - `getMinimumCount()`
  - `getResult()`

## Create-Colony-Logistics Classes And Methods Inspected

- `com.createcolonylogistics.clipboard.RequestAnalysisService`
  - `analyze(ServerLevel, IColony, int)`
  - `clipboardRootRequests(IColony)`
  - `addAssignedRootRequests(IRequestManager, Collection<IToken<?>>, Map<String, IRequest<?>>)`
  - `openRequestsForBuilding(IColony, IBuilding)`
  - `requestedStack(IRequest<?>)`
  - `stackBasedTask(IRequest<?>)`
  - `stackBasedTaskStack(IRequest<?>)`
  - `inspectRequest(...)`
  - `displayStacks(IRequest<?>, ItemStack)`
  - `warehouseStock(IColony, ItemStack)`
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
- `com.createcolonylogistics.client.SmartClipboardScreen`
  - `renderSelectedScroll(...)`
  - `renderHoveredScrollResourceTooltip(...)`
  - `renderHoveredTreeItemTooltip(...)`
  - `renderSmartInfoOrItemTooltip(...)`
  - `smartInfoEntryForStack(ItemStack)`
  - `buildApprovedSmartInfoTooltip(...)`
  - `isArchitectsCutterEntry(...)`
  - `ResourceScrollContent.fromClientSelectedStack(ItemStack)`
  - `ResourceScrollContent.applyPlayerAndDeliveryAmounts(...)`
  - `ResourceScrollContent.deliveryRequests(...)`
  - `ResourceScrollContent.addDeliveryRequests(...)`
  - `ResourceLine.from(BuildingBuilderResource, Map<String, Integer>)`
  - `ResourceLine.warehouseSnapshotKey(...)`
- `com.createcolonylogistics.clipboard.DomumOrnamentumRequestInspector`
  - `materializedRequestedStack(IRequest<?>)`
  - `isDomumOrnamentumStack(ItemStack)`
  - `findArchitectsCutterMatch(Level, ItemStack)`
  - `isMaterializedArchitectsCutterOutput(ItemStack)`
  - `exactComboFingerprint(ItemStack)`
- `com.createcolonylogistics.clipboard.SmartClipboardScrollStorage`
  - `read(ItemStack)`
  - `insertResourceScroll(...)`
  - `sanitizedScrollStack(ItemStack, ScrollLinkSnapshot)`
  - `ScrollLinkSnapshot.from(ItemStack)`

## Side-By-Side Data Path

### MineColonies Resource Scroll / Resource UI

MineColonies' `WindowResourceList` renders from the builder view, not from a tooltip-side request lookup.

1. `WindowResourceList.onOpened()` calls `pullResourcesFromHut()` and registers row callbacks.
2. `pullResourcesFromHut()` gets `BuildingResourcesModuleView` from `BuildingBuilder.View.getModuleViewByType(...)`.
3. It copies `module.getResources().values()` into the window's resource list.
4. `BuildingResourcesModuleView.deserialize(...)` receives `ItemStack`, available amount, and required amount from the server and stores each row as a `BuildingBuilderResource`.
5. Server-side `BuildingResourcesModule.serializeToView(...)` calls `updateAvailableResources()` and serializes `neededResources`.
6. `BuildingResourcesModule.addNeededResource(ItemStack, int)` groups rows by `stack.getDescriptionId() + "-" + stack.getComponentsPatch().hashCode()`.
7. `updateAvailableResources()` computes supplied/available amounts by scanning the assigned builder citizen inventory, builder hut inventory, and external work stations with `ItemStackUtils.compareItemStacksIgnoreStackSize(stack, resourceStack, true, true)`.
8. `WindowResourceList.pullResourcesFromHut()` computes player inventory amount and incoming delivery amount. Delivery matching uses `ItemStackUtils.compareItemStacksIgnoreStackSize(resourceStack, deliveryStack, false, true)`.
9. The right-side indicator is rendered in the row callback:
   - If `BuildingBuilderResource.getAmountInDelivery() > 0`, MineColonies shows `indeliveryicon` and `indeliveryamount`.
   - Else if the `warehouseSnapshot` lookup has a value for `resource.getItem().getDescriptionId() + "-" + resource.getItemStack().getComponentsPatch().hashCode()`, MineColonies shows `inWarehouseIcon` and writes that amount.

MineColonies therefore treats the builder resource row itself as authoritative. It does not require that the same item also be discoverable as a separate top-level clipboard request.

### Create-Colony-Logistics Smart Clipboard / Smart Resource Scrolls

The Smart Resource Scroll list renders rows from the same MineColonies client resource module, but Smart Info is attached by a separate overlay lookup.

1. `SmartClipboardScreen.ResourceScrollContent.fromClientSelectedStack(...)` resolves the selected Resource Scroll's `BuildingBuilder.View`.
2. It gets `BuildingResourcesModuleView` and copies each `BuildingBuilderResource`.
3. `applyPlayerAndDeliveryAmounts(...)` mirrors MineColonies' player and delivery overlay logic.
4. `ResourceLine.from(...)` mirrors MineColonies' `descriptionId + componentsPatch.hashCode()` warehouse snapshot key and renders required/available/missing/incoming/warehouse amounts.
5. When hovering a resource icon, `renderHoveredScrollResourceTooltip(...)` calls `renderSmartInfoOrItemTooltip(...)`.
6. `renderSmartInfoOrItemTooltip(...)` calls `smartInfoEntryForStack(stack)`.
7. `smartInfoEntryForStack(...)` searches only `report.entries()`, first by `ItemStack.isSameItemSameComponents(entry.requestedStack(), stack)`, then by same item id, and only for entries where `isArchitectsCutterEntry(entry)` is true.
8. `report.entries()` comes from `RequestAnalysisService.analyze(...)`, not from the builder resource module.
9. `RequestAnalysisService.analyze(...)` only creates a report row when `requestedStack(request)` returns a stack for a root request from `clipboardRootRequests(colony)`.

This means the Smart Resource Scroll row can be correct while Smart Info is absent. The row is MineColonies builder-module data; the Smart Info overlay is a best-effort match to a separate Smart Clipboard report entry.

## Where Successful Items Succeed

Items that show Smart Info succeed when all of these are true:

- The builder resource row stack is also represented in `SmartClipboardReport.entries()`.
- `RequestAnalysisService.requestedStack(...)` resolves the request to a Domum Ornamentum / Architects Cutter output stack.
- `DomumOrnamentumRequestInspector.findArchitectsCutterMatch(...)` or `isMaterializedArchitectsCutterOutput(...)` marks that report entry as an Architects Cutter entry.
- `SmartClipboardScreen.smartInfoEntryForStack(...)` can match the hovered resource row to that report entry by exact item/components or same item id fallback.

## Where Failing Items Lose Smart Info

The failure point is `SmartClipboardScreen.smartInfoEntryForStack(ItemStack)`.

That method can only attach Smart Info to a Resource Scroll row if the hovered `ResourceLine.stack()` can be matched to an existing Architects Cutter `SmartClipboardReport.Entry`. It does not inspect the builder resource module row itself, the request graph for that resource row, the MineColonies `BuildingBuilderResource`, or the underlying `Stack` / `StackList` predicate that MineColonies uses.

A second contributing failure point is `RequestAnalysisService.requestedStack(IRequest<?>)`. It resolves:

- `IStackBasedTask.getTaskStack()`
- `IDeliverable.getResult()`, if non-empty
- the first `request.getDisplayStacks()` entry

It does not explicitly preserve `Stack.getStack()`, `StackList.getStacks()`, `StackList.getRequestedItems()`, `StackList.matches(...)`, `Stack.matchNBT()`, `Stack.matchDamage()`, `StackList.matchOreDic`, or MineColonies' predicate/list matching semantics as a first-class match key. For `StackList`, `AbstractRequest.getDisplayStacks()` can produce examples by scanning the compatibility manager's global item list through `IDeliverable.matches(...)`; that is a renderable fallback, not necessarily the same exact stack/components used by the builder resource row.

## Most Likely Root Cause

The most likely root cause is a fallback/matching issue between two different authoritative data sources:

- MineColonies displays required resources from `BuildingResourcesModuleView.getResources()` / `BuildingBuilderResource`.
- Create-Colony-Logistics displays the same rows, but attaches Smart Info only by finding a matching Architects Cutter entry in `SmartClipboardReport.entries()`.

Rows that MineColonies can display from the builder resource module can lack Smart Info when the matching request is absent from `report.entries()`, represented as a child request rather than a root report entry, represented as `StackList` / tag alternatives, represented by a display-stack example rather than the exact builder resource stack, or represented with components that do not match the row stack.

This is not primarily a text formatting or tooltip-rendering bug. The locked tooltip renderer works when it receives a matching Smart Info entry. The missing cases fail before formatting, in the lookup that decides whether a Smart Info entry exists.

## Investigation Checklist Results

- Single-item requests using a different request type or wrapper:
  Likely relevant. MineColonies builder requests use `com.minecolonies.api.colony.requestsystem.requestable.Stack` for bucket resource requests. Single-item builder resources can be visible in the resource module while not appearing as separate Smart Clipboard root rows, especially when they are child material requests.

- Requests where the display stack differs from the matching stack:
  Relevant. `AbstractRequest.getDisplayStacks()` builds examples by filtering all known items through `IDeliverable.matches(...)`. For `StackList` this can choose an example stack from alternatives/tags. Our Smart Info overlay later tries to match the hovered builder row against `entry.requestedStack()`, which may be a fallback display example or `IDeliverable.getResult()`.

- ItemStack identity mismatch caused by components, NBT, tags, variants, or damage:
  Relevant. MineColonies resource grouping uses `descriptionId + componentsPatch.hashCode()`. MineColonies matching uses `ItemStackUtils.compareItemStacksIgnoreStackSize(...)`, which can ignore all components unless checked NBT keys are registered, or can compare only selected data components. Our Smart Info lookup first uses strict `ItemStack.isSameItemSameComponents(...)`, then falls back to same item id only for Architects Cutter entries. That fallback can over-match same-item variants, while the strict path can miss legitimate MineColonies predicate matches.

- ItemStorage / IRequest / IToken / predicate mismatch:
  Relevant. MineColonies `ItemStorage.equals(...)`, `Stack.matches(...)`, and `StackList.matches(...)` use MineColonies matching predicates. Our overlay does not keep the original `IRequest`, `IToken`, `ItemStorage`, match flags, or predicate relationship for the resource row.

- Requests that resolve through an alternative item list instead of a direct item:
  Relevant. `StackList` supports alternative stacks and tag overlap (`matchOreDic` path). `AbstractRequest.getDisplayStacks()` also derives examples by testing all compatibility-manager items against `IDeliverable.matches(...)`.

- Grouping or deduplication collapsing entries before Smart Info is attached:
  Probably not the primary issue in our renderer. The Smart Resource Scroll list does not collapse `BuildingBuilderResource` rows beyond MineColonies' own module grouping. However, MineColonies groups required resources by `descriptionId + componentsPatch.hashCode()`, while the Smart Info overlay searches a separate request-entry list; those two groupings are not joined by a stable key.

- Glass panes, glass blocks, stained glass, or framed/derived glass items:
  Plausibly correlated, not proven as a glass-specific cause. Glass-like items often appear in builder resources as variants, panes, framed/derived blocks, or alternative material candidates. That increases the chance that MineColonies has a valid `BuildingBuilderResource` row while our overlay has only a display fallback, a same-item fallback, or no matching Architects Cutter entry.

- MineColonies displaying a renderable fallback while our code requires a stricter match:
  Yes. MineColonies can render from `BuildingBuilderResource` or `getDisplayStacks()` examples. Our Smart Info requires a matching Architects Cutter report entry.

## Representative Examples

The screenshot examples should be treated as resource-row cases, not as proof that the item name itself is special.

- White Bed
- Lantern
- Mossy Stone Brick Wall
- Polished Diorite Fence
- Prismarine Bricks
- Chain
- Light Blue Carpet
- Light Gray Carpet

For these and any similar row, MineColonies can render the required resource because the builder module already contains a `BuildingBuilderResource` with stack, required amount, and available amount. Smart Info is missing when that exact resource row is not joined back to a Smart Clipboard Architects Cutter report entry.

The examples that are vanilla items are especially useful negative controls: if they are not Domum Ornamentum / Architects Cutter outputs, the current code intentionally falls back to the vanilla item tooltip because `isArchitectsCutterEntry(...)` is false. If the expectation is that vanilla ingredient rows should inherit Smart Info from a parent Domum Ornamentum request, the current implementation does not do that. It only finds Smart Info for the hovered stack itself.

## Classification

This is primarily a matching/fallback issue.

Secondary classifications:

- Data collection issue: yes, because `RequestAnalysisService` does not preserve enough MineColonies requestable metadata (`Stack`, `StackList`, alternatives, match flags, parent/child linkage) to later match resource rows reliably.
- Grouping issue: partial, because MineColonies resource grouping uses a module key while our overlay uses report-entry requested stacks.
- Rendering issue: no. Rendering displays Smart Info correctly once a matching entry is found.
- Tooltip formatting issue: no. The locked Smart Info formatter was not involved and was not modified.

## Proposed Minimal Fix

Do not change Smart Info tooltip formatting.

Minimal behavioral fix:

1. Add a server-side Smart Info index to `SmartClipboardReport` that records Architects Cutter Smart Info candidates by stable resource-row match keys:
   - exact MineColonies resource key: `stack.getDescriptionId() + "-" + stack.getComponentsPatch().hashCode()`
   - registry item id
   - request token and parent token, where available
   - display stack keys and requested stack keys
2. In `RequestAnalysisService`, when building an Architects Cutter entry, index all relevant stacks:
   - `entry.requestedStack()`
   - every `entry.displayStacks()`
   - `request.getRequest()` if it is `Stack`: `Stack.getStack()`
   - `request.getRequest()` if it is `StackList`: every `StackList.getStacks()` / `getRequestedItems()`
   - request-tree child stacks for materialized dependency rows
3. In `SmartClipboardScreen.smartInfoEntryForStack(...)`, look up the hovered Resource Scroll row first by the exact MineColonies resource key, then by exact item/components, then by conservative same-item fallback only when the candidate set has exactly one Architects Cutter entry for that item.

This keeps the approved tooltip text, ordering, colors, spacing, and wrapping exactly as-is. It only changes how the existing tooltip data is selected.

## Risks

- Same-item fallback can attach the wrong Smart Info to variants if more than one Architects Cutter output shares an item id with different components. The fix should avoid that by using same-item fallback only for a unique candidate.
- Indexing `StackList` alternatives can over-associate a generic ingredient row with a parent Smart Info entry. The implementation should prefer exact resource keys and exact components before alternatives.
- Adding request-tree or parent inheritance for vanilla ingredient rows changes user expectations: vanilla rows like bed, lantern, chain, or carpet may start showing Smart Info inherited from a parent Domum Ornamentum request. That should be intentional and tested.
- Server report size may grow if every alternative stack is serialized. Keep the index compact by serializing keys, not full duplicate `ItemStack`s, where possible.

## In-Game Test Plan

Use a linked Smart Colony Clipboard with one or more stored MineColonies Resource Scrolls. For each case, compare the MineColonies Resource Scroll window against Smart Resource Scrolls.

1. Baseline Architects Cutter output that already shows Smart Info.
   - Hover the same item in Smart Resource Scrolls.
   - Expected: existing Smart Info tooltip remains byte-for-byte visually unchanged.

2. Single-item builder request.
   - Use a builder work order requiring one of: White Bed, Lantern, Chain, Light Blue Carpet, Light Gray Carpet.
   - Expected before fix: row renders, but Smart Info only appears if there is a matching Architects Cutter report entry.
   - Expected after fix if parent inheritance is intended: row can resolve to the correct parent Smart Info when the request graph proves the relationship.

3. Alternative / tag-like request.
   - Use a work order requiring glass block, glass pane, stained glass, or a variant accepted through `StackList`.
   - Expected: Smart Info lookup follows MineColonies match semantics without over-matching a different same-item variant.

4. Domum Ornamentum / Architects Cutter derived resource.
   - Use Polished Diorite Fence or another framed/derived DO output.
   - Expected: resource row matches by exact `descriptionId + componentsPatch.hashCode()` or exact item/components, not by loose item id unless unique.

5. Vanilla non-DO negative controls.
   - Use Prismarine Bricks, Mossy Stone Brick Wall, Chain, carpets.
   - Expected: if no parent DO Smart Info relationship exists, only vanilla tooltip appears. If parent inheritance is implemented, Smart Info appears only when the row is part of that parent request graph.

6. Multiple variants of the same DO item id.
   - Put two materialized DO variants with different components in the same active build/request set.
   - Expected: no wrong Smart Info cross-attachment; ambiguous same-item fallback should not attach.

7. Delivery / warehouse indicator parity.
   - Put matching item in delivery and in warehouse snapshot.
   - Expected: right-side amount indicator remains in parity with MineColonies; delivery amount takes precedence over warehouse amount.

## Locked Tooltip Confirmation

The locked Smart Info tooltip formatting was not modified in this audit. No tooltip text, ordering, colors, spacing, wrapping, language keys, or formatting logic were changed.

