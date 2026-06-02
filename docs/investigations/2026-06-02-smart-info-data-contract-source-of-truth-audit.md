# Smart Info Data Contract Source-Of-Truth Audit

Date: 2026-06-02

## Scope

This audit investigated the Smart Info data contract for Domum Ornamentum / Architects Cutter items across Smart Colony Clipboard, request-tree hovers, and Smart Resource Scrolls.

Baseline tested by the user: `create_colony_logistics-0.3.0-rc.18.jar`.

The goal was to identify the canonical source of truth for "this exact Domum Ornamentum Architects Cutter output and its MineColonies production metadata" so future fixes are source-level rather than shape-by-shape.

## Non-Goals

- No code patches.
- No asset changes.
- No UI behavior changes.
- No Smart Info tooltip formatting, text, ordering, colors, spacing, wrapping, or language-key changes.
- No version bump.
- No release jar.
- No backup copy.

## Pipeline Map

1. MineColonies request graph:
   `RequestAnalysisService.analyze(...)` iterates `clipboardRootRequests(colony)` and receives real `IRequest<?>` objects.

2. Request stack normalization:
   `requestedStack(IRequest<?>)` selects `IStackBasedTask.getTaskStack()`, then `IDeliverable.getResult()`, then the first `request.getDisplayStacks()` entry.

3. Domum Ornamentum metadata extraction:
   `DomumOrnamentumRequestInspector` checks item namespace, material texture data, textured block components, materialized helper stack, exact combo fingerprint, and semantic materialized-output equality.

4. Architects Cutter recipe lookup:
   `findArchitectsCutterMatch(Level, ItemStack)` resolves textured block components and material texture data into a cutter recipe, generated MineColonies recipe storage, assembled output, recipe id, and material stacks.

5. MineColonies production lookup:
   `ColonyProductionInspector.inspect(...)` checks each `ICraftingBuildingModule` that supports Architects Cutter, uses `module.getFirstRecipe(requestedStack)`, compares known primary output to requested or assembled output, and records `knownBy` / `canLearn`.

6. Smart Clipboard report entry:
   `SmartClipboardReport.Entry` serializes requested stack, display stacks, DO block id, recipe id, combo fingerprint, taught state, known/can-learn buildings, request token, request-tree nodes, and smart info keys.

7. Smart Info index:
   `SmartClipboardReport.buildSmartInfoIndex(...)` indexes only Smart Info entries, using fingerprint, exact stack, resource stack, display stack, tree stack, request token, custom keys, and item id.

8. Clipboard tab hover:
   Main row hover already has an `Entry` and calls `buildApprovedSmartInfoTooltip(entry, displayStack(entry))` directly.

9. Request-tree hover:
   Tree hover has a child stack and parent `Entry`. If exact lookup fails, an Architects Cutter parent entry can be reused.

10. Scrolls tab hover:
   Scroll visible rows come from `BuildingResourcesModuleView.getResources()` / `BuildingBuilderResource`, become `ResourceLine`, and must reverse-map the row stack/resource key back to `SmartClipboardReport.Entry`.

## Data Available By Stage

