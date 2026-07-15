package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.gui.toast.OrderStatusToast;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server → the ordering player: pops an advancement-style {@link OrderStatusToast}. Data-only — the text is
 * built client-side from lang keys ({@code kind} selects the header); {@code requestedItems} and {@code address}
 * fill the body. {@code kind}: 0 = order complete, 1 = gauge invalid.
 */
public record OrderNotificationPacket(int kind, List<RequestedItem> requestedItems, String address)
        implements CustomPacketPayload {

    /** One requested item and its original order amount. */
    public record RequestedItem(ItemStack item, int amount) {
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestedItem> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, RequestedItem::item,
            ByteBufCodecs.VAR_INT, RequestedItem::amount,
            RequestedItem::new);
    }

    public static final int KIND_ORDER_COMPLETE = 0;
    public static final int KIND_GAUGE_INVALID = 1;

    public static final Type<OrderNotificationPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "order_notification"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OrderNotificationPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OrderNotificationPacket::kind,
            RequestedItem.STREAM_CODEC.apply(ByteBufCodecs.list()), OrderNotificationPacket::requestedItems,
            ByteBufCodecs.STRING_UTF8, OrderNotificationPacket::address,
            OrderNotificationPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // Runs client-side only (playToClient).
    public static void handle(OrderNotificationPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> OrderStatusToast.show(packet.kind(), packet.requestedItems(), packet.address()));
    }
}
