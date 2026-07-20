package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.gui.screen.PanelSyncListener;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettings;
import io.github.nbcss.createfactorycontroller.content.network.MissingLinkStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: the authoritative full board snapshot (components + wires + networks + header), stamped
 * with the controller's sync tokens. Sent by the menu-sync flush when a mutation escalated to
 * {@code syncEverything()} (including the {@link RequestPanelResyncPacket} self-heal path); routine changes
 * ride the much smaller {@link SyncPanelDeltaPacket} instead.
 */
public record SyncPanelStatePacket(BlockPos pos,
                                   int epoch,
                                   int revision,
                                   List<VirtualComponentBehaviour> components,
                                   List<Connection> connections,
                                   List<NetworkSettings> networks,
                                   List<MissingLinkStatus> missingLinks,
                                   String controllerName,
                                   boolean controllerPowered)
        implements CustomPacketPayload {

    public static final Type<SyncPanelStatePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "sync_panel_state"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<VirtualComponentBehaviour>> COMPONENT_LIST_CODEC =
        new StreamCodec<>() {
            @Override
            public @NotNull List<VirtualComponentBehaviour> decode(RegistryFriendlyByteBuf buf) {
                int size = buf.readVarInt();
                List<VirtualComponentBehaviour> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    VirtualComponentBehaviour c = ComponentRegistry.readComponent(buf);
                    if (c != null) list.add(c);
                }
                return list;
            }
            @Override
            public void encode(RegistryFriendlyByteBuf buf, List<VirtualComponentBehaviour> components) {
                buf.writeVarInt(components.size());
                for (VirtualComponentBehaviour c : components) ComponentRegistry.writeComponent(buf, c);
            }
        };

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Connection>> CONNECTION_LIST_CODEC =
        new StreamCodec<>() {
            @Override
            public @NotNull List<Connection> decode(RegistryFriendlyByteBuf buf) {
                int size = buf.readVarInt();
                List<Connection> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    Connection c = Connection.fromClient(buf);
                    if (c != null) list.add(c);
                }
                return list;
            }
            @Override
            public void encode(RegistryFriendlyByteBuf buf, List<Connection> connections) {
                buf.writeVarInt(connections.size());
                for (Connection c : connections) c.writeClient(buf);
            }
        };

    private static final StreamCodec<RegistryFriendlyByteBuf, List<NetworkSettings>> NETWORK_LIST_CODEC =
        NetworkSettings.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPanelStatePacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public @NotNull SyncPanelStatePacket decode(RegistryFriendlyByteBuf buf) {
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                int epoch = buf.readVarInt();
                int revision = buf.readVarInt();
                List<VirtualComponentBehaviour> components = COMPONENT_LIST_CODEC.decode(buf);
                List<Connection> connections = CONNECTION_LIST_CODEC.decode(buf);
                List<NetworkSettings> networks = NETWORK_LIST_CODEC.decode(buf);
                List<MissingLinkStatus> missingLinks = MissingLinkStatus.readList(buf);
                String controllerName = buf.readUtf();
                boolean controllerPowered = buf.readBoolean();
                return new SyncPanelStatePacket(pos, epoch, revision, components, connections, networks, missingLinks,
                        controllerName, controllerPowered);
            }
            @Override
            public void encode(RegistryFriendlyByteBuf buf, SyncPanelStatePacket packet) {
                BlockPos.STREAM_CODEC.encode(buf, packet.pos());
                buf.writeVarInt(packet.epoch());
                buf.writeVarInt(packet.revision());
                COMPONENT_LIST_CODEC.encode(buf, packet.components());
                CONNECTION_LIST_CODEC.encode(buf, packet.connections());
                NETWORK_LIST_CODEC.encode(buf, packet.networks());
                MissingLinkStatus.writeList(buf, packet.missingLinks());
                buf.writeUtf(packet.controllerName());
                buf.writeBoolean(packet.controllerPowered());
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

            menu.applyFullSync(packet.epoch(), packet.revision(), packet.components(), packet.connections(),
                    packet.networks(), packet.missingLinks(), packet.controllerName(), packet.controllerPowered());

            // Refresh whichever of this controller's screens is active — the controller canvas itself
            // or a sub-screen (set-item / configure-recipe) that renders the canvas as its background.
            // The sub-screens delegate to the parent so its cached gauge widgets are re-indexed too,
            // otherwise the background count overlay freezes while a sub-screen is open.
            if (mc.screen instanceof PanelSyncListener listener)
                listener.onPanelSync();
        });
    }
}
