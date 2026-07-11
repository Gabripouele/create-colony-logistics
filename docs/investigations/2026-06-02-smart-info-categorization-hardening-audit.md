# Smart Info Categorization Hardening Audit

Date: 2026-06-02

## Scope

This audit investigated the Smart Info categorization and learnability pipeline after the recent fallback work.

Current observed state:

- Vanilla items can show useful `Can learn` information.
- Many Domum Ornamentum / Architects Cutter items show Smart Info and Shape.
- Some DO items, especially Panels, still show Shape only and miss `Can learn`.
- Some wood DO stairs can show Stonemason, which suggests recipe/category data is being joined at the wrong level.

This was a static source and bytecode audit. No Minecraft runtime session was run, so exact per-colony output for the named examples could not be truthfully measured.

## Non-Goals

- No implementation patches.
- No asset changes.
- No UI behavior changes.
- No Smart Info tooltip formatting, text, order, colors, spacing, wrapping, or language-key changes.
- No requester/resolver, accordion, delivery arrow, scrolling, tabs, slots, sorting, or filtering changes.
- No version bump.
- No release jar.
- No backup copy.
- No unrelated dirty files touched.

## MineColonies Source Of Truth Findings

The correct MineColonies source of truth is not the output item name, not the Architects Cutter recipe id alone, and not shape alone. It is the building module's recipe admission logic:

1. The candidate recipe must be a real `IGenericRecipe` / `IRecipeStorage` representing the item being taught or produced.
2. The target `ICraftingBuildingModule` must be able to learn that crafting type.
3. The module must accept the recipe via `module.isRecipeCompatible(recipe)`.
4. Real teaching additionally goes through `addRecipe(...)`, which uses `canRecipeBeAdded(...)`, checking recipe capacity and compatibility against the stored recipe token.

`ICraftingBuildingModule` exposes the relevant API:

- `canLearn(CraftingType)`
- `getSupportedCraftingTypes()`
- `isRecipeCompatible(IGenericRecipe)`
- `getIngredientValidator()`
- `getRecipes()`
- `getFirstRecipe(ItemStack)`
- `canRecipeBeAdded(IToken<?>)`
- `addRecipe(IToken<?>)`

MineColonies real add/remove recipe handling registers or finds an `IRecipeStorage` token, then calls `AbstractCraftingBuildingModule.addRecipe(token)`. That path can reject even a compatible-looking recipe if the module has no space or the token is otherwise not accepted.

Therefore `module.isRecipeCompatible(recipe)` is necessary, but not always sufficient to prove a recipe can actually be taught right now.

## Domum Ornamentum Category Logic

MineColonies' shared `AbstractCraftingBuildingModule.Domum.isRecipeCompatible(...)` does this:

- require the primary output item namespace to be `domum_ornamentum`;
- iterate the recipe input stacks;
- test each input stack against the building module's `getIngredientValidator()`;
- return true if any input stack satisfies that validator.

The DO worker modules provide material/category validators:

| Building module | Validator evidence from bytecode | Meaning |
| --- | --- | --- |
| Sawmill DO module | `CRAFTING_SAWMILL` tags plus `ItemTags.PLANKS` or `ItemTags.LOGS` | wood/log/plank material category |
| Stonemason DO module | `CRAFTING_STONEMASON` tags | stone/masonry material category |
| Glassblower DO module | `CRAFTING_GLASSBLOWER` tags | glass material category |
| Mechanic DO module | negation of sawmill/fletcher/stonemason/glassblower categories | fallback/other material category |
| Fletcher DO module | class exists; exact bytecode was not needed beyond module presence, but MineColonies lists a Fletcher DO module | likely fletcher-tagged materials |

This strongly indicates DO categorization is material/input driven, not output-shape driven. A wood stair should be Sawmill because its generated Architects Cutter input material should match the Sawmill validator. A stone stair should be Stonemason because its generated input material should match the Stonemason validator. Panels, trapdoors, pillars, fences, walls, stairs, slabs, framed outputs, shingles, and panes should be categorized by the material stack(s) in the exact generated recipe.

## Architects Cutter Recipe Source

