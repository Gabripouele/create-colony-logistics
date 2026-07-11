# Single-Material Domum Ornamentum Smart Info Audit

Date: 2026-06-02

## Scope

This audit investigated why single-material Domum Ornamentum / Architects Cutter items can still miss Smart Info in `create_colony_logistics-0.3.0-rc.19.jar`, after the centralized `SmartInfoResolver` was added.

The audit focused on the metadata and recipe pipeline before resolver matching:

- whether single-material DO items become `SmartClipboardReport.Entry` rows;
- whether they pass `SmartClipboardReport.isSmartInfoEntry(...)`;
- whether they produce DO material keys and Architects Cutter recipe matches;
- whether MineColonies production lookup can produce `knownBy` / `canLearn`;
- whether the resolver receives useful keys or receives no valid Smart Info candidate.

## Non-Goals

- No code patches.
- No UI changes.
- No asset changes.
- No version bump.
- No release jar.
- No backup copy.
- No Smart Info tooltip formatting/text/order/color/spacing/wrapping/language-key changes.

## Methods Inspected

- `SmartClipboardReport.isSmartInfoEntry(...)`
- `SmartClipboardReport.buildSmartInfoIndex(...)`
- `DomumOrnamentumRequestInspector.canonicalMaterialKey(...)`
- `DomumOrnamentumRequestInspector.hasArchitectsCutterMetadata(...)`
- `DomumOrnamentumRequestInspector.isMaterializedArchitectsCutterOutput(...)`
- `DomumOrnamentumRequestInspector.findArchitectsCutterMatch(...)`
- `DomumOrnamentumRequestInspector.materialCandidatesFromComponents(...)`
- `DomumOrnamentumRequestInspector.findCutterRecipeHolder(...)`
- `DomumOrnamentumRequestInspector.sameMaterializedDomumOutput(...)`
- `RequestAnalysisService.inspectRequest(...)`
- `RequestAnalysisService.smartInfoKeys(...)`
- `ColonyProductionInspector.inspect(...)`
- `SmartInfoResolver.resolveResource(...)`
- `SmartInfoResolver.resolveStack(...)`

## Pipeline Summary

`RequestAnalysisService.inspectRequest(...)` classifies a request as DO if `DomumOrnamentumRequestInspector.isDomumOrnamentumStack(requestedStack)` is true. It then calls:

- `findArchitectsCutterMatch(level, requestedStack)` for recipe id, assembled output, generated recipe, and recipe storage;
- `ColonyProductionInspector.inspect(colony, level, requestedStack)` for `knownBy` and `canLearn`;
- `smartInfoKeys(...)` to add requested stack keys, materialized requested stack keys, assembled output keys, display stack keys, tree stack keys, request token key, and requestable stack keys.

`SmartClipboardReport.isSmartInfoEntry(...)` accepts an entry only if:

- `entry.doBlockId().startsWith("domum_ornamentum:")`; and
- either a cutter recipe id is present, `isMaterializedArchitectsCutterOutput(entry.requestedStack())` is true, or `hasArchitectsCutterMetadata(entry.requestedStack())` is true.

If a single-material DO output fails all three checks, it can still be a normal report entry, but it will not be indexed as a Smart Info entry. The resolver cannot recover Smart Info from an entry that was never admitted to the Smart Info index and is not directly passed as a Smart Info entry.

## Likely Failure Stage

High confidence: the likely failure is upstream of `SmartInfoResolver`, in DO metadata extraction and Architects Cutter recipe reconstruction.

The strict branches are:

- `canonicalMaterialKey(...)` returns empty if `materialTextureDataIsEmpty(textureData)` is true.
- `canonicalMaterialKey(...)` also returns empty if `texturedComponents(textureData)` is empty.
- `hasArchitectsCutterMetadata(...)` returns false if texture data is null or empty.
- `isMaterializedArchitectsCutterOutput(...)` returns false if texture data is empty or the textured component map is empty.
- `findArchitectsCutterMatch(...)` returns empty if texture data is null/empty, textured block components are empty, a material value is not a `Block`, or assembled recipe output fails `isSameMaterializedCutterOutput(...)`.

This is especially suspicious for one-material/special-shape outputs because a single material may be stored as default or implicit texture data rather than a populated component map. If so, the item can visibly be a DO Architects Cutter item while our metadata checks see "empty material data" and refuse to create recipe/material keys.

## Important Code-Level Finding

`materialCandidatesFromComponents(...)` can scrape material ids from `ItemStack.getComponents()` and `getComponentsPatch()` strings, but it is not used by `findArchitectsCutterMatch(...)`.

It is used by:

- `findCutterRequirements(...)`
- `debugCutterSummary(...)`

