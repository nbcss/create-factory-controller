package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.gui.screen.GaugePromiseInfoClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C reply to {@link RequestGaugePromiseInfoPacket}: the gauge's live in-flight promise counts for the limit box. */
public record GaugePromiseInfoPacket(VirtualComponentPosition gaugePos, int owned, int address)
    implements CustomPacketPayload {

    public static final Type<GaugePromiseInfoPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "gauge_promise_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GaugePromiseInfoPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, p -> p.gaugePos().x(),
            ByteBufCodecs.INT, p -> p.gaugePos().y(),
            ByteBufCodecs.INT, GaugePromiseInfoPacket::owned,
            ByteBufCodecs.INT, GaugePromiseInfoPacket::address,
            (x, y, owned, address) -> new GaugePromiseInfoPacket(new VirtualComponentPosition(x, y), owned, address));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GaugePromiseInfoPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> GaugePromiseInfoClient.update(packet.gaugePos(), packet.owned(), packet.address()));
    }
}
