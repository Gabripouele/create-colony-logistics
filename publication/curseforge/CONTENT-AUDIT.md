# Create Colony Logistics — CurseForge Publication Content Audit

Audit date: 2026-07-11

## 1. Executive Summary

Create Colony Logistics 0.3.6 is a NeoForge 1.21.1 addon whose primary player-facing feature is a separate, craftable Smart Colony Clipboard. The item links explicitly to a colony, presents active requests in a custom interface, stores up to 18 MineColonies Resource Scrolls, and stores and opens one MineColonies Colony Map. Its request view adds search, important-request filtering, request-tree details, warehouse availability, requester/worker details, and production/recipe information where the server can resolve it.

The shipped Create–MineColonies work is narrowly scoped to Create inventory reads against MineColonies-managed inventories: short-lived stock-summary caching, cached factory-panel restocker counts, a warehouse-aware Stockpile Switch snapshot, and a guard against malformed extraction data. Actual insertion and extraction remain authoritative.

The only current binary candidate is `build/libs/create_colony_logistics-0.3.6.jar` (295,456 bytes; SHA-256 `441F89F34AC21B57C41E82435E7DD4FB47A698D33432E91CB603980853FF153A`). Static source/JAR correspondence is strong, but the working tree is not clean and the current candidate has not been run in the interactive QA client. The existing QA runtime used an older 360,116-byte JAR and NeoForge 21.1.235. Consequently, the candidate is not yet sufficiently verified for CurseForge upload and no publication screenshots could be truthfully captured in this audit.

## 2. Authoritative Baseline

| Field | Audited value | Authority/status |
|---|---|---|
| Minecraft | 1.21.1; metadata range `[1.21.1,1.22)` | Candidate metadata |
| NeoForge | 21.1.226; metadata range `[21.1.226,)` | Source and candidate metadata |
| Create | 6.0.10; metadata range `[6.0.10,6.1.0)` | Source and candidate metadata |
| MineColonies | 1.1.1041-1.21.1; metadata range `[1.1.1041-1.21.1,)` | Source and candidate metadata |
| Structurize | 1.0.782-1.21.1-snapshot | Complete QA modpack; transitive MineColonies requirement, not declared directly by this mod |
| BlockUI | 1.0.205-1.21.1 | Complete QA modpack; transitive MineColonies requirement |
| MultiPiston | 1.2.57-1.21.1 | Complete QA modpack; transitive MineColonies requirement |
| Domum Ornamentum | 1.0.220-snapshot | Complete QA modpack artifact `domum-ornamentum-1.0.220-snapshot-main.jar`; required directly for Smart Info/Architect's Cutter data paths |
| Java | 21 | Gradle toolchain and mixin compatibility level |
| Mod version | 0.3.6 | Source and candidate metadata |
| Mod ID | `create_colony_logistics` | Source and candidate metadata |

The candidate was built 2026-07-11 06:07:06 after commit `c60b1f5` (“Post QA cleanup commit”). The current working tree additionally contains uncommitted publication-name changes and removal of diagnostic classes/packets. The JAR contains the new display name and does not contain the removed diagnostic classes, so it corresponds to the present working source in those material respects. It is the sole latest non-sources binary in `build/libs`, but no release manifest, signed tag, or upload-candidate marker identifies it as approved.

**[REQUIRED: Confirm that SHA-256 441F89F34AC21B57C41E82435E7DD4FB47A698D33432E91CB603980853FF153A is the intended CurseForge upload candidate after completing client QA.]**

## 3. Production JAR Audit

### Identity and metadata

| Field | Value |
|---|---|
| Display name | Create Colony Logistics |
| Mod ID | `create_colony_logistics` |
| Version | 0.3.6 |
| Loader | `javafml`, loader range `[4,)` |
| License metadata | MIT |
| Authors | `Create: Colony Logistics contributors` |
| Embedded description | `Integrates Create logistics with MineColonies stock monitoring and colony request management.` |
| Issue tracker | `https://github.com/create-colony-logistics/create-colony-logistics/issues` |
| Other project links | None embedded |
| Loading side | BOTH for Minecraft, NeoForge, Create, and MineColonies dependencies |
| Icon reference | None in metadata |

Publication-facing naming is not fully consistent: the candidate display name omits the colon, while `README.md`, `LICENSE`, author metadata, and older runtime evidence retain “Create: Colony Logistics.” The README and changelog also describe substantially older clipboard behavior and NeoForge 21.1.219, and must not be used as publication authority without revision.

