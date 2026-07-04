package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualRedstoneLinkBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;


public record ConfigureRedstoneLinkPacket(BlockPos pos, VirtualComponentPosition panelPos, boolean receive,
                                          ItemStack red, ItemStack blue) implements CustomPacketPayload {

    public static final Type<ConfigureRedstoneLinkPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "configure_redstone_link"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualComponentPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualComponentPosition::x,
            ByteBufCodecs.INT, VirtualComponentPosition::y,
            VirtualComponentPosition::new
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureRedstoneLinkPacket> STREAM_CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                BlockPos.STREAM_CODEC.encode(buf, pkt.pos);
                POS_CODEC.encode(buf, pkt.panelPos);
                buf.writeBoolean(pkt.receive);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, pkt.red);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, pkt.blue);
            },
            buf -> new ConfigureRedstoneLinkPacket(
                BlockPos.STREAM_CODEC.decode(buf),
                POS_CODEC.decode(buf),
                buf.readBoolean(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf))
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigureRedstoneLinkPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            if (be.componentAt(packet.panelPos()) instanceof VirtualRedstoneLinkBehaviour link)
                link.configure(packet.receive(), packet.red(), packet.blue());
        });
    }
}
