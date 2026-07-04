package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;


public record CycleArrowModePacket(BlockPos pos, VirtualComponentPosition panelPos) implements CustomPacketPayload {

    public static final Type<CycleArrowModePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "cycle_arrow_mode"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualComponentPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualComponentPosition::x,
            ByteBufCodecs.INT, VirtualComponentPosition::y,
            VirtualComponentPosition::new
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, CycleArrowModePacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, CycleArrowModePacket::pos,
            POS_CODEC, CycleArrowModePacket::panelPos,
            CycleArrowModePacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CycleArrowModePacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.cycleArrowMode(packet.panelPos());
        });
    }
}
