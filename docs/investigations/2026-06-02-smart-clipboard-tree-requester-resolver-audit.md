# Smart Clipboard Tree Requester Resolver Audit

Date: 2026-06-02

## Scope

This audit investigated whether Smart Colony Clipboard can show requester and resolver information for parent and child request-tree rows, and how that compares to MineColonies' request tree and request detail UI.

One small UI implementation was made separately in this pass: the Smart Resource Scroll incoming delivery arrow was changed from a drawn primitive glyph to an existing text character. No Smart Info tooltip behavior or formatting was changed.

## MineColonies Classes And Methods Inspected

- `com.minecolonies.core.client.gui.WindowClipBoard`
  - `getOpenRequestsFromBuilding(IBuildingView)`
- `com.minecolonies.core.client.gui.AbstractWindowRequestTree`
  - `getOpenRequestTreeOfBuilding()`
  - `constructTreeFromRequest(IBuildingView, IRequestManager, IRequest<?>, List<RequestWrapper>, int)`
  - `updateRequests()`
  - `fulfillable(IRequest<?>)`
  - `cancellable(IRequest<?>)`
- `com.minecolonies.core.client.gui.WindowRequestDetail`
  - Constructor `WindowRequestDetail(BOWindow, IRequest<?>, int)`
  - `onOpened()`
  - `onUpdate()`
- `com.minecolonies.api.colony.requestsystem.request.IRequest`
  - `getId()`
  - `getRequester()`
  - `getParent()`
  - `hasParent()`
  - `getChildren()`
  - `hasChildren()`
  - `getDisplayStacks()`
  - `getDisplayIcon()`
  - `getResolverToolTip(IColonyView)`
- `com.minecolonies.api.colony.requestsystem.manager.IRequestManager`
  - `getRequestForToken(IToken<?>)`
  - `getResolverForRequest(IToken<?>)`
  - `getResolverForToken(IToken<?>)`
- `com.minecolonies.api.colony.requestsystem.requester.IRequester`
  - `getRequesterDisplayName(IRequestManager, IRequest<?>)`
  - `getLocation()`
- `com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver`
  - `getRequesterDisplayName(IRequestManager, IRequest<?>)`
  - `getRequestType()`
  - `canResolveRequest(...)`

## Create-Colony-Logistics Classes And Methods Inspected

- `com.createcolonylogistics.clipboard.RequestAnalysisService`
  - `analyze(ServerLevel, IColony, int)`
  - `inspectRequest(...)`
  - `requestTree(IRequestManager, IRequest<?>, int, int)`
  - `requestDisplayStack(IRequest<?>)`
  - `requestCount(IRequest<?>)`
  - `quantityDisplay(IRequest<?>, ItemStack)`
  - `requesterDisplayName(...)`
  - `resolverName(IColony, IRequest<?>)`
  - `cutterRequirementTree(...)`
  - `RequestTreeNode`
- `com.createcolonylogistics.clipboard.SmartClipboardReport`
  - `fromAnalysis(...)`
  - `Entry`
  - `RequestTreeNode`
  - `Entry.encode(...)`
  - `Entry.decode(...)`
- `com.createcolonylogistics.client.SmartClipboardScreen`
  - `renderEntry(...)`
  - `drawNameWithQuantity(...)`
  - `drawTreeNodeText(...)`
  - `dependencyValue(...)`
  - `entryHeight(...)`
  - `hasExpandedDetails(...)`
  - `drawResourceAvailabilityIndicator(...)`

## MineColonies Request Tree Behavior

MineColonies' clipboard opens from root requests but keeps each visible tree row connected to the real request object.

`WindowClipBoard.getOpenRequestsFromBuilding(...)` gathers open request tokens from the player and retrying request resolvers, climbs child requests to their parent root with `IRequest.hasParent()` / `getParent()`, and de-duplicates the resulting root requests.

`AbstractWindowRequestTree.getOpenRequestTreeOfBuilding()` then expands each root through `constructTreeFromRequest(...)`. That method adds a `RequestWrapper` containing the actual `IRequest<?>`, its depth, and the building view, then recursively resolves child tokens through `IRequestManager.getRequestForToken(...)`.

Because each row still has its `IRequest<?>`, MineColonies can ask for requester and resolver information per selected parent or child request. `WindowRequestDetail.onOpened()` uses:

- requester: `request.getRequester().getRequesterDisplayName(colony.getRequestManager(), request)`
- requester location: `request.getRequester().getLocation()`
- resolver: `colony.getRequestManager().getResolverForRequest(request.getId())`
- resolver display name: `resolver.getRequesterDisplayName(colony.getRequestManager(), request)`
- resolver tooltip: `request.getResolverToolTip(colonyView)`

MineColonies therefore exposes enough requester/resolver context per real child request while the request still exists. It does not have to reconstruct that context from the rendered item stack.

## Current Create-Colony-Logistics Behavior

`RequestAnalysisService.inspectRequest(...)` already computes requester and resolver fields for the top-level `SmartClipboardReport.Entry`:

- `requestingBuildingName`
- `requestingBuildingPos`
- `requestingWorkerName`
- `dimensionName`
- `resolverName`
- `requestToken`

The request tree is collected separately by `requestTree(...)`. That method recursively visits real child requests through `IRequestManager.getRequestForToken(...)`, so it has access to each child request's token, requester, parent token, resolver, request type, display stacks, and quantity while running server-side.

However, the current analysis-side `RequestTreeNode` only records:

- `depth`
- `stack`
- `count`
- `quantityDisplay`
- `label`

`SmartClipboardReport.fromAnalysis(...)` serializes only those same fields into the client-side `SmartClipboardReport.RequestTreeNode`. After this point, the client has no child request token, parent token, requester display name, resolver display name, requester location, dimension, request type, or synthetic-node marker.

