package io.github.nbcss.content.factorycontroller.packet;

import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.FactoryControllerBlockEntity;
import io.github.nbcss.content.factorycontroller.VirtualPanelPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Attaches the carried gauge at the given panel position. {@code network} carries the client-side selected
 * network so untuned gauges can be assigned without the server tracking a selection. It is null
 * for tuned gauges (the server derives the network from the item itself).
 */
public record AttachComponentPacket(BlockPos pos, VirtualPanelPosition panelPos, @Nullable UUID network) implements CustomPacketPayload {

    public static final Type<AttachComponentPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "attach_component"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualPanelPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualPanelPosition::x,
            ByteBufCodecs.INT, VirtualPanelPosition::y,
            VirtualPanelPosition::new
        );

    /** Nullable UUID: a leading boolean flags presence. */
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> NULLABLE_UUID =
        new StreamCodec<>() {
            @Override
            public UUID decode(RegistryFriendlyByteBuf buf) {
                return buf.readBoolean() ? new UUID(buf.readLong(), buf.readLong()) : null;
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, UUID id) {
                buf.writeBoolean(id != null);
                if (id != null) {
                    buf.writeLong(id.getMostSignificantBits());
                    buf.writeLong(id.getLeastSignificantBits());
                }
            }
        };

    public static final StreamCodec<RegistryFriendlyByteBuf, AttachComponentPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, AttachComponentPacket::pos,
            POS_CODEC, AttachComponentPacket::panelPos,
            NULLABLE_UUID, AttachComponentPacket::network,
            AttachComponentPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(AttachComponentPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.attachComponent(packet.panelPos(), player, packet.network());
        });
    }
}
