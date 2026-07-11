# rc26 Classifier Source-Truth Audit

Date: 2026-06-02

## Scope

This audit investigated why `create_colony_logistics-0.3.0-rc.26.jar` can still misclassify or under-classify Domum Ornamentum / Architects Cutter Smart Info learnability after adding `SmartInfoClassificationService`.

Observed baseline from the user:

- Stripped Dark Oak Wood Panel shows Smart Info and Shape: Panel, but no `Can learn`.
- Stripped Dark Oak Wood Stairs shows Smart Info and Shape: Vanilla Stairs Compat, but can show `Can learn: Stonemason` despite being wood.

This pass followed the audit-only instruction. It reviewed the current source paths and prior solution families, but did not implement a fix.

## Non-Goals

- No implementation patches.
- No permanent debug code.
- No asset changes.
- No UI behavior changes.
- No Smart Info tooltip formatting, text, order, colors, spacing, wrapping, or language-key changes.
- No requester/resolver UI changes.
- No accordion, delivery arrow, scrolling, tabs, slots, sorting, or filtering changes.
- No version bump.
- No Gradle build.
- No release jar.
- No backup copy.
- No fallback expansion or shape/item-specific fix.

## Previous Solution Families Reviewed

| Solution family | Attempted behavior | Current failure stage | Still relevant? |
| --- | --- | --- | --- |
| Direct Clipboard entry path | Clipboard rows keep a `SmartClipboardReport.Entry` and render Smart Info directly. | The failing Scroll/fallback cases often occur after direct entry lookup fails or when the entry was built from weak source data. | Relevant only when request entry source data is exact. |
| Resolver/index matching | `SmartInfoResolver` centralizes lookup and rejects ambiguous matches. | The resolver can still receive generic or shape-level production data. | Relevant for preventing wrong joins, not for generating correct category data. |
| DO key enrichment | Added exact stack, material, fingerprint, recipe-output, request/tree/resource keys. | Keys cannot fix wrong generated recipe inputs or generic recipe source data. | Relevant only if the keyed value is exact materialized output data. |
| Global production fallback | Uses colony-wide production metadata when no report entry matches. | For DO, global Architects Cutter recipes are generic shape recipes, not exact materialized outputs. | Relevant for vanilla/concrete outputs and DO Shape only. |
| Broad DO `Can learn` suppression | Prevented recipe-id-only DO fallback from showing broad generic module lists. | Correctly hides generic lists, but causes Shape-only when exact classification fails. | Still relevant as a safety guard. |
| `SmartInfoClassificationService` | Centralized exact DO learnability via `findArchitectsCutterMatch(...)` and module validation. | Current failures are inside or before this service: the classifier depends on the recipe match it is given. | Relevant, but likely receiving incomplete or wrong source data. |
| Exact Resource Scroll enrichment | Server-side scan of stored scroll builder rows and exact classification. | Scroll-only; still depends on reconstructing an exact cutter recipe from the row stack. | Relevant for visible Scroll rows, not universal. |

Conclusion: do not add another fallback layer yet. The current question is whether the source-of-truth input to `SmartInfoClassificationService` is exact.

## Classifier Input Validation Findings

`SmartInfoClassificationService.buildClassification(...)` uses this path:

1. Normalize the hovered/requested stack to count 1.
2. Check whether it is a Domum Ornamentum stack.
3. Call `DomumOrnamentumRequestInspector.findArchitectsCutterMatch(level, stack)`.
4. If a match exists, validate `match.genericRecipe()` against each Architects Cutter-capable MineColonies module:
   - `module.canLearn(ModCraftingTypes.ARCHITECTS_CUTTER.get())`
   - `module.isRecipeCompatible(match.genericRecipe())`
5. Check known recipes through `module.getFirstRecipe(stack)` and `knownRecipeMatches(...)`.