## Side-By-Side Summary

MineColonies:

- Tree row data remains an `IRequest<?>` wrapper.
- Child rows can resolve requester and resolver from the request manager.
- Request detail can be opened for any row and uses the exact selected request.
- Resolver can be absent, delayed, or unavailable; MineColonies logs/returns instead of fabricating detail text.

Create-Colony-Logistics:

- Parent entries keep requester/resolver summary metadata.
- Child rows are reduced to display-only tree nodes.
- Expanded child rendering cannot show requester/resolver without new serialized fields.
- Client-side reconstruction from item stack would be unreliable because multiple child requests can share the same stack.

## Root Cause

The requester/resolver gap is a data model and serialization issue.

The server-side tree traversal currently has access to the child `IRequest<?>`, but `RequestTreeNode` drops all request identity and ownership metadata before the report reaches `SmartClipboardScreen`. Rendering is not the primary blocker; the data is missing.

## Folded And Expanded Layout Impact

The folded parent row is intentionally compact. It currently shows the item/name/quantity and requester line. Adding child metadata there would fight the accordion layout and should not be done.

Expanded rows have more room, but each child currently consumes `TREE_ROW_HEIGHT = 18`. Adding both requester and resolver below every child item would require dynamic per-node height, otherwise rows will overlap. A safe future implementation should show child metadata only for visible expanded nodes and should calculate row height from the number of metadata lines actually present.

For parent entries, resolver can be added in the expanded detail area below requester with less risk. It still needs corresponding `entryHeight(...)`, hit-zone, and overflow handling updates.

## Child Item Name Color Inconsistency

Parent item names use `drawNameWithQuantity(...)`, which renders the name in `PRIMARY_TEXT` and the quantity in `QUANTITY_TEXT`.

Child tree rows use `drawTreeNodeText(...)`, which sets:

```java
int nameColor = node.depth() <= 1 ? PRIMARY_TEXT : SECONDARY_TEXT;
```

That means deeper child item names are muted while parent and first-level child names are not. If the desired visual rule is that child item names match parent item-name formatting, future rendering should use `PRIMARY_TEXT` for actual item stack names at every tree depth and reserve muted colors for connector markers, labels, and metadata.

## Recommended Future Data Model

Add request context fields to the analysis-side and client-side `RequestTreeNode`:

- `requestToken`
- `parentToken`
- `requesterName`
- `requesterLocation`
- `requesterDimension`
- `resolverName`
- `requestType`
- `synthetic`

Populate these in `RequestAnalysisService.requestTree(...)` while each real `IRequest<?>` is still available. Use `IRequest.getRequester()` and `IRequestManager.getResolverForRequest(request.getId())` with defensive exception handling, matching the existing parent-entry resolver safety style.

Synthetic Architects Cutter fallback nodes from `cutterRequirementTree(...)` do not represent real MineColonies child requests. Mark them as synthetic and either omit requester/resolver metadata or explicitly inherit parent context only if that behavior is desired.

## Recommended Future Rendering

Keep folded rows unchanged.

In expanded parent details:

- show requester as today;
- add resolver under requester when present;
- hide resolver when unavailable rather than writing a noisy unknown value.

For expanded child rows:

- keep the item row compact and aligned with the current icon/name/quantity row;
- show resolver and requester beneath the item only when serialized metadata is present;
- prefer resolver first, then requester, matching the user's requested reading order;
- use item-name formatting consistently with parent rows;
- use muted label/value colors only for metadata lines;
- calculate child row height dynamically.

## Delivery Arrow Implementation

The Resource Scroll delivery indicator now uses the existing text character:

```text
>
```

It is rendered with Minecraft text drawing in the same green color previously used by the primitive delivery glyph: `0xFF4F8F5A`.

No texture asset, atlas sprite, UV coordinates, Factory Gauges asset, MineColonies icon, or new resource file is used. The warehouse glyph was left unchanged.

## Risks Of Future Requester/Resolver Fix

- Resolver availability can change while requests are reassigned, completed, or retried.
- Showing metadata for every child can make expanded cards too tall and harder to scan.
- Synthetic recipe requirement rows can be mistaken for real request rows unless marked clearly in the data model.
- Same-stack child requests cannot be safely joined client-side without request tokens.
- Adding resolver lines affects card height, hover zones, overflow tooltips, and cancel/action hit testing.

## Test Plan For Future Requester/Resolver Work

1. Parent request with assigned resolver:
   Expand the entry. Expected: requester remains visible and resolver appears only in expanded details.

2. Child request with distinct requester and resolver:
   Expand dependencies. Expected: child row shows its own resolver/requester from the child request token, not the parent entry.

3. Child request without resolver:
   Expected: child row remains readable and omits resolver or shows an intentionally chosen fallback.

4. Multiple child requests with the same item stack:
   Expected: requester/resolver metadata stays attached by token and does not cross-match by item.

5. Synthetic Architects Cutter fallback tree:
   Expected: synthetic nodes do not claim real MineColonies requester/resolver metadata unless explicitly inherited.

6. Deep dependency tree:
   Expected: item names use consistent item-name formatting, metadata lines do not overlap, and scrolling remains stable.

7. Cancel and accordion behavior:
   Expected: folded/expanded state, cancel action, hit zones, and scroll behavior remain unchanged except for intentional height adjustments.

## No-Change Confirmations

No requester/resolver tree behavior was implemented in this pass.
No Smart Info tooltip behavior or formatting was modified.
No request filtering, accordion behavior, cancel behavior, scroll behavior, tab icons, slot layout, or scrollbar behavior was intentionally changed.
No Factory Gauges assets, MineColonies texture assets, or new textures were used for the delivery arrow.
