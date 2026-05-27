package com.createcolonylogistics.item;

import com.createcolonylogistics.clipboard.ColonyContextResolver;
import com.createcolonylogistics.clipboard.RequestAnalysisService;
import com.createcolonylogistics.clipboard.SmartClipboardReport;
import com.createcolonylogistics.menu.SmartClipboardMenu;
import com.minecolonies.api.colony.IColony;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;

public class SmartColonyClipboardItem extends Item {
    private static final int MAX_RELEVANT_REQUESTS = 10;

    public SmartColonyClipboardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            preserveMineColoniesClipboardContext(context);
            runReport(serverPlayer, context.getHand(), Optional.of(context.getClickedPos()));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            runReport(serverPlayer, hand, Optional.empty());
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void runReport(ServerPlayer player, InteractionHand hand, Optional<net.minecraft.core.BlockPos> clickedPos) {
        Optional<IColony> colony = ColonyContextResolver.resolve(player, clickedPos);
        if (colony.isEmpty()) {
            player.sendSystemMessage(Component.translatable("item.create_colony_logistics.smart_colony_clipboard.no_colony"));
            return;
        }

        RequestAnalysisService.AnalysisResult result = RequestAnalysisService.analyze(player.serverLevel(), colony.get(), MAX_RELEVANT_REQUESTS);
        SmartClipboardReport report = SmartClipboardReport.fromAnalysis(result);
        int slot = hand == InteractionHand.OFF_HAND ? Inventory.SLOT_OFFHAND : player.getInventory().selected;
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("screen.create_colony_logistics.smart_clipboard.title");
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player ignored) {
                return new SmartClipboardMenu(containerId, inventory, slot, report);
            }
        }, buffer -> {
            buffer.writeVarInt(slot);
            report.encode(buffer);
        });
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
