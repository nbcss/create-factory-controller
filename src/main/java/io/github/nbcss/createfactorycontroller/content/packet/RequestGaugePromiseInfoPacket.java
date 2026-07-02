package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.promise.PromiseCounts;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: the recipe screen (open on one gauge) polls the gauge's live in-flight promise counts — its own and its
 * address's — for the promise-limit box. On-demand only, so nothing is computed when no one is viewing. The server
 * reads the per-tick {@link PromiseCounts} cache and replies with a {@link GaugePromiseInfoPacket}.
 */
public record RequestGaugePromiseInfoPacket(BlockPos pos, VirtualComponentPosition gaugePos)
    implements CustomPacketPayload {

    public static final Type<RequestGaugePromiseInfoPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "request_gauge_promise_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestGaugePromiseInfoPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequestGaugePromiseInfoPacket::pos,
            ByteBufCodecs.INT, p -> p.gaugePos().x(),
            ByteBufCodecs.INT, p -> p.gaugePos().y(),
            (pos, x, y) -> new RequestGaugePromiseInfoPacket(pos, new VirtualComponentPosition(x, y)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestGaugePromiseInfoPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            if (!(be.components.get(packet.gaugePos()) instanceof VirtualGaugeBehaviour g) || g.gaugeId == null) return;
            long now = player.level().getGameTime();
            int owned = PromiseCounts.owned(g.networkId, g.gaugeId.toString(), now);
            int address = PromiseCounts.address(g.networkId, g.recipeAddress, now);
            PacketDistributor.sendToPlayer(player, new GaugePromiseInfoPacket(packet.gaugePos(), owned, address));
        });
    }
}