### Packaging findings

- PASS: no `.java` sources are in the binary candidate; the sources JAR is a separate artifact.
- PASS: no QA runtime, world, test, report, Gradle, or development script files are packaged.
- PASS: removed Smart Clipboard/Smart Scroll recipe diagnostic packet and dumper classes are absent.
- PASS: no horse compatibility package or class is present.
- PASS: `create_colony_logistics.mixins.json` is packaged and declares all five shipped mixins.
- PASS: no access transformer is declared or required by this project.
- PASS: six production network payloads are registered under protocol string `1`.
- PASS: English language data, item model, item texture, GUI textures, recipe, and `pack.mcmeta` are packaged.
- PASS: item recipe ID and model namespace match `create_colony_logistics:smart_colony_clipboard`.
- NOT APPLICABLE: no mod-specific tags or additional data files are used.
- FAIL: `LICENSE` is not packaged in the candidate JAR.
- FAIL: no mod icon is referenced or packaged as a dedicated metadata icon; the 16×16 item texture is not a suitable project icon.
- CAUTION: `MalformedExtractionDiagnostics` remains packaged. It is active protective/error logging, not one of the removed clipboard diagnostic systems and must not be advertised as a feature.
- PASS: candidate metadata contains version 0.3.6; no conflicting internal mod version was found.

No candidate was rebuilt. The original artifact and its hash were preserved.

## 4. Confirmed Feature Inventory

Runtime confirmation means evidence from the existing full-modpack server run. Client-interface behavior remains unconfirmed for the current candidate.

| Feature | Confirmed in source | Confirmed at runtime | Player-facing description | Notes/limitations |
|---|---|---|---|---|
| Smart Colony Clipboard item | Yes | Older 0.3.6 JAR discovered server-side; current candidate not tested | A separate colony-management item crafted from a MineColonies Clipboard and Create components | Coexists with, and does not replace, the standard clipboard |
| Explicit colony linking | Yes | No | Shift-use the Smart Clipboard on a Town Hall to link it | Link is persisted in MineColonies `ColonyId` item data; passive binding is blocked |
| Requests view | Yes | No | Displays up to 250 active requests with item, quantity, requester, worker, position, availability, and expandable request-tree information | Exact fields depend on resolvable server data |
| Search and important filtering | Yes | No | Search requests and switch between all and important requests | Important-only state persists on the item |
| Request cancellation | Yes | No | Cancel eligible requests from the interface | Server packet is authoritative; permission behavior needs interactive verification |
| Smart Info | Yes | No | Shows resolved shape/material, recipe, known-by/can-learn production information for supported requests | Not every item or request has resolvable Smart Info |
| Resource Scroll storage/view | Yes | No | Stores up to 18 actual MineColonies Resource Scrolls and renders their linked builder resource data | Scrolls are moved into/out of clipboard item data, not merely linked shortcuts |
| Colony Map storage/opening | Yes | No | Stores one actual MineColonies Colony Map and opens the MineColonies map interface | Uses the authentic MineColonies map item/interface through a validated server request |
| Remembered tab | Yes | No | Reopens the last selected Requests, Scrolls, or Map tab during the client session | Client-static state; not saved across restarts |
| Multi-colony use | Partial | No | A clipboard can be explicitly linked to one colony at a time | No colony-picker UI was found; relink at a Town Hall |
| Packager stock-summary cache | Yes | Existing older build loaded; behavior not exercised | Reduces repeated read-only Create stock scans over MineColonies combined inventories | Server-side; configurable; 40-tick default TTL |
| Factory-panel restocker count cache | Yes | Existing older build loaded; behavior not exercised | Reuses fresh cached counts for restocking factory panels | Server-only, conditional on a fresh matching cached summary |
| Warehouse Stockpile Switch adapter | Yes | Existing older build loaded; behavior not exercised | Uses a cached MineColonies warehouse snapshot for a Create Stockpile Switch aimed at a Warehouse hut | Server-side; 40-tick default TTL; safe-off fallback |
| Malformed extraction guard | Yes | No mod-specific error observed in existing log | Prevents a known malformed inventory value from crashing a Create extraction path | Targeted compatibility guard; returns an empty extraction on the specific component-map cast failure |

The Clipboard, Resource Scrolls, and Colony Map are not recreated as one generic replacement UI. The Requests view is custom; the Scrolls view stores real Resource Scroll items and presents custom resource rows; the Map tab stores a real Colony Map and delegates opening to the MineColonies map screen.

