package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Client → server: rename the factory controller (blank clears it back to the default name). The
 * server clamps the name to {@link FactoryControllerBlockEntity#MAX_NAME_LENGTH} and re-syncs.
 */
public record RenameControllerPacket(BlockPos pos, String name) implements CustomPacketPayload {

    public static final Type<RenameControllerPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "rename_controller"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameControllerPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RenameControllerPacket::pos,
            ByteBufCodecs.STRING_UTF8, RenameControllerPacket::name,
            RenameControllerPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RenameControllerPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.setCustomName(packet.name());
        });
    }
}
