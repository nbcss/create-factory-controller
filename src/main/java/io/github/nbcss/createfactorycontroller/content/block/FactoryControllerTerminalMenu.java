package io.github.nbcss.createfactorycontroller.content.block;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.ServerConfig;
import io.github.nbcss.createfactorycontroller.content.helper.ControllerAccessor;
import io.github.nbcss.createfactorycontroller.content.item.terminal.RemoteControllerLink;
import io.github.nbcss.createfactorycontroller.content.item.FactoryControllerTerminalItem;
import io.github.nbcss.createfactorycontroller.content.item.terminal.TerminalLinks;
import io.github.nbcss.createfactorycontroller.content.packet.TerminalStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The menu behind a Factory Controller Terminal.
 */
public class FactoryControllerTerminalMenu extends FactoryControllerMenu {
    /** Linked controllers (synced) and the selected index. */
    public List<RemoteControllerLink> links;
    public int activeIndex;
    /** True when the selected controller is live and drivable. */
    public boolean signalPresent;
    /** Mirror of {@link ServerConfig#allowComponentPlacementInTerminal()}. */
    public final boolean placementAllowed;

    /** Dimension of the selected controller (both sides), even when it is not live. */
    @Nullable private ResourceKey<Level> activeDimension;

    // Server-side only
    @Nullable private final ServerPlayer owner;
    @Nullable private final ItemStack remoteItem;
    @Nullable private ServerLevel controllerLevel;

    /** Server-side resolution of a terminal's selected link. {@code be} is null when it is not live. */
    public record Target(@Nullable RemoteControllerLink link,
                         @Nullable ServerLevel level,
                         @Nullable FactoryControllerBlockEntity be) {

        public static Target resolve(MinecraftServer server, TerminalLinks state) {
            RemoteControllerLink link = state.active();
            if (link == null) return new Target(null, null, null);
            ServerLevel level = server.getLevel(link.dimension());
            return new Target(link, level, ControllerAccessor.getLiveBlockEntity(level, link.pos()));
        }

        public BlockPos pos() {
            return link == null ? BlockPos.ZERO : link.pos();
        }

        @Nullable
        public ResourceKey<Level> dimension() {
            return link == null ? null : link.dimension();
        }
    }

    /** Server-side constructor */
    public FactoryControllerTerminalMenu(int syncId, Inventory inv, ServerPlayer owner, TerminalMenuProvider p) {
        super(CreateFactoryController.FACTORY_CONTROLLER_TERMINAL_MENU.get(), syncId, inv,
            p.target().be(), p.target().pos());
        this.owner = owner;
        this.remoteItem = p.terminal();
        this.placementAllowed = ServerConfig.allowComponentPlacementInTerminal();
        applyServerTarget(p.state(), p.target());
    }

    /** Client-side constructor */
    public FactoryControllerTerminalMenu(int syncId, Inventory inv, RegistryFriendlyByteBuf buf) {
        super(CreateFactoryController.FACTORY_CONTROLLER_TERMINAL_MENU.get(), syncId, inv, buf);
        int n = buf.readVarInt();
        List<RemoteControllerLink> read = new ArrayList<>(n);
        for (int i = 0; i < n; i++) read.add(RemoteControllerLink.STREAM_CODEC.decode(buf));
        this.links = List.copyOf(read);
        this.activeIndex = buf.readVarInt();
        this.signalPresent = buf.readBoolean();
        this.placementAllowed = buf.readBoolean();
        this.activeDimension = links.isEmpty() ? null : links.get(activeIndex).dimension();
        this.owner = null;
        this.remoteItem = null;
    }

    /** Writes the terminal's extra menu-open data: the empty board header + terminal fields. */
    public static void writeExtraData(TerminalMenuProvider p, RegistryFriendlyByteBuf buf) {
        FactoryControllerBlockEntity be = p.target().be();
        writeEmptyBoardLayout(buf, p.target().pos(), be != null ? be.syncEpoch() : 0, be != null ? be.syncRevision() : 0,
            displayName(be, p.target().link()), be != null && be.isRedstonePowered());
        buf.writeVarInt(p.state().links().size());
        for (RemoteControllerLink link : p.state().links()) RemoteControllerLink.STREAM_CODEC.encode(buf, link);
        buf.writeVarInt(p.state().activeIndex());
        buf.writeBoolean(be != null);
        buf.writeBoolean(ServerConfig.allowComponentPlacementInTerminal());
    }

