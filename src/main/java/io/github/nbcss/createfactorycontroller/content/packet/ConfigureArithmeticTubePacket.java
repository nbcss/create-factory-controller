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

/** Sets an Arithmetic Tube's operator to a specific one (the operator picker's selection). */
public record ConfigureArithmeticTubePacket(BlockPos pos, VirtualComponentPosition tube, String operator)
    implements CustomPacketPayload {

    public static final Type<ConfigureArithmeticTubePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "configure_arithmetic_tube"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualComponentPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualComponentPosition::x,
            ByteBufCodecs.INT, VirtualComponentPosition::y,
            VirtualComponentPosition::new
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureArithmeticTubePacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureArithmeticTubePacket::pos,
            POS_CODEC, ConfigureArithmeticTubePacket::tube,
            ByteBufCodecs.STRING_UTF8, ConfigureArithmeticTubePacket::operator,
            ConfigureArithmeticTubePacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigureArithmeticTubePacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.configureArithmeticTube(packet.tube(), packet.operator());
        });
    }
}
