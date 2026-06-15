package io.github.nbcss.content.factorycontroller.production;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * One line-item of a {@link ProductionOrder}: a standing task to produce {@code amount} of {@link #item} from a
 * specific orderable gauge and ship it to {@link #address}. While {@link State#ACTIVE} it contributes external
 * demand to its gauge (so the gauge produces) and is monitored against network stock; once enough of the item is
 * available in the network (whether freshly produced or already in stock) it ships as a package (sharing the
 * order's {@code orderId}) and goes {@link State#DONE}. It is {@link State#ABORTED} when its gauge is gone
 * (removed / no longer orderable).
 */
public class ProductionTask {

    public enum State { ACTIVE, DONE, ABORTED }

    /** Target gauge — a stable, position-independent reference (network + gauge id only). */
    public final UUID network;
    public final UUID patternId;

    public final ItemStack item;           // the produced item this task ships (the gauge's filter)
    public final int amount;               // how much to ship (item count, or mB for a fluid)
    public final String address;

    // Shared-order bookkeeping: all packages of one keeper order carry the same orderId.
    public final int orderId;
    public int linkIndex;
    public boolean finalLink;

    public State state = State.ACTIVE;
    public int timer = 0;                  // dispatch throttle, like the gauge's request timer

    public ProductionTask(UUID network, UUID patternId, ItemStack item, int amount, String address,
                          int orderId, int linkIndex, boolean finalLink) {
        this.network = network;
        this.patternId = patternId;
        this.item = item;
        this.amount = amount;
        this.address = address;
        this.orderId = orderId;
        this.linkIndex = linkIndex;
        this.finalLink = finalLink;
    }

    /** Only ACTIVE tasks ship and contribute external demand. */
    public boolean isActive() {
        return state == State.ACTIVE;
    }

    /** No further work will happen — the order may complete once all tasks are terminal. */
    public boolean isTerminal() {
        return state == State.DONE || state == State.ABORTED;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Network", network);
        tag.putUUID("Pattern", patternId);
        tag.put("Item", item.saveOptional(registries));
        tag.putInt("Amount", amount);
        tag.putString("Address", address);
        tag.putInt("OrderId", orderId);
        tag.putInt("LinkIndex", linkIndex);
        tag.putBoolean("FinalLink", finalLink);
        tag.putString("State", state.name());
        tag.putInt("Timer", timer);
        return tag;
    }

    public static ProductionTask load(CompoundTag tag, HolderLookup.Provider registries) {
        ProductionTask r = new ProductionTask(
            tag.getUUID("Network"), tag.getUUID("Pattern"),
            ItemStack.parseOptional(registries, tag.getCompound("Item")),
            tag.getInt("Amount"), tag.getString("Address"),
            tag.getInt("OrderId"), tag.getInt("LinkIndex"), tag.getBoolean("FinalLink"));
        try { r.state = State.valueOf(tag.getString("State")); } catch (IllegalArgumentException ignored) {}
        r.timer = tag.getInt("Timer");
        return r;
    }
}
