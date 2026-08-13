package io.github.nbcss.createfactorycontroller.content.helper;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.ServerConfig;
import io.github.nbcss.createfactorycontroller.content.block.FilterLinkBlock;
import io.github.nbcss.createfactorycontroller.content.block.FilterLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public final class FilterApplication {
    private FilterApplication() {}

    public static int range() {
        return ServerConfig.filterLinkRange();
    }

    public static boolean hasFilter(BlockGetter level, BlockPos pos) {
        return BlockEntityBehaviour.get(level, pos, FilteringBehaviour.TYPE) != null;
    }

    public static void applyFromBox(PackagerBlockEntity packager, ItemStack box) {
        Level level = packager.getLevel();
        if (level == null || level.isClientSide) return;
        // apply links
        BlockPos pos = packager.getBlockPos();
        ItemStack filter = null;
        for (Direction d : Direction.values()) {
            BlockPos adj = pos.relative(d);
            BlockState adjState = level.getBlockState(adj);
            if (adjState.getBlock() == CreateFactoryController.FILTER_LINK.get()
                    && FilterLinkBlock.getConnectedDirection(adjState) == d
                    && level.getBlockEntity(adj) instanceof FilterLinkBlockEntity link) {
                if (filter == null) {
                    PackageFilter pf = box.get(CreateFactoryController.PACKAGE_FILTER.get());
                    filter = pf == null ? ItemStack.EMPTY : pf.stack();
                }
                apply(level, link, filter);
            }
        }
    }

    public static void apply(Level level, FilterLinkBlockEntity link, ItemStack filter) {
        ItemStack toApply = filter.isEmpty() ? ItemStack.EMPTY : filter.copyWithCount(1);
        for (BlockPos target : link.getTargets()) {
            FilteringBehaviour beh = BlockEntityBehaviour.get(level, target, FilteringBehaviour.TYPE);
            if (beh != null) beh.setFilter(toApply);
        }
    }
}
