package io.github.nbcss.content.factorycontroller.packet;

import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.FactoryControllerBlockEntity;
import io.github.nbcss.content.factorycontroller.VirtualPanelPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record AddConnectionPacket(BlockPos pos, VirtualPanelPosition from, VirtualPanelPosition to)
    implements CustomPacketPayload {

    public static final Type<AddConnectionPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "add_connection"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualPanelPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualPanelPosition::x,
            ByteBufCodecs.INT, VirtualPanelPosition::y,
            VirtualPanelPosition::new
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, AddConnectionPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, AddConnectionPacket::pos,
            POS_CODEC, AddConnectionPacket::from,
            POS_CODEC, AddConnectionPacket::to,
            AddConnectionPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AddConnectionPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.addConnection(packet.from(), packet.to());
        });
    }
}
