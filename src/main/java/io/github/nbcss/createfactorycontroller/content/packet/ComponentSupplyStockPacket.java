package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.supply.ComponentSupplyStock;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server → client: the controller's adjacent-container stock for the open menu {@code containerId}. */
public record ComponentSupplyStockPacket(int containerId, ComponentSupplyStock stock) implements CustomPacketPayload {

    public static final Type<ComponentSupplyStockPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "component_supply_stock"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ComponentSupplyStockPacket> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ComponentSupplyStockPacket::containerId,
            ComponentSupplyStock.STREAM_CODEC, ComponentSupplyStockPacket::stock,
            ComponentSupplyStockPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ComponentSupplyStockPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof FactoryControllerMenu menu
                    && menu.containerId == packet.containerId())
                menu.componentSupplyStock = packet.stock();
        });
    }
}
