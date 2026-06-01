package io.github.nbcss.content.factorycontroller;

import io.github.nbcss.CreateFactoryController;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FactoryControllerMenu extends AbstractContainerMenu {

    // Synced data (populated server-side, read client-side)
    public final List<VirtualComponentBehaviour> components = new ArrayList<>();
    public final List<UUID> knownNetworks = new ArrayList<>();
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
        this.components.addAll(be.components.values());
        this.knownNetworks.addAll(be.networks);

        addExtraSlots(OFF_SCREEN, OFF_SCREEN, OFF_SCREEN, OFF_SCREEN, false);
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
            if (b != null) components.add(b);
        }

        int networkCount = buf.readVarInt();
        for (int i = 0; i < networkCount; i++)
            knownNetworks.add(new UUID(buf.readLong(), buf.readLong()));

        addExtraSlots(OFF_SCREEN, OFF_SCREEN, OFF_SCREEN, OFF_SCREEN, false);
    }

    private int extraSlotsStart = -1;
    private int invSlotsStart = -1;
    private int ghostSlotIndex = -1;
    /** Client-side 1-slot holder behind the ghost filter slot (UI only; committed via a packet). */
    public final SimpleContainer ghostInventory = new SimpleContainer(1);

    private void addExtraSlots(int ghostX, int ghostY, int originX, int hotbarY, boolean expanded) {
        extraSlotsStart = slots.size();
        ghostSlotIndex = slots.size();
        addSlot(new GhostSlot(ghostInventory, 0, ghostX, ghostY));

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

    /** Rebuilds the ghost + inventory slots at new positions (client-side only). */
    public void rebuildSlots(int ghostX, int ghostY, int originX, int hotbarY, boolean expanded) {
        if (extraSlotsStart >= 0)
            slots.subList(extraSlotsStart, slots.size()).clear();
        addExtraSlots(ghostX, ghostY, originX, hotbarY, expanded);
    }

    /** Controller view: reposition the player inventory, keeping the ghost slot off-screen. */
    public void repositionSlots(int originX, int hotbarY, boolean expanded) {
        rebuildSlots(OFF_SCREEN, OFF_SCREEN, originX, hotbarY, expanded);
    }

    /** Set-item view: show the ghost slot at the given position alongside the inventory. */
    public void showGhostSlot(int ghostX, int ghostY, int originX, int hotbarY) {
        rebuildSlots(ghostX, ghostY, originX, hotbarY, true);
    }

    public int ghostSlotIndex() { return ghostSlotIndex; }
    public ItemStack getGhostFilter() { return ghostInventory.getItem(0); }

    /** Sets the ghost filter to a single-count copy of {@code stack} (empty clears it). */
    public void setGhostFilter(ItemStack stack) {
        ghostInventory.setItem(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }

    /** Display-only filter slot — vanilla renders it (icon + hover highlight); the screen sets it. */
    private static class GhostSlot extends Slot {
        GhostSlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(@NotNull ItemStack stack) { return false; }
        @Override public boolean mayPickup(@NotNull Player player) { return false; }
        @Override public int getMaxStackSize() { return 1; }
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
        if (index == ghostSlotIndex) return ItemStack.EMPTY;   // never shift-move the ghost slot

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

    public List<VirtualComponentBehaviour> getComponentsInCanvas(int minX, int minY, int maxX, int maxY) {
        return components; //FIXME stub, calculate via input range only
    }

    // ── Data serialization for menu open ──────────────────────────────────

    /** Called by NeoForge when the server opens this menu for a player. */
    public static void writeExtraData(FactoryControllerBlockEntity be, RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(be.getBlockPos());

        buf.writeVarInt(be.components.size());
        for (VirtualComponentBehaviour b : be.components.values()) {
            net.minecraft.nbt.CompoundTag tag = b.toClientNBT(be.getLevel().registryAccess());
            writeCompoundTag(buf, tag);
        }

        buf.writeVarInt(be.networks.size());
        for (UUID id : be.networks) {
            buf.writeLong(id.getMostSignificantBits());
            buf.writeLong(id.getLeastSignificantBits());
        }
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
