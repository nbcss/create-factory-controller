package io.github.nbcss.createfactorycontroller.content.component;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.connection.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
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
        /** Accent colour (0xRRGGBB) of this component kind */
        int color();
        boolean isRequireNetwork();
        VirtualComponentBehaviour create(FactoryControllerBlockEntity controller,
                                          VirtualComponentPosition pos,
                                          Item item,
                                          UUID networkId);
        VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                          CompoundTag tag,
                                          HolderLookup.Provider registries);
        /** Reconstructs a client-side component (controller null) from {@link #writeClient}. Counterpart to
         *  {@link #fromNBT} for the live-sync wire — binary, not NBT. */
        VirtualComponentBehaviour fromClient(net.minecraft.network.RegistryFriendlyByteBuf buf);
    }

    /** The registry id of this component's kind ({@code "GAUGE"}, {@code "FLUID_GAUGE"}, …). */
    String typeId();

    /** Writes everything the client needs to render this component, binary (no NBT keys). Body is ordered
     *  config-then-runtime: it MUST end with exactly the {@link #writeClientState} bytes (implementations call it
     *  as their last write), and {@code Type.fromClient} must end by reading them via {@link #readClientState}.
     *  Read by {@code Type.fromClient}. */
    void writeClient(net.minecraft.network.RegistryFriendlyByteBuf buf);

    /**
     * Writes only the runtime ("hot") fields — the tail of {@link #writeClient}. These are the fields the server
     * mutates during normal operation (counts, satisfied/powered flags, request stamps) and syncs via a cheap
     * per-component STATE delta, as opposed to config fields, which only change on player edits and sync as a
     * FULL body.
     *
     * <p><b>Adding a field:</b> a new runtime field goes in this write/read pair only — it then automatically
     * rides both the full snapshot and the STATE delta. A new config field goes in the {@code writeClient} body /
     * {@code Type.fromClient} instead (its edit path must mark {@code syncComponentFull}).</p>
     */
    void writeClientState(net.minecraft.network.RegistryFriendlyByteBuf buf);

    /** Reads {@link #writeClientState} into this (client-side) instance, mutating it in place — the STATE delta
     *  apply. The menu's widgets wrap behaviour instances, so they observe the new values without a rebuild. */
    void readClientState(net.minecraft.network.RegistryFriendlyByteBuf buf);

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

    int getColor();

    default Component getName() {
        return new ItemStack(getItem()).getHoverName();
    }

    /** Extra info lines describing this component's current config (a gauge's monitored item, a link's frequencies +
     *  mode, a tube's mode). Shown under the component name in tooltips (e.g. the Logical Tube screen's connection
     *  slots). Empty by default. */
    default List<Component> infoTooltip() {
        return List.of();
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    /** Called on every component at the very start of the controller tick, <b>before</b> any connection settle.
     *  Sequential components (logic tubes) commit their previously-computed output here, which — because it runs ahead
     *  of this tick's settles — gives a deterministic one-tick delay; combinational components do nothing. */
    default void preTick() {}

    /** Server tick. No-op on the client snapshot. */
    void tick();

    // ── Connection graph (shared by all component kinds) ─────────────────────

    /** Incoming connections, keyed by the source component's position. */
    Map<VirtualComponentPosition, Connection> targetedBy();

    /** Positions this component points at. */
    Set<VirtualComponentPosition> targeting();

    /** Outgoing connections (this component as source). Symmetric to {@link #targetedBy()}, but as a value collection. */
    Collection<Connection> outgoingConnections();

    /** The connection {@link ConnectionCapability types} this component participates in (its static capabilities). Read by
     *  {@link ConnectionResolver} to decide whether a wire is possible — no {@code instanceof} pair matrix. */
    List<ConnectionCapability> ports();

    /** Validates this component acting as the SOURCE of a {@code type} wire to {@code sink}. (E.g. a gauge source
     *  must carry a filter.) */
    ValidationResult validateAsSource(Connection.Type type, VirtualComponentBehaviour sink);

    /** Validates this component acting as the SINK of a {@code type} wire from {@code source}. (E.g. a gauge sink
     *  caps its ingredient slots; a link rejects a link partner.) */
    ValidationResult validateAsSink(Connection.Type type, VirtualComponentBehaviour source);

    /** Injects a sibling-component lookup for cross-component checks when this behaviour has no controller (client
     *  snapshot). The server leaves it unset and uses the controller. See {@code AbstractVirtualComponent#siblingAt}. */
    void setSiblingLookup(Function<VirtualComponentPosition, VirtualComponentBehaviour> lookup);

    /** Injects the central {@link ConnectionGraph} this component is a view into (client snapshot — the server uses the
     *  controller's graph). */
    void setGraph(ConnectionGraph graph);

    /** Removes this component from the graph entirely (called before removal). */
    void disconnectAll();

    // ── Signal propagation ──────

    /** My current output value for {@code type} (REDSTONE → a {@link RedstoneConnection.State}), or {@code null} if I
     *  am not a source of it. Pure — no side effects; {@link #publish} reads it. */
    ConnectionValue outputValue(Connection.Type type);

    /** Write my {@link #outputValue} onto every outgoing {@code type} edge and, for each edge whose value actually
     *  changed, flag its sink dirty on the controller (folded once at the next {@code settleConnections} drain — see
     *  the coalescing note on {@code FactoryControllerBlockEntity#settleConnections}). A source calls this when its
     *  output may have changed (and on connect). The single, generic implementation lives in
     *  {@code AbstractVirtualComponent}. */
    void publish(Connection.Type type);

    /** Re-fold my incoming wires of {@code type} and apply the result (a gauge refreshes its request gate; a SEND link
     *  its network transmit). Called once per dirty sink by the settle pass, and directly on load/sync reconcile. */
    void onInputChanged(Connection.Type type);

    List<Connection> connectionsToCycle();

    void cycleArrowMode();

    void cycleOperationMode();

    // ── Persistence ─────────────────────────────────────────────────────────

    CompoundTag toNBT(HolderLookup.Provider registries, NbtProfile profile);
}