MineColonies' `ArchitectsCutterCraftingType.findRecipes(...)` generates generic Architects Cutter recipes by:

- iterating every DO Architects Cutter recipe holder;
- resolving the materially textured result block;
- collecting valid skin/material options for each textured component;
- building a generic output stack;
- writing `MaterialTextureData.EMPTY` to the result stack;
- creating an `IGenericRecipe` with the recipe id, generic output, and valid material input choices.

That generic recipe proves shape-level compatibility options. It does not prove that a specific materialized output, such as Stripped Dark Oak Wood Panel, maps to a specific module. If our fallback joins a materialized DO stack to global data by `recipe:<id>`, it can attach category information from a generic recipe that may include multiple material families.

## Current Mod Logic Map

| Path | Input | Building list source | Exact materialized output? | Generic recipe-id use? | Risk |
| --- | --- | --- | --- | --- | --- |
| Report-entry DO path | real MineColonies `IRequest<?>` requested stack | `ColonyProductionInspector.inspect(colony, level, requestedStack)` | Yes, if `findArchitectsCutterMatch(...)` succeeds | No for `canLearn`; recipe id is metadata/key | Best current path, but fails if requested stack cannot reconstruct exact DO recipe |
| Known recipe check | module known recipes via `module.getFirstRecipe(requestedStack)` | `knownRecipeMatches(...)` | Compares requested stack and assembled output | No | Good for already-taught, but depends on stack equality / DO semantic equality |
| Exact can-learn check | `CutterRecipeMatch.genericRecipe()` from requested/materialized stack | `module.canLearn(ARCHITECTS_CUTTER)` and `module.isRecipeCompatible(exactGeneratedRecipe)` | Mostly yes | No | Necessary but not full real teaching proof; does not check recipe capacity |
| Global production fallback | all buildings/modules, all supported crafting types, `findRecipes(...)` | `module.isRecipeCompatible(genericRecipe)` | No for DO generic recipes | Yes | Can be too broad; can classify by shape recipe instead of exact material |
| Exact Resource Scroll DO enrichment | stored Resource Scroll -> server building -> `BuildingResourcesModule.getNeededResources()` | same `ColonyProductionInspector.inspect(...)` on row stack | Yes if row stack reconstructs exact recipe | Keys may still include recipe id | Scroll-only improvement; misses non-scroll hovers and fails if row stack lacks exact recipe metadata |
| SmartInfoResolver direct entry | already-known report `Entry` | existing entry lists | Inherits entry quality | No | Strong if entry was built correctly |
| SmartInfoResolver resource fallback | `ResourceLine` stack and compact keys | `productionIndex` match | Depends on matched production entry | Can match `recipe:<id>` | Main place where generic vs exact entries can be mixed |
| SmartInfoResolver item fallback | item id key if unique | smart info index | No material proof by itself | No | Safe only when unique; weak for same-item DO variants |
| DO material/fingerprint keys | stack components | smart info index / production index | Exact only when component representation aligns | No | Fails when single-material/default material data is implicit or empty |

## Panel Failure Analysis

Observed: Stripped Dark Oak Wood Panel and Dark Oak Planks Panel can show:

```text
Smart Info
Shape: Panel
```

but no `Can learn`.

High-confidence source path:

1. The visible row resolves enough DO context to identify a Panel shape, usually through recipe id or DO metadata.
2. The current fallback refuses to use recipe-id-only generic DO production data for `Can learn`, which is correct because that previously produced broad generic lists.
3. Exact `canLearn` only appears if the row reaches an exact production entry built by `ColonyProductionInspector.inspect(...)`.
4. If the Panel stack cannot reconstruct `CutterRecipeMatch`, or if the exact Resource Scroll enrichment never sees that row, the fallback entry has Shape but no exact teaching list.

Likely reasons Panels still miss `Can learn`, ranked:

1. The exact Resource Scroll enrichment is scroll-row-only. Clipboard/request-tree rows that do not have a stored Resource Scroll row will not get this exact fallback entry.
2. Panel stacks may have single-material/default DO component representation that lets Shape resolve by recipe id but does not produce a strong `CutterRecipeMatch`.
3. The row may match a generic `recipe:<panel-id>` production entry first, while exact material keys are absent or ambiguous.
4. The exact generated Panel recipe may not pass any module validator because the material input stack reconstructed from DO metadata is wrong or missing.
5. Less likely: MineColonies has no compatible DO recipe/module for that Panel shape. Static evidence points more toward missing exact materialized recipe reconstruction than missing shape support.

The omission is probably correct under the current safety rules when exact proof is absent. It is still likely a bug in data gathering because the material category should be derivable from the same exact recipe/storage MineColonies would teach.

## Wood DO Stairs Showing Stonemason

Observed: some wood DO stairs can be labeled learnable by Stonemason.

High-confidence theory:

- A materialized wood stair is being joined to production metadata by shape recipe id, such as a generic stairs-compatible recipe, instead of by exact generated material input.
- MineColonies generic Architects Cutter recipes collect valid material/skin options for the shape. If Stonemason accepts any stone input option on the same generic shape recipe, a recipe-id-only bridge can make the wood materialized output inherit Stonemason.

Other plausible contributors:

- `recipe:<id>` keys are too broad for DO, because recipe id usually represents shape, not material combination.
- `recipe-output:<id>|exactStackKey(output)` is better, but only if the output is the exact materialized output, not the generic empty-material output.
- `isExactDomumProductionMatch(...)` currently treats priority <= output and not `recipe:` as exact. A `recipe-output` key can still be exact or weak depending on whether the production entry's output stack is materialized or generic.
- If the generated exact recipe input for a wood stair is accidentally reconstructed as a stone block, `module.isRecipeCompatible(...)` would truthfully return Stonemason for the wrong generated recipe.

The most likely root is joining at the recipe id / generic shape level, not a MineColonies categorization bug.

## Vanilla Control Analysis

Vanilla fallback is less dangerous because vanilla `RecipeCraftingType.findRecipes(...)` emits concrete vanilla recipe outputs and concrete inputs. For normal vanilla crafting:

- output stack identity usually represents the actual item;
- input stacks usually represent the actual material category;
- recipe id does not usually collapse many materialized variants into one empty-material shape recipe.

Still, vanilla is not guaranteed merely by item id. Correctness still depends on MineColonies module validators and `module.isRecipeCompatible(recipe)`.

Recommended vanilla controls for future runtime validation:

| Control | Expected category basis | Expected result |
| --- | --- | --- |
| Oak stairs or planks | wood input tags | Sawmill / wood-capable module |
| Stone bricks or sandstone | masonry input tags | Stonemason |
| Rails / lantern / mechanism-like item | mechanic recipe/input tags | Mechanic |
| Non-teachable decorative/random item | no compatible module or recipe | no `Can learn` |

Static conclusion: vanilla fallback is structurally safer than DO fallback, but should still be validated with runtime module results.

## DO Shape/Material Matrix

Runtime values were not captured, so this matrix separates expected MineColonies source-of-truth behavior from current risk.

