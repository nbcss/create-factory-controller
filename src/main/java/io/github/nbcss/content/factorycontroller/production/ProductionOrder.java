package io.github.nbcss.content.factorycontroller.production;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single Stock-Keeper order that contained at least one Promise Blueprint: a shared {@link #orderId} (also
 * carried by the instant in-stock package and every promise package, so a Re-Packager can merge them) plus one
 * {@link ProductionTask} per ordered blueprint type. Completes — and is removed by the manager — once every
 * request is no longer {@link ProductionTask.State#ACTIVE}.
 */
public record ProductionOrder(int orderId,
                              UUID network,
                              String address,
                              long createdGameTime,
                              List<ProductionTask> tasks) {

    /**
     * True once every task is terminal (DONE/ABORTED).
     */
    public boolean isComplete() {
        for (ProductionTask r : tasks)
            if (!r.isTerminal()) return false;
        return true;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("OrderId", orderId);
        tag.putUUID("Network", network);
        tag.putString("Address", address);
        tag.putLong("CreatedGameTime", createdGameTime);
        ListTag list = new ListTag();
        for (ProductionTask r : tasks) list.add(r.save(registries));
        tag.put("Tasks", list);
        return tag;
    }

    public static ProductionOrder load(CompoundTag tag, HolderLookup.Provider registries) {
        List<ProductionTask> tasks = new ArrayList<>();
        ListTag list = tag.getList("Tasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
            tasks.add(ProductionTask.load(list.getCompound(i), registries));
        return new ProductionOrder(tag.getInt("OrderId"), tag.getUUID("Network"), tag.getString("Address"),
            tag.getLong("CreatedGameTime"), tasks);
    }
}
