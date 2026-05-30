package io.github.nbcss.content.factorycontroller.packet;

import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.FactoryControllerMenu;
import io.github.nbcss.content.factorycontroller.FactoryControllerScreen;
import io.github.nbcss.content.factorycontroller.VirtualPanelBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → client: full gauge + network snapshot so the open screen stays live.
 * Sent by FactoryControllerBlockEntity.sendData() to any player who has this menu open.
 */
public record SyncPanelStatePacket(BlockPos pos, List<CompoundTag> gaugeTags, List<UUID> networks)
        implements CustomPacketPayload {

    public static final Type<SyncPanelStatePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "sync_panel_state"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<CompoundTag>> TAG_LIST_CODEC =
        new StreamCodec<>() {
            @Override
            public List<CompoundTag> decode(RegistryFriendlyByteBuf buf) {
                int size = buf.readVarInt();
                List<CompoundTag> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    Tag nbt = buf.readNbt();
                    if (nbt instanceof CompoundTag tag) list.add(tag);
                }
                return list;
            }
            @Override
            public void encode(RegistryFriendlyByteBuf buf, List<CompoundTag> tags) {
                buf.writeVarInt(tags.size());
                for (CompoundTag tag : tags) buf.writeNbt(tag);
            }
        };

    private static final StreamCodec<RegistryFriendlyByteBuf, List<UUID>> UUID_LIST_CODEC =
        new StreamCodec<>() {
            @Override
            public List<UUID> decode(RegistryFriendlyByteBuf buf) {
                int size = buf.readVarInt();
                List<UUID> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++)
                    list.add(new UUID(buf.readLong(), buf.readLong()));
                return list;
            }
            @Override
            public void encode(RegistryFriendlyByteBuf buf, List<UUID> ids) {
                buf.writeVarInt(ids.size());
                for (UUID id : ids) {
                    buf.writeLong(id.getMostSignificantBits());
                    buf.writeLong(id.getLeastSignificantBits());
                }
            }
        };

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPanelStatePacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public SyncPanelStatePacket decode(RegistryFriendlyByteBuf buf) {
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                List<CompoundTag> tags = TAG_LIST_CODEC.decode(buf);
                List<UUID> networks = UUID_LIST_CODEC.decode(buf);
                return new SyncPanelStatePacket(pos, tags, networks);
            }
            @Override
            public void encode(RegistryFriendlyByteBuf buf, SyncPanelStatePacket packet) {
                BlockPos.STREAM_CODEC.encode(buf, packet.pos());
                TAG_LIST_CODEC.encode(buf, packet.gaugeTags());
                UUID_LIST_CODEC.encode(buf, packet.networks());
            }
        };

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // This handler only runs on the client (playToClient packet).
    public static void handle(SyncPanelStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            if (!(mc.screen instanceof FactoryControllerScreen screen)) return;
            FactoryControllerMenu menu = screen.getMenu();
            if (!menu.controllerPos.equals(packet.pos())) return;

            menu.gauges.clear();
            for (CompoundTag tag : packet.gaugeTags())
                menu.gauges.add(VirtualPanelBehaviour.fromNBT(null, tag, mc.level.registryAccess()));
            menu.knownNetworks.clear();
            menu.knownNetworks.addAll(packet.networks());

            screen.onPanelSync();
        });
    }
}