| Shape | Wood material expected | Stone material expected | Glass material expected | Metal/mechanic-like expected | Multi-material expected | Current risk |
| --- | --- | --- | --- | --- | --- | --- |
| Stairs | Sawmill if exact input is log/plank/wood-tagged | Stonemason if exact input is masonry-tagged | Glassblower if glass-tagged | Mechanic if not other DO categories | Depends on any accepted exact input component | Wood can show Stonemason if generic shape recipe id wins |
| Slab | Same as material | Same as material | Same as material | Same as material | Depends on components | Same recipe-id risk |
| Wall | Usually material category; stone walls likely Stonemason | Stonemason | Glassblower if glass wall recipe exists | Mechanic fallback | Depends on components | Shape support unknown without runtime recipes |
| Fence | Sawmill for wood | Stonemason for stone-like variants if supported | Glassblower if supported | Mechanic fallback | Depends on components | Shape support and material reconstruction need runtime check |
| Trapdoor | Sawmill for wood | Stonemason for stone-like variants if supported | Glassblower if supported | Mechanic fallback | Depends on components | Previously broad generic list; now may be Shape-only |
| Panel | Sawmill for wood panels | Stonemason for stone panels | Glassblower for glass panels | Mechanic fallback | Depends on components | Missing `Can learn` likely due exact recipe reconstruction/enrichment gap |
| Pillar | Sawmill for wood pillar | Stonemason for stone pillar | Glassblower if supported | Mechanic fallback | Depends on components | Single-material representation risk |
| Framed | Depends on material components; multiple modules possible only if exact recipe has multiple acceptable inputs | Same | Same | Same | Could legitimately show multiple buildings if exact recipe inputs satisfy multiple validators | Must avoid generic same-shape joins |
| Shingle | Based on exact material input | Based on exact material input | Based on exact material input | Based on exact material input | Depends on components | Needs runtime recipe list |
| Pane | Based on exact material input, likely Glassblower for glass | Stonemason if stone-like pane exists | Glassblower | Mechanic fallback | Depends on components | Pane/framed representation can fail semantic matching |
| Other DO cutter shapes | Based on exact generated input material | Based on exact generated input material | Based on exact generated input material | Based on exact generated input material | Depends on components | Need runtime recipe dump |

Do not treat this table as a hard-coded category map. The correct answer should come from exact generated recipe inputs tested against MineColonies module validators.

## Data Gathering Level Comparison

| Level | Correctness | Problems |
| --- | --- | --- |
| A. Hovered item stack only | Good for exact identity if components are complete | Too late for server module checks; can lack request/building context |
| B. Resource Scroll builder row | Good for visible Scroll rows | Scroll-only; not enough for Clipboard and request-tree by itself |
| C. `SmartClipboardReport.Entry` | Good when generated from real request | Root-only or request-entry dependent; can miss fallback-only rows |
| D. MineColonies request token / request graph | Best relationship identity | Not available for every Resource Scroll row |
| E. Exact generated MineColonies recipe storage | Best item/material/shape proof | Requires reliable DO recipe reconstruction |
| F. Building module exact learnability check | Best hut category proof | Can be expensive; must handle recipe capacity vs compatibility distinction |
| G. Global production index | Useful broad fallback for vanilla/concrete outputs | Too generic for DO recipe-id-only materialized outputs |

Canonical source should be:

`exact generated recipe/storage for the materialized output` plus `MineColonies module validation`, anchored to request token or resource row when available.

Global production index should remain a secondary fallback for concrete vanilla outputs and shape-only DO context. It should not be the canonical source for DO hut categorization unless keyed by exact materialized recipe/output.

## Confirmed Divergences

High confidence:

- `recipe:<id>` is too broad for DO categorization because DO recipe ids are shape-level identities.
- Generic Architects Cutter recipes use empty material data and valid material option lists. They are not exact materialized outputs.
- MineColonies DO hut category is based on recipe input material validators, not output shape.
- Current logic mixes exact entries, generic production entries, Resource Scroll-only enrichment, recipe id fallback, material keys, and item fallback in one resolver pipeline.

Medium confidence:

- Panel misses `Can learn` because exact materialized recipe validation is not consistently reached or serialized outside Resource Scroll enrichment.
- Wood stairs show Stonemason because generic shape recipe compatibility leaks through a recipe-id or generic recipe-output fallback.
- `isRecipeCompatible(...)` without `addRecipe(...)`/capacity check may overstate "can learn right now", though it is the same core compatibility gate used by teaching.

Low confidence without runtime:

- Whether specific Panel variants fail because of DO component representation, wrong recipe assembly, missing MineColonies recipe, or no module capacity.
- Whether particular wood stairs are matched through `recipe:`, `recipe-output:`, item id fallback, or wrong exact recipe reconstruction.

## Recommended Hardened Architecture

1. Separate concepts in the data model:
   - Shape metadata: recipe id / DO block id.
   - Exact recipe identity: materialized output key plus exact generated recipe/storage key.
   - Hut category: result of exact module validation against exact generated recipe inputs.
   - Known-by: actual module stored recipe match.
   - Can-learn: module can accept the exact generated recipe and, if possible, can actually add the recipe token.

