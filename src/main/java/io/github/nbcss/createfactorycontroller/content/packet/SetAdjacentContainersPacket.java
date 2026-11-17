package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.helper.ControllerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → server: sets whether the controller's Component Supply uses adjacent containers.
 */
public record SetAdjacentContainersPacket(BlockPos pos, boolean enabled) implements CustomPacketPayload {

    public static final Type<SetAdjacentContainersPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "set_adjacent_containers"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetAdjacentContainersPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetAdjacentContainersPacket::pos,
            ByteBufCodecs.BOOL, SetAdjacentContainersPacket::enabled,
            SetAdjacentContainersPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetAdjacentContainersPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(ControllerAccessor.getBlockEntity(player, packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.setUseAdjacentContainers(packet.enabled());
        });
    }
}