| Stage | Available data | Missing or weak data |
| --- | --- | --- |
| `IRequest<?>` | request token, parent token, children, requester, resolver, request type, display stacks, requestable `Stack` / `StackList`, `IStackBasedTask`, `IDeliverable` result/count | no direct Resource Scroll row key |
| `requestedStack(...)` | item id, count, components patch, full components, description id, display name from chosen stack | may be display fallback rather than MineColonies builder resource row |
| DO inspector | item id, DO namespace, DO textured block, material texture data, component set, exact combo fingerprint, semantic materialized-output comparison | no request token, no MineColonies production state |
| Cutter lookup | recipe id, generated recipe, recipe storage, assembled output, material stacks | can fail if chosen stack lacks expected DO material data or maps to alternate representation |
| Production lookup | knownBy building/module, canLearn building/module, already-taught state from knownBy | depends on requested/assembled output comparison; not tied to Resource Scroll row |
| `SmartClipboardReport.Entry` | requested stack, display stacks, request token, request tree nodes, DO block id, recipe id, combo fingerprint, knownBy, canLearn, taught state, resolver/requester context | no direct link to every `BuildingBuilderResource` row |
| Smart Info index | fingerprint key, exact stack key, resource stack key, display stack keys, tree exact keys, request token, alternatives, item id fallback | built only from report entries, not from visible Scroll resource rows |
| Clipboard main hover | full `Entry`, display stack, all Smart Info metadata | none for top-level entry hovers |
| Request-tree hover | child stack plus parent `Entry` | child-specific Smart Info only if exact/index match; parent fallback may be broader than row identity |
| Scroll `BuildingBuilderResource` | item id, components patch/full components, description id, display name, amount, available, delivery, MineColonies resource key | no request token, parent token, recipe id, knownBy/canLearn, or entry index |
| Scroll `ResourceLine` | one-count row stack, name, amounts, MineColonies resource key | no server-verified canonical Smart Info key |

## Key Comparison Matrix

| Candidate key | Strength | Failure mode |
| --- | --- | --- |
| Exact item id + components patch | Precise, cheap, good for identical stacks | Too strict when request/display/assembled/resource rows represent the same DO output with different component shape |
| Full components | Richest raw stack identity | Expensive/noisy for serialized keys; still too strict if equivalent outputs use different component layout |
| DO exact combo fingerprint | Compact and DO-specific; represents item id plus components patch | Same strictness problem as components patch; only stable when both paths use the same stack representation |
| Architects Cutter recipe id | Stable for shape recipe | Too broad by itself; many material combinations can share recipe id |
| Recipe id + assembled output key | Strong source-level identity for the craftable result | Only exists when cutter lookup succeeds; may differ from request/resource row representation |
| MineColonies resource key | Matches builder resource grouping and warehouse overlays | Tied to `descriptionId + componentsPatch.hashCode()`; useful for exact row identity but not enough to find production metadata unless indexed from same row |
| Request token / parent token | Best relationship key when row maps to a real request | Resource Scroll rows currently do not carry it |
| Semantic DO materialized-output comparison | Best fallback for equivalent DO materialized outputs | Must be ambiguity-guarded; may fail if item id/shape differs despite related parent output |
| Item id fallback | Useful last resort when exactly one candidate exists | Too broad for same-item DO variants; unsafe without uniqueness |

## Canonical Key Strategy

Best source-of-truth strategy:

1. Primary identity: request token / parent token when a visible row can be tied to a real MineColonies request.
2. Primary output key: canonical DO output key generated server-side from the same stack used for Architects Cutter recipe lookup.
3. Craft identity: Architects Cutter recipe id plus assembled output exact key, plus DO material/texture component summary.
4. Row identity: MineColonies resource key for exact `BuildingBuilderResource` row matching.
5. Safe fallback: semantic DO materialized-output comparison, only if exactly one candidate matches.
6. Last fallback: item id only when exactly one Architects Cutter candidate exists.

The key that best represents "this exact Domum Ornamentum Architects Cutter output" is not a single existing key. It should be a canonical composite:

`request relation if available` + `DO item/shape id` + `material texture data/component map` + `assembled output exact key` + `recipe id`

For lookup purposes, this can be serialized as several compact keys with priority rather than one giant string.

## Where Data Diverges

Clipboard main hovers do not diverge meaningfully because they keep the `SmartClipboardReport.Entry`.

Scrolls diverge at the data-source boundary:

- Clipboard Smart Info metadata is entry-centered and generated from root requests.
- Scrolls visible rows are resource-centered and generated from `BuildingResourcesModuleView.getResources()`.
- The resource row has a valid stack and MineColonies resource key, but no entry identity or production metadata.
- The current index is built before the client reconstructs visible Scroll resource rows, so it cannot include row-derived keys unless those keys are generated server-side or serialized separately.

