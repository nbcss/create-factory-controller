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

public record CycleArrowBendPacket(BlockPos pos, VirtualPanelPosition gaugePos) implements CustomPacketPayload {

    public static final Type<CycleArrowBendPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "cycle_arrow_bend"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualPanelPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualPanelPosition::col,
            ByteBufCodecs.INT, VirtualPanelPosition::row,
            VirtualPanelPosition::new
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, CycleArrowBendPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, CycleArrowBendPacket::pos,
            POS_CODEC, CycleArrowBendPacket::gaugePos,
            CycleArrowBendPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CycleArrowBendPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.cycleArrowBend(packet.gaugePos());
        });
    }
}
