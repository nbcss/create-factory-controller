package io.github.nbcss.createfactorycontroller.content.block;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionGraph;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettings;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettingsStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FactoryControllerMenu extends AbstractContainerMenu implements ComponentHolder {
    public final List<VirtualComponentBehaviour> components = new ArrayList<>();
    /** Position → component index for O(1) lookup; mirrors {@link #components}. */
    private final Map<VirtualComponentPosition, VirtualComponentBehaviour> componentsByPosition = new HashMap<>();
    /** Client-side connection store the menu's components are views into (the server's components use the controller's
     *  graph instead). Populated from the synced per-component wires (see the client constructor). */
    private final ConnectionGraph connectionGraph = new ConnectionGraph();
    public final List<UUID> knownNetworks = new ArrayList<>();
    /** Client mirror of every known network's shared settings (synced); one entry per {@link #knownNetworks}
     *  entry, though its customized fields may be empty. Read via {@link #networkSettings(UUID)}, which never
     *  returns null. */
    public final Map<UUID, NetworkSettings> networkSettings = new HashMap<>();
    /** Controller's custom display name (synced); blank means the default translated block name. */
    public String controllerName = "";
    public boolean controllerPowered = false;
    public final BlockPos controllerPos;

    // ── Client-side sync tokens (mirrors of FactoryControllerBlockEntity#syncEpoch/syncRevision) ──
    /** BE-instance token; a delta from a different BE load can never match it. */
    public int syncEpoch;
    /** Revision this menu's state reflects. A delta applies only when its base equals this. */
    public int syncRevision;
    /** True while a full-resync request is in flight, so a burst of bad deltas asks only once.
     *  Cleared when the full snapshot arrives ({@link #applyFullSync}). */
    public boolean resyncPending = false;

    // Server-side: reference to the actual BE
    @Nullable private final FactoryControllerBlockEntity blockEntity;

    // Inventory state used for slot rebuilding.
    // Slots are re-created on each repositionSlots() call because Slot.x/y are final.
    private final Inventory cachedPlayerInventory;
    private static final int OFF_SCREEN = -2000;

    /** Server-side constructor. */
    public FactoryControllerMenu(int syncId, Inventory playerInventory, FactoryControllerBlockEntity be) {
        super(CreateFactoryController.FACTORY_CONTROLLER_MENU.get(), syncId);
        this.blockEntity = be;
        this.controllerPos = be.getBlockPos();
        this.cachedPlayerInventory = playerInventory;

        // Snapshot data from BE for client sync
        setComponents(be.components.values());
        this.knownNetworks.addAll(be.networks);
        this.controllerName = be.customName;
        this.controllerPowered = be.isRedstonePowered();

        addExtraSlots(OFF_SCREEN, OFF_SCREEN, false);
    }

    /** Client-side constructor (called via IMenuTypeExtension). */
    public FactoryControllerMenu(int syncId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(CreateFactoryController.FACTORY_CONTROLLER_MENU.get(), syncId);
        this.blockEntity = null;
        this.controllerPos = buf.readBlockPos();
        this.cachedPlayerInventory = playerInventory;
        this.syncEpoch = buf.readVarInt();
        this.syncRevision = buf.readVarInt();

        int componentCount = buf.readVarInt();
        for (int i = 0; i < componentCount; i++) {
            VirtualComponentBehaviour b = ComponentRegistry.readComponent(buf);
            if (b != null) addComponent(b);
        }
        int connectionCount = buf.readVarInt();
        List<Connection> connections = new ArrayList<>(connectionCount);
        for (int i = 0; i < connectionCount; i++) {
            Connection c = Connection.fromClient(buf);
            if (c != null) connections.add(c);
        }
        loadConnections(connections);   // build the client graph from the synced central edge list

        int networkCount = buf.readVarInt();
        for (int i = 0; i < networkCount; i++) {
            NetworkSettings settings = NetworkSettings.STREAM_CODEC.decode(buf);
            knownNetworks.add(settings.network());
            networkSettings.put(settings.network(), settings);
        }

        this.controllerName = buf.readUtf();
        this.controllerPowered = buf.readBoolean();

        addExtraSlots(OFF_SCREEN, OFF_SCREEN, false);
    }

    private int extraSlotsStart = -1;
    private int invSlotsStart = -1;

    private void addExtraSlots(int originX, int hotbarY, boolean expanded) {
        extraSlotsStart = slots.size();
        invSlotsStart = slots.size();
        // Main inventory (3 rows × 9): off-screen when collapsed, above hotbar when expanded.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int sx = expanded ? originX + col * 18 : OFF_SCREEN;
                int sy = expanded ? hotbarY - 4 - (3 - row) * 18 : OFF_SCREEN;
                addSlot(new Slot(cachedPlayerInventory, col + row * 9 + 9, sx, sy));
            }
        }
        // Hotbar (always visible once positioned).
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(cachedPlayerInventory, col, originX + col * 18, hotbarY));
    }

    /** Rebuilds the inventory slots at new positions (client-side only). */
    public void rebuildSlots(int originX, int hotbarY, boolean expanded) {
        if (extraSlotsStart >= 0)
            slots.subList(extraSlotsStart, slots.size()).clear();
        addExtraSlots(originX, hotbarY, expanded);
    }

    /** Reposition the player inventory for the active overlay. */
    public void repositionSlots(int originX, int hotbarY, boolean expanded) {
        rebuildSlots(originX, hotbarY, expanded);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        if (blockEntity == null) return true;
        return blockEntity.getLevel() != null &&
               player.distanceToSqr(blockEntity.getBlockPos().getX() + 0.5,
                                    blockEntity.getBlockPos().getY() + 0.5,
                                    blockEntity.getBlockPos().getZ() + 0.5) < 64;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        if (invSlotsStart < 0) return ItemStack.EMPTY;

        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;

        ItemStack original = slot.getItem().copy();
        ItemStack stack = slot.getItem();

        int mainStart   = invSlotsStart;        // 27 main inventory slots
        int mainEnd     = invSlotsStart + 27;
        int hotbarStart = invSlotsStart + 27;   // 9 hotbar slots
        int hotbarEnd   = invSlotsStart + 36;

        boolean moved;
        if (index >= mainStart && index < hotbarEnd) {
            // Player inventory → no container slots to move to in this menu,
            // so just shuffle between hotbar and main inventory.
            if (index < mainEnd) {
                // main inventory → hotbar
                moved = moveItemStackTo(stack, hotbarStart, hotbarEnd, false);
            } else {
                // hotbar → main inventory
                moved = moveItemStackTo(stack, mainStart, mainEnd, false);
            }
        } else {
            // Container slot (gauge slot etc.) → player inventory (hotbar first, then main)
            moved = moveItemStackTo(stack, hotbarStart, hotbarEnd, false)
                 || moveItemStackTo(stack, mainStart, mainEnd, false);
        }

        if (!moved) return ItemStack.EMPTY;
        slot.setByPlayer(stack);
        slot.onTake(player, stack);
        return original;
    }

    // ── Component access (keep the list + position index in sync) ─────────────

    /** Adds a component, indexing it by position. A later component at the same cell wins. */
    public void addComponent(VirtualComponentBehaviour component) {
        components.add(component);
        componentsByPosition.put(component.position(), component);
        component.setHolder(this);
        component.setGraph(connectionGraph);
    }

    /** Replaces all components (used on the full sync), rebuilding the position index. */
    public void setComponents(Collection<? extends VirtualComponentBehaviour> newComponents) {
        clearComponents();
        for (VirtualComponentBehaviour c : newComponents) addComponent(c);
    }

    /** Removes every component and clears the position index + the (client) connection graph. */
    public void clearComponents() {
        components.clear();
        componentsByPosition.clear();
        connectionGraph.clear();
    }

    /** Replaces the client connection graph from a synced central edge list (the components' positions are already in). */
    public void loadConnections(List<Connection> edges) {
        connectionGraph.clear();
        for (Connection conn : edges) connectionGraph.add(conn);
        bindConnectionHooks();
    }

    // ── Sync apply (full snapshot + per-item deltas; called by the sync packet handlers) ─────────

    /** Wholesale board replace from a full snapshot (menu-open equivalent). Authoritative: resets the sync
     *  tokens unconditionally, which also ends any pending resync round-trip. */
    public void applyFullSync(int epoch, int revision,
                              Collection<? extends VirtualComponentBehaviour> newComponents,
                              List<Connection> connections,
                              List<NetworkSettings> networks,
                              String name, boolean powered) {
        setComponents(newComponents);
        loadConnections(connections);
        applyNetworkSettings(networks);
        controllerName = name;
        controllerPowered = powered;
        syncEpoch = epoch;
        syncRevision = revision;
        resyncPending = false;
    }

    /** Replaces the synced network-settings mirror (full snapshot, or a delta's networks section). */
    public void applyNetworkSettings(List<NetworkSettings> settings) {
        knownNetworks.clear();
        networkSettings.clear();
        for (NetworkSettings s : settings) {
            knownNetworks.add(s.network());
            networkSettings.put(s.network(), s);
        }
    }

    /** Delta apply: replace-or-add one component (a FULL upsert). Idempotent. */
    public void upsertComponent(VirtualComponentBehaviour component) {
        VirtualComponentBehaviour old = componentsByPosition.remove(component.position());
        if (old != null) components.remove(old);
        addComponent(component);
    }

    /** Delta apply: drop the component at {@code pos} and every wire touching it — mirroring the server's
     *  remove path, whose packet also carries those wires as explicit removals (re-removal is a no-op). */
    public void removeComponentAt(VirtualComponentPosition pos) {
        VirtualComponentBehaviour old = componentsByPosition.remove(pos);
        if (old != null) components.remove(old);
        connectionGraph.disconnect(pos);
    }

    /** The wire currently held for {@code from → to}, or null. Lets the delta handler learn the type of a
     *  wire it is about to remove (for the sink re-fold). */
    public Connection connectionAt(VirtualComponentPosition from, VirtualComponentPosition to) {
        return connectionGraph.get(from, to);
    }

    /** Delta apply: add-or-replace one wire (the graph keeps one wire per endpoint pair). */
    public void putConnection(Connection conn) {
        connectionGraph.add(conn);
    }

    /** Delta apply: remove the wire {@code from → to}, if present. */
    public void removeConnectionAt(VirtualComponentPosition from, VirtualComponentPosition to) {
        connectionGraph.remove(to, from);
    }

    /** Re-folds one sink after its incoming wires changed (the client-side mirror of the server's settle). */
    public void refoldSink(VirtualComponentPosition pos, Connection.Type type) {
        VirtualComponentBehaviour sink = componentAt(pos);
        if (sink != null) sink.onInputChanged(type);
    }

    private void bindConnectionHooks() {
        // Edges arrive with their value; each sink re-folds from them (no callbacks to rebind in the new model).
        for (Connection conn : connectionGraph.connections()) {
            VirtualComponentBehaviour sink = componentAt(conn.to);
            if (sink != null) sink.onInputChanged(conn.type);
        }
    }

    /** O(1) lookup of the component occupying {@code pos}, or {@code null} if the cell is empty. */
    @Override
    public VirtualComponentBehaviour componentAt(VirtualComponentPosition position) {
        return position == null ? null : componentsByPosition.get(position);
    }

    /** Number of components currently on the given network (used by the network selector). */
    public int componentCountIn(UUID network) {
        int n = 0;
        for (VirtualComponentBehaviour c : components)
            if (c instanceof VirtualGaugeBehaviour g && network.equals(g.networkId)) n++;
        return n;
    }

    // ── Data serialization for menu open ──────────────────────────────────

    /** Called by NeoForge when the server opens this menu for a player. */
    public static void writeExtraData(FactoryControllerBlockEntity be, RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(be.getBlockPos());
        // Sync tokens: the deltas that follow this open build on exactly this revision. The end-of-tick
        // flush may re-send state this snapshot already contains (base = this revision) — upserts are
        // idempotent state carriers, so the double-apply is harmless.
        buf.writeVarInt(be.syncEpoch());
        buf.writeVarInt(be.syncRevision());

        buf.writeVarInt(be.components.size());
        for (VirtualComponentBehaviour b : be.components.values())
            ComponentRegistry.writeComponent(buf, b);

        List<Connection> connections = be.connectionGraph().connections();
        buf.writeVarInt(connections.size());
        for (Connection conn : connections) conn.writeClient(buf);

        NetworkSettingsStore settingsStore = NetworkSettingsStore.get(be.getLevel());
        buf.writeVarInt(be.networks.size());
        for (UUID id : be.networks) {
            NetworkSettings settings = settingsStore != null ? settingsStore.get(id) : NetworkSettings.defaultFor(id);
            NetworkSettings.STREAM_CODEC.encode(buf, settings);
        }

        buf.writeUtf(be.customName);
        buf.writeBoolean(be.isRedstonePowered());
    }

    public boolean isRedstonePowered() {
        return controllerPowered;
    }

    /** Controller display name: the custom name, or the default translated block name when unset. */
    public Component controllerDisplayName() {
        return controllerName.isBlank()
            ? Component.translatable("block.createfactorycontroller.factory_controller")
            : Component.literal(controllerName);
    }

    /** The shared settings for a network — never null; a network with no synced entry yields
     *  {@link NetworkSettings#defaultFor}. */
    public NetworkSettings networkSettings(UUID network) {
        return networkSettings.getOrDefault(network, NetworkSettings.defaultFor(network));
    }

    /** Display name for a network: its custom name, or a default "Network #XXXX". */
    public Component networkName(UUID network) {
        return networkSettings(network).displayName();
    }

    /** Custom icon item for a network, or {@link ItemStack#EMPTY} when unset (⇒ draw the default network icon). */
    public ItemStack networkIcon(UUID network) {
        return networkSettings(network).icon();
    }

}
