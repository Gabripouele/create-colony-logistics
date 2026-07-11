# Scrolls Tab Smart Info Tooltip Audit

Date: 2026-06-02

## Scope

This audit investigated Smart Info tooltip failures for visible Domum Ornamentum / Architects Cutter resource rows in the Smart Resource Scrolls tab.

Baseline tested by the user: `create_colony_logistics-0.3.0-rc.18.jar`.

The audit stayed focused on visible Scrolls tab rows where hover tooltip behavior is wrong or incomplete. It did not investigate whether all Clipboard requests appear in the Scrolls list.

## Non-Goals

- Do not implement code patches.
- Do not modify assets.
- Do not change UI behavior.
- Do not change Smart Info tooltip formatting, text, ordering, colors, spacing, wrapping, or language keys.
- Do not change version metadata.
- Do not build a release jar.
- Do not copy anything to the release backup folder.
- Do not compare against unrelated projects.

## Scrolls Tooltip Path Inspected

Create-Colony-Logistics classes and methods inspected:

- `com.createcolonylogistics.client.SmartClipboardScreen`
  - `render(...)`
  - `renderHoveredScrollTooltip(...)`
  - `renderHoveredScrollResourceTooltip(...)`
  - `renderSmartInfoOrItemTooltip(GuiGraphics, ResourceLine, int, int)`
  - `smartInfoEntryForResource(ResourceLine)`
  - `smartInfoEntryForStack(ItemStack)`
  - `smartInfoEntryForKeys(List<String>)`
  - `smartInfoEntryForKey(String)`
  - `uniqueSmartInfoEntryForDomumOutput(ItemStack)`
  - `domumEntryMatchesStack(SmartClipboardReport.Entry, ItemStack)`
  - `uniqueSmartInfoEntryForItem(ItemStack)`
  - `buildApprovedSmartInfoTooltip(...)`
  - `ResourceScrollContent.fromClientSelectedStack(ItemStack)`
  - `ResourceLine.from(BuildingBuilderResource, Map<String, Integer>)`
- `com.createcolonylogistics.clipboard.SmartClipboardReport`
  - `buildSmartInfoIndex(List<Entry>)`
  - `addStackIndex(...)`
  - `exactStackKey(ItemStack)`
  - `resourceStackKey(ItemStack)`
  - `domumFingerprintKey(ItemStack)`
  - `treeParentStackKey(ItemStack)`
  - `isSmartInfoEntry(Entry)`
  - `Entry`
  - `SmartInfoKey`
  - `SmartInfoIndexEntry`
- `com.createcolonylogistics.clipboard.RequestAnalysisService`
  - `analyze(ServerLevel, IColony, int)`
  - `requestedStack(IRequest<?>)`
  - `inspectRequest(...)`
  - `displayStacks(IRequest<?>, ItemStack)`
  - `smartInfoKeys(...)`
  - `addRequestableStackKeys(...)`
  - `requestTree(...)`
  - `requestDisplayStack(IRequest<?>)`
- `com.createcolonylogistics.clipboard.DomumOrnamentumRequestInspector`
  - `isDomumOrnamentumStack(ItemStack)`
  - `hasArchitectsCutterMetadata(ItemStack)`
  - `isMaterializedArchitectsCutterOutput(ItemStack)`
  - `sameMaterializedDomumOutput(ItemStack, ItemStack)`
  - `exactComboFingerprint(ItemStack)`
  - `findArchitectsCutterMatch(Level, ItemStack)`

MineColonies data source inspected through current usage:

- `com.minecolonies.core.colony.buildings.moduleviews.BuildingResourcesModuleView`
  - `getResources()`
- `com.minecolonies.core.colony.buildings.utils.BuildingBuilderResource`
  - `getItemStack()`
  - `getAmount()`
  - `getAvailable()`
  - `getAmountInDelivery()`
  - `getName()`

## Exact Scrolls Tooltip Path

The Scrolls tab renders selected Resource Scroll content from client-side MineColonies builder resource data:

