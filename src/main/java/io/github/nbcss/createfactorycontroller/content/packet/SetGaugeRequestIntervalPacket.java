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
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: set a gauge's request interval from the recipe screen's output arrow — {@code customRequestTimer} in ticks,
 * or {@code 0} to clear the override back to Create's config. Applied immediately (rather than waiting for the
 * screen's confirm) so scrolling/resetting takes effect at once; the server restarts the gauge's countdown at the
 * new value so the change is visible on the next tick.
 */
public record SetGaugeRequestIntervalPacket(BlockPos pos, VirtualComponentPosition gaugePos, int customRequestTimer)
    implements CustomPacketPayload {

    public static final Type<SetGaugeRequestIntervalPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "set_gauge_request_interval"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetGaugeRequestIntervalPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetGaugeRequestIntervalPacket::pos,
            ByteBufCodecs.INT, p -> p.gaugePos().x(),
            ByteBufCodecs.INT, p -> p.gaugePos().y(),
            ByteBufCodecs.INT, SetGaugeRequestIntervalPacket::customRequestTimer,
            (pos, x, y, ticks) -> new SetGaugeRequestIntervalPacket(pos, new VirtualComponentPosition(x, y), ticks));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetGaugeRequestIntervalPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.setGaugeRequestInterval(packet.gaugePos(), packet.customRequestTimer());
        });
    }
}