No citizen roster, building-management dashboard, work-order editor, logistics automation, automatic colony selection, or general Create automation feature was confirmed.

## 5. CurseForge Short Summary Options

**Option A — Balanced:** Enhances the MineColonies clipboard with request details, stored Resource Scrolls and a Colony Map, plus targeted Create–MineColonies compatibility improvements.

**Option B — Clipboard-focused:** Adds a Smart Colony Clipboard for detailed MineColonies requests, Resource Scroll storage, Colony Map access, and targeted Create compatibility fixes.

**Option C — Integration-focused:** Improves selected Create interactions with MineColonies inventories while consolidating colony requests, Resource Scrolls, and the Colony Map in an enhanced clipboard.

## 6. Tagline Options

- Colony information in one clipboard, with targeted Create integration.
- A clearer MineColonies clipboard for colonies built with Create.
- MineColonies request visibility and focused Create compatibility.

## 7. Overview

Create Colony Logistics is a NeoForge addon for players who use MineColonies and Create together. Its Smart Colony Clipboard provides a dedicated view of active colony requests, including requester details, available stock, request dependencies, and production information where that data can be resolved.

The clipboard can also hold up to 18 MineColonies Resource Scrolls and one Colony Map. These are real stored tools: the Scrolls tab presents builder-resource information, while the Map tab opens MineColonies' own map interface. This reduces the number of separate colony tools carried in the player's inventory.

Behind the interface, the mod applies focused compatibility and caching changes to selected Create reads of MineColonies-managed inventories. These changes preserve normal item movement and the established gameplay of both mods.

## 8. Why Create Colony Logistics?

Create supplies production systems and engineering tools; MineColonies supplies long-term material demands and colony objectives. They complement each other, but a developed colony can generate many requests and expose large, combined inventories that Create repeatedly inspects.

Create Colony Logistics reduces that friction in two places. The Smart Colony Clipboard makes request and building-resource information easier to inspect without carrying several separate tools. Targeted integration code then handles specific Create reads of MineColonies inventories more efficiently and defensively. It is a focused companion addon, not a general logistics automation system.

## 9. Features

### Smart Colony Clipboard

- **Detailed request view:** Inspect active requests, quantities, requesters, workers, positions, availability, and expandable dependency details.
- **Search and importance filter:** Find request entries and switch between all and important requests.
- **Production context:** View supported recipe, shape/material, and colony production knowledge when resolvable.
- **Request cancellation:** Cancel eligible request tokens through a server-validated action.
- **Persistent colony link:** Explicitly link the clipboard to a Town Hall and retain that colony on the item.

### MineColonies Integration

- **Resource Scroll storage:** Store up to 18 MineColonies Resource Scrolls and inspect linked builder-resource information.
- **Colony Map storage:** Store one MineColonies Colony Map and open the authentic map interface from the clipboard.
- **Inventory consolidation:** Carry the enhanced clipboard instead of separate stacks of supported colony tools.

### Create Compatibility and Performance

- **Read-only stock-summary cache:** Reuses short-lived summaries for repeated Create monitoring reads of MineColonies combined inventories.
- **Factory-panel restocker counts:** Reuses fresh cached filtered counts where available.
- **Warehouse Stockpile Switch adapter:** Reads a cached warehouse snapshot when a switch directly targets a MineColonies Warehouse hut.
- **Extraction safety guard:** Handles a specific malformed inventory-data failure without changing normal extraction behavior.

## 10. Smart Colony Clipboard

The Smart Colony Clipboard is a separate item that coexists with the standard MineColonies Clipboard. Craft it with a MineColonies Clipboard, Create Andesite Alloy, Andesite Casing and Electron Tube, plus a barrel. It does not silently inherit a colony: shift-use it on a Town Hall to link it explicitly.

Once linked, the Requests view asks the server for up to 250 current colony requests. It adds search, an important-only filter, availability and requester details, expandable request trees, and supported recipe/production context. Information is refreshed when the item is opened or when an interface action requests a new report; continuous live polling was not found.

The item stores real Resource Scroll and Colony Map items. Scroll storage removes matching scrolls from the player inventory and can return them when space is available. The stored Colony Map opens MineColonies' map interface. The selected colony, stored tools, and important-only setting persist on the item; the selected tab persists only for the current client session.

**Included Views**

