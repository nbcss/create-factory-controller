package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Client → server: this viewer's board state can no longer be trusted (epoch/revision mismatch or an
 * unappliable {@link SyncPanelDeltaPacket} entry) — please re-send everything. The server escalates the
 * next flush to the full {@link SyncPanelStatePacket}; going through the normal flush coalesces
 * simultaneous requesters into one snapshot. It reaches every current viewer, which is harmless: the
 * snapshot is authoritative and idempotent.
 */
public record RequestPanelResyncPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<RequestPanelResyncPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "request_panel_resync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestPanelResyncPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RequestPanelResyncPacket::pos,
            RequestPanelResyncPacket::new
        );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestPanelResyncPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            // Only a player actually viewing this controller may trigger its (heavier) full re-send.
            if (!(player.containerMenu instanceof FactoryControllerMenu menu)
                    || !menu.controllerPos.equals(packet.pos())) return;
            if (player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)
                be.syncEverything();
        });
    }
}