The main Smart Info recipe path instead relies on `MaterialTextureData.readFromItemStack(...)` and `getTexturedComponents()`. That means a single-material stack whose material can be seen in component text, but not through non-empty `MaterialTextureData`, may still fail:

- cutter recipe lookup;
- assembled output key generation;
- canonical material key generation;
- can-learn recipe generation.

## Working Vs Failing Comparison

Runtime values were not captured in this pass because no Minecraft client/server session was run and no temporary logging was kept. The table below is the code-path comparison to verify with the requested runtime dump.

| Case | Expected working path | Likely failing path |
| --- | --- | --- |
| Multi-material / standard-shape DO item | `MaterialTextureData` non-empty, `texturedComponents` non-empty, cutter input reconstructed, recipe match found, `cutterRecipeId` present, assembled output key generated, `knownBy`/`canLearn` can populate | Not expected to fail unless ambiguous |
| Stripped Dark Oak Wood Stairs / Vanilla Stairs Compat | Same as working path, or exact/display/request-tree key overlaps an existing Smart Info entry | If still working, resolver receives valid entry/index keys |
| Stripped Dark Oak Wood Trapdoor | May be DO item, but texture data/component map may be empty or represented differently | `canonicalMaterialKey` empty, `findArchitectsCutterMatch` empty, no recipe-output key, can-learn empty |
| Round Stripped Dark Oak Wood Pillar | Same one-material risk | May fail metadata/recipe before resolver |
| Light Blue Brick Extra Shingles | Special-shape one-material risk | May fail assembled-output equality or recipe lookup |
| Framed Sea Lantern | Material may be non-block, block-with-no-item, or implicit/default | `findArchitectsCutterMatch` can reject non-`Block` material values |
| Glass Framed Pane | Pane/framed variant representation risk | Recipe output item id/components may not satisfy `isSameMaterializedCutterOutput(...)` |
| Sand Stone Bricks Stairs | If partial Smart Info appears, likely entry passes Smart Info but recipe/production metadata is missing | Resolver may find entry, but tooltip lacks building context because `knownBy`/`canLearn` is empty |

## Audit Dump Fields Needed

For one working item and at least three failing single-material items, the next runtime dump should capture:

- item id;
- display name;
- description id;
- components patch summary;
- full components summary;
- `isDomumOrnamentumStack`;
- `hasArchitectsCutterMetadata`;
- `isMaterializedArchitectsCutterOutput`;
- `canonicalMaterialKey`;
- `exactComboFingerprint`;
- whether material texture data is present/empty;
- textured component count;
- `texturedComponents` count;
- `materialCandidatesFromComponents(...)` count;
- cutter recipes considered;
- selected cutter recipe id;
- assembled cutter output key;
- whether assembled output equals requested/resource stack;
- whether `RequestReportEntry` is created;
- whether `SmartClipboardReport.isSmartInfoEntry(...)` passes;
- `knownBy`;
- `canLearnCombo`;
- already-taught state;
- Smart Info keys generated;
- Smart Info index entries generated;
- resolver result or ambiguity reason.

## Key Questions Answered

- Are single-material DO outputs missing because `materialTextureDataIsEmpty(...)` returns true?
  Likely for at least some cases. This is the earliest shared strict gate for metadata, material key, materialized-output detection, and recipe reconstruction.

- Are they missing because `texturedComponents(...)` is empty?
  Likely for cases where `MaterialTextureData` exists but the material map is not populated for one-material/default outputs.

- Are they missing because `canonicalMaterialKey(...)` requires a component map and returns empty?
  Yes, this can remove one of the new rc19 canonical keys. It should not alone suppress Smart Info if other keys exist, but it weakens resolver matching.

- Are they missing because `findArchitectsCutterMatch(...)` cannot reconstruct the recipe input for one-material shapes?
  Likely. It reconstructs cutter input from `texturedBlockComponents(...)` plus `texturedComponents(textureData)`. It does not use the component-string material candidate fallback.

- Are they missing because `isSameMaterializedCutterOutput(...)` rejects assembled output?
  Possible. It requires same item id, component count equal to material slot count, non-empty material texture data on both stacks, and equal textured component maps.

- Are they missing because `isSmartInfoEntry(...)` requires recipe id / materialized output / metadata?
  Yes if all three are absent. Metadata-only DO entries are accepted, but single-material items with empty texture data can fail metadata and materialized-output checks.

- Are they present as normal entries but excluded from Smart Info index?
  Very plausible. `buildSmartInfoIndex(...)` skips entries where `isSmartInfoEntry(...)` is false.

- Are they indexed but rejected by ambiguity rules?
  Possible but lower confidence for the observed "single-material category". Resolver ambiguity can only happen after valid Smart Info index keys exist. The stronger suspicion is missing or weak keys before resolver matching.