This means the classifier does not independently discover the authoritative recipe. Its source of truth is currently whatever `findArchitectsCutterMatch(...)` returns.

Important static finding: `findArchitectsCutterMatch(...)` can return no match before module validation if:

- `MaterialTextureData.readFromItemStack(stack)` is null or empty;
- the textured block component list is empty;
- a material entry is missing and becomes `Blocks.AIR`;
- a material value is not a `Block`;
- no Architects Cutter recipe assembles into an output accepted by `isSameMaterializedCutterOutput(...)`;
- the assembled and requested stacks have empty material texture data or mismatched textured component maps.

Important static finding: if `findArchitectsCutterMatch(...)` returns a match with the wrong material stack, the classifier will faithfully validate the wrong generated recipe. MineColonies module validators are material-input driven, so a wrong input stack can make the wrong hut pass.

## Cache Findings

The classifier cache key is:

```text
dimension + colony id + exactStackKey(stack) + canonicalMaterialKey(stack)
```

If `canonicalMaterialKey(stack)` is empty for single-material/default DO outputs, the cache still includes `exactStackKey(stack)`, which includes the item id and components patch. Static inspection does not prove stale cache is the primary cause.

Potential risk: if two visually different DO outputs have identical item id and components patch while material identity is implicit outside the patch, they could share a cache entry. Runtime component dumps are needed to prove or reject this.

## Wood Stair To Stonemason Findings

No runtime stack dump was available, so exact generated inputs could not be captured truthfully. Static source inspection identifies the plausible pass path:

1. A wood stair stack reaches either the direct report path or fallback path.
2. If direct classification runs, Stonemason can only appear if `module.isRecipeCompatible(match.genericRecipe())` returns true for the exact generated recipe.
3. MineColonies DO module compatibility is input-material driven.
4. Therefore Stonemason passing means one of these is true:
   - the generated exact recipe input is actually stone/masonry-tagged;
   - the recipe is not exact and still contains generic valid material options that satisfy Stonemason;
   - the final tooltip did not come from classifier output and instead used global/generic production data;
   - cached or indexed production data matched at the wrong level.

High-confidence static theory: recipe id or generic recipe-output data is still able to participate in fallback resolution for Shape, and possibly in category context if an entry is considered "exact enough" by key priority and output equality. The latest resolver guard checks output equality, which reduces this risk, but it does not prove the generated recipe input is wood.

The first divergence to verify at runtime is:

```text
hovered wood stair stack
-> findArchitectsCutterMatch(...)
-> match.materialStacks()
```

If `materialStacks()` contains a stone-like stack, the classifier decision is correct for bad input. If it contains a wood stack and Stonemason still passes, the MineColonies validator or the recipe object is broader than expected. If classifier output does not contain Stonemason but the tooltip does, the source-of-truth path is being bypassed after classification.

## Panel Findings

For Stripped Dark Oak Wood Panel and Dark Oak Planks Panel, the observed Shape-only tooltip is consistent with this path:

1. The row resolves enough context to identify the Panel recipe id or DO block id.
2. `SmartInfoResolver.productionFallback(...)` can create a Smart Info fallback entry when production metadata has recipe/shape context.
3. DO `knownBy` and `Can learn` are allowed only when `isExactDomumProductionMatch(...)` passes.
4. If exact classification does not produce a production entry with `knownBy` or `canLearn`, the fallback entry has Shape only.

Static evidence suggests Panel likely fails before or inside exact classification, not inside tooltip formatting:

- `findArchitectsCutterMatch(...)` may return empty for single-material/default material data.
- If match is empty, `SmartInfoClassificationService` status becomes `NO_EXACT_RECIPE`, and `canLearn` is empty.
- If match exists but no module accepts `match.genericRecipe()`, status becomes `SHAPE_ONLY`.
- If exact Resource Scroll enrichment does not see the row, the global fallback may still provide Shape but not exact teaching context.

