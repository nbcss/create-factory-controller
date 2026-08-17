package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


public record CycleConnectionArrowModePacket(BlockPos pos, VirtualComponentPosition from, VirtualComponentPosition to,
                                             String connectionType)
    implements CustomPacketPayload {

    public static final Type<CycleConnectionArrowModePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "cycle_connection_arrow_mode"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualComponentPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualComponentPosition::x,
            ByteBufCodecs.INT, VirtualComponentPosition::y,
            VirtualComponentPosition::new
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, CycleConnectionArrowModePacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, CycleConnectionArrowModePacket::pos,
            POS_CODEC, CycleConnectionArrowModePacket::from,
            POS_CODEC, CycleConnectionArrowModePacket::to,
            ByteBufCodecs.STRING_UTF8, CycleConnectionArrowModePacket::connectionType,
            CycleConnectionArrowModePacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CycleConnectionArrowModePacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            Connection.Type type = Connection.Type.get(packet.connectionType());
            if (type == null) return;
            be.cycleConnectionArrowMode(packet.from(), packet.to(), type);
        });
    }
}
