package io.github.nbcss.content.factorycontroller.packet;

import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.FactoryControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record ConfigureGaugePacket(BlockPos pos, int col, int row, ItemStack filter, int amount)
    implements CustomPacketPayload {

    public static final Type<ConfigureGaugePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "configure_gauge"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureGaugePacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigureGaugePacket::pos,
            ByteBufCodecs.INT, ConfigureGaugePacket::col,
            ByteBufCodecs.INT, ConfigureGaugePacket::row,
            ItemStack.OPTIONAL_STREAM_CODEC, ConfigureGaugePacket::filter,
            ByteBufCodecs.INT, ConfigureGaugePacket::amount,
            ConfigureGaugePacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigureGaugePacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.configureGauge(packet.col(), packet.row(), packet.filter(), packet.amount());
        });
    }
}
