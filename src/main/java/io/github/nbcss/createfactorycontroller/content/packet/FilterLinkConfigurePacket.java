package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FilterLinkBlockEntity;
import io.github.nbcss.createfactorycontroller.content.helper.FilterApplication;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record FilterLinkConfigurePacket(BlockPos linkPos, List<BlockPos> targets) implements CustomPacketPayload {
    public static final Type<FilterLinkConfigurePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "filter_link_configure"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FilterLinkConfigurePacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, FilterLinkConfigurePacket::linkPos,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), FilterLinkConfigurePacket::targets,
            FilterLinkConfigurePacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FilterLinkConfigurePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            Level level = player.level();
            if (!level.isLoaded(packet.linkPos())) return;
            if (!(level.getBlockEntity(packet.linkPos()) instanceof FilterLinkBlockEntity link)) return;
            if (!player.blockPosition().closerThan(packet.linkPos(), 64)) return;
            List<BlockPos> valid = new ArrayList<>();
            for (BlockPos t : packet.targets())
                if (t.closerThan(packet.linkPos(), FilterApplication.range())
                        && level.isLoaded(t) && FilterApplication.hasFilter(level, t))
                    valid.add(t.immutable());
            link.setTargets(valid);
            if (link.getPackager() == null)
                player.displayClientMessage(Component.translatable(
                    "createfactorycontroller.filter_link.not_packager").withStyle(ChatFormatting.RED), true);
            else
                player.displayClientMessage(Component.translatable(
                    "createfactorycontroller.filter_link.linked", valid.size()).withStyle(ChatFormatting.GREEN), true);
        });
    }
}
