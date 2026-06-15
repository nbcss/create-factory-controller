package io.github.nbcss.content.factorycontroller.packet;

import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.production.ProductionOrderManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** C2S: player removes an entire production order from the monitoring tab. Server drops it and re-syncs the tab. */
public record RemoveProductionOrderPacket(BlockPos keeperPos, int orderId) implements CustomPacketPayload {

    public static final Type<RemoveProductionOrderPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "remove_production_order"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveProductionOrderPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> { buf.writeBlockPos(pkt.keeperPos); buf.writeInt(pkt.orderId); },
                       buf -> new RemoveProductionOrderPacket(buf.readBlockPos(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RemoveProductionOrderPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ProductionOrderManager mgr = ProductionOrderManager.get(player.serverLevel());
            mgr.removeOrder(packet.orderId());
            if (player.level().getBlockEntity(packet.keeperPos()) instanceof StockTickerBlockEntity keeper) {
                UUID network = keeper.behaviour == null ? null : keeper.behaviour.freqId;
                if (network != null) {
                    long now = player.getServer().overworld().getGameTime();
                    PacketDistributor.sendToPlayer(player, new SyncProductionOrdersPacket(mgr.viewsForNetwork(network, now)));
                }
            }
        });
    }
}
