package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Empties the cursor into the player's inventory. With {@code keepIfFull} (the Return Component key) a survival cursor
 * that does not fit entirely stays put; otherwise the remainder drops at the player. Creative remainders vanish.
 */
public record ReturnCarriedPacket(boolean keepIfFull) implements CustomPacketPayload {

    /** Cleanup paths that must leave the cursor empty. */
    public static final ReturnCarriedPacket DROP_OVERFLOW = new ReturnCarriedPacket(false);
    public static final ReturnCarriedPacket KEEP_IF_FULL = new ReturnCarriedPacket(true);

    public static final Type<ReturnCarriedPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "return_carried"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReturnCarriedPacket> STREAM_CODEC =
        ByteBufCodecs.BOOL.map(ReturnCarriedPacket::new, ReturnCarriedPacket::keepIfFull).cast();

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ReturnCarriedPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack carried = player.containerMenu.getCarried();
            if (carried.isEmpty()) return;
            Inventory inventory = player.getInventory();
            if (packet.keepIfFull() && !player.isCreative() && !canFullyInsert(inventory, carried)) return;
            player.containerMenu.setCarried(ItemStack.EMPTY);
            inventory.add(carried);   // shrinks carried; for creative it also discards the remainder
            if (!carried.isEmpty()) player.drop(carried, false);
            player.containerMenu.broadcastChanges();   // setCarried alone does not push the cursor slot
        });
    }

    /** Whether {@link Inventory#add(ItemStack)} would take all of {@code stack}. */
    public static boolean canFullyInsert(Inventory inventory, ItemStack stack) {
        if (stack.isEmpty()) return true;
        int max = inventory.getMaxStackSize(stack);
        if (stack.isDamaged()) {   // add() only puts damaged items into empty main slots
            for (ItemStack slot : inventory.items) if (slot.isEmpty()) return true;
            return false;
        }
        long room = mergeRoom(inventory.offhand.get(0), stack, max);
        for (ItemStack slot : inventory.items)
            room += slot.isEmpty() ? max : mergeRoom(slot, stack, max);
        return room >= stack.getCount();
    }

    private static int mergeRoom(ItemStack slot, ItemStack stack, int max) {
        if (slot.isEmpty() || !slot.isStackable() || !ItemStack.isSameItemSameComponents(slot, stack)) return 0;
        return Math.max(0, max - slot.getCount());
    }
}
