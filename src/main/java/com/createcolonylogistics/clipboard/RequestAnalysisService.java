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

        for (IBuilding requesterBuilding : colony.getBuildingManager().getBuildings().values().stream()
                .sorted(Comparator.comparing(RequestAnalysisService::buildingDisplayName))
                .toList()) {
            for (IRequest<?> request : openRequestsForBuilding(colony, requesterBuilding)) {
                activeRequestCount++;
                Optional<ItemStack> requestedStack = requestedStack(request);
                if (requestedStack.isEmpty()) {
                    continue;
                }

                if (reported >= limit) {
                    capped = true;
                    continue;
                }

                RequestReportEntry entry = inspectRequest(level, colony, requesterBuilding, request, requestedStack.get(), !lessImportantRequests.contains(request.getId()));
                grouped.computeIfAbsent(entry.requesterName(), ignored -> new ArrayList<>()).add(entry);
                reported++;
            }
        }

        return new AnalysisResult(colony.getName(), colony.getID(), colony.getBuildingManager().getBuildings().size(), activeRequestCount, grouped, reported, capped);
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
        boolean domumRequest = DomumOrnamentumRequestInspector.isDomumOrnamentumStack(requestedStack);
        ColonyProductionInspector.ProductionKnowledge knowledge = domumRequest
                ? ColonyProductionInspector.inspect(colony, requestedStack)
                : new ColonyProductionInspector.ProductionKnowledge(Collections.emptyList(), Collections.emptyList());
        Optional<ResourceLocation> cutterRecipe = domumRequest
                ? DomumOrnamentumRequestInspector.findCutterRecipe(level.getRecipeManager(), level.registryAccess(), requestedStack)
                : Optional.empty();

        return new RequestReportEntry(
                buildingDisplayName(building),
                buildingPosition(building),
                workerName(building, colony.getRequestManager(), request),
                dimensionName(request),
                resolverName(colony, request),
                request.getId().toString(),
                requestedStack.copy(),
                displayStacks(request, requestedStack),
                requestedStack.getHoverName(),
                requestedStack.getCount(),
                important,
                warehouseStock(colony, requestedStack),
                DomumOrnamentumRequestInspector.itemId(requestedStack),
                cutterRecipe,
                DomumOrnamentumRequestInspector.exactComboFingerprint(requestedStack),
                !knowledge.knownBy().isEmpty(),
                knowledge.knownBy(),
                knowledge.canLearn(),
                requestTree(colony.getRequestManager(), request, 0, 16)
        );
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
        if (building.getCustomName() != null && !building.getCustomName().isBlank()) {
            return building.getCustomName();
        }
        return building.getBuildingDisplayName();
    }

    private static Optional<BlockPos> buildingPosition(IBuilding building) {
        try {
            return Optional.ofNullable(building.getLocation()).map(location -> location.getInDimensionLocation());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> workerName(IBuilding building, IRequestManager manager, IRequest<?> request) {
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
                    return Optional.empty();
                }
                current = manager.getRequestForToken(current.getParent());
                if (current == null) {
                    return Optional.empty();
                }
                Optional<String> parentWorker = workerName(building, current.getId());
                if (parentWorker.isPresent()) {
                    return parentWorker;
                }
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static Optional<String> workerName(IBuilding building, IToken<?> requestToken) {
        try {
            return building.getCitizenForRequest(requestToken).map(ICitizenData::getName);
        } catch (RuntimeException ignored) {
            return Optional.empty();
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
        nodes.add(new RequestTreeNode(depth, requestDisplayStack(request), requestCount(request), request.getShortDisplayString().getString()));
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
        Optional<ItemStack> stack = requestedStack(request);
        return stack.map(ItemStack::getCount).orElse(1);
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
            boolean important,
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
            String label
    ) {
    }
}
