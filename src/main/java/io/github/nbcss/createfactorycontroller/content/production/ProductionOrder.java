package io.github.nbcss.createfactorycontroller.content.production;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single Stock-Keeper order that contained at least one Production Blueprint: a shared {@link #orderId} (also
 * carried by the instant in-stock package and every promise package, so a Re-Packager can merge them) plus one
 * {@link Task} per ordered blueprint type. Completes — and is removed by the manager — once every task is
 * terminal (no longer active).
 */
public record ProductionOrder(int orderId,
                              UUID network,
                              String address,
                              long createdGameTime,
                              List<Task> tasks) {

    /**
     * True once every task has been {@link Task.State#SENT}. An {@link Task.State#INVALID_PATTERN} task does NOT
     * complete the order — it keeps the order open (so the player sees the problem) until manual removal.
     */
    public boolean isComplete() {
        for (Task r : tasks)
            if (r.state != Task.State.SENT) return false;
        return true;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("OrderId", orderId);
        tag.putUUID("Network", network);
        tag.putString("Address", address);
        tag.putLong("CreatedGameTime", createdGameTime);
        ListTag list = new ListTag();
        for (Task r : tasks) list.add(r.save(registries));
        tag.put("Tasks", list);
        return tag;
    }

    public static ProductionOrder load(CompoundTag tag, HolderLookup.Provider registries) {
        List<Task> tasks = new ArrayList<>();
        ListTag list = tag.getList("Tasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
            tasks.add(Task.load(list.getCompound(i), registries));
        return new ProductionOrder(tag.getInt("OrderId"), tag.getUUID("Network"), tag.getString("Address"),
            tag.getLong("CreatedGameTime"), tasks);
    }

    public static class Task {

        public enum State {
            WAITING("createfactorycontroller.gui.production_status_waiting", ChatFormatting.WHITE),
            PROCESSING("createfactorycontroller.gui.production_status_processing", ChatFormatting.YELLOW),
            INVALID_PATTERN("createfactorycontroller.gui.production_status_invalid_pattern", ChatFormatting.RED),
            SENT("createfactorycontroller.gui.production_status_sent", ChatFormatting.GREEN);
            private final String translationKey;
            private final ChatFormatting format;
            State(String translationKey, ChatFormatting format){
                this.translationKey = translationKey;
                this.format = format;
            }
            public boolean isActive() {
                return this != SENT;
            }
            public Component getComponent() {
                return Component.translatable(translationKey).withStyle(format);
            }
        }

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

        public State state = State.PROCESSING;
        public int timer = 0;                  // dispatch throttle, like the gauge's request timer

        public Task(UUID network, @Nullable UUID patternId, ItemStack item, int amount, String address,
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

        /** An already-in-stock real item bundled into the order: no gauge, shipped instantly, recorded as SENT so
         *  it shows in the order entry. */
        public static Task completed(UUID network, ItemStack item, int amount, String address,
                                     int orderId, int linkIndex, boolean finalLink) {
            Task t = new Task(network, null, item, amount, address, orderId, linkIndex, finalLink);
            t.state = State.SENT;
            return t;
        }

        /** Active tasks still ship and contribute external demand (see {@link State#isActive()}). */
        public boolean isActive() {
            return state.isActive();
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

        public static Task load(CompoundTag tag, HolderLookup.Provider registries) {
            Task r = new Task(
                tag.getUUID("Network"), tag.hasUUID("Pattern") ? tag.getUUID("Pattern") : null,
                ItemStack.parseOptional(registries, tag.getCompound("Item")),
                tag.getInt("Amount"), tag.getString("Address"),
                tag.getInt("OrderId"), tag.getInt("LinkIndex"), tag.getBoolean("FinalLink"));
            try {
                r.state = State.valueOf(tag.getString("State"));
            } catch (IllegalArgumentException e) {
                r.state = State.PROCESSING;
            }
            r.timer = tag.getInt("Timer");
            return r;
        }
    }
}
