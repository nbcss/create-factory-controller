package io.github.nbcss.createfactorycontroller.content.gui.screen.controller;

import io.github.nbcss.createfactorycontroller.content.packet.RetuneCarriedPacket;
import io.github.nbcss.createfactorycontroller.content.supply.ComponentSupplyEntry;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** The client-only phantom a player holds after taking from Component Supply. No real item moves until placement. */
final class ComponentSupplyCursor {

    @Nullable private ComponentSupplyEntry entry;
    @Nullable private UUID network;
    private ItemStack displayStack = ItemStack.EMPTY;

    boolean isHolding() {
        return entry != null;
    }

    @Nullable
    ComponentSupplyEntry entry() {
        return entry;
    }

    @Nullable
    UUID network() {
        return network;
    }

    void take(ComponentSupplyEntry entry) {
        this.entry = entry;
        this.network = null;
        rebuildDisplay();
    }

    void retune(@Nullable UUID network) {
        if (entry == null || !entry.networked() || Objects.equals(this.network, network)) return;
        this.network = network;
        rebuildDisplay();
    }

    void release() {
        entry = null;
        network = null;
        displayStack = ItemStack.EMPTY;
    }

    /** A stack carrying the phantom's tuning, so the carried-item helpers work on it unchanged. */
    ItemStack displayStack() {
        return displayStack;
    }

    private void rebuildDisplay() {
        displayStack = new ItemStack(entry.item());
        if (entry.networked() && network != null) RetuneCarriedPacket.apply(displayStack, network);
    }
}