1. `render(...)` routes Scrolls-tab hovers to `renderHoveredScrollTooltip(...)` first, then `renderHoveredScrollResourceTooltip(...)`.
2. `renderHoveredScrollTooltip(...)` handles the stored Resource Scroll slot itself and intentionally renders the vanilla scroll tooltip.
3. `renderHoveredScrollResourceTooltip(...)` rebuilds visible rows with `buildClientResourceScrollRows(selected)`.
4. `buildClientResourceScrollRows(...)` delegates to `ResourceScrollContent.fromClientSelectedStack(selected)`.
5. `ResourceScrollContent.fromClientSelectedStack(...)` resolves the selected scroll to a `BuildingBuilder.View`, reads `BuildingResourcesModuleView.getResources().values()`, copies each `BuildingBuilderResource`, applies player/delivery amounts, sorts them, and maps each row through `ResourceLine.from(...)`.
6. A visible row hover calls `renderSmartInfoOrItemTooltip(graphics, resource, mouseX, mouseY)`.
7. That method calls `smartInfoEntryForResource(resource)`.
8. If a matching `SmartClipboardReport.Entry` is found, the locked `buildApprovedSmartInfoTooltip(...)` is used with the resource row stack. Otherwise the vanilla item tooltip is rendered.

The Scrolls tab does not pass a `SmartClipboardReport.Entry`, request token, parent token, request tree parent entry, cutter recipe id, known-by list, can-learn list, or resolver/requester context into the tooltip lookup. It passes a `ResourceLine`, which currently contains:

- a one-count copy of `BuildingBuilderResource.getItemStack()`;
- display name and amounts;
- delivery/warehouse indicator state;
- a MineColonies-style resource key from `descriptionId + "-" + componentsPatch.hashCode()`.

The Scrolls tab can access the global `SmartClipboardReport.smartInfoIndex()`, but the visible row itself is still not represented as a report entry. It must reverse-map the row stack/key back to an entry.

## Current rc18 Scrolls Lookup

`smartInfoEntryForResource(ResourceLine)` tries:

1. `stackLookupKeys(resource.stack())`
   - DO fingerprint key if the row stack is Domum Ornamentum;
   - exact item plus components patch key;
   - resource stack key.
2. `resource.resourceKey()`
   - MineColonies resource-row key.
3. `uniqueSmartInfoEntryForDomumOutput(resource.stack())`
   - only if `hasArchitectsCutterMetadata(rowStack)` is true;
   - scans report entries and uses `sameMaterializedDomumOutput(...)` against each entry's requested stack, display stacks, and request-tree node stacks;
   - returns only if exactly one entry matches.
4. `uniqueSmartInfoEntryForItem(resource.stack())`
   - only if exactly one Architects Cutter entry exists for that item id.

This is still a context-free reverse lookup. It is safer than a broad same-item match, but it can only find Smart Info if the report already contains a compatible entry or indexed key.

## Clipboard Tooltip Path Comparison

Clipboard tab main row hover uses a direct entry path:

1. `renderHoveredItemTooltip(...)` identifies the hovered report row.
2. It gets the already-known `SmartClipboardReport.Entry`.
3. It calls `displayStack(entry)`.
4. It renders `buildApprovedSmartInfoTooltip(entry, shownStack)` directly.

That path does not need to rediscover Smart Info from an arbitrary item stack. The entry already carries:

- requested stack;
- display stacks;
- DO block id;
- cutter recipe id;
- combo fingerprint;
- known-by and can-learn building context;
- request token;
- request-tree nodes;
- smart info match keys.

Request-tree hover is partly context-aware too:

1. `renderHoveredTreeItemTooltip(...)` has both the child stack and the parent `Entry`.
2. It calls `renderSmartInfoOrItemTooltip(graphics, stack, entry, ...)`.
3. `smartInfoEntryForStack(stack, parentEntry)` first tries exact keys.
4. If no exact match exists and the parent is an Architects Cutter entry, it returns the parent entry.

This explains why Clipboard Smart Info improved while Scrolls still fails: Clipboard hovers have entry context. Scroll resource rows do not.

## Smart Info Index Creation

`SmartClipboardReport.buildSmartInfoIndex(...)` indexes only report entries that pass `isSmartInfoEntry(entry)`.

For each qualifying entry it indexes:

- entry combo fingerprint;
- requested stack keys;
- display stack keys;
- request-tree node exact keys under `tree:` prefix;
- request token;
- custom `SmartInfoKey` values from `RequestAnalysisService`;
- item id fallback.

`RequestAnalysisService.smartInfoKeys(...)` adds keys for:

- requested stack;
- requested stack DO fingerprint;
- MineColonies materialized requested stack, when available;
- assembled Architects Cutter output, when recipe matching succeeds;
- display stacks;
- request-tree node stacks as tree-parent keys;
- request token;
- `Stack` / `StackList` requestable stacks and alternatives.

Important limitation: these keys are generated from Clipboard root requests and their known request graph. They are not generated from every `BuildingBuilderResource` row that appears in a linked Resource Scroll.

## Working Vs Failing Examples

Known working comparison:

- Stripped Dark Oak Wood Stairs / Vanilla Stairs Compat:
  This likely works because the visible Scrolls row stack can be joined to a report entry by at least one existing path: exact stack/components, DO fingerprint, materialized-output comparison, display stack, request-tree stack, or unique item fallback. Once the entry is found, the locked tooltip shows Smart Info and building context.

Visible failing examples:

- Stripped Dark Oak Wood Trapdoor
- Round Stripped Dark Oak Wood Pillar
- Light Blue Brick Extra Shingles
- Framed Sea Lantern
- Glass Framed Pane

For these rows, the row itself is visible, so MineColonies has a valid `BuildingBuilderResource` stack. The failure is not row rendering. The failure is that `smartInfoEntryForResource(...)` cannot identify one unambiguous Smart Info entry for that row.

Most likely per-example behavior:

- DO stack detection likely succeeds if the item namespace is `domum_ornamentum`.
- `hasArchitectsCutterMetadata(...)` likely succeeds for materialized framed/single-shape rows that carry textured block and material texture data.
- `exactComboFingerprint(...)` can be computed for any non-empty DO row stack.
- A resource-row key can be computed from `descriptionId + componentsPatch.hashCode()`.
- The Smart Info index may still lack a matching key because it is populated from `SmartClipboardReport.Entry` data, not directly from Resource Scroll rows.
- The rc18 semantic match can still miss when the row stack and entry stack differ by shape item id, component layout, material component count, display-stack fallback, or parent/child request relationship.
- Same-item fallback can be blocked correctly when more than one Architects Cutter candidate shares the same item id.

## Confirmed Failure Point

The confirmed failure point is the Scrolls tab reverse lookup:

`renderHoveredScrollResourceTooltip(...)` -> `renderSmartInfoOrItemTooltip(ResourceLine)` -> `smartInfoEntryForResource(ResourceLine)`

The locked tooltip renderer is not the problem. If `smartInfoEntryForResource(...)` returns an entry, `buildApprovedSmartInfoTooltip(...)` renders the same approved Smart Info format used by Clipboard.

## Root-Cause Theory

High confidence: this is a missing context and matching/index coverage issue in the Scrolls tab, not a tooltip formatting or tooltip routing issue.

The Scrolls row is built from `BuildingResourcesModuleView` / `BuildingBuilderResource`, while Smart Info metadata is built from `SmartClipboardReport.Entry` objects derived from Clipboard root requests. A visible Scroll row therefore has MineColonies resource-module authority, but not the request entry identity that Clipboard hovers have.

Medium-high confidence: some shapes fail because rc18 still matches by keys or by a strict semantic DO comparison that requires the row and candidate stacks to describe the same materialized output closely enough. Trapdoors, pillars, shingles, framed panes, and framed lantern-like outputs are likely to expose alternate shape/result/display representations. If the report entry uses a parent request, display fallback, assembled output, or request-tree stack that does not compare as the same materialized output, the Scroll row falls back to vanilla tooltip.

Medium confidence: this is also an index population issue. The current Smart Info index is entry-centered. It does not serialize compact lookup keys derived from the actual visible Resource Scroll rows or from a server-side join between builder resources and requests.

Low confidence: this is a pure shape/category bug. Shape categories appear correlated because they tend to have more alternate representations, but the same underlying issue is the lack of stable context between resource row and report entry.

## Specific Questions Answered

- Is this a Scrolls tab bare-stack lookup problem?
  Yes, mostly. rc18 now passes a `ResourceLine`, not only a bare `ItemStack`, but the lookup is still effectively row-stack/resource-key to report-entry reverse matching.

- Is this a missing `ResourceLine` key/context problem?
  Yes. `ResourceLine` carries the row stack and MineColonies resource key, but not request token, parent token, entry index, recipe id, DO shape key, server-verified match keys, or parent Smart Info context.

