# Smart Info Tooltip Source Selection Rendering Audit

Date: 2026-06-02

## Scope

This audit investigated why visible Smart Info tooltips can differ from the rc.27 runtime recipe diagnostic output.

Current runtime diagnostic baseline supplied by the user:

- `DomumOrnamentumRequestInspector.findArchitectsCutterMatch(...)` is producing correct materialized recipe data for tested Domum Ornamentum / Architects Cutter items.
- `SmartInfoClassificationService` diagnostics appear correct for uploaded examples:
  - material is wood when the visible item is wood;
  - category resolves to Sawmill;
  - Stonemason is false for wood examples;
  - many examples return `knownBy: Sawmill`;
  - `canLearn` can be empty when the recipe is already known.
- Remaining visible tooltip issues include Shape-only Panel tooltips and stale or incorrect `Can learn` values such as Stonemason on wood DO stairs.

This audit focused only on final tooltip entry source selection and rendering.

## Non-Goals

- No implementation patches.
- No permanent debug code.
- No asset changes.
- No UI behavior changes.
- No Smart Info tooltip formatting, text, order, colors, spacing, wrapping, or language-key changes.
- No requester/resolver, accordion, delivery arrow, scrolling, tabs, slots, sorting, or filtering changes.
- No recipe-generation, material-reconstruction, module-validation, or generic fallback architecture changes.
- No version bump.
- No Gradle build.
- No release jar.
- No backup copy.

## Hover-To-Tooltip Path Map

| Hover path | Input to resolver | Resolver method | First selected source that can win | Tooltip stack shown |
| --- | --- | --- | --- | --- |
| Clipboard main row item icon | The row's `SmartClipboardReport.Entry` | `SmartInfoResolver.resolveEntry(entry)` | Direct row entry if `SmartClipboardReport.isSmartInfoEntry(entry)` is true; otherwise production fallback for `entry.requestedStack()` | `displayStack(entry)` |
| Request-tree item icon | Child node stack plus parent row entry | `resolveStack(stack, parentEntry)` | Exact index match; otherwise parent entry if parent is Smart Info; otherwise unique item match; otherwise production fallback | Hovered node stack |
| Resource Scroll resource row | `ResourceLine.stack()` plus `ResourceLine.smartInfoKeys()` | `resolveResource(ResourceLookup)` | Exact index match; semantic DO match; unique item match; otherwise production fallback | Resource row stack |

The final tooltip does not call `SmartInfoClassificationService` directly. It renders whatever `SmartInfoResolver` returns from already serialized report data.

## Resolver Candidate Source And Priority Map

`SmartClipboardReport` priorities are:

| Priority | Meaning |
| --- | --- |
| `0` | request link / request token |
| `10` | exact stack |
| `15` | resource stack |
| `20` | output / recipe-output |
| `25` | DO material |
| `30` | DO fingerprint |
| `35` | display stack |
| `40` | request-tree parent stack |
| `70` | alternative stack |
| `80` | semantic |
| `100` | item id |

Resolver order is not one single global priority queue:

1. Direct main-row entry wins immediately when it is already a Smart Info entry.
2. Indexed exact/key lookup wins before semantic, item, or production fallback.
3. Request-tree hovers can fall back to the parent entry before item or production fallback.
4. Resource-row hovers try semantic DO matching before item and production fallback.
5. Production fallback is last, but it can create an ephemeral `SmartClipboardReport.Entry`.

Within indexed and production lookups, the lowest effective priority wins. Equal-priority matches from different entries or production objects become ambiguous and return no match.

## Selected Entry Fields

The visible tooltip consumes only the selected `SmartClipboardReport.Entry`.

Relevant fields:

- Shape comes from `entry.doBlockId()` through `humanizeDomumShape(entry)`.
- Architects Cutter presence comes from `entry.cutterRecipeId()` or materialized/metadata checks.
- Known recipe context is stored as `entry.recipeKnownBy()`.
- Learnable context is stored as `entry.canLearnCombo()`.
- Taught state is stored as `entry.exactComboAlreadyTaught()`, but the tooltip rendering path does not use that field directly.

Production fallback builds an ephemeral entry with:

- `doBlockId` from the matched `ProductionInfo`;
- `cutterRecipeId` from the matched `ProductionInfo`;
- `recipeKnownBy` from filtered `ProductionInfo.knownBy`;
- `canLearnCombo` from filtered `ProductionInfo.canLearn`;
- `smartInfoKeys` copied from the matched production entry.