This is why fixes have felt like whack-a-mole: each fix adds another fallback for a representation mismatch, but the Scroll row still lacks a canonical relationship to the server-side request/recipe/production record.

## Example Comparison Table

Static code inspection cannot recover actual runtime components for the named examples without in-game dumps. The table below uses the same audit fields for each example and marks where runtime confirmation is needed.

| Example | Expected Clipboard entry data | Expected Scroll row data | Likely divergence | Expected lookup result today |
| --- | --- | --- | --- | --- |
| Stripped Dark Oak Wood Stairs / Vanilla Stairs Compat | DO entry with requested/display stack, recipe or metadata, knownBy/canLearn, fingerprint, index keys | `BuildingBuilderResource` row with matching or semantically matching DO stack/resource key | Low; stack/key likely overlaps report entry | Smart Info works |
| Stripped Dark Oak Wood Trapdoor | DO entry may use parent/display/assembled representation | visible row has trapdoor-shaped DO stack and resource key | row exact/fingerprint may not equal entry keys; semantic match may not find same item/shape | Missing or incomplete Smart Info |
| Round Stripped Dark Oak Wood Pillar | DO entry likely has materialized pillar output or parent output | visible row has pillar row stack | item id/component map may differ from indexed stack | Missing or incomplete Smart Info |
| Light Blue Brick Extra Shingles | DO entry may use recipe/display fallback | visible row has shingle output and resource key | recipe id may exist but row-derived key is not indexed | Missing or incomplete Smart Info |
| Framed Sea Lantern | DO entry may have material data with nonstandard ingredient/output relationship | visible row has framed lantern stack | assembled output or texture data comparison may not equal requested/display stack | Missing or incomplete Smart Info |
| Glass Framed Pane | DO entry may use pane/framed/glass alternate representation | visible row has pane stack | pane/glass variant exact keys and semantic comparison can diverge | Missing or incomplete Smart Info |
| Sand Stone Bricks Stairs | likely DO entry with partial metadata | visible row may exact-match shape but lack knownBy/canLearn join | production metadata may exist on entry but row maps to weaker candidate or vanilla tooltip | Partial or inconsistent Smart Info |

Recommended runtime dump format for future verification:

- item id
- hover name
- description id
- components patch
- full components summary
- DO block id
- DO material texture data summary
- DO textured block component ids
- exact combo fingerprint
- cutter recipe id
- assembled output exact key
- MineColonies resource key
- request token / parent token
- display stack keys
- requestable stack keys
- request-tree stack keys
- Smart Info index keys and priorities
- final matched entry index or ambiguity reason
- knownBy / canLearn / already-taught state

## Confirmed Source-Level Failure

The confirmed source-level failure is that canonical Smart Info identity is generated from request/report data, while Scrolls hover identity is generated from resource-row data after request context has been dropped.

Current problem categories:

- Bad/incomplete key generation: yes, because keys do not cover actual visible Scroll resource rows.
- Key generation too late: yes, Scroll row keys are generated client-side after server analysis has already built the report index.
- Different source objects: yes, Clipboard uses `SmartClipboardReport.Entry`; Scrolls uses `BuildingBuilderResource`.
- Resource rows lacking server-side report context: yes, this is the main issue.
- DO fingerprint inconsistency: possible, because fingerprint uses exact components patch.
- Assembled output mismatch: possible, especially when request stack, display stack, and assembled cutter output differ.
- MineColonies resource key mismatch: possible unless the same row key is indexed against the entry.
- SmartInfoIndex missing row-derived keys: yes.
- Tooltip lookup fallback timing: secondary; fallback is compensating for missing canonical context.

## Recommended Source-Level Architecture

1. Introduce a centralized `SmartInfoResolver` concept.
   - Clipboard main rows, request-tree rows, and Scrolls rows should all ask the same resolver for an entry.
   - The resolver should return either one unambiguous entry or no entry.
   - Tooltip rendering should remain separate and unchanged.