## Resolver Reachability

The resolver is reached by Clipboard, request-tree, and Scroll hovers in rc19. However, it can only return an entry if:

- the direct entry path passes `SmartClipboardReport.isSmartInfoEntry(...)`;
- or the stack/resource lookup hits an indexed Smart Info entry;
- or semantic/item fallback finds exactly one indexed Smart Info entry.

If a failing single-material item never becomes a Smart Info entry, the resolver is reached but has no valid candidate to return. That is not a resolver-routing failure.

## Production Lookup Impact

`ColonyProductionInspector.inspect(...)` calls `findArchitectsCutterMatch(...)` again.

If the cutter match is absent:

- `exactRecipes` is empty;
- `canLearn` cannot populate because compatibility is checked against generated exact recipes;
- `knownBy` can still populate only if `module.getFirstRecipe(requestedStack)` returns a known recipe and `knownRecipeMatches(...)` succeeds against the requested stack without needing the cutter match.

So single-material outputs can show no building context even if the resolver finds a Smart Info entry, because production metadata may have failed earlier with the recipe reconstruction.

## Confirmed Failure Stage

From static inspection, the most likely failure stage is:

`DomumOrnamentumRequestInspector.findArchitectsCutterMatch(...)` and related DO metadata gates, before Smart Info index/resolver matching.

The exact branch needs runtime confirmation, but the leading candidates are:

1. `materialTextureDataIsEmpty(textureData)` is true for one-material/default outputs.
2. `texturedComponents(textureData)` is empty.
3. `findArchitectsCutterMatch(...)` reconstructs the wrong or incomplete Architects Cutter input.
4. `isSameMaterializedCutterOutput(...)` rejects the assembled output.
5. `isSmartInfoEntry(...)` excludes the entry when recipe id, materialized output, and metadata checks all fail.

## Recommended Future Source-Level Fix

Do not change tooltip formatting.

Recommended fix:

1. Add a real DO diagnostic helper that returns structured metadata:
   - texture data present/empty;
   - textured block component count;
   - textured component map count;
   - material candidates from component strings;
   - recipe lookup result;
   - assembled output comparison result.

2. Relax Smart Info entry admission for DO Architects Cutter-like stacks only when safe:
   - If item namespace is DO and the stack can be associated with an Architects Cutter recipe by exact output, compatible item id plus material candidates, or MineColonies known recipe, allow a Smart Info entry even when `MaterialTextureData` is empty.
   - Keep vanilla/non-DO resources excluded.

3. Teach `findArchitectsCutterMatch(...)` a fallback path:
   - First use current `MaterialTextureData` path.
   - If empty, use `materialCandidatesFromComponents(...)` to build cutter input.
   - Validate assembled output by exact stack, same DO item id plus compatible material key, or a conservative single-material comparison.

4. Split material key generation into two sources:
   - `MaterialTextureData` canonical key when available.
   - component-token material key fallback when texture data is empty but exactly one material candidate is found.

5. Keep ambiguity rules:
   - If multiple material candidates or multiple compatible recipes exist, return no Smart Info rather than guessing.
   - No display-name matching.
   - No broad same-item fallback unless unique.

6. Production lookup should reuse the same successful cutter match:
   - Avoid recomputing a stricter match in `ColonyProductionInspector.inspect(...)`.
   - Pass or share the resolved `CutterRecipeMatch` so `canLearn` can populate for single-material items.

## Risks

- Component-string material parsing can over-detect unrelated resource ids in components.
- Compatible item-id recipe fallback can choose the wrong shape recipe if not constrained.
- Single-material default DO outputs may intentionally omit material data, making exact material identity difficult.
- Relaxing `isSmartInfoEntry(...)` too broadly could show Smart Info on non-cutter DO decorative items.
- Reusing runtime recipe matches across production lookup must avoid stale or client-only state.

## Validation Plan

Runtime validation should test:

1. Working control:
   Stripped Dark Oak Wood Stairs / Vanilla Stairs Compat.

2. At least three failing single-material examples:
   trapdoor, pillar, pane/shingle/framed block.

3. For each, capture the audit dump fields listed above.

4. Confirm whether the entry is absent, present but not `isSmartInfoEntry`, indexed but unmatched, or matched but lacks production metadata.

5. Confirm already-taught recipes still show Smart Info and known-by context.

6. Confirm vanilla negative controls remain vanilla.

7. Confirm multiple same-item DO variants do not cross-match.

## Validation

No Gradle build was run. This was a documentation-only audit and no implementation changes were made in this pass.

## No-Change Confirmation

No permanent code changes were made in this audit pass.
No UI changes were made.
No assets were changed.
No version metadata was changed.
No release jar was built.
No backup copy was created.
The locked Smart Info tooltip formatting was not changed.