## KnownBy Vs CanLearn Rendering

`buildApprovedSmartInfoTooltip(...)` adds:

1. vanilla item tooltip lines;
2. blank line;
3. Smart Info header;
4. Shape line when `humanizeDomumShape(entry)` is non-blank;
5. one teaching-feedback line when `teachingFeedbackBuildings(entry)` is non-empty.

The teaching-feedback list is:

```java
entry.canLearnCombo().isEmpty() ? entry.recipeKnownBy() : entry.canLearnCombo()
```

Answers:

1. Yes, `knownBy` is displayed, but only indirectly as the fallback value when `canLearnCombo` is empty.
2. If an entry has `knownBy: Sawmill` and `canLearn: []`, the current tooltip should show the existing `Can learn` line with `Sawmill`.
3. The tooltip is not designed to ignore `knownBy`; however, it uses the same `Can learn` label for both `canLearnCombo` and `recipeKnownBy`.
4. A Panel tooltip showing Shape only is not explained by `knownBy` being ignored. It means the final selected tooltip entry has empty `canLearnCombo` and empty `recipeKnownBy`, or the tooltip is selecting a Shape-only fallback entry instead of an exact known-by entry.
5. No separate visible known/produced-by label is currently used in the locked tooltip. A later display-model change would be needed if known recipes should use distinct text, but this audit made no tooltip changes.

## Report Data Vs Diagnostic Data

The rc.27 diagnostic path and the normal tooltip path are different:

| Path | Source |
| --- | --- |
| Ctrl+F9 diagnostic | Sends hovered stack to server and computes fresh `SmartInfoClassificationService.classify(...)` output live. |
| Visible tooltip | Uses client-side `SmartClipboardReport` already received from the server, then resolver/index/production fallback selection. |

This is the key divergence.

The diagnostic can be correct while the tooltip is wrong if:

- the report was generated before the correct classification data existed;
- the report is stale on the client;
- exact classification exists only in the live diagnostic and was never serialized into report entries or production index;
- the exact production entry exists in `productionIndex` but does not match the hovered row's resolver keys;
- a generic/stale production entry matches earlier or more cleanly than the exact entry;
- a direct/indexed/parent entry wins before production fallback and contains older or weaker `knownBy` / `canLearnCombo`.

## Panel Shape-Only Analysis

For Panel rows that visually show:

```text
Smart Info
Shape: Panel
```

but no teaching line, the selected entry almost certainly has:

- usable `doBlockId` or recipe id context;
- empty `recipeKnownBy`;
- empty `canLearnCombo`.

If the rc.27 diagnostic for the same hovered Panel reports `knownBy: Sawmill`, then the visible tooltip is not rendering that exact live classification result.

Most likely source paths:

1. Resource-row exact classification exists only in the diagnostic and not in serialized report data for that row.
2. Exact classification exists in `productionIndex`, but `resolveResource(...)` does not match it by the row's keys.
3. Production fallback selects a generic Shape-only DO production entry after exact/index/semantic/item lookup fail.
4. A direct or parent entry wins before the exact production entry and has only shape metadata.

Because `teachingFeedbackBuildings(...)` falls back to `recipeKnownBy`, Panel Shape-only is not a rendering omission of known-by. It is a selected-entry/source-selection or report-serialization mismatch.

## Wood Stairs Stonemason Analysis

The tooltip can display Stonemason only if the final selected `SmartClipboardReport.Entry` supplies Stonemason through either:

- `entry.canLearnCombo()`, or
- `entry.recipeKnownBy()` when `canLearnCombo()` is empty.

There is no independent tooltip-side Stonemason calculation.

If the rc.27 diagnostic for the same wood stair says Sawmill and Stonemason false, but the visible tooltip says Stonemason, then one of these is happening:

1. The tooltip selected an old report entry generated from stale classifier data.
2. The tooltip selected a generic or stale production fallback entry.
3. The tooltip selected a parent/request-tree entry whose production fields differ from the hovered child stack.
4. The tooltip selected an item-id or indexed match from another same-item DO variant.
5. The client screen is still rendering a report generated before the diagnostic-correct classification was serialized.

High-confidence conclusion: if Stonemason appears visibly, the selected entry contains Stonemason. The tooltip formatter is not inventing it.

## Generic Or Stale Entries Outranking Exact Entries

Static source inspection shows exact classifier entries can fail to become the selected tooltip entry even when live classification is correct:

