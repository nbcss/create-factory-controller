package io.github.nbcss.createfactorycontroller.content.component;

import io.github.nbcss.createfactorycontroller.content.VirtualPanelConnection;
import io.github.nbcss.createfactorycontroller.content.VirtualPanelPosition;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;

/**
 * A component that can be placed in the controller's virtual panel.
 */
public interface VirtualComponentBehaviour extends VirtualProvidesConnection {

    // ── Identity & placement ────────────────────────────────────────────────

    /** Where this component sits on the board. */
    VirtualPanelPosition position();

    /** Repositions this component on the board. The controller re-keys the component map and the
     *  connection graph around it (see {@code FactoryControllerBlockEntity#moveComponent}). */
    void setPosition(VirtualPanelPosition pos);

    /** Item id this component renders as / refunds to. */
    ResourceLocation getItemId();

    /** Discriminator used to dispatch NBT deserialization (see {@link ComponentRegistry}). */
    ResourceLocation getTypeId();

    /**
     * GUI-sprite folder for this component's body. The canvas widget renders {@code {folder}/back}
     * first and {@code {folder}/front} last (16×16 sprites stretched to the cell), sandwiching any
     * content between them. Custom gauges return their own folder to supply their own look.
     */
    ResourceLocation getTexture();

    // ── Lifecycle ───────────────────────────────────────────────────────────

    /** Server tick. No-op on the client snapshot. */
    void tick();

    // ── Connection graph (shared by all component kinds) ─────────────────────

    /** Incoming connections, keyed by the source component's position. */
    Map<VirtualPanelPosition, VirtualPanelConnection> targetedBy();

    /** Positions this component points at. */
    Set<VirtualPanelPosition> targeting();

    /** Adds an incoming connection from {@code fromPos} (the controller validates existence). */
    void addConnection(VirtualPanelPosition fromPos);

    /** Removes the incoming connection from {@code fromPos}, if any. */
    void removeConnection(VirtualPanelPosition fromPos);

    /** Removes this component from the graph entirely (called before removal). */
    void disconnectAll();

    /** Cycles the arrow-bend rendering mode of all outgoing connections. */
    void cycleArrowBend();

    // ── Persistence ─────────────────────────────────────────────────────────

    CompoundTag toNBT(HolderLookup.Provider registries);

    /**
     * Serialization for client sync — only the fields the canvas needs (position, item/texture,
     * status, connections). Defaults to the full {@link #toNBT}; components trim it to keep the
     * broadcast small. Detailed config (recipe counts, addresses, …) is pulled on demand instead.
     */
    default CompoundTag toClientNBT(HolderLookup.Provider registries) {
        return toNBT(registries);
    }

    default CompoundTag toItemNBT(HolderLookup.Provider registries) {
        return toNBT(registries);
    }
}
