package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrderManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client → server: the requesting player subscribes to status toasts for the production order they're placing
 * now. Correlated to the order by ({@code network}, {@code address}) — sent from the Stock Keeper's
 * {@code sendIt()} just before Create's order packet, so the pending subscription is already registered when
 * {@code interceptProductionOrder} creates the order and consumes it.
 */
public record RegisterOrderNotificationPacket(UUID network, String address) implements CustomPacketPayload {

    public static final Type<RegisterOrderNotificationPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "register_order_notification"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RegisterOrderNotificationPacket> STREAM_CODEC =
        StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RegisterOrderNotificationPacket::network,
            ByteBufCodecs.STRING_UTF8, RegisterOrderNotificationPacket::address,
            RegisterOrderNotificationPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RegisterOrderNotificationPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ProductionOrderManager mgr = ProductionOrderManager.get(player.serverLevel());
            if (mgr != null)
                mgr.subscribe(packet.network(), packet.address(), player.getUUID(),
                    player.getServer().overworld().getGameTime());
        });
    }
}
