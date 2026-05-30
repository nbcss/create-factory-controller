package io.github.nbcss.content.factorycontroller;

import io.github.nbcss.CreateFactoryController;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FactoryControllerMenu extends AbstractContainerMenu {

    // Synced data (populated server-side, read client-side)
    public final List<VirtualPanelBehaviour> gauges = new ArrayList<>();
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
        this.gauges.addAll(be.gauges.values());
        this.knownNetworks.addAll(be.knownNetworks);

        addPlayerInventorySlots(OFF_SCREEN, OFF_SCREEN, false);
    }

    /** Client-side constructor (called via IMenuTypeExtension). */
    public FactoryControllerMenu(int syncId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(CreateFactoryController.FACTORY_CONTROLLER_MENU.get(), syncId);
        this.blockEntity = null;
        this.controllerPos = buf.readBlockPos();
        this.cachedPlayerInventory = playerInventory;

        int gaugeCount = buf.readVarInt();
        for (int i = 0; i < gaugeCount; i++) {
            CompoundTagHelper helper = new CompoundTagHelper(buf);
            VirtualPanelBehaviour b = VirtualPanelBehaviour.fromNBT(null, helper.tag(), playerInventory.player.level().registryAccess());
            gauges.add(b);
        }

        int networkCount = buf.readVarInt();
        for (int i = 0; i < networkCount; i++)
            knownNetworks.add(new UUID(buf.readLong(), buf.readLong()));

        addPlayerInventorySlots(OFF_SCREEN, OFF_SCREEN, false);
    }

    // First inventory slot index within this.slots (gauge slots come before player inv).
    // Set once in addPlayerInventorySlots, used by repositionSlots to find and remove them.
    private int invSlotsStart = -1;

    private void addPlayerInventorySlots(int originX, int hotbarY, boolean expanded) {
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

    /**
     * Rebuilds inventory slots at new positions.
     * Must be called client-side only (Slot.x/y are final; rebuild is the only option).
     *
     * @param originX  left edge of the 9-slot grid, menu-relative (relative to leftPos)
     * @param hotbarY  top edge of the hotbar row, menu-relative (relative to topPos)
     * @param expanded whether the 3×9 main inventory rows are shown above the hotbar
     */
    public void repositionSlots(int originX, int hotbarY, boolean expanded) {
        // Remove the 36 player inventory slots that were added last.
        if (invSlotsStart >= 0)
            slots.subList(invSlotsStart, slots.size()).clear();
        addPlayerInventorySlots(originX, hotbarY, expanded);
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null) return true;
        return blockEntity.getLevel() != null &&
               player.distanceToSqr(blockEntity.getBlockPos().getX() + 0.5,
                                    blockEntity.getBlockPos().getY() + 0.5,
                                    blockEntity.getBlockPos().getZ() + 0.5) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
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

    public List<VirtualPanelBehaviour> getComponentsInCanvas(int minX, int minY, int maxX, int maxY) {
        return gauges; //FIXME stub, calculate via input range only
    }

    // ── Data serialization for menu open ──────────────────────────────────

    /** Called by NeoForge when the server opens this menu for a player. */
    public static void writeExtraData(FactoryControllerBlockEntity be, RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(be.getBlockPos());

        buf.writeVarInt(be.gauges.size());
        for (VirtualPanelBehaviour b : be.gauges.values()) {
            net.minecraft.nbt.CompoundTag tag = b.toNBT(be.getLevel().registryAccess());
            writeCompoundTag(buf, tag);
        }

        buf.writeVarInt(be.knownNetworks.size());
        for (UUID id : be.knownNetworks) {
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
