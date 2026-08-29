package io.github.nbcss.logisticscontrol.content.block;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity;
import com.simibubi.create.content.redstone.displayLink.LinkWithBulbBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import io.github.nbcss.logisticscontrol.CreateLogisticsControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FilterLinkBlockEntity extends LinkWithBulbBlockEntity {
    private final List<BlockPos> targets = new ArrayList<>();

    public FilterLinkBlockEntity(BlockPos pos, BlockState state) {
        super(CreateLogisticsControl.FILTER_LINK_BE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    public List<BlockPos> getTargets() {
        return targets;
    }

    public void setTargets(List<BlockPos> newTargets) {
        targets.clear();
        for (BlockPos p : newTargets) targets.add(p.immutable());
        notifyUpdate();
    }

    public void flash() {
        if (level == null) return;
        if (level.isClientSide) {
            pulse();
            return;
        }
        sendPulseNextSync();
        notifyUpdate();
    }

    @Nullable
    public PackagerBlockEntity getPackager() {
        if (level == null) return null;
        BlockPos source = worldPosition.relative(FilterLinkBlock.getConnectedDirection(getBlockState()).getOpposite());
        if (level.getBlockEntity(source) instanceof PackagerBlockEntity packager
                && !(packager instanceof RepackagerBlockEntity))
            return packager;
        return null;
    }

    @Override
    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        targets.clear();
        for (long packed : tag.getLongArray("Targets")) targets.add(BlockPos.of(packed).offset(worldPosition));
    }

    @Override
    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        long[] packed = new long[targets.size()];
        for (int i = 0; i < targets.size(); i++) packed[i] = targets.get(i).subtract(worldPosition).asLong();
        tag.putLongArray("Targets", packed);
    }
}
