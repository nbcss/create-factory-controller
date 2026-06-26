package io.github.nbcss.createfactorycontroller.content.component;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.utility.CreateLang;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionCapability;
import io.github.nbcss.createfactorycontroller.content.component.connection.RedstoneConnection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ValidationResult;

import net.createmod.catnip.data.Couple;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A Redstone Link placed on the controller board. It carries two frequency channels (Red/Blue, like Create's
 * link) and a Send/Receive flag ({@link #receive}), and registers itself on Create's redstone-link frequency
 * network as an {@link IRedstoneLinkable} so it interacts with in-world links within range (using the controller's
 * block pos).
 *
 * <p>Connections to gauges are stored on this link ({@code targetedBy[gaugePos]}, so they never count against a
 * gauge's 9-ingredient cap). In SEND mode the link is powered when ANY connected gauge has a target amount that is
 * currently in stock, and it broadcasts that power. In RECEIVE mode it is powered by the network and gates its
 * connected gauges' requests (handled in the controller pre-pass).</p>
 */
public class VirtualRedstoneLinkBehaviour extends AbstractVirtualComponent implements IRedstoneLinkable {

    public static final VirtualComponentBehaviour.Type TYPE = new VirtualComponentBehaviour.Type(){

        @Override
        public String id() {
            return "REDSTONE_LINK";
        }

        @Override
        public List<ResourceLocation> items() {
            return List.of(AllBlocks.REDSTONE_LINK.getId());
        }

        @Override
        public boolean isRequireNetwork() {
            return false;
        }

        @Override
        public VirtualComponentBehaviour create(FactoryControllerBlockEntity controller,
                                                VirtualComponentPosition pos,
                                                Item item,
                                                java.util.UUID networkId) {
            return new VirtualRedstoneLinkBehaviour(controller, pos, item);
        }

        @Override
        public VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                                 CompoundTag tag,
                                                 HolderLookup.Provider registries) {
            return VirtualRedstoneLinkBehaviour.fromNBT(controller, tag, registries);
        }
    };

    public static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "factory_controller/redstone_link");
    /** Power level broadcast by a powered SEND link. */
    private static final int TRANSMIT_STRENGTH = 15;

    /** {@code true} = RECEIVE (listener, gates gauges); {@code false} = SEND (transmitter, driven by gauges). */
    public boolean receive = false;
    public ItemStack redFreq = ItemStack.EMPTY;
    public ItemStack blueFreq = ItemStack.EMPTY;

    /** Computed each tick: SEND → driven by connected gauges; RECEIVE → driven by the network. Synced for overlays. */
    public boolean powered = false;
    /** Last power pushed to us by the redstone network (RECEIVE mode). Server-only. */
    private int receivedStrength = 0;
    /** True once registered on Create's link network (server), so (un)registration is idempotent. */
    private boolean registered = false;
    private int lastTransmitted = -1;

    public VirtualRedstoneLinkBehaviour(FactoryControllerBlockEntity controller, VirtualComponentPosition position,
                                        Item item) {
        super(controller, position, item);
    }

    @Override public ResourceLocation getTexture() { return TEXTURE; }

    /** A link speaks REDSTONE only; its mode is decisive for direction (RECEIVE drives gauges = SOURCE, SEND reads
     *  them = SINK), so a wired gauge follows the link. */
    @Override
    public java.util.List<ConnectionCapability> ports() {
        return java.util.List.of(new ConnectionCapability(Connection.Type.REDSTONE,
                receive ? ConnectionCapability.Role.SOURCE : ConnectionCapability.Role.SINK));
    }

    // Two links bridge each other wirelessly, so a board wire between links is meaningless — reject a link partner
    // (whichever role this link takes). Gauge partners are always allowed (uniqueness handles duplicates).
    @Override
    public ValidationResult validateAsSource(Connection.Type channel, VirtualComponentBehaviour sink) {
        if (channel == Connection.Type.REDSTONE && !receive) return fail();
        return sink instanceof VirtualRedstoneLinkBehaviour ? fail() : ValidationResult.SUCCESS;
    }

    @Override
    public ValidationResult validateAsSink(Connection.Type channel, VirtualComponentBehaviour source) {
        if (channel == Connection.Type.REDSTONE && receive) return fail();
        return source instanceof VirtualRedstoneLinkBehaviour ? fail() : ValidationResult.SUCCESS;
    }

    private static ValidationResult fail() {
        return ValidationResult.fail(() -> CreateLang.translate("factory_panel.connection_aborted")
                .style(ChatFormatting.WHITE).component());
    }

    // ── Power computation ──────────────────────────────────────────────────────

    /**
     * Recomputes overall power, then pushes any transmit change onto the network. SEND power is event-driven:
     * connected source components push their state into their {@link RedstoneConnection}, and the connection notifies
     * this redstone component as the sink. RECEIVE is updated live in {@link #setReceivedStrength}. Gauge↔redstone
     * component connections are stored on this component
     * ({@code link.targetedBy[gauge]}).
     */
    public void updatePower() {
        // The network handler self-pushes power only to real LinkBehaviours, never to our virtual link, so a receiver
        // must pull its own current power (covers joining an already-steady transmitter / registration ordering).
        if (receive) receivedStrength = pullNetworkPower();
        boolean changed = recomputePower();
        if (receive) pushNetworkPowerToConnections();
        if (changed && controller != null) { controller.setChanged(); controller.sendData(); }

        // Push transmit changes onto the network so receivers update (SEND links).
        int transmit = getTransmittedStrength();
        if (transmit != lastTransmitted) {
            lastTransmitted = transmit;
            notifyNetwork();
        }
    }

    /** The strongest in-range transmitted signal on this link's frequency network — mirrors the handler's
     *  {@code updateNetworkOf} power scan (which never targets our virtual link's own received strength). */
    private int pullNetworkPower() {
        Level level = controller == null ? null : controller.getLevel();
        if (level == null || level.isClientSide || !registered) return 0;
        int power = 0;
        for (IRedstoneLinkable other : handler().getNetworkOf(level, this))
            if (other != this && RedstoneLinkNetworkHandler.withinRange(this, other))
                power = Math.max(power, other.getTransmittedStrength());
        return power;
    }

    /** Recomputes {@link #powered}. Returns whether the redstone component's overall power changed.
     *  Does not touch the network (no transmit notify) — safe to call from the network's {@code setReceivedStrength}. */
    private boolean recomputePower() {
        boolean any = false;
        for (Connection conn : graph().incomingConnections(position, Connection.Type.REDSTONE))
            if (conn instanceof RedstoneConnection rc && rc.powered()) {
                any = true;
                break;
            }
        boolean now = receive ? receivedStrength > 0 : any;
        if (now == powered) return false;
        powered = now;
        return true;
    }

    private void pushNetworkPowerToConnections() {
        if (controller == null) return;
        boolean output = receive && powered;
        for (Connection conn : graph().outgoingConnections(position, Connection.Type.REDSTONE))
            if (conn instanceof RedstoneConnection rc)
                rc.setValue(output);
    }

    @Override
    public void onConnectAsSource(Connection conn) {
        if (conn instanceof RedstoneConnection)
            pushNetworkPowerToConnections();
    }

    @Override
    public void onConnectAsSink(Connection conn) {
        if (conn instanceof RedstoneConnection rc) {
            rc.setOnChanged(c -> recomputeIncomingRedstone());
            recomputeIncomingRedstone();
        }
    }

    @Override
    public void onDisconnectAsSink(Connection conn) {
        if (conn instanceof RedstoneConnection)
            recomputeIncomingRedstone();
    }

    private void recomputeIncomingRedstone() {
        if (receive) return;
        boolean changed = recomputePower();
        if (changed && controller != null) { controller.setChanged(); controller.sendData(); }
        updateTransmittedPower();
    }

    private void updateTransmittedPower() {
        int transmit = getTransmittedStrength();
        if (transmit == lastTransmitted) return;
        lastTransmitted = transmit;
        notifyNetwork();
    }

    @Override
    public void tick() {
        // Redstone-link power is refreshed on the controller's lazy tick (see updatePower), not every tick.
    }

    // ── Frequencies / mode ─────────────────────────────────────────────────────

    /**
     * Applies a full link configuration in one shot — Send/Receive mode plus both type frequencies. Frequency
     * items are stored count-1 and never consumed; the network is re-keyed only if a frequency actually changed.
     * Drives the redstone-link GUI / per-click / R-toggle interactions (each resends the unchanged fields).
     */
    public void configure(boolean receive, ItemStack red, ItemStack blue) {
        ItemStack r = red.copy();  r.setCount(1);
        ItemStack b = blue.copy(); b.setCount(1);
        boolean freqChanged = !ItemStack.isSameItemSameComponents(r, redFreq)
                           || !ItemStack.isSameItemSameComponents(b, blueFreq);
        boolean modeChanged = this.receive != receive;
        if (!freqChanged && !modeChanged) return;

        if (freqChanged) removeFromNetwork();   // network membership is keyed by frequency → leave with the old key
        redFreq = r;
        blueFreq = b;
        this.receive = receive;
        lastTransmitted = -1;                    // force a transmit re-evaluation
        if (modeChanged) reorientRedstoneConnections();
        if (freqChanged) addToNetwork();         // rejoin with the new key

        // A config change takes effect immediately, not on the next lazy tick.
        updatePower();
        pushNetworkPowerToConnections();
        for (Connection conn : graph().incomingConnections(position, Connection.Type.REDSTONE))
            if (controller != null && controller.components.get(conn.from) instanceof VirtualGaugeBehaviour gauge)
                gauge.publishRedstoneOutput();
        if (controller != null) { controller.setChanged(); controller.sendData(); }
    }

    private void reorientRedstoneConnections() {
        java.util.List<Connection> redstone = new java.util.ArrayList<>();
        redstone.addAll(graph().incomingConnections(position, Connection.Type.REDSTONE));
        redstone.addAll(graph().outgoingConnections(position, Connection.Type.REDSTONE));
        for (Connection conn : redstone) {
            if (position.equals(conn.from) == receive) continue;

            VirtualComponentBehaviour oldSource = siblingAt(conn.from);
            VirtualComponentBehaviour oldSink = siblingAt(conn.to);

            graph().reverse(conn);

            if (oldSource != null) oldSource.onDisconnectAsSource(conn);
            if (oldSink != null) oldSink.onDisconnectAsSink(conn);

            VirtualComponentBehaviour newSource = siblingAt(conn.from);
            VirtualComponentBehaviour newSink = siblingAt(conn.to);
            if (newSink != null) newSink.onConnectAsSink(conn);
            if (newSource != null) newSource.onConnectAsSource(conn);
        }
    }

    /** The interact (R) key on the board: toggle Send/Receive, keeping the current frequencies. */
    @Override
    public void onInteract() {
        configure(!receive, redFreq, blueFreq);
        if (controller != null)
            controller.playWrenchRotateSound();
    }

    // ── IRedstoneLinkable ──────────────────────────────────────────────────────

    @Override public boolean isListening() { return receive; }
    @Override public int getTransmittedStrength() { return !receive && powered ? TRANSMIT_STRENGTH : 0; }

    /**
     * The redstone network's event hook: called whenever this receiver's incoming power changes. Updates the gate on
     * connected gauges <b>immediately</b> (real-time), rather than waiting for the lazy-tick poll. Never re-notifies
     * the network (a receiver transmits nothing), so it's safe to run mid network update.
     */
    @Override
    public void setReceivedStrength(int networkPower) {
        if (this.receivedStrength == networkPower) return;
        this.receivedStrength = networkPower;
        if (!receive) return;
        boolean changed = recomputePower();
        pushNetworkPowerToConnections();
        if (changed && controller != null) { controller.setChanged(); controller.sendData(); }
    }

    @Override
    public boolean isAlive() {
        return controller != null && !controller.isRemoved()
            && controller.components.get(position) == this && controller.getLevel() != null;
    }

    @Override
    public Couple<Frequency> getNetworkKey() {
        return Couple.create(Frequency.of(redFreq), Frequency.of(blueFreq));
    }

    @Override
    public BlockPos getLocation() {
        return controller == null ? BlockPos.ZERO : controller.getBlockPos();
    }

    // ── Network lifecycle (server only) ────────────────────────────────────────

    private RedstoneLinkNetworkHandler handler() { return Create.REDSTONE_LINK_NETWORK_HANDLER; }

    public void addToNetwork() {
        Level level = controller == null ? null : controller.getLevel();
        if (level == null || level.isClientSide || registered) return;
        handler().addToNetwork(level, this);
        registered = true;
    }

    public void removeFromNetwork() {
        Level level = controller == null ? null : controller.getLevel();
        if (level == null || level.isClientSide || !registered) return;
        handler().removeFromNetwork(level, this);
        registered = false;
    }

    private void notifyNetwork() {
        Level level = controller == null ? null : controller.getLevel();
        if (level == null || level.isClientSide || !registered) return;
        handler().updateNetworkOf(level, this);
    }

    // ── NBT ────────────────────────────────────────────────────────────────────


    @Override
    public CompoundTag toNBT(HolderLookup.Provider registries, NbtProfile profile) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", TYPE.id());
        tag.put("Pos", position.toNBT());
        tag.putString("Item", getItemId().toString());
        tag.putBoolean("Receive", receive);
        tag.put("RedFreq", redFreq.saveOptional(registries));
        tag.put("BlueFreq", blueFreq.saveOptional(registries));
        if (profile.includesRuntime())
            tag.putBoolean("Powered", powered);
        // Connections live in the controller's central graph (written there), not per-component.
        return tag;
    }

    public static VirtualRedstoneLinkBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                                       CompoundTag tag, HolderLookup.Provider registries) {
        VirtualComponentPosition pos = VirtualComponentPosition.fromNBT(tag.getCompound("Pos"));
        ResourceLocation itemId = ResourceLocation.parse(tag.getString("Item"));
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(itemId);
        VirtualRedstoneLinkBehaviour b = new VirtualRedstoneLinkBehaviour(controller, pos, item);
        b.receive = tag.getBoolean("Receive");
        b.redFreq = ItemStack.parseOptional(registries, tag.getCompound("RedFreq"));
        b.blueFreq = ItemStack.parseOptional(registries, tag.getCompound("BlueFreq"));
        b.powered = tag.getBoolean("Powered");
        // Connections are loaded centrally by the controller / menu, not from the component tag.
        return b;
    }
}
