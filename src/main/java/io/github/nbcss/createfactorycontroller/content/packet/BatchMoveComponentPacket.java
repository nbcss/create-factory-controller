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

import java.util.List;


public record BatchMoveComponentPacket(BlockPos pos, List<VirtualComponentPosition> sources, int dx, int dy)
    implements CustomPacketPayload {

    public static final Type<BatchMoveComponentPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "batch_move_component"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualComponentPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualComponentPosition::x,
            ByteBufCodecs.INT, VirtualComponentPosition::y,
            VirtualComponentPosition::new
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, BatchMoveComponentPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, BatchMoveComponentPacket::pos,
            POS_CODEC.apply(ByteBufCodecs.list()), BatchMoveComponentPacket::sources,
            ByteBufCodecs.INT, BatchMoveComponentPacket::dx,
            ByteBufCodecs.INT, BatchMoveComponentPacket::dy,
            BatchMoveComponentPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BatchMoveComponentPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.moveComponents(packet.sources(), packet.dx(), packet.dy());
        });
    }
}
