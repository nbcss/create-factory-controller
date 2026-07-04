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

/** Client → server: swap the direction of the wire {@code from → to} (becomes {@code to → from}), if the reversed
 *  orientation is legal ({@link io.github.nbcss.createfactorycontroller.content.component.connection.Connection#canReverse
 *  Connection.canReverse}). Used by the Logical Tube settings screen's shift-click and the cycle-operation-mode
 *  keybinding on a hovered signal wire. */
public record ReverseConnectionPacket(BlockPos pos, VirtualComponentPosition from, VirtualComponentPosition to)
    implements CustomPacketPayload {

    public static final Type<ReverseConnectionPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "reverse_connection"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualComponentPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualComponentPosition::x,
            ByteBufCodecs.INT, VirtualComponentPosition::y,
            VirtualComponentPosition::new
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, ReverseConnectionPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, ReverseConnectionPacket::pos,
            POS_CODEC, ReverseConnectionPacket::from,
            POS_CODEC, ReverseConnectionPacket::to,
            ReverseConnectionPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ReverseConnectionPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.reverseConnection(packet.from(), packet.to());
        });
    }
}
