package com.createcolonylogistics.clipboard;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.items.component.ColonyId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

public final class ColonyContextResolver {
    private ColonyContextResolver() {
    }

    public static Optional<IColony> resolve(ServerPlayer player, Optional<BlockPos> clickedPos) {
        Level level = player.level();

        if (clickedPos.isPresent()) {
            IBuilding building = IMinecoloniesAPI.getInstance().getColonyManager().getBuilding(level, clickedPos.get());
            if (building != null && building.getColony() != null) {
                return Optional.of(building.getColony());
            }
        }

        return Optional.ofNullable(IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, player.blockPosition()));
    }

    public static Optional<IColony> resolveLinkedClipboard(ItemStack clipboard) {
        if (clipboard.isEmpty() || !ColonyId.readFromItemStack(clipboard).hasColonyId()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ColonyId.readColonyFromItemStack(clipboard));
    }
}
