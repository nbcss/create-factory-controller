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
import org.jetbrains.annotations.Nullable;

import java.util.UUID;


public record AttachComponentPacket(BlockPos pos, VirtualComponentPosition panelPos, @Nullable UUID network) implements CustomPacketPayload {

    public static final Type<AttachComponentPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "attach_component"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualComponentPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualComponentPosition::x,
            ByteBufCodecs.INT, VirtualComponentPosition::y,
            VirtualComponentPosition::new
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
