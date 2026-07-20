package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry;
import io.github.nbcss.createfactorycontroller.content.component.SyncCodecs;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionKey;
import io.github.nbcss.createfactorycontroller.content.gui.screen.PanelSyncListener;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettings;
import io.github.nbcss.createfactorycontroller.content.network.MissingLinkStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Server → client: one tick's worth of board changes for an open controller menu — the routine replacement
 * for re-sending the whole board ({@link SyncPanelStatePacket}, now reserved for escalations/resyncs).
 *
 * <p><b>Versioning.</b> Stamped with the BE's {@code (epoch, baseRevision → newRevision)}. The client
 * applies a delta only when the epoch matches and {@code baseRevision} equals its current revision;
 * anything else (BE reloaded, gap, unparseable entry) triggers ONE {@link RequestPanelResyncPacket} and the
 * server answers with the authoritative full snapshot — every protocol fault self-heals.</p>
 *
 * <p><b>Bodies are captured bytes.</b> Component/connection payloads are opaque {@code byte[]}s produced by
 * {@link SyncCodecs#capture} on the server thread at flush time: network-thread encoding never touches live
 * mutable state, and each body's length frames it so the client can skip one bad entry instead of
 * desyncing the buffer. A FULL body is {@code ComponentRegistry.writeComponent} (type id + everything); a
 * STATE body is {@code pos + writeClientState} and mutates the client's existing instance in place.</p>
 *
 * <p><b>Apply order</b> (fixed, makes remove+re-add and remove-with-wires atomic within one packet):
 * connection removals → component removals → component upserts → connection upserts → header/networks/diagnostics →
 * re-fold sinks whose incoming wires changed → {@link PanelSyncListener#onPanelSync()}.</p>
 */
public record SyncPanelDeltaPacket(BlockPos pos,
                                   int epoch,
                                   int baseRevision,
                                   int newRevision,
                                   List<VirtualComponentPosition> removedComponents,
                                   List<ConnectionKey> removedConnections,
                                   List<ComponentUpsert> componentUpserts,
                                   List<byte[]> connectionUpserts,
                                   @Nullable List<NetworkSettings> networks,
                                   @Nullable List<MissingLinkStatus> missingLinks,
                                   @Nullable HeaderState header)
        implements CustomPacketPayload {

    /** One component change: the FULL body ({@code writeComponent}) or the STATE tail ({@code pos + writeClientState}). */
    public record ComponentUpsert(boolean full, byte[] body) {}

    /** Controller name + redstone power, sent together whenever either changed (both are tiny). */
    public record HeaderState(String name, boolean powered) {}

    public static final Type<SyncPanelDeltaPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "sync_panel_delta"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<NetworkSettings>> NETWORK_LIST_CODEC =
        NetworkSettings.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPanelDeltaPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public @NotNull SyncPanelDeltaPacket decode(RegistryFriendlyByteBuf buf) {
                BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                int epoch = buf.readVarInt();
                int baseRevision = buf.readVarInt();
                int newRevision = buf.readVarInt();
                int n = buf.readVarInt();
                List<VirtualComponentPosition> removedComponents = new ArrayList<>(n);
                for (int i = 0; i < n; i++) removedComponents.add(SyncCodecs.readPos(buf));
                n = buf.readVarInt();
                List<ConnectionKey> removedConnections = new ArrayList<>(n);
                for (int i = 0; i < n; i++) removedConnections.add(ConnectionKey.read(buf));
                n = buf.readVarInt();
                List<ComponentUpsert> componentUpserts = new ArrayList<>(n);
                for (int i = 0; i < n; i++) componentUpserts.add(new ComponentUpsert(buf.readBoolean(), buf.readByteArray()));
                n = buf.readVarInt();
                List<byte[]> connectionUpserts = new ArrayList<>(n);
                for (int i = 0; i < n; i++) connectionUpserts.add(buf.readByteArray());
                List<NetworkSettings> networks = buf.readBoolean() ? NETWORK_LIST_CODEC.decode(buf) : null;
                List<MissingLinkStatus> missingLinks = buf.readBoolean() ? MissingLinkStatus.readList(buf) : null;
                HeaderState header = buf.readBoolean() ? new HeaderState(buf.readUtf(), buf.readBoolean()) : null;
                return new SyncPanelDeltaPacket(pos, epoch, baseRevision, newRevision, removedComponents,
                        removedConnections, componentUpserts, connectionUpserts, networks, missingLinks, header);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, SyncPanelDeltaPacket packet) {
                BlockPos.STREAM_CODEC.encode(buf, packet.pos());
                buf.writeVarInt(packet.epoch());
                buf.writeVarInt(packet.baseRevision());
                buf.writeVarInt(packet.newRevision());
                buf.writeVarInt(packet.removedComponents().size());
                for (VirtualComponentPosition pos : packet.removedComponents()) SyncCodecs.writePos(buf, pos);
                buf.writeVarInt(packet.removedConnections().size());
                for (ConnectionKey key : packet.removedConnections()) key.write(buf);
                buf.writeVarInt(packet.componentUpserts().size());
                for (ComponentUpsert upsert : packet.componentUpserts()) {
                    buf.writeBoolean(upsert.full());
                    buf.writeByteArray(upsert.body());
                }
                buf.writeVarInt(packet.connectionUpserts().size());
                for (byte[] body : packet.connectionUpserts()) buf.writeByteArray(body);
                buf.writeBoolean(packet.networks() != null);
                if (packet.networks() != null) NETWORK_LIST_CODEC.encode(buf, packet.networks());
                buf.writeBoolean(packet.missingLinks() != null);
                if (packet.missingLinks() != null) MissingLinkStatus.writeList(buf, packet.missingLinks());
                buf.writeBoolean(packet.header() != null);
                if (packet.header() != null) {
                    buf.writeUtf(packet.header().name());
                    buf.writeBoolean(packet.header().powered());
                }
            }
        };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** A sink whose incoming wires changed this delta — re-folded once after everything applied. */
    private record Refold(VirtualComponentPosition sink, Connection.Type type) {}

    // This handler only runs on the client (playToClient packet).
    public static void handle(SyncPanelDeltaPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            if (!(mc.player.containerMenu instanceof FactoryControllerMenu menu)) return;
            if (!menu.controllerPos.equals(packet.pos())) return;

            // Not the state this delta was built on (BE reload, missed packet) → full resync instead.
            if (packet.epoch() != menu.syncEpoch || packet.baseRevision() != menu.syncRevision) {
                requestResync(menu);
                return;
            }

            RegistryAccess registries = mc.level.registryAccess();
            Set<Refold> refolds = new LinkedHashSet<>();
            // A body we couldn't parse/apply is skipped (its length frames it); the board may then be
            // missing one item, so finish applying the rest and ask for the authoritative snapshot.
            boolean broken = false;

            for (ConnectionKey key : packet.removedConnections()) {
                Connection old = menu.connectionAt(key.from(), key.to());
                if (old == null) continue;   // e.g. already dropped by a component removal — fine
                menu.removeConnectionAt(key.from(), key.to());
                refolds.add(new Refold(key.to(), old.type));
            }

            for (VirtualComponentPosition pos : packet.removedComponents())
                menu.removeComponentAt(pos);

            for (ComponentUpsert upsert : packet.componentUpserts()) {
                try {
                    RegistryFriendlyByteBuf body = SyncCodecs.wrap(upsert.body(), registries);
                    if (upsert.full()) {
                        VirtualComponentBehaviour component = ComponentRegistry.readComponent(body);
                        if (component == null) broken = true;   // unknown type — skip, resync below
                        else menu.upsertComponent(component);
                    } else {
                        VirtualComponentBehaviour existing = menu.componentAt(SyncCodecs.readPos(body));
                        if (existing == null) broken = true;    // STATE for a component we don't have
                        else existing.readClientState(body);
                    }
                } catch (Exception e) {
                    broken = true;
                }
            }

            for (byte[] body : packet.connectionUpserts()) {
                try {
                    Connection conn = Connection.fromClient(SyncCodecs.wrap(body, registries));
                    if (conn == null) { broken = true; continue; }
                    menu.putConnection(conn);
                    refolds.add(new Refold(conn.to, conn.type));
                } catch (Exception e) {
                    broken = true;
                }
            }

            if (packet.networks() != null)
                menu.applyNetworkSettings(packet.networks());
            if (packet.missingLinks() != null)
                menu.applyMissingLinkStatuses(packet.missingLinks());
            if (packet.header() != null) {
                menu.controllerName = packet.header().name();
                menu.controllerPowered = packet.header().powered();
            }

            for (Refold refold : refolds)
                menu.refoldSink(refold.sink(), refold.type());

            // Advance even when broken: most of the delta applied, and the requested snapshot supersedes
            // everything anyway — this just keeps follow-up deltas applying instead of double-requesting.
            menu.syncRevision = packet.newRevision();

            if (mc.screen instanceof PanelSyncListener listener)
                listener.onPanelSync();

            if (broken)
                requestResync(menu);
        });
    }

    private static void requestResync(FactoryControllerMenu menu) {
        if (menu.resyncPending) return;   // one request per fault burst; the snapshot clears the flag
        menu.resyncPending = true;
        PacketDistributor.sendToServer(new RequestPanelResyncPacket(menu.controllerPos));
    }
}
