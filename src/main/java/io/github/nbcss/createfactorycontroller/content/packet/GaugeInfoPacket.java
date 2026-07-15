package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.gui.screen.GaugeInfoClient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C reply to {@link RequestGaugePromiseInfoPacket}: the gauge's live in-flight promise counts (for the limit
 * box) plus its request-timer state (for the output-arrow animation) — {@code timer} (current countdown),
 * {@code maxTimer} (effective reset value), and {@code ticking} (whether it's actively counting down).
 */
public record GaugeInfoPacket(VirtualComponentPosition gaugePos, int owned, int address,
                              int timer, int maxTimer, boolean ticking)
    implements CustomPacketPayload {

    public static final Type<GaugeInfoPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "gauge_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GaugeInfoPacket> STREAM_CODEC =
        StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.gaugePos().x());
                buf.writeInt(p.gaugePos().y());
                buf.writeInt(p.owned());
                buf.writeInt(p.address());
                buf.writeInt(p.timer());
                buf.writeInt(p.maxTimer());
                buf.writeBoolean(p.ticking());
            },
            buf -> new GaugeInfoPacket(new VirtualComponentPosition(buf.readInt(), buf.readInt()),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GaugeInfoPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> GaugeInfoClient.update(packet.gaugePos(), packet.owned(), packet.address(),
            packet.timer(), packet.maxTimer(), packet.ticking()));
    }
}
