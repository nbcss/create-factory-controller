package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.helper.ControllerAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Places a Component Supply item: the server takes one from the player's sources instead of the cursor. */
public record ComponentSupplyAttachPacket(BlockPos pos, VirtualComponentPosition panelPos, ResourceLocation itemId,
                                      @Nullable UUID network) implements CustomPacketPayload {

    public static final Type<ComponentSupplyAttachPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "component_supply_attach"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ComponentSupplyAttachPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, ComponentSupplyAttachPacket::pos,
            AttachComponentPacket.POS_CODEC, ComponentSupplyAttachPacket::panelPos,
            ResourceLocation.STREAM_CODEC, ComponentSupplyAttachPacket::itemId,
            AttachComponentPacket.NULLABLE_UUID, ComponentSupplyAttachPacket::network,
            ComponentSupplyAttachPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ComponentSupplyAttachPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(ControllerAccessor.getBlockEntity(player, packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            be.attachFromComponentSupply(packet.panelPos(), player, packet.itemId(), packet.network());
        });
    }
}
