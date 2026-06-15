package io.github.nbcss.content.factorycontroller.packet;

import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.production.ProductionOrderView;
import io.github.nbcss.content.factorycontroller.production.ProductionOrdersClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** S2C: the open production orders on the viewed keeper's network, for the monitoring tab. */
public record SyncProductionOrdersPacket(List<ProductionOrderView> orders) implements CustomPacketPayload {

    public static final Type<SyncProductionOrdersPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "sync_production_orders"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncProductionOrdersPacket> STREAM_CODEC =
        StreamCodec.composite(
            ProductionOrderView.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncProductionOrdersPacket::orders,
            SyncProductionOrdersPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SyncProductionOrdersPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ProductionOrdersClient.update(packet.orders()));
    }
}
