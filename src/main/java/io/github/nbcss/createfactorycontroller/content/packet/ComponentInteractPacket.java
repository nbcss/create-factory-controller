package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.VirtualPanelPosition;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The "interact" key (R) on a hovered component. The server dispatches by component type: a gauge cycles its outgoing
 * connection arrow-bend mode; a redstone link toggles its Send/Receive mode.
 */
public record ComponentInteractPacket(BlockPos pos, VirtualPanelPosition panelPos) implements CustomPacketPayload {

    public static final Type<ComponentInteractPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "component_interact"));

    private static final StreamCodec<RegistryFriendlyByteBuf, VirtualPanelPosition> POS_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.INT, VirtualPanelPosition::x,
            ByteBufCodecs.INT, VirtualPanelPosition::y,
            VirtualPanelPosition::new
        );

    public static final StreamCodec<RegistryFriendlyByteBuf, ComponentInteractPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, ComponentInteractPacket::pos,
            POS_CODEC, ComponentInteractPacket::panelPos,
            ComponentInteractPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ComponentInteractPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.interactComponent(packet.panelPos());
        });
    }
}
