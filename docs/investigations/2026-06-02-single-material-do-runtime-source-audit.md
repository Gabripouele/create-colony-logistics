# Single-Material DO Runtime Source Audit

Date: 2026-06-02

## Scope

This audit attempted to identify the real runtime source of remaining Smart Info failures for single-material Domum Ornamentum / Architects Cutter items in `create_colony_logistics-0.3.0-rc.19.jar`.

Baseline:

- Centralized `SmartInfoResolver` is implemented.
- Clipboard, request-tree, and Smart Resource Scroll hovers route through the resolver.
- Static inspection suggests the remaining failure may occur before resolver matching, in DO metadata extraction, cutter recipe reconstruction, Smart Info entry admission, or production lookup.

## Non-Goals

- No implementation fix.
- No permanent debug code.
- No UI changes.
- No Smart Info tooltip formatting/text/order/color/spacing/wrapping/language-key changes.
- No version bump.
- No release jar.
- No backup copy.

## Diagnostic Method Used

No new temporary diagnostics were added in this pass.

I searched for existing runtime logs and debug artifacts in the workspace and inspected the existing debug hooks:

- `RequestAnalysisService.debugDump(...)`
- `DomumOrnamentumRequestInspector.debugCutterSummary(...)`
- `SmartClipboardScreen.dumpSelectedScrollDebug(...)`
- `ServerboundSmartClipboardDebugPacket`
- `ServerboundSmartScrollDebugPacket`

No usable runtime log artifact for the requested working/failing item set was present in the repository. Because no Minecraft client/server session was run during this pass, the requested per-item runtime values could not be captured truthfully.

## Existing Debug Coverage

Existing debug hooks can already capture some relevant data:

- request token and request class;
- requestable class and state;
- requested stack name / registry id / component presence;
- DO classification;
- display stack count;
- `IStackBasedTask` and `IDeliverable` presence;
- requester/building/worker diagnostics;
- dependency graph source;
- `DomumOrnamentumRequestInspector.debugCutterSummary(...)` for requested stack;
- Smart Resource Scroll selected stack and rendered resource rows.

Existing debug hooks do not yet capture all requested fields:

- explicit `hasArchitectsCutterMetadata` result;
- explicit `isMaterializedArchitectsCutterOutput` result;
- `canonicalMaterialKey` result;
- `MaterialTextureData` null/empty state;
- textured component count and material component ids;
- candidate cutter recipe count;
- rejection reason for each assembled output;
- whether `SmartClipboardReport.isSmartInfoEntry(...)` passed and why;
- generated Smart Info keys per entry;
- generated Smart Info index entries per entry;
- resolver ambiguity/no-candidate reason.

## Runtime Evidence Status

| Item | Runtime data captured? | Notes |
| --- | --- | --- |
| Stripped Dark Oak Wood Stairs / Vanilla Stairs Compat | No | Known working control, but no current log was available. |
| Round Stripped Dark Oak Wood Pillar | No | Runtime branch not confirmed. |
| Stripped Dark Oak Wood Trapdoor | No | Runtime branch not confirmed. |
| Glass Framed Pane | No | Runtime branch not confirmed. |
| Framed Sea Lantern | No | Runtime branch not confirmed. |
| Light Blue Brick Extra Shingles | No | Runtime branch not confirmed. |

## Current Evidence From Code Inspection

The strongest static evidence remains upstream of the resolver:

1. `findArchitectsCutterMatch(...)` returns empty if `MaterialTextureData` is null/empty.
2. `findArchitectsCutterMatch(...)` reconstructs cutter input only from `MaterialTextureData.getTexturedComponents()`.
3. `materialCandidatesFromComponents(...)` can scrape material ids from stack components, but it is not used by the main `findArchitectsCutterMatch(...)` path.
4. `canonicalMaterialKey(...)`, `hasArchitectsCutterMetadata(...)`, and `isMaterializedArchitectsCutterOutput(...)` all depend on non-empty texture data.
5. `SmartClipboardReport.isSmartInfoEntry(...)` can exclude a DO entry if recipe id, materialized-output detection, and metadata detection all fail.
6. `ColonyProductionInspector.inspect(...)` recomputes `findArchitectsCutterMatch(...)`, so `canLearn` can be empty for the same reason even if an entry exists.

## Provisional Failure Stage

Runtime confirmation is still required, but the most likely failure stage is:

`DomumOrnamentumRequestInspector.findArchitectsCutterMatch(...)`

or one of the shared metadata gates immediately before/around it:

