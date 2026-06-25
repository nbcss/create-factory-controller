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
import net.minecraft.world.item.ItemStack;

/** Sets a gauge's filter item (the produced output) from the set-item overlay or a carried item. */
public record GaugeSetItemPacket(BlockPos pos, VirtualComponentPosition panelPos, ItemStack filter, boolean ignoreData)
    implements CustomPacketPayload {

    public static final Type<GaugeSetItemPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "gauge_set_item"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualComponentPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualComponentPosition::x,
            ByteBufCodecs.INT, VirtualComponentPosition::y,
            VirtualComponentPosition::new
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, GaugeSetItemPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, GaugeSetItemPacket::pos,
            POS_CODEC, GaugeSetItemPacket::panelPos,
            ItemStack.OPTIONAL_STREAM_CODEC, GaugeSetItemPacket::filter,
            ByteBufCodecs.BOOL, GaugeSetItemPacket::ignoreData,
            GaugeSetItemPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GaugeSetItemPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.setComponentItem(packet.panelPos(), packet.filter(), packet.ignoreData());
        });
    }
}