- `SmartClipboardReport.smartInfoIndex` indexes only `entries`, not every exact `productionIndex` classification.
- Exact Resource Scroll classifications are serialized as `ProductionInfo`, not as report `Entry` rows.
- Main row direct entries win immediately if admitted as Smart Info entries, even if their fields are older or weaker than a fresher production classification.
- Request-tree hovers can return the parent entry before production fallback.
- Production fallback can only use serialized production entries and resolver keys; it does not recompute classification.
- `resolveProduction(...)` can reject equal-priority competing production entries as ambiguous.

For DO production fallback, broad generic `knownBy` / `canLearn` is filtered unless `isExactDomumProductionMatch(...)` passes. That guard reduces recipe-id-only leakage, but it does not prove that the selected entry is the exact classifier result from the diagnostic.

## Confirmed Divergence Point

Confirmed from source:

The visible tooltip renders a selected serialized `SmartClipboardReport.Entry`; the Ctrl+F9 diagnostic computes fresh live classification for the hovered stack.

Therefore, when diagnostic output is correct but the tooltip is wrong, the divergence is after recipe generation and classification, in one of:

1. report serialization/integration of exact classification results;
2. resolver source selection;
3. stale report or cached production/classification data;
4. direct/parent/item/generic production entry winning over the exact classifier-backed source.

The divergence is not in `buildApprovedSmartInfoTooltip(...)` except for the existing design choice that known-by and can-learn share the same visible label.

## Recommended Next Step

Do not patch recipe generation.

Add temporary diagnostic output for final tooltip source selection, guarded behind the existing debug action or a similarly intentional debug trigger. The dump should record, for the exact hovered tooltip:

- hover path: main row, request-tree, or Resource Scroll row;
- resolver method called;
- input stack exact key, resource key, material key, fingerprint, and recipe-output key;
- every candidate source considered:
  - direct entry;
  - indexed key match;
  - semantic DO match;
  - parent entry;
  - item-id fallback;
  - production fallback;
- candidate priority and matched key;
- candidate `doBlockId`, recipe id, `recipeKnownBy`, `canLearnCombo`, `exactComboAlreadyTaught`;
- selected entry source;
- final teaching list returned by `teachingFeedbackBuildings(...)`;
- whether a fresh live classifier result for the same stack differs from the selected serialized entry.

Only after that evidence should a fix choose between report serialization, resolver priority, stale cache invalidation, or tooltip display-model changes.

## Risks

- Changing resolver priority without source evidence can reintroduce broad generic DO `Can learn` lists.
- Displaying a distinct known-by label would alter locked tooltip text/formatting and must be treated as a separate UI change.
- Treating live diagnostics as the same as report data can mask serialization bugs.
- Stale report and cache behavior can look like classification failure unless the selected entry source is logged.
- Parent-entry fallback is useful for request-tree context but can be wrong if used for a child stack with different material/category data.

## Validation Plan

Future runtime validation should capture final tooltip source for:

1. Stripped Dark Oak Wood Panel.
2. Dark Oak Planks Panel.
3. Stripped Dark Oak Wood Stairs that visibly shows Stonemason.
4. A known-good wood stair that shows Sawmill.
5. A stone stair expected to show Stonemason.
6. Framed Polished Diorite.
7. Light Blue Brick Extra Shingles.
8. A Resource Scroll row and a Clipboard/request-tree row for the same item, if available.

For each item, compare:

- fresh Ctrl+F9 classifier result;
- serialized report entry, if any;
- serialized production entry, if any;
- selected resolver candidate source;
- final tooltip teaching list.

Expected evidence:

- If Panel diagnostic has `knownBy: Sawmill` and tooltip is Shape-only, selected tooltip entry is not the exact classifier-backed source.
- If wood stair diagnostic has Stonemason false and tooltip shows Stonemason, selected tooltip entry contains stale/generic Stonemason.
- If exact classifier-backed production entries exist but lose, the resolver candidate log will show which priority/source won.

## Validation

No Gradle build was run. This was an audit-only documentation pass.

## No-Change Confirmation

No implementation code was changed.
No permanent debug code was added.
No assets were changed.
No UI behavior was changed.
No Smart Info tooltip formatting was changed.
No tooltip text, order, colors, spacing, wrapping, or language keys were changed.
No requester/resolver, accordion, delivery arrow, scrolling, tabs, slots, sorting, or filtering behavior was changed.
No version metadata was changed.
No release jar was built.
No backup copy was created.

