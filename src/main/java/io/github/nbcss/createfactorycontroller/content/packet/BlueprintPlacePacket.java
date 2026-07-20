package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
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
import java.util.UUID;

/**
 * Places a stored blueprint on a controller board. Blueprints live in the client's game directory, so the
 * gzipped file travels with the request; {@code networks} binds each network placeholder (in order) to a
 * real network. Everything here is untrusted — {@link FactoryControllerBlockEntity#placeBlueprint} validates
 * the payload, the destination cells, the materials and the network bindings before it commits.
 */
public record BlueprintPlacePacket(BlockPos pos, VirtualComponentPosition anchor, byte[] blueprint,
                                   List<UUID> networks) implements CustomPacketPayload {

    /** Serverbound custom payloads cap at 32767 bytes; leave room for the rest of the record. */
    public static final int MAX_PAYLOAD_BYTES = 30000;
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

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintPlacePacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, BlueprintPlacePacket::pos,
            POS_CODEC, BlueprintPlacePacket::anchor,
            ByteBufCodecs.byteArray(MAX_PAYLOAD_BYTES), BlueprintPlacePacket::blueprint,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_NETWORK_SLOTS)), BlueprintPlacePacket::networks,
            BlueprintPlacePacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BlueprintPlacePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            // Only a player actually viewing this controller may place onto it.
            if (!(player.containerMenu instanceof FactoryControllerMenu menu)
                    || !menu.controllerPos.equals(packet.pos())) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.placeBlueprint(packet.blueprint(), packet.anchor(), packet.networks(), player);
        });
    }
}
