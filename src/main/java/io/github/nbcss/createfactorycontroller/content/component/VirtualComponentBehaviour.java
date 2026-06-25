package io.github.nbcss.createfactorycontroller.content.component;

import io.github.nbcss.createfactorycontroller.content.component.connection.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A component that can be placed in the controller's virtual panel.
 */
public interface VirtualComponentBehaviour {
    interface Type {
        String id();
        ResourceLocation itemId();
        boolean isRequireNetwork();
        VirtualComponentBehaviour create();
    }

    // ── Identity & placement ────────────────────────────────────────────────

    /** Where this component sits on the board. */
    VirtualComponentPosition position();

    /** Repositions this component on the board. The controller re-keys the component map and the
     *  connection graph around it (see {@code FactoryControllerBlockEntity#moveComponent}). */
    void setPosition(VirtualComponentPosition pos);

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
    Map<VirtualComponentPosition, Connection> targetedBy();

    /** Positions this component points at. */
    Set<VirtualComponentPosition> targeting();

    /** The connection {@link ConnectionCapability channels} this component participates in (its static capabilities). Read by
     *  {@link ConnectionValidator} to decide whether a wire is possible — no {@code instanceof} pair matrix. */
    List<ConnectionCapability> ports();

    /** This component's live role on {@code type}: a decisive {@link ConnectionCapability.Role#SOURCE}/{@link ConnectionCapability.Role#SINK} (e.g. a
     *  redstone link's mode) or {@link ConnectionCapability.Role#BOTH} when it defers (the resolver then uses the creation order). */
    ConnectionCapability.Role liveRole(Connection.Type channel);

    /** Validates this component acting as the SOURCE of a {@code type} wire to {@code sink}. (E.g. a gauge source
     *  must carry a filter.) */
    ValidationResult validateAsSource(Connection.Type channel, VirtualComponentBehaviour sink);

    /** Validates this component acting as the SINK of a {@code type} wire from {@code source}. (E.g. a gauge sink
     *  caps its ingredient slots; a link rejects a link partner.) */
    ValidationResult validateAsSink(Connection.Type channel, VirtualComponentBehaviour source);

    /** Injects a sibling-component lookup for cross-component checks when this behaviour has no controller (client
     *  snapshot). The server leaves it unset and uses the controller. See {@code AbstractVirtualComponent#siblingAt}. */
    void setSiblingLookup(Function<VirtualComponentPosition, VirtualComponentBehaviour> lookup);

    /** Injects the central {@link ConnectionGraph} this component is a view into (client snapshot — the server uses the
     *  controller's graph). */
    void setGraph(ConnectionGraph graph);

    /** Adds an incoming connection from {@code sourcePos} (the controller validates existence). */
    void addConnection(VirtualComponentPosition sourcePos);

    /** Removes the incoming connection from {@code fromPos}, if any. */
    void removeConnection(VirtualComponentPosition fromPos);

    /** Removes this component from the graph entirely (called before removal). */
    void disconnectAll();

    void onInteract();

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

    /**
     * Used when sync data to item, should not include data which is runtime-determined.
     */
    default CompoundTag toItemNBT(HolderLookup.Provider registries) {
        return toNBT(registries);
    }
}
