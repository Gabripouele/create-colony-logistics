# Cutter Recipe Source-Truth Runtime Audit

Date: 2026-06-02

## Scope

This audit focused only on the exact recipe-generation path:

```text
DomumOrnamentumRequestInspector.findArchitectsCutterMatch(...)
```

The goal was to determine whether the recipe object passed into Smart Info classification is actually the correct materialized Architects Cutter recipe for each hovered/requested Domum Ornamentum output.

Current baseline:

- `SmartInfoClassificationService` exists in rc.26.
- Resolver, indexing, fallback behavior, Resource Scroll enrichment, tooltip rendering, and UI paths have already been audited.
- Remaining observed failures point toward the source data produced by `findArchitectsCutterMatch(...)`.

## Non-Goals

- Do not implement patches.
- Do not add permanent diagnostics.
- Do not revisit SmartInfoResolver matching.
- Do not revisit Smart Info index lookup.
- Do not revisit recipe-id fallback behavior.
- Do not revisit generic fallback architecture.
- Do not revisit tooltip formatting.
- Do not revisit Scroll, Clipboard, or request-tree rendering.
- Do not change assets.
- Do not change UI behavior.
- Do not change Smart Info tooltip formatting, text, order, colors, spacing, wrapping, or language keys.
- Do not bump version metadata.
- Do not run a release build.
- Do not build or copy a release jar.

## Tested Items

The requested runtime target set was:

- Framed Polished Diorite
- Light Blue Brick Extra Shingles
- Stripped Dark Oak Wood Panel
- Dark Oak Planks Panel
- Stripped Dark Oak Wood Stairs
- Round Stripped Dark Oak Wood Pillar
- Stripped Dark Oak Wood Trapdoor
- optionally one stone stair, one stone panel, and one glass-based DO item

Runtime values for these items were not captured in this pass because no Minecraft client/server session or saved runtime dump was available in the workspace. The sections below therefore distinguish static source findings from runtime fields that still need capture.

## Exact Recipe Generation Path

`findArchitectsCutterMatch(Level, ItemStack)` currently does the following:

1. Rejects empty, non-DO, or null-level stacks.
2. Uses MineColonies/Domum reflection to resolve the DO textured block:
   `DomumOrnamentumUtils.getBlock(stack)`.
3. Reads DO material texture data:
   `MaterialTextureData.readFromItemStack(stack)`.
4. Rejects the stack if texture data is null or `isEmpty()`.
5. Reads textured block components from `IMateriallyTexturedBlock.getComponents()`.
6. Reads component-to-material entries from `textureData.getTexturedComponents()`.
7. For each textured block component:
   - looks up the component id;
   - reads the material from the component map;
   - substitutes `Blocks.AIR` when the component id is missing;
   - rejects the whole match if the material is not a `Block`;
   - converts the block to an `ItemStack`;
   - writes that stack into the Architects Cutter input inventory.
8. Creates `ArchitectsCutterRecipeInput` from that inventory.
9. Calls `level.getRecipeManager().getRecipesFor(architectsCutterType, input, level)`.
10. Assembles each candidate recipe.
11. Keeps only assembled outputs accepted by `isSameMaterializedCutterOutput(assembled, requestedStack, materialStacks.size())`.
12. Chooses the first sorted match.
13. Builds:
   - `GenericRecipe` with the selected recipe id, assembled output, and `materialStacks` as inputs;
   - `RecipeStorage` with the same material stacks and assembled output;
   - `CutterRecipeMatch(recipeId, genericRecipe, recipeStorage, assembledOutput, materialStacks)`.

This is the recipe object Smart Info classification is asked to validate.

## Generated Recipe Dump

No runtime-generated recipe dump was available.

Expected dump format for each target item:

| Field | Runtime value |
| --- | --- |
| requested item id | not captured |
| requested display name | not captured |
| requested components | not captured |
| requested material key | not captured |
| requested fingerprint | not captured |
| `findArchitectsCutterMatch` present | not captured |
| recipe id | not captured |
| generated recipe output | not captured |
| generated storage primary output | not captured |
| assembled output exact key | not captured |
| generated material input count | not captured |
| generated material input ids | not captured |

