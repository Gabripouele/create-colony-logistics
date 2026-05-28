package com.createcolonylogistics.item;

import com.createcolonylogistics.clipboard.ColonyContextResolver;
import com.createcolonylogistics.clipboard.RequestAnalysisService;
import com.createcolonylogistics.clipboard.SmartClipboardReport;
import com.createcolonylogistics.clipboard.SmartClipboardScrollStorage;
import com.createcolonylogistics.clipboard.SmartClipboardRecipeTeachingService;
import com.createcolonylogistics.network.ClientboundSmartClipboardReportPacket;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
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
            preserveMineColoniesClipboardContext(context);
            if (serverPlayer.isShiftKeyDown()) {
                IBuilding building = IMinecoloniesAPI.getInstance().getColonyManager().getBuilding(context.getLevel(), context.getClickedPos());
                if (building != null && building.getColony() != null) {
                    SmartClipboardRecipeTeachingService.teachRequestedArchitectsCutterRecipes(
                            serverPlayer,
                            serverPlayer.serverLevel(),
                            building.getColony(),
                            building
                    );
                    return InteractionResult.SUCCESS;
                }
            }
            runReport(serverPlayer, Optional.of(context.getClickedPos()));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            runReport(serverPlayer, Optional.empty());
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void runReport(ServerPlayer player, Optional<net.minecraft.core.BlockPos> clickedPos) {
        Optional<IColony> colony = ColonyContextResolver.resolve(player, clickedPos);
        if (colony.isEmpty()) {
            player.sendSystemMessage(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.no_colony"));
            return;
        }

        RequestAnalysisService.AnalysisResult result = RequestAnalysisService.analyze(player.serverLevel(), colony.get(), MAX_REPORT_REQUESTS);
        ItemStack clipboard = SmartClipboardScrollStorage.findSmartClipboard(player).orElse(ItemStack.EMPTY);
        PacketDistributor.sendToPlayer(player, new ClientboundSmartClipboardReportPacket(
                SmartClipboardReport.fromAnalysis(result, SmartClipboardScrollStorage.read(clipboard))
        ));
    }

    private void preserveMineColoniesClipboardContext(UseOnContext context) {
        try {
            Object blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
            if (blockEntity == null) {
                return;
            }

            Method writer = blockEntity.getClass().getMethod("writeColonyToItemStack", ItemStack.class);
            writer.invoke(blockEntity, context.getItemInHand());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Not a MineColonies building tile or no compatible clipboard context writer.
        }
    }
}
