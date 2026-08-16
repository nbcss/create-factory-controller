package io.github.nbcss.createfactorycontroller.content.component;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.AllBlocks;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionCapability;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionKey;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionResolver;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionValue;
import io.github.nbcss.createfactorycontroller.content.component.connection.NumberConnection;
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
 * A Redstone Link placed on the controller board. It carries two frequency keys (Red/Blue, like Create's
 * link) and a Send/Receive flag ({@link #receive}), and registers itself on Create's redstone-link frequency
 * network as an {@link IRedstoneLinkable} so it interacts with in-world links within range (using the controller's
 * block pos).
 *
 * <p>A link speaks only the REDSTONE type, so its wires never count against a gauge's 9-ingredient cap. In SEND
 * mode the link is the wire's sink: it is powered when ANY connected gauge has a target amount currently in stock,
 * and broadcasts that power. In RECEIVE mode it is the wire's source: powered by the network, it gates its connected
 * gauges' requests (handled in the controller pre-pass). The wires themselves live in the controller's central
 * {@link io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionGraph}, not on the link.</p>
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

        @Override public int color() { return 0xFC8068; }

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

        @Override
        public VirtualComponentBehaviour fromClient(net.minecraft.network.RegistryFriendlyByteBuf buf) {
            VirtualComponentPosition pos = SyncCodecs.readPos(buf);
            Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(buf.readResourceLocation());
            VirtualRedstoneLinkBehaviour b = new VirtualRedstoneLinkBehaviour(null, pos, item);
            b.receive = buf.readBoolean();
            b.redFreq = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            b.blueFreq = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            b.readClientState(buf);
            return b;
        }
    };

    @Override public String typeId() { return TYPE.id(); }

    @Override
    public void writeClient(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        SyncCodecs.writePos(buf, position);
        buf.writeResourceLocation(getItemId());
        buf.writeBoolean(receive);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, redFreq);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, blueFreq);
        writeClientState(buf);
    }

    @Override
    public void writeClientState(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(strength);
    }

    @Override
    public void readClientState(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        strength = buf.readVarInt();
    }

    public static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "factory_controller/redstone_link");
    /** Power level broadcast by a powered SEND link. */
    private static final int TRANSMIT_STRENGTH = 15;

    /** {@code true} = RECEIVE (listener, gates gauges); {@code false} = SEND (transmitter, driven by gauges). */
    public boolean receive = false;
    public ItemStack redFreq = ItemStack.EMPTY;
    public ItemStack blueFreq = ItemStack.EMPTY;

    /** Current signal level (0–15) */
    public int strength = 0;
    /** True once registered on Create's link network (server), so (un)registration is idempotent. */
    private boolean registered = false;
    private int lastTransmitted = -1;

    /** On/off view of {@link #strength} — the boolean the redstone gate/output and overlays care about today. */
    public boolean isPowered() { return strength > 0; }

    public VirtualRedstoneLinkBehaviour(FactoryControllerBlockEntity controller, VirtualComponentPosition position,
                                        Item item) {
        super(controller, position, item);
    }

    @Override public ResourceLocation getTexture() { return TEXTURE; }

    @Override public int getColor() { return TYPE.color(); }

    /** Info lines: the two type frequencies and the current Send/Receive mode. */
    @Override
    public java.util.List<net.minecraft.network.chat.Component> infoTooltip() {
        return java.util.List.of(
            freqLine(1, redFreq),
            freqLine(2, blueFreq),
            net.minecraft.network.chat.Component.translatable("createfactorycontroller.gui.mode_prefix",
                net.minecraft.network.chat.Component.translatable(receive
                        ? "createfactorycontroller.gui.redstone_link.mode.receive"
                        : "createfactorycontroller.gui.redstone_link.mode.send").withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));
    }

    private static net.minecraft.network.chat.Component freqLine(int index, ItemStack freq) {
        net.minecraft.network.chat.Component value = freq.isEmpty()
            ? net.minecraft.network.chat.Component.translatable("createfactorycontroller.gui.info.none")
            : freq.getHoverName().copy();
        return net.minecraft.network.chat.Component.translatable("createfactorycontroller.gui.info.frequency",
                index, value.copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY);
    }

    @Override
    public java.util.List<ConnectionCapability> ports() {
        ConnectionCapability.Role role = receive ? ConnectionCapability.Role.SOURCE : ConnectionCapability.Role.SINK;
        // NUMBER is mode-decisive exactly like REDSTONE: source in RECEIVE (outputs strength), sink in SEND.
        return java.util.List.of(new ConnectionCapability(RedstoneConnection.TYPE, role),
                                 new ConnectionCapability(NumberConnection.TYPE, role));
    }

    @Override
    public ValidationResult validateAsSource(Connection.Type type, VirtualComponentBehaviour sink) {
        if (RedstoneConnection.TYPE.equals(type) && !receive) return fail(this, sink);
        return ValidationResult.SUCCESS;
    }

    @Override
    public ValidationResult validateAsSink(Connection.Type type, VirtualComponentBehaviour source) {
        if (RedstoneConnection.TYPE.equals(type) && receive) return fail(source, this);
        return ValidationResult.SUCCESS;
    }

    private static ValidationResult fail(VirtualComponentBehaviour source, VirtualComponentBehaviour sink) {
        return ValidationResult.fail(() -> ConnectionResolver.cannotConnect(source, sink));
    }

    // ── Power computation ──────────────────────────────────────────────────────

    /**
     * Pushes any change in transmit strength onto the network.
     */
    public void updatePower() {
        int transmit = getTransmittedStrength();
        if (transmit != lastTransmitted) {
            lastTransmitted = transmit;
            notifyNetwork();
        }
    }

    /** Apply a network power reading (RECEIVE mode): store the new {@link #strength} and push the gate to wired gauges
     *  ({@link RedstoneConnection} dedupes when the on/off state is unchanged). No-op for a SEND link. */
    private void setReceived(int value) {
        if (!receive || value == strength) return;
        strength = value;
        publish(RedstoneConnection.TYPE);
        publish(NumberConnection.TYPE);   // a RECEIVE link is also the NUMBER source (outputs its strength)
        if (controller != null) { controller.setChanged(); controller.syncComponentState(position); }
    }

    /** (Re)establish our received power on a join or mode flip, where an already-steady transmitter won't re-fire on its
     *  own. Re-drives each ON transmitter on our frequency so Create's push sets our strength through the handler's range
     *  check — which Sable projects for bridged sub-level links. A direct {@code withinRange()} scan can't: it sees only
     *  raw block positions (a sub-level link sits in a far storage region), reads out-of-range, and returns 0. */
    private void refreshReceivedFromNetwork() {
        Level level = controller == null ? null : controller.getLevel();
        if (level == null || level.isClientSide || !registered || !receive) return;
        for (IRedstoneLinkable other : List.copyOf(handler().getNetworkOf(level, this)))
            if (other != this && other.getTransmittedStrength() > 0)
                handler().updateNetworkOf(level, other);
    }

    /** A RECEIVE link is the wire's source for both REDSTONE (drives wired gauges with its current network power)
     *  and NUMBER (outputs its network strength, 0-15). A SEND link sources nothing onto the board — its output is
     *  the network transmit, computed in {@link #recomputeSendStrength}. */
    @Override
    public ConnectionValue outputValue(Connection.Type type) {
        if (!receive) return null;
        if (RedstoneConnection.TYPE.equals(type))
            return isPowered() ? RedstoneConnection.State.POWERED : RedstoneConnection.State.UNPOWERED;
        if (NumberConnection.TYPE.equals(type))
            return new NumberConnection.NumberValue(strength);
        return null;
    }

    /** A SEND link sinks both REDSTONE and NUMBER into its transmit strength; a RECEIVE link sinks nothing (the
     *  network owns its strength). */
    @Override
    public void onInputChanged(Connection.Type type) {
        if (receive) return;
        recomputeSendStrength();
    }

    /** SEND transmit strength = MAX over all inputs: any powered redstone edge counts as 15, each number edge as
     *  its value floored (tolerant) and clamped to 0-15. With no NUMBER edges this reduces to the redstone-only
     *  {@code any powered ? 15 : 0} fold. */
    private void recomputeSendStrength() {
        int best = 0;
        for (Connection c : graph().incomingConnections(position, RedstoneConnection.TYPE))
            if (c instanceof RedstoneConnection rc && rc.powered()) { best = TRANSMIT_STRENGTH; break; }
        for (Connection c : graph().incomingConnections(position, NumberConnection.TYPE))
            if (c instanceof NumberConnection nc)
                best = Math.max(best, Math.clamp(NumberConnection.floorTolerant(nc.doubleValue()), 0, 15));
        if (best == strength) return;
        strength = best;
        updateTransmittedPower();
        if (controller != null) { controller.setChanged(); controller.syncComponentState(position); }
    }

    @Override
    public List<Connection> connectionsToCycle() {
        List<Connection> result = super.connectionsToCycle();
        for (Connection conn : targetedBy().values())
            if (conn != null) result.add(conn);
        return result;
    }

    private void updateTransmittedPower() {
        int transmit = getTransmittedStrength();
        if (transmit == lastTransmitted) return;
        lastTransmitted = transmit;
        notifyNetwork();
    }

    @Override
    public void tick() {
        // Nothing per-tick: SEND power is event-driven (onInputChanged), RECEIVE push-driven (setReceivedStrength);
        // network (re)registration and transmit re-notify run on the lazy tick (updateState).
    }

    @Override
    public void lazyTick() {
        updateState();
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
        int previousStrength = strength;
        boolean freqChanged = !ItemStack.isSameItemSameComponents(r, redFreq)
                           || !ItemStack.isSameItemSameComponents(b, blueFreq);
        boolean modeChanged = this.receive != receive;
        if (!freqChanged && !modeChanged) return;

        if (freqChanged)
            removeFromNetwork();
        redFreq = r;
        blueFreq = b;
        this.receive = receive;
        lastTransmitted = -1;

        if (modeChanged || receive)
            strength = 0;
        if (modeChanged)
            reorientConnections();
        if (freqChanged)
            addToNetwork();

        refreshReceivedFromNetwork();
        if (receive && strength != previousStrength) {
            publish(RedstoneConnection.TYPE);
            publish(NumberConnection.TYPE);
        }
        updatePower();
        if (controller != null) {
            controller.settleConnections();
            controller.setChanged();
            controller.syncComponentFull(position);   // mode/frequencies are config
        }
    }

    private void reorientConnections() {
        java.util.List<Connection> connections = new java.util.ArrayList<>();
        connections.addAll(graph().incomingConnections(position));
        connections.addAll(graph().outgoingConnections(position));
        java.util.Map<Connection.Type, java.util.Set<VirtualComponentPosition>> affected = new java.util.LinkedHashMap<>();
        for (Connection conn : connections) {
            java.util.Set<VirtualComponentPosition> affectedForType =
                    affected.computeIfAbsent(conn.type, ignored -> new java.util.LinkedHashSet<>());
            affectedForType.add(conn.from);
            affectedForType.add(conn.to);                            // both endpoints (same set after the reverse)
            if (position.equals(conn.from) == receive) continue;    // already oriented for the new mode
            VirtualComponentBehaviour newSource = siblingAt(conn.to);
            VirtualComponentBehaviour newSink = siblingAt(conn.from);
            if (!ConnectionResolver.validate(conn.type, newSource, newSink).isSuccess()) {
                if (controller != null)
                    controller.syncConnectionRemoved(ConnectionKey.of(conn));
                graph().remove(conn.to, conn.from);
                continue;
            }
            if (controller != null)
                controller.syncConnectionRemoved(ConnectionKey.of(conn));   // reversing re-keys the wire
            graph().reverse(conn);
            if (controller != null)
                controller.syncConnection(ConnectionKey.of(conn));
        }
        // Every affected component's incoming AND outgoing set may have changed: re-publish its output (writes edges +
        // flags its sinks) and flag itself so it re-folds its own inputs. settleConnections (in configure) folds once.
        for (var entry : affected.entrySet())
            for (VirtualComponentPosition p : entry.getValue()) {
                VirtualComponentBehaviour behaviour = siblingAt(p);
                if (behaviour == null) continue;
                behaviour.publish(entry.getKey());
                if (controller != null) controller.markSinkDirty(p, entry.getKey());
            }
    }

    /** The operation-mode key on the board: toggle Send/Receive, keeping the current frequencies. */
    @Override
    public void cycleOperationMode() {
        configure(!receive, redFreq, blueFreq);
    }

    // ── IRedstoneLinkable ──────────────────────────────────────────────────────

    @Override public boolean isListening() { return receive; }
    @Override public int getTransmittedStrength() { return receive ? 0 : strength; }

    /**
     * The redstone network's event hook: called whenever this receiver's incoming power changes. Drives the gate on
     * connected gauges <b>immediately</b>. Never re-notifies the network (a receiver transmits nothing), so it's safe to
     * run mid network update.
     */
    @Override
    public void setReceivedStrength(int networkPower) {
        setReceived(networkPower);   // stores the level + drives wired gauges (RECEIVE) if the gate changed
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

    // Controller lifecycle hooks: a link's Create-network membership simply follows its presence in the controller.
    @Override public void onAdded()   { updateState(); }
    @Override public void onRemoved() { removeFromNetwork(); }
    @Override public void onUnload()  { removeFromNetwork(); }

    /** Register on Create's network if needed, then re-evaluate power. Idempotent — also the {@link #lazyTick} upkeep. */
    private void updateState() {
        addToNetwork();
        updatePower();
    }

    private void addToNetwork() {
        Level level = controller == null ? null : controller.getLevel();
        if (level == null || level.isClientSide || registered) return;
        handler().addToNetwork(level, this);
        registered = true;
        refreshReceivedFromNetwork();
    }

    private void removeFromNetwork() {
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
        if (profile.includesRuntime() && strength != 0)
            tag.putInt("Strength", strength);
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
        b.strength = tag.contains("Strength") ? tag.getInt("Strength")
                   : tag.getBoolean("Powered") ? TRANSMIT_STRENGTH : 0;
        return b;
    }
}