The exact divergence for Panel cannot be proven without runtime values. The first value to capture is whether `findArchitectsCutterMatch(...)` returns a match for the actual hovered panel stack and what its `materialStacks()` are.

## Source-Of-Truth Comparison

| Source | MineColonies authority level | Current classifier usage | Divergence risk |
| --- | --- | --- | --- |
| Hovered/request stack | Carries item/components, but may be implicit or display-oriented. | Primary classifier input. | May lack explicit material texture data for panels/single-material shapes. |
| Architects Cutter recipe holder | Shape recipe source. | Used indirectly through `findArchitectsCutterMatch(...)`. | Recipe id alone is shape-level, not material-level. |
| Generated exact recipe | Best available materialized recipe object for validation. | `CutterRecipeMatch.genericRecipe()` is used. | Correct only if `findArchitectsCutterMatch(...)` reconstructed exact material inputs. |
| Generated recipe storage/token | Closest to teachable MineColonies storage. | Built in `CutterRecipeMatch.recipeStorage()` but not used for can-learn/capacity checks. | May miss `canRecipeBeAdded(...)` / capacity proof. |
| Module validator | Hut-category source of truth. | Used through `module.isRecipeCompatible(...)`. | Reliable only if recipe input stacks are exact. |
| Known recipe storage | Already-taught source of truth. | Used through `module.getFirstRecipe(stack)` and `knownRecipeMatches(...)`. | Depends on stack equality / semantic DO equality. |
| Global production index | Broad fallback for concrete outputs. | Still used by resolver fallback. | For DO, generic Architects Cutter recipes are shape-level and must remain Shape-only. |

Authoritative answer should come from exact generated materialized recipe/storage plus MineColonies module validation. Current classifier uses module validation, but its recipe input source may not yet be authoritative for all DO variants.

## Universality Analysis

Current architecture is not fully universal yet.

| Category | Same classifier path? | Remaining alternate/special path |
| --- | --- | --- |
| Stairs | Yes when direct/classified; fallback can still provide Shape. | Generic global production fallback can still provide Shape. |
| Slab | Same as stairs. | Same fallback and recipe reconstruction risks. |
| Fence | Same as stairs. | Exact match depends on material data reconstruction. |
| Wall | Same as stairs. | Exact match depends on material data reconstruction. |
| Trapdoor | Same as stairs. | Single-material/default material data can force Shape-only. |
| Panel | Same as stairs when classifier gets exact recipe. | Observed Shape-only suggests exact source data is missing or rejected. |
| Pillar | Same as stairs. | Single-material/default material data risk. |
| Pane | Same as stairs. | Pane/framed/glass representation can diverge from exact stack keys. |
| Shingle | Same as stairs. | Needs runtime recipe/output validation. |
| Framed | Same classifier path if DO stack reconstructs exact recipe. | Multi-material can legitimately pass multiple modules only if exact inputs do. |
| Vanilla outputs | Not classified by `SmartInfoClassificationService`. | Still use global production fallback, which is structurally safer because outputs are concrete. |

Remaining special-case paths:

- Client-side `resourceRowKeys(...)` still calls `findArchitectsCutterMatch(...)` to add recipe-derived keys.
- Global production fallback still exists for all items.
- DO fallback still allows Shape from recipe/doBlock context without exact `Can learn`.
- Unique item fallback still exists for Smart Info entries.
- Request-tree parent fallback still exists.
- Resource Scroll exact enrichment is scroll-specific.

These are not automatically wrong, but they prove the architecture is not yet a single universal classification source for every hover path.

## Confirmed Root Cause / Confidence-Ranked Theories

Confirmed from source:

1. `SmartInfoClassificationService` centralizes the decision, but not the upstream recipe reconstruction.
2. The classifier's decision is only as exact as `findArchitectsCutterMatch(...)`.
3. `findArchitectsCutterMatch(...)` rejects empty/implicit material data and validates assembled outputs strictly.
4. DO Shape can still come from fallback metadata even when exact learnability is absent.
5. Vanilla outputs still use a different fallback source.

