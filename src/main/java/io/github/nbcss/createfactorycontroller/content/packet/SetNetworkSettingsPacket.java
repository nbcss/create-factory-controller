package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettings;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettingsStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Client → server: set a network's shared {@link NetworkSettings} (custom name + icon). A blank name and
 * empty icon clear the entry back to the default. The server clamps the name, forces the icon to a single
 * item, writes the world-global {@link NetworkSettingsStore}, and re-syncs every open controller that uses
 * the network (settings are shared).
 */
public record SetNetworkSettingsPacket(BlockPos controllerPos, UUID network, String name, ItemStack icon)
        implements CustomPacketPayload {

    public static final Type<SetNetworkSettingsPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "set_network_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetNetworkSettingsPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetNetworkSettingsPacket::controllerPos,
            UUIDUtil.STREAM_CODEC, SetNetworkSettingsPacket::network,
            ByteBufCodecs.STRING_UTF8, SetNetworkSettingsPacket::name,
            ItemStack.OPTIONAL_STREAM_CODEC, SetNetworkSettingsPacket::icon,
            SetNetworkSettingsPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SetNetworkSettingsPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.controllerPos()) instanceof FactoryControllerBlockEntity be)) return;
            // Only a controller that actually uses this network may edit it.
            if (!be.networks.contains(packet.network())) return;

            String name = packet.name() == null ? "" : packet.name().strip();
            if (name.length() > FactoryControllerBlockEntity.MAX_NAME_LENGTH)
                name = name.substring(0, FactoryControllerBlockEntity.MAX_NAME_LENGTH);
            ItemStack icon = packet.icon().isEmpty() ? ItemStack.EMPTY : packet.icon().copyWithCount(1);

            NetworkSettingsStore store = NetworkSettingsStore.get(player.level());
            if (store == null) return;
            store.set(packet.network(), new NetworkSettings(packet.network(), name, icon));

            // Shared settings: refresh every open controller that references this network.
            FactoryControllerBlockEntity.resyncNetworkViewers(player.getServer(), packet.network());
        });
    }
}