- **Requests:** Active requests and expanded request/production information.
- **Scrolls:** Eighteen storage slots and selected builder-resource details.
- **Smart Colony Map:** One stored Colony Map with access to the MineColonies map interface.

No colony selection menu was found. With no valid linked colony, the UI receives an empty report and the player is told that no colony could be resolved. Ownership/officer restrictions were not explicitly implemented in the item class; request cancellation and underlying MineColonies operations still require runtime permission verification.

## 11. Create and MineColonies Integration

The compatibility work targets Create components that inspect or extract from MineColonies-managed inventories: packager availability summaries, restocking factory panels, Stockpile Switches aimed at Warehouse huts, and one malformed extraction failure. The improvements combine performance and stability, are primarily server-side, and activate only for the relevant handler or building types. Ordinary Create inventories and normal item-transfer authority are left unchanged.

## 12. Performance

Create can repeatedly build stock summaries by scanning MineColonies combined inventory handlers. Create Colony Logistics caches those read-only summaries for a short period and can reuse their filtered counts for restocking factory panels. A separate adapter builds short-lived snapshots for Stockpile Switches aimed directly at MineColonies Warehouse huts.

These optimizations are limited to the named integration points. They do not claim higher client FPS, faster colony AI, faster world generation, or universal Create/MineColonies performance gains. No reproducible publication benchmark for version 0.3.6 is included.

**Technical note for maintainers:** `PackagerBlockEntityMixin` wraps `getAvailableItems`; `FactoryPanelBehaviourMixin` short-circuits `getLevelInStorage` only for restockers with a fresh cached count; `ThresholdSwitchBlockEntityMixin` replaces the Warehouse-target read with a cached `WarehouseThresholdSnapshot`. Cache keys use MineColonies handler identity and weak references, returned summaries are copied, defaults are 40 ticks, and errors fall back or move the switch to a safe-off state. `InvManipulationBehaviourMixin` separately guards a specific component-map cast failure.

## 13. Requirements

- Minecraft: exactly 1.21.1 for the audited publication target; metadata accepts `[1.21.1,1.22)`.
- NeoForge: 21.1.226 minimum; metadata range `[21.1.226,)`.
- Java: 21.
- Create: 6.0.10 minimum and below 6.1.0; metadata range `[6.0.10,6.1.0)`.
- MineColonies: 1.1.1041-1.21.1 minimum; metadata range `[1.1.1041-1.21.1,)`.
- Structurize: QA baseline 1.0.782-1.21.1-snapshot.
- BlockUI: QA baseline 1.0.205-1.21.1.
- MultiPiston: QA baseline 1.2.57-1.21.1.
- Domum Ornamentum: declared version 1.0.220-snapshot from QA artifact `domum-ornamentum-1.0.220-snapshot-main.jar`; directly required for supported Architect's Cutter Smart Info paths.
- Optional dependencies declared by this mod: none.
- Confirmed incompatible platforms: Forge and Fabric builds are unsupported by this NeoForge artifact.

**[REQUIRED: Confirm the supported publication ranges for Structurize, BlockUI, MultiPiston, and Domum Ornamentum rather than publishing the QA snapshot versions as broad compatibility promises.]**

## 14. Client and Server Compatibility

The mod is declared `BOTH` and contains a custom client screen plus server-side item analysis, storage mutations, caching, and mixins. Install the same 0.3.6 JAR on clients and dedicated servers. Single-player is supported by the integrated client/server architecture. Multiplayer is intended and uses NeoForge payloads registered under protocol string `1`.

Server-only installation cannot supply the custom client item screen or payload handlers. Client-only installation cannot perform server analysis, mutations, or server mixins. Exact disconnect/error behavior for a one-sided install was not tested. Protocol registration exists, but explicit accept-vanilla or optional-channel behavior was not configured; matching installation should be treated as required.

## 15. Installation

1. Install NeoForge 21.1.226 or a verified compatible 21.1.x release for Minecraft 1.21.1.
2. Install the required Create, MineColonies, and MineColonies dependency versions listed above.
3. Place `create_colony_logistics-0.3.6.jar` in the `mods` folder on every client and server using the world.
4. Start the game/server and confirm the mod list reports Create Colony Logistics 0.3.6.

## 16. Configuration

The server configuration is generated per world at `world/serverconfig/create_colony_logistics-server.toml`. Dedicated-server paths use that same location below the active world directory. Values are read through NeoForge's server config; restart/reload behavior was not explicitly tested, so restart the world/server after changes.