Static finding: the generated recipe is materialized only if `materialStacks` are materialized correctly from the requested stack's texture data. If the texture data is empty, implicit, incomplete, or maps a component to the wrong block, the generated recipe will be absent or wrong before MineColonies module validation runs.

## Generated Ingredient Dump

No runtime ingredient dump was available.

Required runtime fields per generated ingredient:

- item id;
- display name;
- count;
- registry id;
- item/block tags;
- inferred category as MineColonies would see it;
- component id that produced it;
- raw material value from `getTexturedComponents()`.

Static finding: `findArchitectsCutterMatch(...)` does not currently expose component ids or rejection reasons. If a component id is absent from `texturedComponents`, it silently uses `Blocks.AIR`; if that produces an empty item stack, matching fails. That is an important possible Panel/single-material failure point.

## Module Validation Dump

No runtime module validation dump was available.

The current validation object is `match.genericRecipe()`, built by this mod from `materialStacks` and the assembled output. For every Architects Cutter-capable module, the runtime dump must capture:

```text
module label
module id
canLearn(ARCHITECTS_CUTTER)
isRecipeCompatible(match.genericRecipe())
which generated ingredient, if any, matched the module validator
known recipe result
disabled/held recipe token result
```

Static finding: Smart Info classification uses:

```text
module.canLearn(ARCHITECTS_CUTTER)
module.isRecipeCompatible(match.genericRecipe())
```

Actual teaching uses a deeper path in `SmartClipboardRecipeTeachingService`:

```text
module.isRecipeCompatible(match.genericRecipe())
module.getFirstRecipe(match.assembledOutput())
recipeManager.getRecipeId(match.recipeStorage())
module.isDisabled(token)
module.holdsRecipe(token)
recipeManager.checkOrAddRecipe(match.recipeStorage())
module.addRecipe(token)
```

So `isRecipeCompatible(...)` is the category gate, but real teaching also depends on recipe storage/token admission and `module.addRecipe(...)`.

## Wood Stair Investigation

Runtime target:

- Stripped Dark Oak Wood Stairs

Observed by user:

- Smart Info appears.
- Shape: Vanilla Stairs Compat appears.
- `Can learn: Stonemason` can appear despite wood material.

Runtime values not captured:

- generated material input;
- generated input tags;
- whether Sawmill passes;
- whether Stonemason passes;
- which ingredient triggers Stonemason;
- whether the generated recipe contains generic material options;
- whether the generated output differs from the hovered stack.

Static possibilities:

1. If `match.materialStacks()` contains a stone or masonry-tagged stack, then Stonemason is behaving correctly and `findArchitectsCutterMatch(...)` generated the wrong material input.
2. If `match.materialStacks()` contains stripped dark oak wood and Stonemason still passes, then the recipe object given to `isRecipeCompatible(...)` is broader than expected or MineColonies' validator accepts that input for another reason.
3. If `SmartInfoClassificationService` does not return Stonemason but the tooltip shows it, the source-of-truth path is being bypassed after recipe generation. This audit did not re-investigate resolver/fallback by instruction.

Most important runtime question:

```text
What is match.materialStacks() for the wood stair?
```

Without that value, the root cause cannot be confirmed.

## Panel Investigation

Runtime targets:

- Stripped Dark Oak Wood Panel
- Dark Oak Planks Panel

Observed by user:

- Smart Info appears.
- Shape: Panel appears.
- `Can learn` is missing.

Runtime values not captured:

- whether `findArchitectsCutterMatch(...)` succeeds;
- recipe id;
- generated input stacks;
- generated output stack;
- module pass/fail details.

Static possibilities:

1. `findArchitectsCutterMatch(...)` returns empty because Panel stacks have empty or implicit `MaterialTextureData`.
2. The textured component list is present, but `getTexturedComponents()` lacks the component ids required for Panel.
3. A missing component maps to `Blocks.AIR`, causing an empty material stack or no matching assembled output.
4. A match is produced, but no module accepts the generated recipe because the generated material input is wrong or absent.
5. A match is produced and modules accept it, but that result is discarded later. This cannot be evaluated here without re-entering resolver/report paths, which this audit was instructed not to revisit.

