# DO Fallback Can-Learn Breadth Audit

Date: 2026-06-02

## Scope

This audit investigated why the global Smart Info fallback can produce a broad `Can learn` list for single-material Domum Ornamentum / Architects Cutter rows after the centralized resolver work.

Example observed by the user:

- Stripped Dark Oak Wood Trapdoor
- Shape: Vanilla Trapdoors Compat
- Can learn: Sawmill, Stonemason, Fletcher, Mechanic, Glassblower

No implementation code, UI, tooltip formatting, language keys, version metadata, release jar, or backup folder changes were made in this pass.

## Non-Goals

- Do not change locked Smart Info tooltip formatting.
- Do not change tooltip text, ordering, colors, spacing, wrapping, or language keys.
- Do not change Smart Clipboard accordion, requester/resolver display, scroll behavior, delivery indicators, tabs, slots, or vanilla tooltip fallback.
- Do not bump the mod version.
- Do not build or copy a release jar.
- Do not implement a fix in this pass.

## Observed Issue

The new global fallback can make a single-material DO row show a much wider `Can learn` list than expected.

The example list is not proven to be arbitrary. Those buildings are not included merely because they exist, or because they have any crafting module. They are included through MineColonies crafting-module APIs. However, the current fallback can still be too broad for the exact hovered materialized output, because it can join the hovered DO row to global production metadata by Architects Cutter recipe id instead of by exact generated materialized recipe/output validation.

## Exact Code Path

Global fallback production data is created during report analysis:

1. `RequestAnalysisService.analyze(...)` calls `ColonyProductionInspector.inspectGlobal(colony, level)`.
2. `inspectGlobal(...)` walks every colony building and every `ICraftingBuildingModule`.
3. For learnable recipes, `learnableRecipes(module, level)` reads each supported crafting type.
4. `safeCanLearn(module, type)` calls `module.canLearn(type)`.
5. `CraftingType.findRecipes(...)` supplies recipes for that type.
6. `safeIsRecipeCompatible(module, recipe)` calls `module.isRecipeCompatible(recipe)`.
7. Compatible recipe outputs are accumulated as `ProductionInfo.canLearn`.
8. `ProductionAccumulator` adds compact keys, including recipe keys for recipe-backed outputs.
9. `SmartClipboardReport` serializes the global `productionIndex`.

Scroll hover fallback then uses that data:

1. `SmartInfoResolver.resolveResource(...)` receives a `ResourceLine`.
2. `resourceRowKeys(...)` adds exact stack, resource, DO material/fingerprint, item, and recipe-derived keys where available.
3. For DO rows, it calls `DomumOrnamentumRequestInspector.findArchitectsCutterMatch(...)` on the hovered/resource stack and adds `recipe:<id>`.
4. If direct entry, index, parent, semantic, and unique-item matches fail, `productionFallback(...)` tries `report.productionIndex()`.
5. If a production entry matches, `fallbackEntry(...)` creates an ephemeral Smart Info entry with the hovered stack and the global production `knownBy` / `canLearn` lists.
6. The existing locked tooltip builder renders that entry.

## MineColonies Recipe Source

Bytecode inspection of MineColonies' `ArchitectsCutterCraftingType.findRecipes(...)` shows it generates generic Architects Cutter recipes by:

- iterating all Architects Cutter recipe holders;
- finding the materially textured result block;
- collecting valid material/skin options for textured components;
- setting `MaterialTextureData.EMPTY` on the result stack;
- building a `GenericRecipe` with the cutter recipe id, generic output, and valid material input choices.

That means global learnability is based on a generic shape recipe and its valid material options, not on the exact visible materialized stack such as "Stripped Dark Oak Wood Trapdoor".

## Is The Broad List Wrong?

Technically, not proven wrong from static inspection alone.

If Sawmill, Stonemason, Fletcher, Mechanic, and Glassblower appear, the current source path indicates each one passed:

- `module.canLearn(type)` for the relevant crafting type;
- recipe emission from MineColonies' Architects Cutter crafting type;
- `module.isRecipeCompatible(genericRecipe)`.

So the list is likely valid according to MineColonies' generic recipe compatibility API.

Player-facing, it can still be over-broad for the exact hovered DO row. The fallback can connect the hovered materialized output to production metadata through `recipe:<id>`, while the production metadata itself was built from the generic Architects Cutter recipe with empty material data. That does not prove each building can learn the exact generated materialized recipe/output that the normal report-entry path uses.

## Exactness Comparison

| Check | Existing report-entry path | Global production fallback |
| --- | --- | --- |
| Uses actual request context | Yes | No |
| Resolves cutter match from requested/hovered DO stack | Yes | Hover side yes; production-index side uses generic recipes |
| Builds exact generated recipe/storage for materialized output | Yes | No, not for global index |
| Validates module against exact generated cutter recipe | Yes, through the matched `CutterRecipeMatch` path | No; validates module against MineColonies generic recipe |
| Checks exact output/materialized stack | Stronger for known recipes and generated match | Weaker; may bridge by `recipe:<id>` |
| Uses recipe id | Yes, as metadata | Yes, and it can be the decisive fallback key |
| Uses exact ingredient/material combination | Yes when cutter match succeeds | Generic valid skin/material choices, not necessarily the exact hovered material |

