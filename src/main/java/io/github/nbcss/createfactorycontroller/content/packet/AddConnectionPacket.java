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

public record AddConnectionPacket(BlockPos pos, String connectionType,
                                  VirtualComponentPosition source, VirtualComponentPosition sink)
    implements CustomPacketPayload {

    public static final Type<AddConnectionPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "add_connection"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualComponentPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualComponentPosition::x,
            ByteBufCodecs.INT, VirtualComponentPosition::y,
            VirtualComponentPosition::new
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, AddConnectionPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, AddConnectionPacket::pos,
            ByteBufCodecs.STRING_UTF8, AddConnectionPacket::connectionType,
            POS_CODEC, AddConnectionPacket::source,
            POS_CODEC, AddConnectionPacket::sink,
            AddConnectionPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AddConnectionPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.addConnection(packet.connectionType(), packet.source(), packet.sink());
        });
    }
}
