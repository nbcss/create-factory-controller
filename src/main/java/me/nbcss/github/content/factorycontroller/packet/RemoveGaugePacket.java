package me.nbcss.github.content.factorycontroller.packet;

import me.nbcss.github.CreateFactoryController;
import me.nbcss.github.content.factorycontroller.FactoryControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record RemoveGaugePacket(BlockPos pos, int col, int row) implements CustomPacketPayload {

    public static final Type<RemoveGaugePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "remove_gauge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveGaugePacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RemoveGaugePacket::pos,
            ByteBufCodecs.INT, RemoveGaugePacket::col,
            ByteBufCodecs.INT, RemoveGaugePacket::row,
            RemoveGaugePacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RemoveGaugePacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.removeGauge(packet.col(), packet.row(), player);
        });
    }
}