    /** The live controller's name, or (with no signal) the selected link's last-known name. */
    private static String displayName(@Nullable FactoryControllerBlockEntity be, @Nullable RemoteControllerLink link) {
        return be != null ? be.customName : link != null ? link.cachedName() : "";
    }

    /** Stores the live controller's current name on the terminal item so tabs/HUD can show it while unloaded. */
    public static TerminalLinks rememberName(ItemStack terminal, TerminalLinks state, Target target) {
        if (target.be() == null) return state;
        TerminalLinks refreshed = state.withActiveName(target.be().customName);
        if (refreshed != state) terminal.set(CreateFactoryController.TERMINAL_LINKS.get(), refreshed);
        return refreshed;
    }

    // ── Server-side target tracking ────────────────────────────────────────

    private void applyServerTarget(TerminalLinks state, Target target) {
        links = state.links();
        activeIndex = state.activeIndex();
        controllerPos = target.pos();
        activeDimension = target.dimension();
        controllerLevel = target.level();
        blockEntity = target.be();
        signalPresent = target.be() != null;
    }

    public void updateControllerPage() {
        if (owner == null || remoteItem == null) return;
        TerminalLinks state = FactoryControllerTerminalItem.linksOf(remoteItem);
        Target target = Target.resolve(owner.server, state);
        applyServerTarget(rememberName(remoteItem, state, target), target);
        sendState(true);
        if (target.be() != null) target.be().syncEverything();
    }

    /** Called every server tick while open: detects the selected controller going live or dropping out
     *  (unloaded, left block-ticking range, broken — or replaced by a new instance) and pushes the change. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (owner == null || remoteItem == null || links.isEmpty()) return;
        FactoryControllerBlockEntity be = ControllerAccessor.getLiveBlockEntity(controllerLevel, controllerPos);
        if (be == blockEntity) {
            // Follow renames on the item's cached name, so tabs/HUD still show it once the controller unloads.
            if (be != null && !be.customName.equals(links.get(activeIndex).cachedName()))
                links = rememberName(remoteItem, FactoryControllerTerminalItem.linksOf(remoteItem),
                    new Target(links.get(activeIndex), controllerLevel, be)).links();
            return;
        }
        blockEntity = be;
        signalPresent = be != null;
        if (be != null)
            links = rememberName(remoteItem, FactoryControllerTerminalItem.linksOf(remoteItem),
                new Target(links.get(activeIndex), controllerLevel, be)).links();
        sendState(false);
        if (be != null) be.syncEverything();
    }

    private void sendState(boolean retarget) {
        if (owner == null) return;
        RemoteControllerLink link = links.isEmpty() ? null : links.get(activeIndex);
        PacketDistributor.sendToPlayer(owner, new TerminalStatePacket(containerId, links, activeIndex,
            controllerPos, Optional.ofNullable(activeDimension), signalPresent,
            displayName(blockEntity, link), retarget));
    }

    // ── Client-side state apply ────────────────────────────────────────────

    /** Applies a server state push */
    public void applyState(TerminalStatePacket state) {
        links = state.links();
        activeIndex = state.activeIndex();
        controllerPos = state.pos();
        activeDimension = state.dimension().orElse(null);
        signalPresent = state.signal();
        controllerName = state.name();
        controllerPowered = false;
        clearComponents();
        applyNetworkSettings(List.of());
        applyMissingLinkStatuses(List.of());
        syncEpoch = -1;
        syncRevision = -1;
        resyncPending = signalPresent;
    }

    // ── Accessors ──────────────────────────────────────────────────────────

    @Override
    public boolean hasSignal() {
        return signalPresent;
    }

    @Override
    @Nullable
    public ResourceKey<Level> activeControllerDimension() {
        return activeDimension;
    }

    /** Server-side level of the selected controller (for {@code ControllerAccess} packet routing). */
    @Nullable
    public ServerLevel activeControllerLevel() {
        return controllerLevel;
    }

    /** The terminal stack this menu was opened from, if the player still holds it in either hand (server-side). */
    @Nullable
    public ItemStack heldTerminal(Player player) {
        if (remoteItem == null || !(remoteItem.getItem() instanceof FactoryControllerTerminalItem)) return null;
        return player.getMainHandItem() == remoteItem || player.getOffhandItem() == remoteItem
            ? remoteItem : null;
    }

    /** A terminal has no world position to stand near; it stays valid while the player still holds that terminal. */
    @Override
    public boolean stillValid(@NotNull Player player) {
        return heldTerminal(player) != null;
    }
}