| Option | Default | Allowed values | Effect |
|---|---:|---|---|
| `enableMineColoniesSummaryCache` | `true` | Boolean | Enables cached read-only Create summaries for MineColonies combined handlers |
| `cacheTtlTicks` | `40` | 1–12,000 | Maximum age of a cached stock summary |
| `enableWarehouseStockpileSwitchAdapter` | `true` | Boolean | Enables the Warehouse-aware Stockpile Switch adapter |
| `warehouseStockpileCacheTtlTicks` | `40` | 1–12,000 | Maximum age of a cached warehouse snapshot |
| `smartClipboardProductionCacheTtlTicks` | `100` | 1–12,000 | Reuse period for Smart Clipboard production fallback data |
| `debugLogging` | `false` | Boolean | Enables additional cache-decision diagnostics |
| `logCacheStatsIntervalTicks` | `1200` | 0–72,000 | Periodic cache-stat interval when debug logging is on; 0 disables periodic stats |

## 17. Known Limitations

- The clipboard must be explicitly linked to one colony at a Town Hall; there is no multi-colony picker.
- Reports are capped at 250 active requests.
- Smart Info depends on resolvable request, recipe, worker, and colony production data and is not guaranteed for every item.
- Resource Scroll detail requires a valid linked builder/resource context.
- The Map tab requires a stored MineColonies Colony Map.
- Stock monitoring can lag by the configured cache TTL; actual item movement does not use cached authority.
- A Warehouse Stockpile Switch uses a safe-off fallback if its snapshot cannot be obtained.
- Current-candidate client behavior, permission edge cases, and one-sided network behavior remain unverified.

## 18. Credits and Acknowledgements

**A Goldstein Industries Endeavor**

Create Colony Logistics is an independent addon for Create and MineColonies. Create is by simibubi and contributors. MineColonies, Structurize, BlockUI, MultiPiston, and Domum Ornamentum are by their respective teams and contributors. Their names and assets remain the property of their respective owners. This project does not imply affiliation with or endorsement by the Create or MineColonies teams.

Project code is licensed under MIT. The repository does not identify borrowed third-party art or code requiring an additional project-specific notice, but the candidate JAR currently omits its own MIT license file.

**[REQUIRED: Confirm the legal/project owner name behind “A Goldstein Industries Endeavor” and any individual contributors to credit.]**

## 19. Links Checklist

| Link | Status/value |
|---|---|
| Source repository | Repository remote was not established by publication metadata. **[REQUIRED: Add public source repository URL]** |
| Issue tracker | `https://github.com/create-colony-logistics/create-colony-logistics/issues` (embedded; public reachability not verified) |
| Documentation | `README.md` exists but is outdated. **[REQUIRED: Publish updated documentation URL]** |
| Changelog | `CHANGELOG.md` exists but stops at 0.3.0-rc.33 and is outdated |
| License file | `LICENSE` (MIT) exists at repository root; absent from candidate JAR |
| Support/community | **[REQUIRED: Add public support or community URL]** |
| CurseForge project | **[REQUIRED: Add CurseForge project URL after project creation]** |
| Modrinth project | **[REQUIRED: Confirm whether Modrinth publication is intended and add URL if applicable]** |

## 20. Screenshot Inventory

No screenshots were captured. The project-local QA documentation states that client launch and join validation are interactive; the existing recorded pass is server-only, the QA client contains an older JAR, and no automated in-game input/capture workflow or publication-ready colony scene was found. Fabricating images or relabeling older captures would violate the audit rules.

| Filename | What it should show | Feature demonstrated | Recommended page placement | Status |
|---|---|---|---|---|
| `01-smart-colony-clipboard-overview.png` | Populated Requests tab | Main clipboard experience | Main gallery image | **[REQUIRED: Capture from current candidate in QA client]** |
| `02-additional-colony-information.png` | Expanded request tree and Smart Info/availability | Information beyond standard clipboard | Smart Clipboard section | **[REQUIRED: Capture from current candidate in QA client]** |
| `03-resource-scrolls-integration.png` | Selected stored builder Resource Scroll with populated rows | Scroll storage and resource details | Smart Clipboard section | **[REQUIRED: Capture from current candidate in QA client]** |
| `04-smart-colony-map.png` | Stored map and opened MineColonies map interface | Authentic Colony Map integration | Smart Clipboard section | **[REQUIRED: Capture from current candidate in QA client]** |
| `05-clipboard-navigation.png` | Requests, Scrolls, and Map tabs plus storage controls | Consolidated navigation | Features section | **[REQUIRED: Capture from current candidate in QA client]** |
| `06-create-powered-colony-context.png` | Clean developed colony with nearby Create machinery | Context only, without implying automation | Create/MineColonies integration section | **[REQUIRED: Stage and capture in isolated QA world]** |

