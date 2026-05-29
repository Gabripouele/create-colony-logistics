package com.createcolonylogistics.item;

import com.createcolonylogistics.clipboard.ColonyContextResolver;
import com.createcolonylogistics.clipboard.RequestAnalysisService;
import com.createcolonylogistics.clipboard.SmartClipboardReport;
import com.createcolonylogistics.clipboard.SmartClipboardScrollStorage;
import com.createcolonylogistics.clipboard.SmartClipboardRecipeTeachingService;
import com.createcolonylogistics.network.ClientboundSmartClipboardReportPacket;
import com.createcolonylogistics.registry.CCLItems;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.lang.reflect.Method;
import java.util.Optional;

public class SmartColonyClipboardItem extends Item {
    private static final int MAX_REPORT_REQUESTS = 250;

    public SmartColonyClipboardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.isShiftKeyDown()) {
                IBuilding building = IMinecoloniesAPI.getInstance().getColonyManager().getBuilding(context.getLevel(), context.getClickedPos());
                if (building != null && building.getColony() != null) {
                    if (building instanceof ITownHall) {
                        linkMineColoniesClipboardContext(serverPlayer, context.getHand(), context);
                        return InteractionResult.SUCCESS;
                    }
                    SmartClipboardRecipeTeachingService.teachRequestedArchitectsCutterRecipes(
                            serverPlayer,
                            serverPlayer.serverLevel(),
                            building.getColony(),
                            building
                    );
                    return InteractionResult.SUCCESS;
                }
            }
            runReport(serverPlayer, context.getItemInHand());
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            runReport(serverPlayer, stack);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void runReport(ServerPlayer player, ItemStack clipboard) {
        Optional<IColony> colony = ColonyContextResolver.resolveLinkedClipboard(clipboard);
        if (colony.isEmpty()) {
            player.sendSystemMessage(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.no_colony"));
            return;
        }

        RequestAnalysisService.AnalysisResult result = RequestAnalysisService.analyze(player.serverLevel(), colony.get(), MAX_REPORT_REQUESTS);
        PacketDistributor.sendToPlayer(player, new ClientboundSmartClipboardReportPacket(
                SmartClipboardReport.fromAnalysis(result, SmartClipboardScrollStorage.read(clipboard))
        ));
    }

    private void linkMineColoniesClipboardContext(ServerPlayer player, InteractionHand hand, UseOnContext context) {
        ItemStack usedStack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || usedStack.isEmpty() || !usedStack.is(CCLItems.SMART_COLONY_CLIPBOARD.get())) {
            return;
        }
        try {
            Object blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
            if (blockEntity == null) {
                return;
            }

            Method writer = blockEntity.getClass().getMethod("writeColonyToItemStack", ItemStack.class);
            SmartClipboardColonyLinkGuard.runWithExplicitLink(() -> {
                try {
                    writer.invoke(blockEntity, usedStack);
                } catch (ReflectiveOperationException exception) {
                    throw new SmartClipboardLinkException(exception);
                }
            });
            player.sendSystemMessage(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.linked",
                    IMinecoloniesAPI.getInstance().getColonyManager().getBuilding(context.getLevel(), context.getClickedPos()).getColony().getName())
                    .withStyle(ChatFormatting.GRAY));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Not a MineColonies building tile or no compatible clipboard context writer.
        }
    }

    private static class SmartClipboardLinkException extends RuntimeException {
        private SmartClipboardLinkException(Throwable cause) {
            super(cause);
        }
    }
}
