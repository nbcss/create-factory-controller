package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.client.FilterLinkSelectionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FilterLinkPlacedPacket(BlockPos pos) implements CustomPacketPayload {
    public static final Type<FilterLinkPlacedPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "filter_link_placed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterLinkPlacedPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, FilterLinkPlacedPacket::pos,
            FilterLinkPlacedPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FilterLinkPlacedPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> FilterLinkSelectionHandler.flush(packet.pos()));
    }
}
