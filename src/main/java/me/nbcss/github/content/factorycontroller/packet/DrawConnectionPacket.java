package me.nbcss.github.content.factorycontroller.packet;

import me.nbcss.github.CreateFactoryController;
import me.nbcss.github.content.factorycontroller.FactoryControllerBlockEntity;
import me.nbcss.github.content.factorycontroller.VirtualPanelPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record DrawConnectionPacket(BlockPos pos, VirtualPanelPosition from, VirtualPanelPosition to, int amount)
    implements CustomPacketPayload {

    public static final Type<DrawConnectionPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "draw_connection"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualPanelPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualPanelPosition::col,
            ByteBufCodecs.INT, VirtualPanelPosition::row,
            VirtualPanelPosition::new
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, DrawConnectionPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, DrawConnectionPacket::pos,
            POS_CODEC, DrawConnectionPacket::from,
            POS_CODEC, DrawConnectionPacket::to,
            ByteBufCodecs.INT, DrawConnectionPacket::amount,
            DrawConnectionPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DrawConnectionPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.drawConnection(packet.from(), packet.to(), packet.amount());
        });
    }
}
