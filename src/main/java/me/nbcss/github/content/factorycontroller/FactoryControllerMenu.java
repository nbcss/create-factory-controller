package me.nbcss.github.content.factorycontroller;

import me.nbcss.github.CreateFactoryController;
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
    public final Set<UUID> knownNetworks = new LinkedHashSet<>();
    @Nullable public UUID selectedNetwork;
    public final BlockPos controllerPos;

    // Server-side: reference to the actual BE
    @Nullable private final FactoryControllerBlockEntity blockEntity;

    /** Server-side constructor. */
    public FactoryControllerMenu(int syncId, Inventory playerInventory, FactoryControllerBlockEntity be) {
        super(CreateFactoryController.FACTORY_CONTROLLER_MENU.get(), syncId);
        this.blockEntity = be;
        this.controllerPos = be.getBlockPos();

        // Snapshot data from BE for client sync
        this.gauges.addAll(be.gauges.values());
        this.knownNetworks.addAll(be.knownNetworks);
        this.selectedNetwork = be.selectedNetwork;

        addPlayerInventorySlots(playerInventory);
    }

    /** Client-side constructor (called via IMenuTypeExtension). */
    public FactoryControllerMenu(int syncId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(CreateFactoryController.FACTORY_CONTROLLER_MENU.get(), syncId);
        this.blockEntity = null;
        this.controllerPos = buf.readBlockPos();

        int gaugeCount = buf.readVarInt();
        for (int i = 0; i < gaugeCount; i++) {
            CompoundTagHelper helper = new CompoundTagHelper(buf);
            VirtualPanelBehaviour b = VirtualPanelBehaviour.fromNBT(null, helper.tag(), playerInventory.player.level().registryAccess());
            gauges.add(b);
        }

        int networkCount = buf.readVarInt();
        for (int i = 0; i < networkCount; i++)
            knownNetworks.add(new UUID(buf.readLong(), buf.readLong()));

        if (buf.readBoolean())
            selectedNetwork = new UUID(buf.readLong(), buf.readLong());

        addPlayerInventorySlots(playerInventory);
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        // Main inventory (3 rows of 9)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 174 + row * 18));
        // Hotbar
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 232));
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
        return ItemStack.EMPTY;
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

        buf.writeBoolean(be.selectedNetwork != null);
        if (be.selectedNetwork != null) {
            buf.writeLong(be.selectedNetwork.getMostSignificantBits());
            buf.writeLong(be.selectedNetwork.getLeastSignificantBits());
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
