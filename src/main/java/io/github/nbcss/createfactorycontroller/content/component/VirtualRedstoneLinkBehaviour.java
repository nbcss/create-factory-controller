package io.github.nbcss.createfactorycontroller.content.component;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.VirtualPanelConnection;
import io.github.nbcss.createfactorycontroller.content.VirtualPanelPosition;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

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

    public static final ResourceLocation TYPE_ID =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "redstone_link");
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

    public VirtualRedstoneLinkBehaviour(FactoryControllerBlockEntity controller, VirtualPanelPosition position,
                                        ResourceLocation itemId) {
        super(controller, position, itemId);
    }

    @Override public ResourceLocation getTypeId() { return TYPE_ID; }
    @Override public ResourceLocation getTexture() { return TEXTURE; }

    // A link holds all its connected gauges (link-side storage) and inherits the uncapped base addConnection.
    @Override
    protected VirtualPanelConnection createConnection(VirtualPanelPosition from, VirtualComponentBehaviour source) {
        return new RedstoneConnection(from);
    }

    // ── Power computation ──────────────────────────────────────────────────────

    /**
     * Recomputes per-connection and overall power, then pushes any transmit change onto the network. Driven from the
     * controller's <b>lazy tick</b> for the SEND side (it depends on connected gauges' stock, which isn't an event);
     * the RECEIVE side is updated live in {@link #setReceivedStrength}. RECEIVE: every connection (and the link) is
     * powered by the network. SEND: each connection tracks its own gauge driving it ({@code gauge.powersLink()}), and
     * the link is powered if ANY connection is. Gauge↔link connections are stored on the link
     * ({@code link.targetedBy[gauge]}).
     */
    public void updatePower() {
        // The network handler self-pushes power only to real LinkBehaviours, never to our virtual link, so a receiver
        // must pull its own current power (covers joining an already-steady transmitter / registration ordering).
        if (receive) receivedStrength = pullNetworkPower();
        boolean changed = recomputePower();
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

    /** Recomputes {@link #powered} + each connection's powered flag. Returns whether the link's overall power changed.
     *  Does not touch the network (no transmit notify) — safe to call from the network's {@code setReceivedStrength}. */
    private boolean recomputePower() {
        boolean changed = false;
        boolean any = false;
        for (VirtualPanelPosition gaugePos : targetedBy.keySet()) {
            if (!(targetedBy.get(gaugePos) instanceof RedstoneConnection rc)) continue;
            boolean connPowered = receive
                ? receivedStrength > 0
                : controller != null
                    && controller.components.get(gaugePos) instanceof VirtualGaugeBehaviour g && g.powersLink();
            if (connPowered) any = true;
            if (rc.powered != connPowered) { rc.powered = connPowered; changed = true; }
        }
        boolean now = receive ? receivedStrength > 0 : any;
        if (now != powered) { powered = now; changed = true; }
        return changed;
    }

    /** Re-applies the redstone gate ({@code redstonePowered}) to every gauge wired to this link. */
    private void refreshGauges() {
        if (controller == null) return;
        for (VirtualPanelPosition gaugePos : targetedBy.keySet())
            if (controller.components.get(gaugePos) instanceof VirtualGaugeBehaviour g)
                g.redstonePowered = g.isGatedByLink();
    }

    /** Whether this link is a powered receiver (so it gates the gauges wired to it). */
    public boolean gatesGauge() {
        return receive && powered;
    }

    @Override
    public void tick() {
        // Redstone-link power is refreshed on the controller's lazy tick (see updatePower), not every tick.
    }

    // ── Frequencies / mode ─────────────────────────────────────────────────────

    /**
     * Applies a full link configuration in one shot — Send/Receive mode plus both channel frequencies. Frequency
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
        if (freqChanged) addToNetwork();         // rejoin with the new key

        // A config change takes effect immediately, not on the next lazy tick: recompute power (which also re-notifies
        // the network so receivers update) and refresh the redstone gate on every gauge wired to this link.
        updatePower();
        refreshGauges();
        if (controller != null) { controller.setChanged(); controller.sendData(); }
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
        refreshGauges();
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
    public CompoundTag toItemNBT(HolderLookup.Provider registries) {
        CompoundTag tag = super.toItemNBT(registries);
        tag.remove("Powered");
        return tag;
    }

    @Override
    public CompoundTag toNBT(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", getTypeId().toString());
        tag.put("Pos", position.toNBT());
        tag.putString("LinkItem", itemId.toString());
        tag.putBoolean("Receive", receive);
        tag.put("RedFreq", redFreq.saveOptional(registries));
        tag.put("BlueFreq", blueFreq.saveOptional(registries));
        tag.putBoolean("Powered", powered);

        ListTag targetedByList = new ListTag();
        for (VirtualPanelConnection conn : targetedBy.values())
            targetedByList.add(conn.toNBT());
        tag.put("TargetedBy", targetedByList);

        ListTag targetingList = new ListTag();
        for (VirtualPanelPosition pos : targeting)
            targetingList.add(pos.toNBT());
        tag.put("Targeting", targetingList);
        return tag;
    }

    @Override
    public CompoundTag toClientNBT(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", getTypeId().toString());
        tag.put("Pos", position.toNBT());
        tag.putString("LinkItem", itemId.toString());
        tag.putBoolean("Receive", receive);
        tag.put("RedFreq", redFreq.saveOptional(registries));
        tag.put("BlueFreq", blueFreq.saveOptional(registries));
        tag.putBoolean("Powered", powered);

        ListTag targetedByList = new ListTag();
        for (VirtualPanelConnection conn : targetedBy.values())
            targetedByList.add(conn.toNBT());
        tag.put("TargetedBy", targetedByList);
        return tag;
    }

    public static VirtualRedstoneLinkBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                                       CompoundTag tag, HolderLookup.Provider registries) {
        VirtualPanelPosition pos = VirtualPanelPosition.fromNBT(tag.getCompound("Pos"));
        ResourceLocation itemId = ResourceLocation.parse(tag.getString("LinkItem"));
        VirtualRedstoneLinkBehaviour b = new VirtualRedstoneLinkBehaviour(controller, pos, itemId);
        b.receive = tag.getBoolean("Receive");
        b.redFreq = ItemStack.parseOptional(registries, tag.getCompound("RedFreq"));
        b.blueFreq = ItemStack.parseOptional(registries, tag.getCompound("BlueFreq"));
        b.powered = tag.getBoolean("Powered");

        ListTag targetedByList = tag.getList("TargetedBy", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < targetedByList.size(); i++) {
            RedstoneConnection conn = RedstoneConnection.fromNBT(targetedByList.getCompound(i));
            b.targetedBy.put(conn.from, conn);
        }
        ListTag targetingList = tag.getList("Targeting", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < targetingList.size(); i++)
            b.targeting.add(VirtualPanelPosition.fromNBT(targetingList.getCompound(i)));
        return b;
    }
}
