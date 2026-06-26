package io.github.nbcss.createfactorycontroller.content.block;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionGraph;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
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
    /** Client mirror of the controller's per-network nicknames (synced); see {@link #networkName}. */
    public final Map<UUID, String> networkNicknames = new HashMap<>();
    /** Controller's custom display name (synced); blank means the default translated block name. */
    public String controllerName = "";
    public boolean controllerPowered = false;
    public final BlockPos controllerPos;

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

        int componentCount = buf.readVarInt();
        for (int i = 0; i < componentCount; i++) {
            CompoundTagHelper helper = new CompoundTagHelper(buf);
            VirtualComponentBehaviour b = ComponentRegistry.fromNBT(null, helper.tag(), playerInventory.player.level().registryAccess());
            if (b != null) addComponent(b);
        }
        int connectionCount = buf.readVarInt();
        List<net.minecraft.nbt.CompoundTag> connectionTags = new ArrayList<>(connectionCount);
        for (int i = 0; i < connectionCount; i++) connectionTags.add(new CompoundTagHelper(buf).tag());
        loadConnections(connectionTags);   // build the client graph from the synced central edge list

        int networkCount = buf.readVarInt();
        for (int i = 0; i < networkCount; i++) {
            UUID id = new UUID(buf.readLong(), buf.readLong());
            knownNetworks.add(id);
            String nick = buf.readUtf();
            if (!nick.isBlank()) networkNicknames.put(id, nick);
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
        // Client behaviours carry no controller; give them the sibling lookup + the client connection graph so
        // validation and targetedBy()/targeting() resolve. Both are harmless on the server (the controller wins).
        component.setSiblingLookup(this::componentAt);
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
    public void loadConnections(List<net.minecraft.nbt.CompoundTag> edges) {
        connectionGraph.readTagList(edges);
        bindConnectionHooks();
    }

    private void bindConnectionHooks() {
        for (io.github.nbcss.createfactorycontroller.content.component.connection.Connection conn : connectionGraph.connections()) {
            VirtualComponentBehaviour source = componentAt(conn.from);
            VirtualComponentBehaviour sink = componentAt(conn.to);
            if (sink != null) sink.onConnectAsSink(conn);
            if (source != null) source.onConnectAsSource(conn);
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

        buf.writeVarInt(be.components.size());
        for (VirtualComponentBehaviour b : be.components.values()) {
            net.minecraft.nbt.CompoundTag tag = b.toNBT(be.getLevel().registryAccess(), VirtualComponentBehaviour.NbtProfile.CLIENT);
            writeCompoundTag(buf, tag);
        }

        List<net.minecraft.nbt.CompoundTag> connectionTags = be.connectionGraph().toTagList();
        buf.writeVarInt(connectionTags.size());
        for (net.minecraft.nbt.CompoundTag tag : connectionTags) writeCompoundTag(buf, tag);

        buf.writeVarInt(be.networks.size());
        for (UUID id : be.networks) {
            buf.writeLong(id.getMostSignificantBits());
            buf.writeLong(id.getLeastSignificantBits());
            buf.writeUtf(be.networkNicknames.getOrDefault(id, ""));
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

    /** Display name for a network: its nickname, or a default "Network #XXXX" from the last 4 UUID chars. */
    public Component networkName(UUID network) {
        String nick = networkNicknames.get(network);
        if (nick != null && !nick.isBlank()) return Component.literal(nick);
        String s = network.toString();
        return Component.translatable("createfactorycontroller.network.default", s.substring(0, 6).toUpperCase());
    }

    private static void writeCompoundTag(RegistryFriendlyByteBuf buf, net.minecraft.nbt.CompoundTag tag) {
        try {
            net.minecraft.network.FriendlyByteBuf plain = new net.minecraft.network.FriendlyByteBuf(buf);
            plain.writeNbt(tag);
        } catch (Exception e) {
            buf.writeNbt(tag);
        }
    }

    // Inner helper to read CompoundTag from buf in client constructor
    private record CompoundTagHelper(net.minecraft.nbt.CompoundTag tag) {
        CompoundTagHelper(RegistryFriendlyByteBuf buf) {
            this((net.minecraft.nbt.CompoundTag) buf.readNbt());
        }
    }
}
