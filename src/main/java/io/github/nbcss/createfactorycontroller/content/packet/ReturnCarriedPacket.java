package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Empties the cursor into the player's inventory, dropping the remainder when it does not fit. */
public record ReturnCarriedPacket() implements CustomPacketPayload {

    public static final Type<ReturnCarriedPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "return_carried"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReturnCarriedPacket> STREAM_CODEC =
        StreamCodec.unit(new ReturnCarriedPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ReturnCarriedPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack carried = player.containerMenu.getCarried();
            if (carried.isEmpty()) return;
            player.containerMenu.setCarried(ItemStack.EMPTY);
            if (!player.getInventory().add(carried))
                player.drop(carried, false);
            player.containerMenu.broadcastChanges();   // setCarried alone does not push the cursor slot
        });
    }
}
