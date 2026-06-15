package io.github.nbcss.content.factorycontroller.production;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One line-item of a {@link ProductionOrder}: a standing task to produce {@code amount} of {@link #item} from a
 * specific orderable gauge and ship it to {@link #address}. While {@link State#ACTIVE} it contributes external
 * demand to its gauge (so the gauge produces) and is monitored against network stock; once enough of the item is
 * available in the network (whether freshly produced or already in stock) it ships as a package (sharing the
 * order's {@code orderId}) and goes {@link State#DONE}. It is {@link State#ABORTED} when its gauge is gone
 * (removed / no longer orderable).
 *
 * <p>A task with a {@code null} {@link #patternId} represents a real, already-in-stock item bundled into the same
 * keeper order: it ships instantly (via Create's normal dispatch) and is recorded here purely so it shows in the
 * order entry as immediately {@link State#DONE}. Such tasks never contribute demand and are never produced.</p>
 */
public class ProductionTask {

    public enum State { ACTIVE, DONE, ABORTED }

    /** Target gauge — a stable, position-independent reference. {@code network} is always set; {@code patternId}
     *  is null for an in-stock "real item" task. */
    public final UUID network;
    @Nullable public final UUID patternId;

    public final ItemStack item;           // the produced item this task ships (the gauge's filter)
    public final int amount;               // how much to ship (item count, or mB for a fluid)
    public final String address;

    // Shared-order bookkeeping: all packages of one keeper order carry the same orderId.
    public final int orderId;
    public int linkIndex;
    public boolean finalLink;

    public State state = State.ACTIVE;
    public int timer = 0;                  // dispatch throttle, like the gauge's request timer

    public ProductionTask(UUID network, @Nullable UUID patternId, ItemStack item, int amount, String address,
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

    /** An already-in-stock real item bundled into the order: no gauge, shipped instantly, recorded as DONE so it
     *  shows in the order entry. */
    public static ProductionTask completed(UUID network, ItemStack item, int amount, String address,
                                           int orderId, int linkIndex, boolean finalLink) {
        ProductionTask t = new ProductionTask(network, null, item, amount, address, orderId, linkIndex, finalLink);
        t.state = State.DONE;
        return t;
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
        if (patternId != null) tag.putUUID("Pattern", patternId);
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
            tag.getUUID("Network"), tag.hasUUID("Pattern") ? tag.getUUID("Pattern") : null,
            ItemStack.parseOptional(registries, tag.getCompound("Item")),
            tag.getInt("Amount"), tag.getString("Address"),
            tag.getInt("OrderId"), tag.getInt("LinkIndex"), tag.getBoolean("FinalLink"));
        try { r.state = State.valueOf(tag.getString("State")); } catch (IllegalArgumentException ignored) {}
        r.timer = tag.getInt("Timer");
        return r;
    }
}