- empty `MaterialTextureData`;
- empty `texturedComponents(...)`;
- no reconstructed material stack;
- no candidate cutter recipe;
- assembled output rejected by `isSameMaterializedCutterOutput(...)`;
- resulting entry excluded by `SmartClipboardReport.isSmartInfoEntry(...)`.

## Key Questions Status

- Do failing single-material DO items have empty `MaterialTextureData`?
  Not confirmed at runtime.

- Do they have empty `texturedComponents(...)`?
  Not confirmed at runtime.

- Does `materialCandidatesFromComponents(...)` recover useful materials when the main material texture path fails?
  Not confirmed at runtime. Static inspection shows this fallback is not used by the main matcher.

- Does `findArchitectsCutterMatch(...)` return empty for failing items?
  Not confirmed at runtime.

- If recipe candidates exist, why are they rejected?
  Not confirmed at runtime. Current code has no retained per-candidate rejection logging.

- Does `isSameMaterializedCutterOutput(...)` reject visually valid outputs?
  Not confirmed at runtime.

- Are failing items normal report entries but excluded from Smart Info index?
  Not confirmed at runtime.

- Are failing items valid Smart Info entries but rejected by resolver ambiguity?
  Not confirmed at runtime. Based on static inspection, this is less likely than upstream missing metadata.

- Is the failure in production lookup only, or earlier in recipe/metadata detection?
  Not confirmed at runtime. Static inspection suggests earlier recipe/metadata detection can also suppress production lookup.

## Resolver Architecture Status

The resolver architecture appears to be routed correctly from code inspection:

- Clipboard main row hovers call `SmartInfoResolver.resolveEntry(...)`.
- Request-tree hovers call `SmartInfoResolver.resolveStack(stack, parentEntry)`.
- Scroll resource-row hovers call `SmartInfoResolver.resolveResource(...)`.

If single-material items do not become Smart Info entries or do not generate useful keys, resolver routing cannot recover them. The resolver can only choose among indexed Smart Info entries or direct Smart Info entries.

## Recommended Runtime Diagnostic Plan

Add temporary guarded diagnostics, then remove them after capture unless placed behind an intentional existing debug action.

Best low-risk location:

- Extend the existing Smart Clipboard debug dump path rather than adding always-on logging.
- Add the requested DO metadata fields to `RequestAnalysisService.debugDump(...)` and `DomumOrnamentumRequestInspector.debugCutterSummary(...)`.
- Add per-candidate rejection reasons in a temporary diagnostic-only cutter summary helper.

Required output per item:

- item id;
- display name;
- description id;
- components patch summary;
- full components summary;
- `isDomumOrnamentumStack`;
- `hasArchitectsCutterMetadata`;
- `isMaterializedArchitectsCutterOutput`;
- `exactComboFingerprint`;
- `canonicalMaterialKey`;
- `MaterialTextureData` null/empty;
- textured component count;
- textured block component count;
- material component ids;
- material candidate count and values;
- cutter recipe candidate count;
- selected recipe id;
- generated input summary;
- assembled output key;
- assembled output comparison result;
- exact rejection reason;
- `RequestReportEntry` created;
- `isSmartInfoEntry` result and reason;
- generated Smart Info keys;
- generated Smart Info index entries;
- resolver input keys;
- resolver result / ambiguity reason;
- production `knownBy`, `canLearn`, already-taught state.

## Recommended Future Implementation Plan

After runtime capture identifies the exact branch:

1. If `MaterialTextureData` is empty but component-string candidates exist, add a safe fallback material-key and cutter-input path.
2. If candidate recipes exist but assembled output comparison is too strict, relax comparison only for validated single-material DO outputs.
3. If entries exist but fail `isSmartInfoEntry(...)`, add a narrowly scoped admission path for DO stacks associated with a validated Architects Cutter recipe or exact known MineColonies recipe.
4. If production lookup recomputation is stricter than report generation, share or pass the successful `CutterRecipeMatch`.
5. Keep resolver ambiguity rules unchanged unless runtime evidence points specifically at resolver rejection.

## Risks

- Adding diagnostics without a guard can spam logs.
- Component-string parsing can over-detect resource ids.
- Relaxing single-material matching without recipe validation can attach Smart Info to the wrong DO shape.
- Retaining debug-only code would add noise and maintenance cost.

## Validation

No Gradle build was run. No code changes were made in this pass, so a build was not needed.

## No-Change Confirmation

No permanent code changes were made.
No temporary code was added.
No UI changes were made.
No assets were changed.
No Smart Info tooltip formatting was changed.
No version metadata was changed.
No release jar was built.
No backup copy was created.

## Blocker

The requested real runtime comparison cannot be completed from the current workspace alone because no runtime dump/log for the working and failing items is available, and no Minecraft session was run during this pass.