Most important runtime question:

```text
Does findArchitectsCutterMatch(...) return a CutterRecipeMatch for the actual Panel stack?
```

If yes, the next question is:

```text
What are match.materialStacks() and which module validators accept them?
```

## Working-Item Comparison

Runtime working controls requested:

- Framed Polished Diorite
- Light Blue Brick Extra Shingles

No runtime comparison was captured.

Expected comparison fields:

| Field | Working control | Failing Panel/Stair |
| --- | --- | --- |
| texture data present | not captured | not captured |
| textured component count | not captured | not captured |
| generated material stack ids | not captured | not captured |
| assembled output equals requested | not captured | not captured |
| recipe id | not captured | not captured |
| module validator pass list | not captured | not captured |

The comparison should identify whether working items have explicit material component maps while Panels/single-material outputs rely on implicit/default material data.

## Exact Divergence Point

Static source evidence narrows the likely first divergence to one of these points inside `findArchitectsCutterMatch(...)`:

1. `MaterialTextureData.readFromItemStack(requestedStack)` returns empty for a visible materialized item.
2. `textureData.getTexturedComponents()` lacks the component id required by the textured block.
3. The component map contains a material that is not a `Block`.
4. The generated material stack is a valid block item but represents the wrong material family.
5. The recipe manager returns candidates for that generated input, but assembled outputs fail `isSameMaterializedCutterOutput(...)`.
6. The selected recipe assembles a plausible output, but the `GenericRecipe` inputs are too broad or wrong for MineColonies module category validation.

The audit cannot choose between these without runtime data from the actual target stacks.

## Is The Generated Recipe Correct?

Not confirmed.

Static criteria for correctness:

- `match.recipeId()` is the shape recipe expected for the hovered output.
- `match.assembledOutput()` has the same item id and material texture map as the hovered stack.
- `match.materialStacks()` are the exact material inputs that visually produced the hovered stack.
- `match.genericRecipe().getInputs()` contains only those exact material stacks, not generic alternatives.
- `match.recipeStorage().getPrimaryOutput()` matches `match.assembledOutput()`.

Any mismatch means the classifier is validating the wrong recipe.

## Generic Or Materialized?

The mod-created `GenericRecipe` is intended to be materialized:

- output is `primary.output().copy()`;
- inputs are one-option lists generated from `materialStacks`;
- storage inputs are built from the same `materialStacks`.

However, it is only truly materialized if `materialStacks` were recovered correctly. The method name `genericRecipe()` is potentially misleading: it is an `IGenericRecipe` instance, but it should represent one exact generated materialized recipe.

## Is Module Validation Correct?

Static answer: module validation is probably the right category gate only after exact recipe generation is proven correct.

MineColonies DO compatibility is input-material driven. Therefore:

- A wood generated input should make wood-capable modules pass.
- A stone generated input should make Stonemason pass.
- A glass generated input should make Glassblower pass.
- Multi-material outputs may legitimately pass more than one module if exact generated inputs satisfy more than one validator.

If Stonemason passes for a wood output, the first suspicion should be the generated recipe inputs, not the module validator.

## Deeper MineColonies Validation

A deeper source of truth does exist for actual teaching:

```text
IRecipeStorage
-> IRecipeManager.checkOrAddRecipe(storage)
-> IToken
-> ICraftingBuildingModule.addRecipe(token)
```

`module.isRecipeCompatible(match.genericRecipe())` is necessary for category compatibility, but it is not the final teaching-admission operation. The current Smart Info `Can learn` path is therefore a compatibility hint, not full proof that `addRecipe(...)` would succeed at that moment.

The current mod's teaching service already uses the deeper path when actually teaching:

- compatibility check;
- known/held/disabled token checks;
- `checkOrAddRecipe(match.recipeStorage())`;
- `module.addRecipe(token)`.

## Confirmed Root Cause Or Theories

Confirmed from source:

1. `findArchitectsCutterMatch(...)` is the first place exact materialized recipe data is generated.
2. The classifier validates the recipe object produced by this method.
3. If this method emits no match or wrong material inputs, classification cannot recover correct `Can learn`.
4. Real teaching uses deeper recipe-storage/token admission after compatibility.

High-confidence theories:

1. Panel lacks `Can learn` because `findArchitectsCutterMatch(...)` fails before producing exact material stacks, or produces material stacks no module accepts.
2. Wood stairs can become Stonemason if `match.materialStacks()` is stone-like, generic, or otherwise not the expected wood material.
3. The next useful work is a diagnostic dump of `CutterRecipeMatch`, not another fallback or resolver change.

Medium-confidence theories:

1. Single-material/default DO stacks store material data implicitly, making `MaterialTextureData.isEmpty()` or `getTexturedComponents()` unreliable for Panels/trapdoors/pillars.
2. `isSameMaterializedCutterOutput(...)` may reject valid materialized outputs if assembled/requested texture maps differ structurally.
3. The generated `GenericRecipe` may be accepted by modules differently from the final `RecipeStorage` token admission path.

Low-confidence theories:

1. MineColonies has no Panel-compatible module category.
2. MineColonies Stonemason intentionally accepts the wood stair generated input.

## Recommended Next Step

Add temporary or debug-action-only diagnostics around `findArchitectsCutterMatch(...)` and `SmartInfoClassificationService` classification, then remove or keep only behind an intentional debug action.

The diagnostic should print, for the actual hovered/requested stack:

- stack item id, hover name, exact components, material key, fingerprint;
- texture data null/empty;
- textured block component ids;
- textured component map entries;
- generated `materialStacks`;
- recipe candidates returned by `getRecipesFor(...)`;
- per-candidate assembled output and rejection reason;
- selected recipe id;
- `GenericRecipe` input stacks and primary output;
- `RecipeStorage` inputs and primary output;
- per-module `canLearn`, `isRecipeCompatible`, known recipe, disabled/held token status;
- if practical, whether `checkOrAddRecipe(recipeStorage)` would produce a token without mutating state.

Do not add item-specific or shape-specific behavior before this runtime evidence exists.

## Risks

- Runtime diagnostics can be noisy if not guarded.
- Calling deeper recipe-manager token APIs for diagnostics may mutate state if not carefully avoided.
- Assuming `isRecipeCompatible(...)` equals teachability can overstate current learnability.
- Assuming recipe id means material category can reintroduce the broad generic fallback bug.
- Fixing Panels without understanding material stack generation can break stairs, trapdoors, pillars, panes, or framed outputs.

## Validation Plan

Run the guarded dump on:

1. Framed Polished Diorite.
2. Light Blue Brick Extra Shingles.
3. Stripped Dark Oak Wood Panel.
4. Dark Oak Planks Panel.
5. Stripped Dark Oak Wood Stairs.
6. Round Stripped Dark Oak Wood Pillar.
7. Stripped Dark Oak Wood Trapdoor.
8. One stone stair.
9. One stone panel.
10. One glass-based DO item.

For each, confirm:

- exact material inputs match the visible material;
- generated output matches the hovered stack;
- recipe storage primary output matches generated output;
- Sawmill/Stonemason/Glassblower/Mechanic/Fletcher pass only when an exact generated ingredient satisfies their validator;
- actual teaching path agrees with Smart Info learnability where capacity/disabled state allows.

## Validation

No Gradle build was run. No Minecraft runtime session was run. No exact per-item runtime recipe dump was available, so the requested runtime table remains unfilled rather than guessed.

## No-Change Confirmation

No implementation code was changed in this audit pass.
No permanent debug code was added.
No assets were changed.
No UI behavior was changed.
No Smart Info tooltip formatting was changed.
No tooltip text, order, colors, spacing, wrapping, or language keys were changed.
No requester/resolver, accordion, delivery arrow, scrolling, tabs, slots, sorting, or filtering behavior was changed.
No version metadata was changed in this audit pass.
No release jar was built.
No backup copy was created.

This repository already contained earlier rc.26 implementation changes before this audit report was created; this audit did not add more implementation changes.