- Is this a missing DO fingerprint problem?
  Partly. The row DO fingerprint is computed client-side, but it only helps when the report index has the same fingerprint. If row and entry use different component representations for the same visible output, the fingerprint will not join them.

- Is this an index population problem?
  Yes. The index is populated from report entries, display stacks, request-tree nodes, and requestable stacks. It is not populated from actual Scrolls tab `BuildingBuilderResource` rows.

- Is this a shape/category matching problem that only appears in Scrolls rows?
  Likely correlated, not exclusive. Single-shape/framed/pane/trapdoor/lantern/pillar/shingle rows are more likely to differ between resource-row stack and report-entry stack.

- Is this because Scrolls rows are built from `BuildingResourcesModuleView` / `BuildingBuilderResource` instead of `SmartClipboardReport.Entry`?
  Yes. This is the central architectural difference from the working Clipboard path.

## Recommended Future Implementation Plan

Do not change `buildApprovedSmartInfoTooltip(...)`.

Recommended fix:

1. Add compact, server-built Smart Info match keys for visible Resource Scroll rows or for the same MineColonies builder resources that become visible rows.
   - Include exact components key.
   - Include MineColonies resource key.
   - Include DO fingerprint.
   - Include recipe id or assembled-output key when available.
   - Include request token / parent token when the builder resource can be tied to a real request.

2. Change Scrolls hover to call a context-aware lookup method.
   - Input should be `ResourceLine` plus its compact lookup keys.
   - Prefer exact resource key and exact components.
   - Then try DO fingerprint / recipe identity.
   - Then try explicit request-token or parent-entry relationship.
   - Then try semantic DO materialized-output match.
   - Use same-item fallback only when exactly one Architects Cutter candidate exists.

3. Preserve vanilla tooltip behavior.
   - If the row is not DO metadata-bearing and has no explicit Smart Info key, render vanilla tooltip.
   - If multiple Smart Info candidates match at the same fallback priority, render vanilla tooltip rather than guessing.

4. Avoid Clipboard regression.
   - Leave `renderHoveredItemTooltip(...)` direct-entry path unchanged.
   - Leave request-tree parent-entry fallback unchanged unless separately tested.
   - Keep all new Scrolls matching isolated to the Scrolls resource-row path.

## Risks Of Future Fix

- Over-indexing generic `StackList` alternatives can attach parent Smart Info to unrelated ingredient rows.
- Same-item fallback can cross-match variants with the same DO item id but different materials or shapes.
- Parent inheritance can surprise users if vanilla-looking child resources begin showing parent DO Smart Info without clear request-token proof.
- Serializing full stacks for every resource row can grow packet size. Prefer compact keys.
- Shape/category-specific matching can become brittle if Domum Ornamentum changes component representation.

## Future Validation Plan

In-game cases:

1. Clipboard main row for an already working DO request.
   Expected: Smart Info remains unchanged.

2. Scrolls row for Stripped Dark Oak Wood Stairs / Vanilla Stairs Compat.
   Expected: Smart Info and building context still appear.

3. Scrolls rows for Stripped Dark Oak Wood Trapdoor and Round Stripped Dark Oak Wood Pillar.
   Expected after future fix: Smart Info appears only when the row joins to a unique DO/Architects Cutter entry.

4. Scrolls rows for Light Blue Brick Extra Shingles, Framed Sea Lantern, and Glass Framed Pane.
   Expected after future fix: exact shape/material row joins correctly; no wrong cross-attachment to another framed variant.

5. Multiple variants sharing an item id.
   Expected: ambiguous same-item fallback refuses to attach Smart Info.

6. Vanilla negative controls.
   Expected: normal item tooltip unless there is an explicit request-token or parent-entry relationship.

7. Scroll slot hover.
   Expected: stored Resource Scroll item still uses vanilla scroll tooltip.

## Validation

No Gradle build was run. This was an audit-only pass with no code, asset, version, release-jar, or backup-folder changes. A build was not needed to validate a documentation-only report.

## No-Change Confirmation

No implementation code was changed in this audit.
No assets were changed.
No UI behavior was changed.
No Smart Info tooltip formatting was changed.
No tooltip text, order, colors, spacing, wrapping, or language keys were changed.
No version metadata was changed.
No release jar was built.
No backup copy was created.
