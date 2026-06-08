package io.github.nbcss.content.factorycontroller.packet;

import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.ComponentRegistry;
import io.github.nbcss.content.factorycontroller.FactoryControllerMenu;
import io.github.nbcss.content.factorycontroller.gui.FactoryControllerScreen;
import io.github.nbcss.content.factorycontroller.VirtualComponentBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server → client: full gauge + network snapshot so the open screen stays live.
 * Sent by FactoryControllerBlockEntity.sendData() to any player who has this menu open.
 */
public record SyncPanelStatePacket(BlockPos pos,
                                   List<CompoundTag> gaugeTags,
                                   List<UUID> networks,
                                   List<String> networkNames,
                                   String controllerName)
        implements CustomPacketPayload {

    public static final Type<SyncPanelStatePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "sync_panel_state"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<CompoundTag>> TAG_LIST_CODEC =
        new StreamCodec<>() {
            @Override
            public @NotNull List<CompoundTag> decode(RegistryFriendlyByteBuf buf) {
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
            public @NotNull List<UUID> decode(RegistryFriendlyByteBuf buf) {
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

    private static final StreamCodec<RegistryFriendlyByteBuf, List<String>> STRING_LIST_CODEC =
        new StreamCodec<>() {
            @Override
            public @NotNull List<String> decode(RegistryFriendlyByteBuf buf) {
                int size = buf.readVarInt();
                List<String> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) list.add(buf.readUtf());
                return list;
            }
            @Override
            public void encode(RegistryFriendlyByteBuf buf, List<String> names) {
                buf.writeVarInt(names.size());
                for (String s : names) buf.writeUtf(s);
            }
        };

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPanelStatePacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public @NotNull SyncPanelStatePacket decode(RegistryFriendlyByteBuf buf) {
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                List<CompoundTag> tags = TAG_LIST_CODEC.decode(buf);
                List<UUID> networks = UUID_LIST_CODEC.decode(buf);
                List<String> names = STRING_LIST_CODEC.decode(buf);
                String controllerName = buf.readUtf();
                return new SyncPanelStatePacket(pos, tags, networks, names, controllerName);
            }
            @Override
            public void encode(RegistryFriendlyByteBuf buf, SyncPanelStatePacket packet) {
                BlockPos.STREAM_CODEC.encode(buf, packet.pos());
                TAG_LIST_CODEC.encode(buf, packet.gaugeTags());
                UUID_LIST_CODEC.encode(buf, packet.networks());
                STRING_LIST_CODEC.encode(buf, packet.networkNames());
                buf.writeUtf(packet.controllerName());
            }
        };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    // This handler only runs on the client (playToClient packet).
    public static void handle(SyncPanelStatePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            // Update the shared menu no matter which screen of this controller is open (controller,
            // set-item, or configure-recipe) so live state — promise/stock counts etc. — stays current.
            if (!(mc.player.containerMenu instanceof FactoryControllerMenu menu)) return;
            if (!menu.controllerPos.equals(packet.pos())) return;

            menu.clearComponents();
            for (CompoundTag tag : packet.gaugeTags()) {
                VirtualComponentBehaviour b = ComponentRegistry.fromNBT(null, tag, mc.level.registryAccess());
                if (b != null) menu.addComponent(b);
            }
            menu.knownNetworks.clear();
            menu.knownNetworks.addAll(packet.networks());
            menu.networkNicknames.clear();
            List<UUID> nets = packet.networks();
            List<String> names = packet.networkNames();
            for (int i = 0; i < nets.size() && i < names.size(); i++)
                if (!names.get(i).isBlank()) menu.networkNicknames.put(nets.get(i), names.get(i));
            menu.controllerName = packet.controllerName();

            if (mc.screen instanceof FactoryControllerScreen screen)
                screen.onPanelSync();
        });
    }
}
