package com.createcolonylogistics.item;

import com.createcolonylogistics.clipboard.ColonyContextResolver;
import com.createcolonylogistics.clipboard.RequestAnalysisService;
import com.createcolonylogistics.clipboard.RequestReportFormatter;
import com.minecolonies.api.colony.IColony;
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

import java.util.Optional;

public class SmartColonyClipboardItem extends Item {
    private static final int MAX_RELEVANT_REQUESTS = 10;

    public SmartColonyClipboardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
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

        RequestAnalysisService.AnalysisResult result = RequestAnalysisService.analyze(player.serverLevel(), colony.get(), MAX_RELEVANT_REQUESTS);
        RequestReportFormatter.format(result).forEach(player::sendSystemMessage);
    }
}
