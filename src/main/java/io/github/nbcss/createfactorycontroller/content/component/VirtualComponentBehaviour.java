package io.github.nbcss.createfactorycontroller.content.component;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.connection.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * A component that can be placed in the controller's virtual panel.
 */
public interface VirtualComponentBehaviour {
    enum NbtProfile {
        SERVER(true, true),
        CLIENT(true, false),
        EXPORT(false, false);

        private final boolean runtime;
        private final boolean serverOnly;

        NbtProfile(boolean runtime, boolean serverOnly) {
            this.runtime = runtime;
            this.serverOnly = serverOnly;
        }

        public boolean includesRuntime() { return runtime; }
        public boolean includesServerOnly() { return serverOnly; }
    }

    interface Type {
        String id();
        List<ResourceLocation> items();
        boolean isRequireNetwork();
        VirtualComponentBehaviour create(FactoryControllerBlockEntity controller,
                                          VirtualComponentPosition pos,
                                          Item item,
                                          UUID networkId);
        VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                          CompoundTag tag,
                                          HolderLookup.Provider registries);
    }

    // ── Identity & placement ────────────────────────────────────────────────

    /** Where this component sits on the board. */
    VirtualComponentPosition position();

    /** Repositions this component on the board. The controller re-keys the component map and the
     *  connection graph around it (see {@code FactoryControllerBlockEntity#moveComponent}). */
    void setPosition(VirtualComponentPosition pos);

    /** Item id this component renders as / refunds to. */
    default ResourceLocation getItemId(){
        return BuiltInRegistries.ITEM.getKey(getItem());
    }

    /** Item this component renders as / refunds to. */
    Item getItem();

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
     *  {@link ConnectionResolver} to decide whether a wire is possible — no {@code instanceof} pair matrix. */
    List<ConnectionCapability> ports();

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

    /** Removes this component from the graph entirely (called before removal). */
    void disconnectAll();

    default void onConnectAsSource(Connection connection) {}
    default void onConnectAsSink(Connection connection) {}
    default void onDisconnectAsSource(Connection connection) {}
    default void onDisconnectAsSink(Connection connection) {}

    void onInteract();

    // ── Persistence ─────────────────────────────────────────────────────────

    CompoundTag toNBT(HolderLookup.Provider registries, NbtProfile profile);
}