High-confidence theories:

1. Panel lacks `Can learn` because `findArchitectsCutterMatch(...)` returns empty or returns a recipe no module accepts for the actual panel stack.
2. Wood stairs show Stonemason because the recipe being validated is not actually wood-material exact, or the tooltip result is bypassing classifier output through a production fallback/index match.
3. The next fix should inspect/generated recipe inputs, not add another resolver fallback.

Medium-confidence theories:

1. Single-material/default DO stacks may encode material in a form not visible to `MaterialTextureData.getTexturedComponents()`.
2. Cache keys may be too weak if material is implicit outside components patch, but this is not proven.
3. `CutterRecipeMatch.genericRecipe()` may not be equivalent to the final `IRecipeStorage` MineColonies would register for teaching capacity checks.

Low-confidence theories:

1. Panels are unsupported by MineColonies modules.
2. Stonemason categorization is a MineColonies bug rather than wrong recipe input data.

## Recommended Next Step

Do not patch behavior yet.

Add guarded diagnostics to the existing debug action path, not always-on logging. For the failing panel and wood stair, capture:

- hovered stack item id, components patch, full component summary;
- `canonicalMaterialKey`;
- `findArchitectsCutterMatch(...)` present/absent;
- recipe id;
- assembled output exact key;
- generated material input stacks and their item ids;
- generated recipe primary output;
- generated recipe storage primary output;
- each Architects Cutter-capable module label;
- `module.canLearn(ARCHITECTS_CUTTER)` result;
- `module.isRecipeCompatible(exactRecipe)` result;
- known recipe result;
- classifier status/reason;
- production index match key/priority used by resolver;
- final entry source: direct report, index, semantic, item, or production fallback.

Only after that runtime evidence should implementation proceed.

## Risks

- Adding fallback logic now could mask the real source divergence.
- Shape-specific panel or stair fixes could misclassify other DO outputs.
- If generated material stacks are wrong, module validation will be confidently wrong.
- If diagnostics are always-on, report generation logs can become noisy.
- If the true MineColonies teaching source is `canRecipeBeAdded(...)`, `isRecipeCompatible(...)` may still overstate current learnability.

## Validation Plan

Runtime cases:

1. Stripped Dark Oak Wood Panel.
2. Dark Oak Planks Panel.
3. Stripped Dark Oak Wood Stairs that currently shows Stonemason.
4. A stone DO stair expected to show Stonemason.
5. Wood trapdoor, fence, pillar, slab.
6. Glass pane/framed glass variant.
7. Framed Polished Diorite and Light Blue Brick Extra Shingles.
8. Vanilla wood teachable item.
9. Vanilla stone teachable item.
10. Vanilla non-teachable item.
11. Already-taught DO recipe.

Expected evidence:

- Wood outputs have wood material input stacks before Sawmill validation.
- Stone outputs have stone material input stacks before Stonemason validation.
- Panel either gets exact material inputs and module validation, or records the precise reason no exact recipe exists.
- DO `Can learn` appears only from exact classifier output.
- Generic recipe-id fallback remains Shape-only.

## Validation

No Gradle build was run because this was an audit-only documentation pass. No Minecraft runtime session was run, so item-specific runtime values remain unconfirmed.

## No-Change Confirmation

No implementation code was changed in this audit pass.
No assets were changed.
No UI behavior was changed.
No Smart Info tooltip formatting was changed.
No tooltip text, order, colors, spacing, wrapping, or language keys were changed.
No requester/resolver, accordion, delivery arrow, scrolling, tabs, slots, sorting, or filtering behavior was changed.
No version metadata was changed in this audit pass.
No release jar was built.
No backup copy was created.

This repository already contained rc.26 implementation changes before this audit report was written; this audit did not add more implementation changes.