After capture, the best main gallery candidate should be Screenshot 1; Screenshot 2 should lead the Smart Colony Clipboard section; Screenshot 6 should lead the integration section. Any image showing debug UI, diagnostic chat, the old JAR, the live production world, or misleading direct automation should not be used.

Intended output path: `publication/curseforge/screenshots/`. No images were created, committed, or left untracked.

## 21. Icon and Banner Audit

### Project icon

- Existing mod-specific image: `src/main/resources/assets/create_colony_logistics/textures/item/smart_colony_clipboard.png`.
- Dimensions/format: 16×16 PNG.
- Branding: authentic Smart Colony Clipboard item art, but not a dedicated project icon and too small for a polished CurseForge project icon.
- Small-size readability: appropriate in-game at 16×16; insufficient source resolution for larger CurseForge presentation.
- JAR status: packaged as the item texture, not referenced as a mod/project icon.
- Separate upload copy: required.

### Header/banner

No banner exists. The 256×256 GUI textures are interface assets, not headers, and no existing asset includes “A Goldstein Industries Endeavor.”

**Banner asset brief:** Create a 1600×400 PNG master (with a centered 1200×300 safe area). Use the title “Create Colony Logistics” and the branding line “A Goldstein Industries Endeavor.” Show an authentic in-game Smart Colony Clipboard interface as the main subject, with a clean developed MineColonies settlement and recognizable but non-dominant Create machinery in the background. Available authentic project assets are the 16×16 clipboard item texture and 256×256 clipboard GUI textures; final environmental subjects should come from real QA screenshots. Avoid AI-generated game imagery, fake automation links, unreadably small GUI text, shaders that obscure the interface, third-party logos used as endorsements, debug overlays, and promises of automated logistics.

## 22. Unverified or Missing Information

- **[REQUIRED: Run the exact candidate hash in the isolated QA client and confirm client launch, join, registry sync, and protocol compatibility.]**
- **[REQUIRED: Open and exercise Requests, Scrolls, and Smart Colony Map views with populated data.]**
- **[REQUIRED: Verify owner/officer/non-member permission behavior and request cancellation authorization.]**
- **[REQUIRED: Verify behavior with no colony, relinking between colonies, invalid/deleted colony links, and one-sided installation.]**
- **[REQUIRED: Capture all six required PNG screenshots from the exact approved candidate.]**
- **[REQUIRED: Resolve build reproducibility: the approved offline script's project-local cache lacks NeoGradle 7.1.36 artifacts, so this audit could not independently compile the source.]**
- **[REQUIRED: Re-run full-modpack server QA with NeoForge 21.1.226, or explicitly approve and document 21.1.235 as the tested loader.]**
- **[REQUIRED: Remove the unrelated horse compatibility mods from any publication-validation claim; they were present in the existing complete QA pack but no horse code exists in this JAR.]**
- **[REQUIRED: Update README, changelog, contributor/author naming, and package the MIT license before final upload approval.]**
- **[REQUIRED: Supply/confirm public source, documentation, support, CurseForge, and optional Modrinth links.]**

Existing server evidence: the complete QA modpack booted on NeoForge 21.1.235 and discovered an older `create_colony_logistics-0.3.6.jar`; the copied QA world loaded, with no mod-specific CCL exception identified in the reviewed log. That run also contained unrelated modpack warnings/errors and cannot substitute for current-candidate client validation.

## 23. Recommended Next Publication Step

Do not upload the current artifact yet. First, make the intended production state explicit (commit or otherwise freeze the current diagnostic removals and naming), restore reproducible offline compilation, rebuild a fresh candidate with `LICENSE` and a declared icon if desired, and record its new SHA-256. Deploy only that candidate to the isolated QA client/server at the publication loader baseline, exercise every documented view and permission edge case, review both logs, and capture the six PNGs into `publication/curseforge/screenshots/`. Once those checks pass, update this report's runtime column and missing placeholders, then use Sections 5–18 as the factual CurseForge copy foundation.
