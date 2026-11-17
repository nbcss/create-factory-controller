package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.helper.ControllerAccessor;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Places a stored blueprint on a controller board.
 */
public record BlueprintPlacePacket(BlockPos pos, VirtualComponentPosition anchor, byte[] blueprint,
                                   List<UUID> networks, Optional<BlockPos> boxMin,
                                   Optional<BlockPos> boxMax, boolean fromSupply) implements CustomPacketPayload {

    public static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    /** One binding per network placeholder — at most one per component, so the board cap bounds it. */
    private static final int MAX_NETWORK_SLOTS = 1024;

    public static final Type<BlueprintPlacePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "blueprint_place"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualComponentPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualComponentPosition::x,
            ByteBufCodecs.INT, VirtualComponentPosition::y,
            VirtualComponentPosition::new
        );

    private static final StreamCodec<RegistryFriendlyByteBuf, byte[]> PAYLOAD_CODEC =
        ByteBufCodecs.byteArray(MAX_PAYLOAD_BYTES).cast();
    private static final StreamCodec<RegistryFriendlyByteBuf, List<UUID>> NETWORKS_CODEC =
        UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_NETWORK_SLOTS)).cast();
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<BlockPos>> BOX_CODEC =
        ByteBufCodecs.optional(BlockPos.STREAM_CODEC).cast();

    // Seven fields: past StreamCodec.composite's arity, so written by hand.
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintPlacePacket> STREAM_CODEC = StreamCodec.of(
        (buf, p) -> {
            BlockPos.STREAM_CODEC.encode(buf, p.pos);
            POS_CODEC.encode(buf, p.anchor);
            PAYLOAD_CODEC.encode(buf, p.blueprint);
            NETWORKS_CODEC.encode(buf, p.networks);
            BOX_CODEC.encode(buf, p.boxMin);
            BOX_CODEC.encode(buf, p.boxMax);
            buf.writeBoolean(p.fromSupply);
        },
        buf -> new BlueprintPlacePacket(BlockPos.STREAM_CODEC.decode(buf), POS_CODEC.decode(buf),
            PAYLOAD_CODEC.decode(buf), NETWORKS_CODEC.decode(buf), BOX_CODEC.decode(buf), BOX_CODEC.decode(buf),
            buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BlueprintPlacePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            // Only a player actually viewing this controller may place onto it.
            if (!(player.containerMenu instanceof FactoryControllerMenu menu)
                    || !menu.controllerPos.equals(packet.pos())) return;
            if (!(ControllerAccessor.getBlockEntity(player, packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.placeBlueprint(packet.blueprint(), packet.anchor(), packet.networks(),
                    packet.boxMin().orElse(null), packet.boxMax().orElse(null), packet.fromSupply(), player);
        });
    }
}
