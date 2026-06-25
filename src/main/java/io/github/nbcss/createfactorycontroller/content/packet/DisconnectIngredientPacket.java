package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sent immediately when the player clicks an ingredient slot in {@code ConfigureRecipeScreen}, to
 * disconnect that incoming connection ({@code ingredient → gauge}) on the server right away.
 */
public record DisconnectIngredientPacket(BlockPos pos, VirtualComponentPosition ingredient, VirtualComponentPosition gauge)
    implements CustomPacketPayload {

    public static final Type<DisconnectIngredientPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "disconnect_ingredient"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualComponentPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualComponentPosition::x,
            ByteBufCodecs.INT, VirtualComponentPosition::y,
            VirtualComponentPosition::new
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, DisconnectIngredientPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, DisconnectIngredientPacket::pos,
            POS_CODEC, DisconnectIngredientPacket::ingredient,
            POS_CODEC, DisconnectIngredientPacket::gauge,
            DisconnectIngredientPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DisconnectIngredientPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.removeConnection(packet.ingredient(), packet.gauge());
        });
    }
}
