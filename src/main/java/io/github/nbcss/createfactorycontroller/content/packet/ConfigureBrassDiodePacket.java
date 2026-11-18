package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.redstone.BrassDiodeBehaviour;
import io.github.nbcss.createfactorycontroller.content.helper.ControllerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Brass diode settings screen commit: redstone interval (ticks) + output inversion. */
public record ConfigureBrassDiodePacket(BlockPos pos, VirtualComponentPosition diode, int interval, boolean inverted)
    implements CustomPacketPayload {

    public static final Type<ConfigureBrassDiodePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "configure_brass_diode"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualComponentPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualComponentPosition::x,
            ByteBufCodecs.INT, VirtualComponentPosition::y,
            VirtualComponentPosition::new
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureBrassDiodePacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureBrassDiodePacket::pos,
            POS_CODEC, ConfigureBrassDiodePacket::diode,
            ByteBufCodecs.VAR_INT, ConfigureBrassDiodePacket::interval,
            ByteBufCodecs.BOOL, ConfigureBrassDiodePacket::inverted,
            ConfigureBrassDiodePacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigureBrassDiodePacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(ControllerAccessor.getBlockEntity(player, packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            if (be.componentAt(packet.diode()) instanceof BrassDiodeBehaviour diode)
                diode.configure(packet.interval(), packet.inverted());
        });
    }
}
