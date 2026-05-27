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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

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
            RequestReportEntry entry = inspectRequest(level, colony, requesterBuilding, request, requestedStack.get(), !lessImportantRequests.contains(request.getId()));
            grouped.computeIfAbsent(entry.requesterName(), ignored -> new ArrayList<>()).add(entry);
            reported++;
        }

        return new AnalysisResult(colony.getName(), colony.getID(), colony.getBuildingManager().getBuildings().size(), activeRequestCount, grouped, reported, capped);
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
        Optional<ItemStack> direct = stackBasedTaskStack(request instanceof IStackBasedTask task ? task : null);
        if (direct.isPresent()) {
            return direct;
        }
        try {
            return request.getRequestOfType(IStackBasedTask.class).flatMap(RequestAnalysisService::stackBasedTaskStack);
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

    private static RequestReportEntry inspectRequest(ServerLevel level, IColony colony, IBuilding building, IRequest<?> request, ItemStack requestedStack, boolean important) {
        boolean minimumStockRequest = isMinimumStockRequest(request);
        boolean domumRequest = DomumOrnamentumRequestInspector.isDomumOrnamentumStack(requestedStack);
        ColonyProductionInspector.ProductionKnowledge knowledge = domumRequest
                ? ColonyProductionInspector.inspect(colony, requestedStack)
                : new ColonyProductionInspector.ProductionKnowledge(Collections.emptyList(), Collections.emptyList());
        Optional<ResourceLocation> cutterRecipe = domumRequest
                ? DomumOrnamentumRequestInspector.findCutterRecipe(level.getRecipeManager(), level.registryAccess(), requestedStack)
                : Optional.empty();
        List<RequestTreeNode> tree = requestTree(colony.getRequestManager(), request, 0, 16);
        if (domumRequest) {
            List<RequestTreeNode> cutterTree = cutterRequirementTree(level, requestedStack);
            if (!cutterTree.isEmpty()) {
                tree = cutterTree;
            }
        }

        return new RequestReportEntry(
                requesterDisplayName(colony.getRequestManager(), request, building),
                building == null ? Optional.empty() : buildingPosition(building),
                minimumStockRequest ? Optional.empty() : workerName(building, colony.getRequestManager(), request),
                dimensionName(request),
                resolverName(colony, request),
                request.getId().toString(),
                requestedStack.copy(),
                displayStacks(request, requestedStack),
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
                tree
        );
    }

    private static List<RequestTreeNode> cutterRequirementTree(ServerLevel level, ItemStack requestedStack) {
        List<DomumOrnamentumRequestInspector.IngredientRequirement> requirements =
                DomumOrnamentumRequestInspector.findCutterRequirements(level.getRecipeManager(), level.registryAccess(), requestedStack);
        if (requirements.isEmpty()) {
            return List.of();
        }
        List<RequestTreeNode> nodes = new ArrayList<>();
        nodes.add(new RequestTreeNode(0, requestedStack.copy(), requestedStack.getCount(), "x" + Math.max(1, requestedStack.getCount()), requestedStack.getHoverName().getString()));
        for (DomumOrnamentumRequestInspector.IngredientRequirement requirement : requirements) {
            ItemStack stack = requirement.stack().copyWithCount(requirement.count());
            nodes.add(new RequestTreeNode(1, stack, requirement.count(), "x" + Math.max(1, requirement.count()), stack.getHoverName().getString()));
        }
        return nodes;
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

    private static Optional<String> workerName(IBuilding building, IRequestManager manager, IRequest<?> request) {
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
        return requesterDisplayWorker(manager, request);
    }

    private static Optional<String> workerName(IBuilding building, IToken<?> requestToken) {
        try {
            return building.getCitizenForRequest(requestToken).map(ICitizenData::getName);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
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
        try {
            Object resolver = colony.getRequestManager().getResolverForRequest(request.getId());
            return Optional.ofNullable(resolver).map(value -> value.getClass().getSimpleName());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static List<RequestTreeNode> requestTree(IRequestManager manager, IRequest<?> request, int depth, int remaining) {
        List<RequestTreeNode> nodes = new ArrayList<>();
        if (remaining <= 0) {
            return nodes;
        }
        ItemStack displayStack = requestDisplayStack(request);
        nodes.add(new RequestTreeNode(depth, displayStack, requestCount(request), quantityDisplay(request, displayStack), request.getShortDisplayString().getString()));
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
            List<RequestTreeNode> requestTree
    ) {
    }

    public record RequestTreeNode(
            int depth,
            ItemStack stack,
            int count,
            String quantityDisplay,
            String label
    ) {
    }
}
