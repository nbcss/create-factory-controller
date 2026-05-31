package io.github.nbcss.content.factorycontroller;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;

/**
 * A component that can be placed in the controller's virtual panel.
 *
 * <p>This generalizes "anything attachable to the board" — gauges today, Redstone Link /
 * Display Link and third-party Deployer-derived components in the future. It deliberately
 * mirrors how Deployer unifies gauges ({@code AbstractPanelBehaviour}) and links
 * ({@code AbstractPanelSupportBehaviour}) under one connection contract, but without any
 * block-entity / world coupling: a component lives only as data inside
 * {@link FactoryControllerBlockEntity}, addressed by a {@link VirtualPanelPosition}.</p>
 *
 * <p>Every component participates in the same connection graph (so a link can target a gauge,
 * etc.), hence the graph accessors live here rather than on a gauge-specific subtype.
 * Shared graph mechanics are implemented once in {@link AbstractVirtualComponent}.</p>
 */
public interface VirtualComponentBehaviour extends VirtualProvidesConnection {

    // ── Identity & placement ────────────────────────────────────────────────

    /** Where this component sits on the board. */
    VirtualPanelPosition position();

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
}
