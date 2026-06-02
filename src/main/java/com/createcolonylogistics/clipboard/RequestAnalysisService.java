package com.createcolonylogistics.clipboard;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.IStackBasedTask;
import com.minecolonies.api.colony.requestsystem.requestable.MinimumStack;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.createcolonylogistics.CreateColonyLogistics;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public final class RequestAnalysisService {
    private RequestAnalysisService() {
    }

    public static AnalysisResult analyze(ServerLevel level, IColony colony, int limit) {
        Map<String, List<RequestReportEntry>> grouped = new LinkedHashMap<>();
        Set<IToken<?>> lessImportantRequests = lessImportantRequests(colony);
        WorkerGroups workerGroups = WorkerGroups.from(colony);
        int reported = 0;
        int activeRequestCount = 0;
        boolean capped = false;

        for (IRequest<?> request : clipboardRootRequests(colony).stream()
                .sorted(Comparator.comparing(RequestAnalysisService::requesterSortName))
                .toList()) {
            activeRequestCount++;
            Optional<ItemStack> requestedStack = requestedStack(request);
            if (requestedStack.isEmpty()) {
                continue;
            }

            if (reported >= limit) {
                capped = true;
                continue;
            }

            IBuilding requesterBuilding = buildingForRequest(colony, request).orElse(null);
            RequestReportEntry entry = inspectRequest(level, colony, requesterBuilding, request, requestedStack.get(), !lessImportantRequests.contains(request.getId()), workerGroups);
            grouped.computeIfAbsent(entry.requesterName(), ignored -> new ArrayList<>()).add(entry);
            reported++;
        }

        return new AnalysisResult(colony.getName(), colony.getID(), colony.getBuildingManager().getBuildings().size(), activeRequestCount, grouped, reported, capped);
    }

    /**
     * TEMPORARY DEBUG: dumps the runtime request chain for one Smart Clipboard row.
     * Remove once requester/worker/dependency parity has been verified in-game.
     */
    public static void debugDump(ServerPlayer player, IColony colony, String requestToken, String rowItemName, String quantityDisplay,
                                 String requesterDisplay, Optional<String> workerDisplay, boolean minimumStockRequest,
                                 boolean expandable, String expandableReason, int clientDependencyCount) {
        IRequestManager manager = colony.getRequestManager();
        Optional<IRequest<?>> request = findRequestByToken(colony, requestToken);
        CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] Player: {} colony={} ({}) token={}",
                player.getGameProfile().getName(), colony.getName(), colony.getID(), requestToken);
        CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] Row: item={} quantity={} requester={} worker={} minimumStock={} expandable={} reason={} clientDependencies={}",
                rowItemName, quantityDisplay, requesterDisplay, workerDisplay.orElse("none"), minimumStockRequest, expandable, expandableReason, clientDependencyCount);

        if (request.isEmpty()) {
            CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] Request lookup: not found in current clipboard/request graph");
            return;
        }

        IRequest<?> currentRequest = request.get();
        IBuilding building = buildingForRequest(colony, currentRequest).orElse(null);
        WorkerGroups workerGroups = WorkerGroups.from(colony);
        Optional<ItemStack> requestedStack = requestedStack(currentRequest);
        boolean domumStack = requestedStack.map(DomumOrnamentumRequestInspector::isDomumOrnamentumStack).orElse(false);
        List<RequestTreeNode> graphTree = requestTree(manager, currentRequest, 0, 16);
        boolean graphDependencies = hasDependencyNodes(graphTree);
        List<RequestTreeNode> cutterTree = requestedStack
                .filter(DomumOrnamentumRequestInspector::isDomumOrnamentumStack)
                .map(stack -> cutterRequirementTree(player.serverLevel(), currentRequest, stack))
                .orElse(List.of());
        String dependencySource = graphDependencies ? "MineColonies child graph" : (!cutterTree.isEmpty() ? "DO synthetic fallback" : "none");

        CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] Request: class={} requestable={} state={} id={}",
                className(currentRequest), requestableClassName(currentRequest), safeState(currentRequest), currentRequest.getId());
        CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] Stack: requested={} registry={} hasComponents={} isDomum={} displayStacks={} IStackBasedTask={} IDeliverable={}",
                requestedStack.map(stack -> stack.getHoverName().getString()).orElse("none"),
                requestedStack.map(RequestAnalysisService::itemId).orElse("none"),
                requestedStack.map(stack -> !stack.getComponents().isEmpty()).orElse(false),
                domumStack,
                displayStackCount(currentRequest),
                stackBasedTask(currentRequest).isPresent(),
                deliverable(currentRequest).isPresent());
        stackBasedTask(currentRequest).ifPresent(task -> CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] IStackBasedTask: taskStack={} displayCount={} displayPrefix={}",
                safeTaskStackName(task), safeTaskDisplayCount(task), safeTaskDisplayPrefix(task)));
        CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] Requester: class={} display='{}' building={} position={}",
                requesterClassName(currentRequest), requesterDisplay(manager, currentRequest), buildingDisplayName(building),
                building == null ? "none" : buildingPosition(building).map(BlockPos::toShortString).orElse("none"));
        CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] RequesterDisplayFallback current: {}",
                requesterDisplayWorkerDiagnostics(manager, currentRequest));
        CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] Worker direct: {}", building == null ? "none (no building)" : workerName(building, currentRequest.getId()).orElse("none"));
        OwningWorker owningWorker = owningOrderWorker(building, manager, currentRequest);
        CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] OwningOrder: token={} class={} requesterDisplay='{}' worker={} inherited={} reason={}",
                owningWorker.token(),
                owningWorker.requestClass(),
                owningWorker.requesterDisplay(),
                owningWorker.name().orElse("none"),
                owningWorker.name().isPresent(),
                owningWorker.reason());
        WorkerGroupInheritance groupInheritance = workerGroups.inherit(building, minimumStockRequest);
        CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] WorkerGroup: building={} candidates={} inherit={} worker={} sourceToken={} reason={}",
                buildingGroupId(building),
                groupInheritance.candidates(),
                groupInheritance.name().isPresent(),
                groupInheritance.name().orElse("none"),
                groupInheritance.sourceToken(),
                groupInheritance.reason());

        dumpParentChain(colony, manager, building, currentRequest);
        WorkerDebug workerDebug = workerDebug(building, manager, currentRequest, workerGroups);
        CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] Worker final: {} source={}", workerDebug.name().orElse("none"), workerDebug.source());
        CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] Dependencies: source={} graphNodes={} graphDependencyNodes={} childTokens={} syntheticNodes={} serializedClientDependencyNodes={}",
                dependencySource, graphTree.size(), dependencyCount(graphTree), childCount(currentRequest), cutterTree.size(), clientDependencyCount);
        CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] Domum: isDO={} cutterSyntheticAttempted={} syntheticDependencyNodes={}",
                domumStack, domumStack, dependencyCount(cutterTree));
        if (domumStack && requestedStack.isPresent()) {
            CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] DomumCutter: {}",
                    DomumOrnamentumRequestInspector.debugCutterSummary(player.serverLevel().getRecipeManager(), player.serverLevel().registryAccess(), requestedStack.get()));
        }
        CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] Expandable: {} reason={}", expandable, expandableReason);
    }

    private static Optional<IRequest<?>> findRequestByToken(IColony colony, String requestToken) {
        IRequestManager manager = colony.getRequestManager();
        Set<String> seen = new HashSet<>();
        for (IRequest<?> root : clipboardRootRequests(colony)) {
            Optional<IRequest<?>> found = findRequestInTree(manager, root, requestToken, seen, 0);
            if (found.isPresent()) {
                return found;
            }
        }
        for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
            for (IRequest<?> request : openRequestsForBuilding(colony, building)) {
                Optional<IRequest<?>> found = findRequestInTree(manager, request, requestToken, seen, 0);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<IRequest<?>> findRequestInTree(IRequestManager manager, IRequest<?> request, String requestToken, Set<String> seen, int depth) {
        if (request == null || depth > 32 || !seen.add(request.getId().toString())) {
            return Optional.empty();
        }
        if (request.getId().toString().equals(requestToken)) {
            return Optional.of(request);
        }
        if (!request.hasChildren()) {
            return Optional.empty();
        }
        for (IToken<?> child : request.getChildren()) {
            try {
                Optional<IRequest<?>> found = findRequestInTree(manager, manager.getRequestForToken(child), requestToken, seen, depth + 1);
                if (found.isPresent()) {
                    return found;
                }
            } catch (RuntimeException ignored) {
                // Requests can resolve while diagnostics are being gathered.
            }
        }
        return Optional.empty();
    }

    private static void dumpParentChain(IColony colony, IRequestManager manager, IBuilding building, IRequest<?> request) {
        IRequest<?> current = request;
        for (int depth = 0; current != null && depth < 16; depth++) {
            try {
                if (!current.hasParent()) {
                    break;
                }
                current = manager.getRequestForToken(current.getParent());
                if (current == null) {
                    break;
                }
                IBuilding parentBuilding = null;
                try {
                    BlockPos location = current.getRequester().getLocation().getInDimensionLocation();
                    parentBuilding = colony.getBuildingManager().getBuilding(location);
                } catch (RuntimeException ignored) {
                    // Keep the parent diagnostics best-effort.
                }
                IBuilding workerBuilding = parentBuilding == null ? building : parentBuilding;
                CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] Parent[{}]: id={} class={} requesterDisplay='{}' displayFallback={} building={} worker={}",
                        depth,
                        current.getId(),
                        className(current),
                        requesterDisplay(manager, current),
                        requesterDisplayWorkerDiagnostics(manager, current),
                        buildingDisplayName(workerBuilding),
                        workerBuilding == null ? "none" : workerName(workerBuilding, current.getId()).orElse("none"));
            } catch (RuntimeException ignored) {
                CreateColonyLogistics.LOGGER.info("[SmartClipboardDebug] Parent[{}]: unavailable", depth);
                break;
            }
        }
    }

    private static WorkerDebug workerDebug(IBuilding building, IRequestManager manager, IRequest<?> request, WorkerGroups workerGroups) {
        if (building != null) {
            Optional<String> directWorker = workerName(building, request.getId());
            if (directWorker.isPresent()) {
                return new WorkerDebug(directWorker, "direct");
            }

            IRequest<?> current = request;
            for (int depth = 0; depth < 16; depth++) {
                try {
                    if (!current.hasParent()) {
                        break;
                    }
                    current = manager.getRequestForToken(current.getParent());
                    if (current == null) {
                        break;
                    }
                    Optional<String> parentWorker = workerName(building, current.getId());
                    if (parentWorker.isPresent()) {
                        return new WorkerDebug(parentWorker, "parent[" + depth + "]");
                    }
                } catch (RuntimeException ignored) {
                    break;
                }
            }
        }
        Optional<String> requesterDisplayWorker = requesterDisplayWorker(manager, request);
        if (requesterDisplayWorker.isPresent()) {
            return new WorkerDebug(requesterDisplayWorker, "requester-display fallback");
        }
        WorkerGroupInheritance groupInheritance = workerGroups.inherit(building, false);
        if (groupInheritance.name().isPresent()) {
            return new WorkerDebug(groupInheritance.name(), "worker-group " + groupInheritance.describe());
        }
        return new WorkerDebug(Optional.empty(), "none");
    }

    private static String className(Object value) {
        return value == null ? "none" : value.getClass().getName();
    }

    private static String requestableClassName(IRequest<?> request) {
        try {
            return className(request.getRequest());
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    private static String requesterClassName(IRequest<?> request) {
        try {
            return className(request.getRequester());
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    private static String requesterDisplay(IRequestManager manager, IRequest<?> request) {
        try {
            return request.getRequester().getRequesterDisplayName(manager, request).getString();
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    private static String requesterDisplayWorkerDiagnostics(IRequestManager manager, IRequest<?> request) {
        String display = requesterDisplay(manager, request);
        if (display == null || display.isBlank()) {
            return "rejected: blank display";
        }
        if (!display.contains(":")) {
            return "rejected: no ':' separator";
        }
        String requester = display.substring(0, display.indexOf(':')).trim();
        String worker = display.substring(display.indexOf(':') + 1).trim();
        if (!isPlayerFacingName(requester)) {
            return "rejected: requester side is not player-facing";
        }
        if (!isPlayerFacingName(worker)) {
            return "rejected: worker side is not player-facing";
        }
        return "accepted: worker='" + worker + "'";
    }

    private static String safeState(IRequest<?> request) {
        try {
            return String.valueOf(request.getState());
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    private static int displayStackCount(IRequest<?> request) {
        try {
            return request.getDisplayStacks().size();
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static int childCount(IRequest<?> request) {
        try {
            return request.hasChildren() ? request.getChildren().size() : 0;
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static int dependencyCount(List<RequestTreeNode> nodes) {
        int count = 0;
        for (RequestTreeNode node : nodes) {
            if (node.depth() > 0) {
                count++;
            }
        }
        return count;
    }

    private static String itemId(ItemStack stack) {
        try {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    private static String safeTaskStackName(IStackBasedTask task) {
        try {
            ItemStack stack = task.getTaskStack();
            return stack.isEmpty() ? "empty" : stack.getHoverName().getString() + " " + itemId(stack);
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    private static int safeTaskDisplayCount(IStackBasedTask task) {
        try {
            return task.getDisplayCount();
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static String safeTaskDisplayPrefix(IStackBasedTask task) {
        try {
            Component prefix = task.getDisplayPrefix();
            return prefix == null ? "none" : prefix.getString();
        } catch (RuntimeException ignored) {
            return "unavailable";
        }
    }

    private record WorkerDebug(Optional<String> name, String source) {
    }

    private record OwningWorker(Optional<String> name, String token, String requestClass, String requesterDisplay, String reason) {
        static OwningWorker none(String reason) {
            return new OwningWorker(Optional.empty(), "none", "none", "none", reason);
        }

        String describe() {
            return "token=" + token + " class=" + requestClass + " requesterDisplay='" + requesterDisplay + "' reason=" + reason;
        }
    }

    private static final class WorkerGroups {
        private final Map<String, WorkerGroup> groups;

        private WorkerGroups(Map<String, WorkerGroup> groups) {
            this.groups = groups;
        }

        static WorkerGroups from(IColony colony) {
            Map<String, WorkerGroup> groups = new LinkedHashMap<>();
            for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                String groupId = buildingGroupId(building);
                WorkerGroup group = groups.computeIfAbsent(groupId, ignored -> new WorkerGroup(groupId, buildingDisplayName(building)));
                try {
                    for (ICitizenData citizen : building.getAllAssignedCitizen()) {
                        for (IRequest<?> request : building.getOpenRequests(citizen.getId())) {
                            if (request != null && isActive(request.getState())) {
                                group.add(citizen.getName(), request);
                            }
                        }
                    }
                } catch (RuntimeException ignored) {
                    // Some buildings do not expose worker request state during every tick.
                }
            }
            return new WorkerGroups(groups);
        }

        WorkerGroupInheritance inherit(IBuilding building, boolean minimumStockRequest) {
            if (minimumStockRequest) {
                return WorkerGroupInheritance.rejected("minimum stock");
            }
            String groupId = buildingGroupId(building);
            WorkerGroup group = groups.get(groupId);
            if (group == null) {
                return WorkerGroupInheritance.rejected("no building group");
            }
            return group.inherit();
        }
    }

    private static final class WorkerGroup {
        private final String id;
        private final String buildingName;
        private final Map<String, WorkerCandidate> candidates = new LinkedHashMap<>();

        private WorkerGroup(String id, String buildingName) {
            this.id = id;
            this.buildingName = buildingName;
        }

        void add(String workerName, IRequest<?> request) {
            if (workerName == null || workerName.isBlank()) {
                return;
            }
            candidates.putIfAbsent(workerName, new WorkerCandidate(workerName, request.getId().toString(), className(request)));
        }

        WorkerGroupInheritance inherit() {
            if (candidates.isEmpty()) {
                return WorkerGroupInheritance.rejected("no candidates");
            }
            if (candidates.size() > 1) {
                return new WorkerGroupInheritance(Optional.empty(), "none", id, buildingName, candidates(), "multiple workers");
            }
            WorkerCandidate candidate = candidates.values().iterator().next();
            return new WorkerGroupInheritance(Optional.of(candidate.workerName()), candidate.token(), id, buildingName, candidates(), "unique worker candidate");
        }

        String candidates() {
            if (candidates.isEmpty()) {
                return "[]";
            }
            return candidates.values().stream()
                    .map(candidate -> candidate.workerName() + "@" + candidate.token() + "(" + candidate.requestClass() + ")")
                    .toList()
                    .toString();
        }
    }

    private record WorkerCandidate(String workerName, String token, String requestClass) {
    }

    private record WorkerGroupInheritance(Optional<String> name, String sourceToken, String groupId, String buildingName, String candidates, String reason) {
        static WorkerGroupInheritance rejected(String reason) {
            return new WorkerGroupInheritance(Optional.empty(), "none", "none", "none", "[]", reason);
        }

        String describe() {
            return "building=" + buildingName + " group=" + groupId + " candidates=" + candidates + " sourceToken=" + sourceToken + " reason=" + reason;
        }
    }

    private static Collection<IRequest<?>> clipboardRootRequests(IColony colony) {
        Map<String, IRequest<?>> roots = new LinkedHashMap<>();
        IRequestManager manager = colony.getRequestManager();
        try {
            addAssignedRootRequests(manager, manager.getPlayerResolver().getAllAssignedRequests(), roots);
            addAssignedRootRequests(manager, manager.getRetryingRequestResolver().getAllAssignedRequests(), roots);
        } catch (RuntimeException ignored) {
            // Match MineColonies' clipboard source when available; fall back to building-open requests if resolver state is unavailable.
            for (IBuilding building : colony.getBuildingManager().getBuildings().values()) {
                for (IRequest<?> request : openRequestsForBuilding(colony, building)) {
                    roots.putIfAbsent(request.getId().toString(), request);
                }
            }
        }
        return roots.values();
    }

    private static void addAssignedRootRequests(IRequestManager manager, Collection<IToken<?>> tokens, Map<String, IRequest<?>> roots) {
        for (IToken<?> token : tokens) {
            try {
                IRequest<?> request = manager.getRequestForToken(token);
                request = rootRequest(manager, request);
                if (request != null && isActive(request.getState())) {
                    roots.putIfAbsent(request.getId().toString(), request);
                }
            } catch (RuntimeException ignored) {
                // Requests can disappear while MineColonies updates the resolver state.
            }
        }
    }

    private static IRequest<?> rootRequest(IRequestManager manager, IRequest<?> request) {
        IRequest<?> current = request;
        for (int depth = 0; current != null && depth < 16; depth++) {
            try {
                if (!current.hasParent()) {
                    return current;
                }
                current = manager.getRequestForToken(current.getParent());
            } catch (RuntimeException ignored) {
                return current;
            }
        }
        return current;
    }

    private static String requesterSortName(IRequest<?> request) {
        try {
            return request.getRequester().getLocation().toString();
        } catch (RuntimeException ignored) {
            return request.getId().toString();
        }
    }

    private static Set<IToken<?>> lessImportantRequests(IColony colony) {
        Set<IToken<?>> requests = new HashSet<>();
        try {
            for (ICitizenData citizen : colony.getCitizenManager().getCitizens()) {
                IJob<?> job = citizen.getJob();
                if (job != null) {
                    requests.addAll(job.getAsyncRequests());
                }
            }
        } catch (RuntimeException ignored) {
            // MineColonies' clipboard treats job async requests as less-important; if unavailable, keep all visible.
        }
        return requests;
    }

    private static Collection<IRequest<?>> openRequestsForBuilding(IColony colony, IBuilding building) {
        List<IRequest<?>> requests = new ArrayList<>();
        for (Collection<IToken<?>> tokens : building.getOpenRequestsByRequestableType().values()) {
            for (IToken<?> token : tokens) {
                try {
                    IRequest<?> request = colony.getRequestManager().getRequestForToken(token);
                    if (isActive(request.getState())) {
                        requests.add(request);
                    }
                } catch (RuntimeException ignored) {
                    // Requests can disappear while the request system updates; this read-only report skips them.
                }
            }
        }
        return requests;
    }

    private static boolean isActive(RequestState state) {
        return state != RequestState.RESOLVED
                && state != RequestState.COMPLETED
                && state != RequestState.OVERRULED
                && state != RequestState.CANCELLED
                && state != RequestState.RECEIVED
                && state != RequestState.FAILED;
    }

    private static Optional<ItemStack> requestedStack(IRequest<?> request) {
        Optional<ItemStack> stackBasedTask = stackBasedTaskStack(request);
        if (stackBasedTask.isPresent()) {
            return stackBasedTask;
        }

        Object requestable = request.getRequest();
        if (requestable instanceof IDeliverable deliverable) {
            ItemStack result = deliverable.getResult();
            if (!result.isEmpty()) {
                return Optional.of(result.copyWithCount(deliverable.getCount()));
            }
        }

        for (ItemStack stack : request.getDisplayStacks()) {
            if (!stack.isEmpty()) {
                return Optional.of(stack.copy());
            }
        }

        return Optional.empty();
    }

    private static Optional<ItemStack> stackBasedTaskStack(IRequest<?> request) {
        return stackBasedTask(request).flatMap(RequestAnalysisService::stackBasedTaskStack);
    }

    private static Optional<IStackBasedTask> stackBasedTask(IRequest<?> request) {
        if (request instanceof IStackBasedTask task) {
            return Optional.of(task);
        }
        try {
            return request.getRequestOfType(IStackBasedTask.class);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<ItemStack> stackBasedTaskStack(IStackBasedTask task) {
        if (task == null) {
            return Optional.empty();
        }
        try {
            ItemStack stack = task.getTaskStack();
            if (!stack.isEmpty()) {
                return Optional.of(stack.copyWithCount(Math.max(1, task.getDisplayCount())));
            }
        } catch (RuntimeException ignored) {
            // Fall through to display stacks.
        }
        return Optional.empty();
    }

    private static RequestReportEntry inspectRequest(ServerLevel level, IColony colony, IBuilding building, IRequest<?> request, ItemStack requestedStack, boolean important, WorkerGroups workerGroups) {
        boolean minimumStockRequest = isMinimumStockRequest(request);
        boolean domumRequest = DomumOrnamentumRequestInspector.isDomumOrnamentumStack(requestedStack);
        ColonyProductionInspector.ProductionKnowledge knowledge = domumRequest
                ? ColonyProductionInspector.inspect(colony, level, requestedStack)
                : new ColonyProductionInspector.ProductionKnowledge(Collections.emptyList(), Collections.emptyList());
        Optional<ResourceLocation> cutterRecipe = domumRequest
                ? DomumOrnamentumRequestInspector.findArchitectsCutterMatch(level, requestedStack).map(DomumOrnamentumRequestInspector.CutterRecipeMatch::recipeId)
                : Optional.empty();
        // Pipeline priority: keep MineColonies' own request graph authoritative.
        // Synthetic Domum Ornamentum cutter inputs are only supplemental when the
        // request graph has no child dependency nodes to display.
        List<RequestTreeNode> tree = requestTree(colony.getRequestManager(), request, 0, 16);
        if (domumRequest && !hasDependencyNodes(tree)) {
            List<RequestTreeNode> cutterTree = cutterRequirementTree(level, request, requestedStack);
            if (!cutterTree.isEmpty()) {
                tree = cutterTree;
            }
        }
        List<ItemStack> displayStacks = displayStacks(request, requestedStack);
        List<SmartInfoMatchKey> smartInfoKeys = domumRequest
                ? smartInfoKeys(request, requestedStack, displayStacks, tree)
                : List.of();

        return new RequestReportEntry(
                requesterDisplayName(colony.getRequestManager(), request, building),
                building == null ? Optional.empty() : buildingPosition(building),
                minimumStockRequest ? Optional.empty() : workerName(building, colony.getRequestManager(), request, workerGroups),
                dimensionName(request),
                resolverName(colony, request),
                request.getId().toString(),
                requestedStack.copy(),
                displayStacks,
                requestedStack.getHoverName(),
                requestedStack.getCount(),
                quantityDisplay(request, requestedStack),
                important,
                minimumStockRequest,
                warehouseStock(colony, requestedStack),
                DomumOrnamentumRequestInspector.itemId(requestedStack),
                cutterRecipe,
                DomumOrnamentumRequestInspector.exactComboFingerprint(requestedStack),
                !knowledge.knownBy().isEmpty(),
                knowledge.knownBy(),
                knowledge.canLearn(),
                tree,
                smartInfoKeys
        );
    }

    private static List<SmartInfoMatchKey> smartInfoKeys(IRequest<?> request, ItemStack requestedStack, List<ItemStack> displayStacks, List<RequestTreeNode> tree) {
        List<SmartInfoMatchKey> keys = new ArrayList<>();
        addStackKeys(keys, requestedStack, SmartClipboardReport.SMART_INFO_PRIORITY_EXACT);
        addKey(keys, SmartClipboardReport.domumFingerprintKey(requestedStack), SmartClipboardReport.SMART_INFO_PRIORITY_FINGERPRINT);
        DomumOrnamentumRequestInspector.materializedRequestedStack(request)
                .ifPresent(stack -> addStackKeys(keys, stack, SmartClipboardReport.SMART_INFO_PRIORITY_EXACT));
        for (ItemStack stack : displayStacks) {
            addStackKeys(keys, stack, SmartClipboardReport.SMART_INFO_PRIORITY_DISPLAY);
        }
        for (RequestTreeNode node : tree) {
            addKey(keys, SmartClipboardReport.treeParentStackKey(node.stack()), SmartClipboardReport.SMART_INFO_PRIORITY_TREE_PARENT);
        }
        addKey(keys, SmartClipboardReport.requestTokenKey(request.getId().toString()), SmartClipboardReport.SMART_INFO_PRIORITY_REQUEST_TOKEN);
        addRequestableStackKeys(keys, request);
        return keys.stream().distinct().toList();
    }

    private static void addRequestableStackKeys(List<SmartInfoMatchKey> keys, IRequest<?> request) {
        try {
            addRequestableStackKeys(keys, request.getRequest());
        } catch (RuntimeException ignored) {
            // Optional MineColonies requestable details.
        }
        try {
            request.getRequestOfType(com.minecolonies.api.colony.requestsystem.requestable.Stack.class)
                    .ifPresent(stack -> addRequestableStackKeys(keys, stack));
        } catch (RuntimeException ignored) {
            // Optional MineColonies requestable details.
        }
        try {
            request.getRequestOfType(com.minecolonies.api.colony.requestsystem.requestable.StackList.class)
                    .ifPresent(stackList -> addRequestableStackKeys(keys, stackList));
        } catch (RuntimeException ignored) {
            // Optional MineColonies requestable details.
        }
    }

    private static void addRequestableStackKeys(List<SmartInfoMatchKey> keys, Object requestable) {
        if (requestable instanceof com.minecolonies.api.colony.requestsystem.requestable.Stack stackRequest) {
            try {
                addStackKeys(keys, stackRequest.getStack(), SmartClipboardReport.SMART_INFO_PRIORITY_EXACT);
            } catch (RuntimeException ignored) {
                // Optional MineColonies requestable details.
            }
            try {
                for (ItemStack stack : stackRequest.getRequestedItems()) {
                    addAlternativeStackKey(keys, stack);
                }
            } catch (RuntimeException ignored) {
                // Optional MineColonies requestable details.
            }
        } else if (requestable instanceof com.minecolonies.api.colony.requestsystem.requestable.StackList stackListRequest) {
            try {
                for (ItemStack stack : stackListRequest.getStacks()) {
                    addAlternativeStackKey(keys, stack);
                }
            } catch (RuntimeException ignored) {
                // Optional MineColonies requestable details.
            }
            try {
                for (ItemStack stack : stackListRequest.getRequestedItems()) {
                    addAlternativeStackKey(keys, stack);
                }
            } catch (RuntimeException ignored) {
                // Optional MineColonies requestable details.
            }
        }
    }

    private static void addStackKeys(List<SmartInfoMatchKey> keys, ItemStack stack, int priority) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        addKey(keys, SmartClipboardReport.exactStackKey(stack), priority);
        addKey(keys, SmartClipboardReport.resourceStackKey(stack), Math.max(priority, SmartClipboardReport.SMART_INFO_PRIORITY_RESOURCE));
        if (DomumOrnamentumRequestInspector.isDomumOrnamentumStack(stack)) {
            addKey(keys, SmartClipboardReport.domumFingerprintKey(stack), Math.min(priority, SmartClipboardReport.SMART_INFO_PRIORITY_FINGERPRINT));
        }
    }

    private static void addAlternativeStackKey(List<SmartInfoMatchKey> keys, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        addKey(keys, SmartClipboardReport.alternativeStackKey(stack), SmartClipboardReport.SMART_INFO_PRIORITY_ALTERNATIVE);
    }

    private static void addKey(List<SmartInfoMatchKey> keys, String key, int priority) {
        if (key != null && !key.isBlank()) {
            keys.add(new SmartInfoMatchKey(key, priority));
        }
    }

    private static List<RequestTreeNode> cutterRequirementTree(ServerLevel level, IRequest<?> request, ItemStack requestedStack) {
        ItemStack materializedStack = DomumOrnamentumRequestInspector.materializedRequestedStack(request)
                .map(stack -> stack.copyWithCount(Math.max(1, requestedStack.getCount())))
                .orElse(requestedStack);
        List<DomumOrnamentumRequestInspector.IngredientRequirement> requirements =
                DomumOrnamentumRequestInspector.findCutterRequirements(level.getRecipeManager(), level.registryAccess(), materializedStack);
        if (requirements.isEmpty()) {
            return List.of();
        }
        List<RequestTreeNode> nodes = new ArrayList<>();
        nodes.add(RequestTreeNode.synthetic(0, requestedStack.copy(), requestedStack.getCount(), "x" + Math.max(1, requestedStack.getCount()), requestedStack.getHoverName().getString()));
        for (DomumOrnamentumRequestInspector.IngredientRequirement requirement : requirements) {
            ItemStack stack = requirement.stack().copyWithCount(requirement.count());
            nodes.add(RequestTreeNode.synthetic(1, stack, requirement.count(), "x" + Math.max(1, requirement.count()), stack.getHoverName().getString()));
        }
        return nodes;
    }

    private static boolean hasDependencyNodes(List<RequestTreeNode> nodes) {
        return nodes.stream().anyMatch(node -> node.depth() > 0);
    }

    private static Optional<IBuilding> buildingForRequest(IColony colony, IRequest<?> request) {
        try {
            BlockPos location = request.getRequester().getLocation().getInDimensionLocation();
            return Optional.ofNullable(colony.getBuildingManager().getBuilding(location));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static String requesterDisplayName(IRequestManager manager, IRequest<?> request, IBuilding fallbackBuilding) {
        String buildingName = buildingDisplayName(fallbackBuilding);
        if (!buildingName.isBlank()) {
            return buildingName;
        }
        try {
            String displayName = request.getRequester().getRequesterDisplayName(manager, request).getString();
            if (!displayName.isBlank()) {
                return requesterBuildingPart(displayName);
            }
        } catch (RuntimeException ignored) {
            // Fall back to the building display name.
        }
        return fallbackBuilding == null ? request.getRequester().getClass().getSimpleName() : buildingName;
    }

    private static String requesterBuildingPart(String displayName) {
        int separator = displayName.indexOf(':');
        String value = separator >= 0 ? displayName.substring(0, separator) : displayName;
        return humanizeBuildingName(value);
    }

    private static List<ItemStack> displayStacks(IRequest<?> request, ItemStack fallback) {
        List<ItemStack> stacks = new ArrayList<>();
        try {
            for (ItemStack stack : request.getDisplayStacks()) {
                if (!stack.isEmpty()) {
                    ItemStack copy = stack.copy();
                    if (copy.getCount() <= 0) {
                        copy.setCount(Math.max(1, fallback.getCount()));
                    }
                    stacks.add(copy);
                }
            }
        } catch (RuntimeException ignored) {
            // Fall back to the resolved request stack below.
        }
        if (stacks.isEmpty() && !fallback.isEmpty()) {
            stacks.add(fallback.copy());
        }
        if (stacks.isEmpty()) {
            stackBasedTaskStack(request).ifPresent(stacks::add);
        }
        return stacks;
    }

    private static int warehouseStock(IColony colony, ItemStack requestedStack) {
        int total = 0;
        for (IWareHouse warehouse : colony.getBuildingManager().getWareHouses()) {
            try {
                Object tile = warehouse.getClass().getMethod("getTileEntity").invoke(warehouse);
                if (tile == null) {
                    continue;
                }
                Method matchingStacks = tile.getClass().getMethod("getMatchingItemStacksInWarehouse", Predicate.class);
                @SuppressWarnings("unchecked")
                List<Object> matches = (List<Object>) matchingStacks.invoke(tile, (Predicate<ItemStack>) stack -> ItemStack.isSameItemSameComponents(stack, requestedStack));
                for (Object match : matches) {
                    Object stack = match.getClass().getMethod("getA").invoke(match);
                    if (stack instanceof ItemStack itemStack) {
                        total += itemStack.getCount();
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return -1;
            }
        }
        return total;
    }

    private static String buildingDisplayName(IBuilding building) {
        if (building == null) {
            return "";
        }
        try {
            if (building.getBuildingType() != null) {
                Object hutBlock = building.getBuildingType().getClass().getMethod("getBuildingBlock").invoke(building.getBuildingType());
                Object hutNameValue = hutBlock == null ? null : hutBlock.getClass().getMethod("getHutName").invoke(hutBlock);
                if (hutNameValue instanceof String hutName && !hutName.isBlank()) {
                    return humanizeBuildingName(hutName);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fall back to MineColonies' building display below.
        }
        try {
            String displayName = building.getBuildingDisplayName();
            if (displayName != null && !displayName.isBlank()) {
                return humanizeBuildingName(displayName);
            }
        } catch (RuntimeException ignored) {
            // Fall through to custom name only if MineColonies does not expose a type display.
        }
        if (building.getCustomName() != null && !building.getCustomName().isBlank()) {
            return humanizeBuildingName(building.getCustomName());
        }
        return "";
    }

    private static String humanizeBuildingName(String name) {
        String value = name.replaceAll("(?i)\\bHut\\b", " ")
                .replaceAll("(?i)^blockhut", "")
                .replaceAll("(?<=[a-z])(?=[A-Z])", " ")
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        if (value.equalsIgnoreCase("flower")) {
            return "Florist";
        }
        if (value.isBlank()) {
            return "";
        }
        String[] words = value.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase());
        }
        return result.toString();
    }

    private static Optional<BlockPos> buildingPosition(IBuilding building) {
        try {
            return Optional.ofNullable(building.getLocation()).map(location -> location.getInDimensionLocation());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static String buildingGroupId(IBuilding building) {
        if (building == null) {
            return "none";
        }
        return buildingPosition(building)
                .map(BlockPos::toShortString)
                .orElseGet(() -> buildingDisplayName(building));
    }

    private static Optional<String> workerName(IBuilding building, IRequestManager manager, IRequest<?> request, WorkerGroups workerGroups) {
        if (building != null) {
            Optional<String> directWorker = workerName(building, request.getId());
            if (directWorker.isPresent()) {
                return directWorker;
            }

            // MineColonies often assigns the citizen to the parent builder/order request, while material or crafting child
            // requests keep only the parent token. For display parity, inherit only from that explicit request parent chain.
            IRequest<?> current = request;
            for (int depth = 0; depth < 16; depth++) {
                try {
                    if (!current.hasParent()) {
                        break;
                    }
                    current = manager.getRequestForToken(current.getParent());
                    if (current == null) {
                        break;
                    }
                    Optional<String> parentWorker = workerName(building, current.getId());
                    if (parentWorker.isPresent()) {
                        return parentWorker;
                    }
                } catch (RuntimeException ignored) {
                    break;
                }
            }
        }
        Optional<String> requesterDisplayWorker = requesterDisplayWorker(manager, request);
        if (requesterDisplayWorker.isPresent()) {
            return requesterDisplayWorker;
        }
        return workerGroups.inherit(building, false).name();
    }

    private static Optional<String> workerName(IBuilding building, IToken<?> requestToken) {
        try {
            return building.getCitizenForRequest(requestToken).map(ICitizenData::getName);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static OwningWorker owningOrderWorker(IBuilding building, IRequestManager manager, IRequest<?> request) {
        if (building == null) {
            return OwningWorker.none("no building");
        }
        try {
            for (ICitizenData citizen : building.getAllAssignedCitizen()) {
                Collection<IRequest<?>> openRequests = building.getOpenRequests(citizen.getId());
                for (IRequest<?> openRequest : openRequests) {
                    if (openRequest == null || !isActive(openRequest.getState())) {
                        continue;
                    }
                    if (requestContainsToken(manager, openRequest, request.getId(), 0)) {
                        return new OwningWorker(
                                Optional.of(citizen.getName()),
                                openRequest.getId().toString(),
                                className(openRequest),
                                requesterDisplay(manager, openRequest),
                                openRequest.getId().toString().equals(request.getId().toString())
                                        ? "worker open request token"
                                        : "worker open request child graph"
                        );
                    }
                }
            }
        } catch (RuntimeException ignored) {
            return OwningWorker.none("building worker open requests unavailable");
        }
        return OwningWorker.none("no worker open request contains token");
    }

    private static boolean requestContainsToken(IRequestManager manager, IRequest<?> root, IToken<?> token, int depth) {
        if (root == null || token == null || depth > 32) {
            return false;
        }
        if (root.getId().toString().equals(token.toString())) {
            return true;
        }
        if (!root.hasChildren()) {
            return false;
        }
        for (IToken<?> child : root.getChildren()) {
            try {
                if (requestContainsToken(manager, manager.getRequestForToken(child), token, depth + 1)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // Request may resolve while scanning.
            }
        }
        return false;
    }

    private static Optional<String> requesterDisplayWorker(IRequestManager manager, IRequest<?> request) {
        IRequest<?> current = request;
        for (int depth = 0; current != null && depth < 16; depth++) {
            Optional<String> worker = requesterDisplayWorkerForRequest(manager, current);
            if (worker.isPresent()) {
                return worker;
            }
            try {
                if (!current.hasParent()) {
                    break;
                }
                current = manager.getRequestForToken(current.getParent());
            } catch (RuntimeException ignored) {
                break;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> requesterDisplayWorkerForRequest(IRequestManager manager, IRequest<?> request) {
        try {
            String displayName = request.getRequester().getRequesterDisplayName(manager, request).getString();
            if (!isPlayerFacingRequesterDisplay(displayName)) {
                return Optional.empty();
            }
            String worker = displayName.substring(displayName.indexOf(':') + 1).trim();
            return isPlayerFacingName(worker) ? Optional.of(worker) : Optional.empty();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static boolean isPlayerFacingRequesterDisplay(String value) {
        if (value == null || value.isBlank() || !value.contains(":")) {
            return false;
        }
        String requester = value.substring(0, value.indexOf(':')).trim();
        String worker = value.substring(value.indexOf(':') + 1).trim();
        return isPlayerFacingName(requester) && isPlayerFacingName(worker);
    }

    private static boolean isPlayerFacingName(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase();
        return !lower.contains("com.")
                && !lower.contains("minecolonies")
                && !lower.contains("request")
                && !lower.contains("token")
                && !value.contains("{")
                && !value.contains("}")
                && !value.contains("@");
    }

    private static boolean isMinimumStockRequest(IRequest<?> request) {
        try {
            if (request.getRequest() instanceof MinimumStack) {
                return true;
            }
            return request.getRequestOfType(MinimumStack.class).isPresent();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Optional<String> dimensionName(IRequest<?> request) {
        try {
            return Optional.ofNullable(request.getRequester())
                    .map(requester -> requester.getLocation().getDimension().location().toString());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> resolverName(IColony colony, IRequest<?> request) {
        return resolverName(colony.getRequestManager(), request);
    }

    private static Optional<String> resolverName(IRequestManager manager, IRequest<?> request) {
        try {
            Object resolver = manager.getResolverForRequest(request.getId());
            if (resolver == null) {
                return Optional.empty();
            }
            Optional<String> displayName = resolverDisplayName(manager, request, resolver);
            Optional<String> workerName = displayName.flatMap(RequestAnalysisService::displayWorkerPart);
            if (workerName.isPresent()) {
                return workerName;
            }
            Optional<String> requesterName = displayName.flatMap(RequestAnalysisService::displayRequesterPart);
            if (requesterName.isPresent()) {
                return requesterName;
            }
            return friendlyResolverFallback(resolver.getClass().getSimpleName());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> resolverDisplayName(IRequestManager manager, IRequest<?> request, Object resolver) {
        if (!(resolver instanceof IRequestResolver<?> requestResolver)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(requestResolver.getRequesterDisplayName(manager, request))
                    .map(Component::getString)
                    .filter(value -> !value.isBlank());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> displayWorkerPart(String displayName) {
        int separator = displayName.indexOf(':');
        if (separator < 0) {
            return Optional.empty();
        }
        String worker = displayName.substring(separator + 1).trim();
        return isPlayerFacingName(worker) ? Optional.of(worker) : Optional.empty();
    }

    private static Optional<String> displayRequesterPart(String displayName) {
        int separator = displayName.indexOf(':');
        String requester = separator >= 0 ? displayName.substring(0, separator).trim() : displayName.trim();
        if (!isUsefulResolverLabel(requester)) {
            return Optional.empty();
        }
        return Optional.of(humanizeBuildingName(requester));
    }

    private static boolean isUsefulResolverLabel(String value) {
        if (!isPlayerFacingName(value)) {
            return false;
        }
        String lower = value.toLowerCase();
        return !lower.contains("resolver")
                && !lower.contains("production")
                && !lower.contains("standard")
                && !lower.contains("public worker")
                && !lower.contains("private worker");
    }

    private static Optional<String> friendlyResolverFallback(String resolverClassName) {
        if (resolverClassName == null || resolverClassName.isBlank()) {
            return Optional.empty();
        }
        String lower = resolverClassName.toLowerCase();
        if (lower.contains("player")) {
            return Optional.of("Player");
        }
        if (lower.contains("retry")) {
            return Optional.of("Pending");
        }
        if (lower.contains("warehouse") || lower.contains("delivery") || lower.contains("pickup")) {
            return Optional.of("Warehouse");
        }
        if (lower.contains("crafting") || lower.contains("station")) {
            return Optional.of("Crafter");
        }
        if (lower.contains("building")) {
            return Optional.of("Builder");
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> requesterLocation(IRequest<?> request) {
        try {
            return Optional.ofNullable(request.getRequester())
                    .map(requester -> requester.getLocation().getInDimensionLocation());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> parentToken(IRequest<?> request) {
        try {
            return request.hasParent() ? Optional.ofNullable(request.getParent()).map(Object::toString) : Optional.empty();
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static String requestType(IRequest<?> request) {
        try {
            Object requestable = request.getRequest();
            return requestable == null ? className(request) : requestable.getClass().getSimpleName();
        } catch (RuntimeException ignored) {
            return className(request);
        }
    }

    private static List<RequestTreeNode> requestTree(IRequestManager manager, IRequest<?> request, int depth, int remaining) {
        List<RequestTreeNode> nodes = new ArrayList<>();
        if (remaining <= 0) {
            return nodes;
        }
        ItemStack displayStack = requestDisplayStack(request);
        nodes.add(new RequestTreeNode(
                depth,
                displayStack,
                requestCount(request),
                quantityDisplay(request, displayStack),
                request.getShortDisplayString().getString(),
                Optional.ofNullable(request.getId()).map(Object::toString),
                parentToken(request),
                Optional.of(requesterDisplayName(manager, request, null)),
                requesterLocation(request),
                dimensionName(request),
                resolverName(manager, request),
                requestType(request),
                false
        ));
        if (!request.hasChildren()) {
            return nodes;
        }
        for (IToken<?> child : request.getChildren()) {
            try {
                IRequest<?> childRequest = manager.getRequestForToken(child);
                if (childRequest != null) {
                    nodes.addAll(requestTree(manager, childRequest, depth + 1, remaining - nodes.size()));
                }
            } catch (RuntimeException ignored) {
                // Requests can be resolved while the report is being built.
            }
            if (nodes.size() >= remaining) {
                break;
            }
        }
        return nodes;
    }

    private static ItemStack requestDisplayStack(IRequest<?> request) {
        Optional<ItemStack> stackBasedTask = stackBasedTaskStack(request);
        if (stackBasedTask.isPresent()) {
            return stackBasedTask.get();
        }

        Optional<ItemStack> stack = requestedStack(request);
        if (stack.isPresent()) {
            return stack.get();
        }
        for (ItemStack displayStack : request.getDisplayStacks()) {
            if (!displayStack.isEmpty()) {
                return displayStack.copy();
            }
        }
        return ItemStack.EMPTY;
    }

    private static int requestCount(IRequest<?> request) {
        Optional<ItemStack> stackBasedTask = stackBasedTaskStack(request);
        if (stackBasedTask.isPresent()) {
            return Math.max(1, stackBasedTask.get().getCount());
        }

        Optional<IDeliverable> deliverable = deliverable(request);
        if (deliverable.isPresent()) {
            return Math.max(1, deliverable.get().getCount());
        }
        Optional<ItemStack> stack = requestedStack(request);
        return stack.map(ItemStack::getCount).orElse(1);
    }

    private static String quantityDisplay(IRequest<?> request, ItemStack fallback) {
        Optional<ItemStack> stackBasedTask = stackBasedTaskStack(request);
        if (stackBasedTask.isPresent()) {
            return "x" + Math.max(1, stackBasedTask.get().getCount());
        }

        Optional<IDeliverable> deliverable = deliverable(request);
        if (deliverable.isPresent()) {
            int minimum = Math.max(0, deliverable.get().getMinimumCount());
            int count = Math.max(1, deliverable.get().getCount());
            if (minimum > 0 && minimum != count) {
                return minimum + "-" + count;
            }
            return "x" + count;
        }
        return "x" + Math.max(1, fallback.getCount());
    }

    private static Optional<IDeliverable> deliverable(IRequest<?> request) {
        try {
            if (request.getRequest() instanceof IDeliverable deliverable) {
                return Optional.of(deliverable);
            }
            return request.getRequestOfType(IDeliverable.class);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public record AnalysisResult(String colonyName, int colonyId, int buildingCount, int activeRequestCount, Map<String, List<RequestReportEntry>> groupedEntries, int reportedCount, boolean capped) {
    }

    public record RequestReportEntry(
            String requesterName,
            Optional<BlockPos> requesterPosition,
            Optional<String> workerName,
            Optional<String> dimensionName,
            Optional<String> resolverName,
            String requestToken,
            ItemStack requestedStack,
            List<ItemStack> displayStacks,
            Component requestedItemName,
            int requestedCount,
            String quantityDisplay,
            boolean important,
            boolean minimumStockRequest,
            int warehouseStock,
            ResourceLocation domumBlockId,
            Optional<ResourceLocation> cutterRecipe,
            String exactComboFingerprint,
            boolean exactComboTaught,
            List<String> knownBy,
            List<String> canLearn,
            List<RequestTreeNode> requestTree,
            List<SmartInfoMatchKey> smartInfoKeys
    ) {
    }

    public record SmartInfoMatchKey(String key, int priority) {
    }

    public record RequestTreeNode(
            int depth,
            ItemStack stack,
            int count,
            String quantityDisplay,
            String label,
            Optional<String> requestToken,
            Optional<String> parentToken,
            Optional<String> requesterName,
            Optional<BlockPos> requesterLocation,
            Optional<String> requesterDimension,
            Optional<String> resolverName,
            String requestType,
            boolean synthetic
    ) {
        private static RequestTreeNode synthetic(int depth, ItemStack stack, int count, String quantityDisplay, String label) {
            return new RequestTreeNode(
                    depth,
                    stack,
                    count,
                    quantityDisplay,
                    label,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    "synthetic",
                    true
            );
        }
    }
}