2. Generate canonical Smart Info keys server-side during report analysis.
   - For every Smart Info entry, generate keys from requested stack, materialized requested stack, assembled cutter output, display stacks, requestable stacks, request-tree stacks, recipe id, DO material data, and request token.
   - Use prioritized keys rather than display-name matching.

3. Enrich Resource Scroll row data with compact lookup keys.
   - Best option: when stored scroll data is refreshed or report analysis runs, join visible builder resources to request graph entries server-side and serialize row lookup keys.
   - Include MineColonies resource key, exact stack key, DO fingerprint, DO material key, assembled output key if resolved, and request/parent token if known.

4. Build `SmartInfoIndex` from both report entries and row-derived keys.
   - Row-derived keys should point to existing `Entry` indexes.
   - Do not duplicate tooltip data.
   - Keep all row keys compact.

5. Use strict priority and ambiguity rules.
   - Request token / explicit row-entry link wins.
   - Exact row key and exact components win next.
   - DO material key / recipe id plus assembled output next.
   - Semantic DO materialized-output match next.
   - Same item id only if exactly one candidate exists.
   - Ambiguity renders vanilla tooltip.

6. Preserve already-taught visibility.
   - `knownBy` should remain Smart Info metadata, not a filter.
   - Already-taught entries should still be Smart Info entries; only can-learn feedback remains conditional as today.

7. Protect vanilla negative controls.
   - No Smart Info for non-DO resources unless an explicit request-token/parent-entry relationship maps them to a DO parent and that behavior is intentionally enabled.
   - No display-name matching.

## Ambiguity Rules

- Never choose between multiple entries with the same priority key match.
- Never use item id fallback if more than one Architects Cutter candidate shares that item id.
- Never use display name as identity.
- Never infer parent Smart Info for a vanilla row unless request-token lineage proves the relationship.
- Prefer no Smart Info over wrong Smart Info.

## Risks

- Server-side row/request joins can be hard because builder resource rows may be grouped outputs rather than one request.
- DO material data reflection can be brittle if Domum Ornamentum internals change.
- More keys increase packet size; compact strings and entry indexes should be used.
- Over-indexing `StackList` alternatives can attach Smart Info too broadly.
- A centralized resolver changes multiple hover paths and needs careful regression testing.

## Future Validation Plan

1. Clipboard main row:
   Hover a known working DO request. Expected: existing Smart Info unchanged.

2. Request-tree child row:
   Hover a child of a DO request. Expected: direct exact match or explicit parent fallback, no wrong same-item match.

3. Scroll working control:
   Stripped Dark Oak Wood Stairs / Vanilla Stairs Compat. Expected: Smart Info and building context remain present.

4. Scroll failing examples:
   Stripped Dark Oak Wood Trapdoor, Round Stripped Dark Oak Wood Pillar, Light Blue Brick Extra Shingles, Framed Sea Lantern, Glass Framed Pane, Sand Stone Bricks Stairs. Expected after future fix: each visible DO row resolves through canonical row/request/recipe keys or returns vanilla tooltip with a clear ambiguity reason in debug logs.

5. Multiple same-item variants:
   Put two DO variants with the same item id and different materials/components in active requests. Expected: no cross-match.

6. Already-taught recipe:
   Confirm knownBy entries still show Smart Info even when canLearn is empty.

7. Vanilla negative controls:
   Confirm ordinary builder resources still show vanilla tooltip unless explicit lineage is implemented and verified.

## Validation

No Gradle build was run. This was an audit-only documentation pass with no code, asset, version, release-jar, or backup-folder changes.

## No-Change Confirmation

No implementation code was changed.
No assets were changed.
No UI behavior was changed.
No Smart Info tooltip formatting was changed.
No tooltip text, order, colors, spacing, wrapping, or language keys were changed.
No version metadata was changed.
No release jar was built.
No backup copy was created.
