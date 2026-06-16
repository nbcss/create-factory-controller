package io.github.nbcss.createfactorycontroller.content.packet;

import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrderManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** C2S: the monitoring tab asks the server for the open promise orders on the viewed keeper's network. */
public record RequestProductionOrdersPacket(BlockPos keeperPos) implements CustomPacketPayload {

    public static final Type<RequestProductionOrdersPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "request_production_orders"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestProductionOrdersPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> buf.writeBlockPos(pkt.keeperPos),
                       buf -> new RequestProductionOrdersPacket(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestProductionOrdersPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.keeperPos()) instanceof StockTickerBlockEntity keeper)) return;
            UUID network = keeper.behaviour == null ? null : keeper.behaviour.freqId;
            if (network == null) return;
            long now = player.getServer().overworld().getGameTime();
            PacketDistributor.sendToPlayer(player, new SyncProductionOrdersPacket(
                ProductionOrderManager.get(player.serverLevel()).viewsForNetwork(network, now)));
        });
    }
}