2. Centralize exact recipe classification server-side:
   - Create one service that accepts an exact stack plus optional request token/resource-row context.
   - It resolves `CutterRecipeMatch`.
   - It builds or references exact `IRecipeStorage`.
   - It evaluates MineColonies modules with the same logic used by teaching.
   - It returns typed results: `knownBy`, `canLearnExact`, `shapeOnlyReason`, `ambiguityReason`.

3. Keep global production fallback, but restrict DO use:
   - DO recipe-id-only fallback may display Shape.
   - DO recipe-id-only fallback must not display `Can learn`.
   - DO `Can learn` must come from exact materialized recipe classification.

4. Make all hover paths consume the same classified result:
   - Clipboard main row.
   - Request-tree row.
   - Resource Scroll row.
   - Future Smart Info entry points.

5. Avoid hard-coded shape or material maps:
   - Use MineColonies `getIngredientValidator()` and exact generated recipe inputs.
   - Use tags as MineColonies sees them, not local string guesses.

6. Add diagnostics before another behavior change:
   - For each candidate stack, log recipe id, exact output key, material candidates, input stack ids/tags, module validator pass/fail, recipe capacity, known recipe match, selected production entry key, and resolver match priority.

## Performance Risks And Caching

Correct categorization can be computed without scanning everything on every open if the system caches the right layer.

Recommended cache levels:

- Per-report recipe lookup cache by `CraftingType`, already useful for global scans.
- Exact DO classification cache by:
  `dimension + colony id + exact stack key + recipe id + material key`.
- Module capability snapshot by:
  `colony id + building position + module id + crafting type + recipe mode/version`.
- Short TTL for production data, invalidated when a recipe is taught/removed, building changes, or scroll contents change.

Avoid:

- Per-hover server queries for every tooltip; that risks latency and packet spam.
- Full global production scans for DO categorization on every open.
- Client-only exact classification; the client cannot authoritatively ask server building modules.

Safest architecture:

- Build fast request/report data on open.
- Compute exact DO classifications only for visible or referenced stacks: report entries, request-tree DO stacks, stored Resource Scroll resource rows.
- Cache exact classifications server-side.
- Use global production as vanilla/concrete fallback, not as DO categorization authority.

## Future Validation Plan

Runtime validation should capture for each target item:

- item id, display name, components patch, full components summary;
- DO block id, recipe id, material key, fingerprint;
- exact `CutterRecipeMatch` present/absent;
- exact generated recipe inputs;
- input stack tags relevant to Sawmill/Stonemason/Glassblower/Fletcher/Mechanic;
- each module's `canLearn(ARCHITECTS_CUTTER)` result;
- each module's `isRecipeCompatible(exactGeneratedRecipe)` result;
- whether `addRecipe` / recipe capacity would accept the token if practical to inspect safely;
- known recipe match result;
- production index entry matched by resolver, including key and priority;
- final tooltip lines.

Cases:

1. Stripped Dark Oak Wood Panel.
2. Dark Oak Planks Panel.
3. Wood DO stairs currently showing Stonemason.
4. Stone DO stairs expected Stonemason.
5. Wood trapdoor, fence, pillar, slab.
6. Stone wall, panel, pillar.
7. Glass pane/framed glass variant.
8. Framed Polished Diorite and Light Blue Brick Extra Shingles.
9. Vanilla wood item, stone item, mechanic item, non-teachable item.
10. Already-taught DO recipe.

Expected hardened behavior:

- Shape may appear from recipe id.
- `Can learn` appears only from exact module validation.
- Wood variants do not inherit Stonemason through generic shape recipes.
- Panels either show exact category or a diagnostic reason why exact categorization is unavailable.
- Vanilla fallback remains useful.

## No-Change Confirmation

No implementation code was changed in this audit.
No assets were changed.
No UI behavior was changed.
No Smart Info tooltip formatting was changed.
No tooltip text, order, colors, spacing, wrapping, or language keys were changed.
No requester/resolver, accordion, delivery arrow, scrolling, tabs, slots, sorting, or filtering behavior was changed.
No version metadata was changed.
No Gradle build was run.
No release jar was built.
No backup copy was created.

