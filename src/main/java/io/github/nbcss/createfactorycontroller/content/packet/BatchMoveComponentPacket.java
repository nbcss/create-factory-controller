package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.VirtualPanelPosition;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Relocates a whole selection of components by a uniform cell delta {@code (dx, dy)} — the Selection-Mode batch
 * relocate. The server moves them <b>atomically</b>: it commits only if EVERY source lands on an in-board cell that is
 * empty or vacated by another moving component, and otherwise moves nothing (deny blip). Which positions move is driven
 * by the client-only selection state.
 */
public record BatchMoveComponentPacket(BlockPos pos, List<VirtualPanelPosition> sources, int dx, int dy)
    implements CustomPacketPayload {

    public static final Type<BatchMoveComponentPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "batch_move_component"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualPanelPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualPanelPosition::x,
            ByteBufCodecs.INT, VirtualPanelPosition::y,
            VirtualPanelPosition::new
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