## Module Inclusion Table

| Building/module label | Why it can appear today | Exact materialized-output proof? |
| --- | --- | --- |
| Sawmill | Its crafting module accepted the generic Architects Cutter recipe through `module.canLearn(type)` and `module.isRecipeCompatible(recipe)`. | Not required by the global fallback. |
| Stonemason | Same generic recipe compatibility path. | Not required by the global fallback. |
| Fletcher | Same generic recipe compatibility path. | Not required by the global fallback. |
| Mechanic | Same generic recipe compatibility path. | Not required by the global fallback. |
| Glassblower | Same generic recipe compatibility path. | Not required by the global fallback. |

The exact set depends on the colony's buildings, modules, supported crafting types, MineColonies recipe compatibility rules, and the generated generic Architects Cutter recipe.

## Single-Material Vs Multi-Material

Single-material DO rows are more likely to hit the global fallback because the exact report-entry/index path can fail when material data is empty, implicit, or represented differently from the request/report stack. Once the resolver falls through to global production, the `recipe:<id>` bridge can attach a generic recipe-level `Can learn` list.

Multi-material or already well-indexed DO rows are more likely to resolve through a real `SmartClipboardReport.Entry`, exact stack key, material key, request-tree key, or semantic materialized-output match. Those paths preserve the original report-entry production metadata and avoid the generic fallback list.

If a multi-material row also falls through to global production by recipe id, it has the same theoretical breadth risk.

## Vanilla Comparison

Vanilla fallback is less risky because MineColonies vanilla `RecipeCraftingType.findRecipes(...)` recipes normally have concrete vanilla outputs. Exact stack and output keys can represent the hovered item more directly.

The DO case is different: the global Architects Cutter recipe output is a generic materially textured result with empty material data, and the hovered row is a materialized DO stack. The recipe-id bridge fills that gap, but it is broader than exact materialized-output identity.

## Root Cause

High confidence: the broad `Can learn` list comes from the global production fallback using generic MineColonies Architects Cutter learnable recipes, then matching a materialized DO row to that global production entry by recipe id.

Medium confidence: the listed buildings are technically compatible with the generic Architects Cutter recipe under MineColonies' own module compatibility checks.

High confidence: the fallback is weaker than the normal report-entry path for exact materialized DO outputs, because it does not require successful exact generated recipe/output validation per module before showing `Can learn`.

Low confidence: the issue is item-name-specific. Trapdoors, pillars, fences, walls, panels, panes, and similar shapes are more likely to expose this because single-material metadata can fail earlier and push resolution into fallback.

## Recommended Future Behavior

Keep the current direct/index/report-entry Smart Info paths authoritative.

For DO global fallback:

1. Resolve the hovered/resource stack with `findArchitectsCutterMatch(...)`.
2. Validate `canLearn` against the exact generated `CutterRecipeMatch.genericRecipe()` or shared exact production lookup, not only the generic `ArchitectsCutterCraftingType.findRecipes(...)` recipe.
3. Do not use recipe-id-only matches to populate `canLearn` for DO materialized rows unless exact recipe/output validation has succeeded.
4. If exact validation cannot be proven, either omit the `Can learn` line or show only exact `knownBy` data.
5. Preserve strict ambiguity rules: no display-name matching, no broad same-item guessing, and no choosing between multiple same-priority candidates.
6. Keep vanilla fallback behavior separate, because concrete vanilla outputs are less ambiguous.

## Risks Of Future Fix

- Hiding generic `Can learn` may remove useful hints for buildings that really can learn the generic shape recipe.
- Exact generated DO recipe validation may fail for the same single-material metadata reasons that caused the fallback to be needed.
- Sharing exact production lookup logic can increase coupling between resolver fallback and request analysis.
- A recipe-id-only fallback may still be useful for Shape display, but it should not imply exact materialized `Can learn` support.

## Validation Plan

Future implementation should test:

1. Stripped Dark Oak Wood Trapdoor single-material row.
   Expected: `Can learn` appears only for modules that pass exact generated DO recipe validation, or is hidden cleanly.

2. Round Stripped Dark Oak Wood Pillar, fences, walls, and panels.
   Expected: no broad generic module list unless exact validation succeeds.

3. Multi-material DO control such as Framed Polished Diorite.
   Expected: existing report-entry Smart Info remains unchanged.

4. Known exact recipe already taught.
   Expected: known-by behavior remains accurate and already-taught Smart Info still appears.

5. Vanilla controls.
   Expected: vanilla fallback remains unchanged and does not use DO recipe-id-only behavior.

6. Multiple same-recipe or same-item DO variants.
   Expected: no cross-attachment and no ambiguous fallback.

## Validation

This was a static source and dependency-bytecode audit. A Gradle build was not run because no implementation files, resources, version metadata, or build files were changed.

## No-Change Confirmation

No implementation code was changed.
No assets were changed.
No UI behavior was changed.
No Smart Info tooltip formatting was changed.
No tooltip text, order, colors, spacing, wrapping, or language keys were changed.
No version metadata was changed.
No release jar was built.
No backup copy was created.
