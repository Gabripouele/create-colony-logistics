package com.createcolonylogistics.clipboard;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class RequestAnalysisService {
    private RequestAnalysisService() {
    }

    public static AnalysisResult analyze(ServerLevel level, IColony colony, int limit) {
        Map<String, List<RequestReportEntry>> grouped = new LinkedHashMap<>();
        int relevant = 0;
        int activeRequestCount = 0;
        boolean capped = false;

        for (IBuilding requesterBuilding : colony.getBuildingManager().getBuildings().values().stream()
                .sorted(Comparator.comparing(RequestAnalysisService::buildingDisplayName))
                .toList()) {
            for (IRequest<?> request : openRequestsForBuilding(colony, requesterBuilding)) {
                activeRequestCount++;
                Optional<ItemStack> requestedStack = requestedStack(request);
                if (requestedStack.isEmpty() || !DomumOrnamentumRequestInspector.isDomumOrnamentumStack(requestedStack.get())) {
                    continue;
                }

                if (relevant >= limit) {
                    capped = true;
                    continue;
                }

                RequestReportEntry entry = inspectRequest(level, colony, requesterBuilding, request, requestedStack.get());
                grouped.computeIfAbsent(entry.requesterName(), ignored -> new ArrayList<>()).add(entry);
                relevant++;
            }
        }

        return new AnalysisResult(colony.getName(), colony.getID(), colony.getBuildingManager().getBuildings().size(), activeRequestCount, grouped, relevant, capped);
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

    private static RequestReportEntry inspectRequest(ServerLevel level, IColony colony, IBuilding building, IRequest<?> request, ItemStack requestedStack) {
        ColonyProductionInspector.ProductionKnowledge knowledge = ColonyProductionInspector.inspect(colony, requestedStack);
        Optional<ResourceLocation> cutterRecipe = DomumOrnamentumRequestInspector.findCutterRecipe(level.getRecipeManager(), level.registryAccess(), requestedStack);

        return new RequestReportEntry(
                buildingDisplayName(building),
                buildingPosition(building),
                workerName(building, request.getId()),
                request.getId().toString(),
                requestedStack.copy(),
                requestedStack.getHoverName(),
                requestedStack.getCount(),
                warehouseStock(colony, requestedStack),
                DomumOrnamentumRequestInspector.itemId(requestedStack),
                cutterRecipe,
                DomumOrnamentumRequestInspector.exactComboFingerprint(requestedStack),
                !knowledge.knownBy().isEmpty(),
                knowledge.knownBy(),
                knowledge.canLearn()
        );
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

    private static Optional<String> workerName(IBuilding building, IToken<?> requestToken) {
        try {
            return building.getCitizenForRequest(requestToken).map(ICitizenData::getName);
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
            String requestToken,
            ItemStack requestedStack,
            Component requestedItemName,
            int requestedCount,
            int warehouseStock,
            ResourceLocation domumBlockId,
            Optional<ResourceLocation> cutterRecipe,
            String exactComboFingerprint,
            boolean exactComboTaught,
            List<String> knownBy,
            List<String> canLearn
    ) {
    }
}
