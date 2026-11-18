package io.github.nbcss.createfactorycontroller.content.component.redstone;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.AbstractVirtualComponent;
import io.github.nbcss.createfactorycontroller.content.component.SyncCodecs;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionCapability;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionValue;
import io.github.nbcss.createfactorycontroller.content.component.connection.RedstoneConnection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ValidationResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.UUID;

/**
 * Shared logic of Create's brass diodes (Pulse Repeater / Extender / Timer), mirroring {@code BrassDiodeBlockEntity}
 * plus the vanilla {@code DiodeBlock} input latch. Subclasses only supply {@link #updateState}.
 */
public abstract class BrassDiodeBehaviour extends AbstractVirtualComponent {

    /** Create's {@code maxState.between(2, 60 * 20 * 60)}. */
    public static final int MIN_INTERVAL = 2, MAX_INTERVAL = 60 * 20 * 60;
    /** Vanilla {@code DiodeBlock#getDelay} of a brass diode. */
    private static final int INPUT_DELAY = 2;

    public static final ResourceLocation SPRITE_ROOT =
            ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "factory_controller/diodes");

    protected int redstoneInterval = defaultInterval();
    protected boolean inverted;
    /** Latched input — Create's {@code POWERED}. */
    protected boolean powered;
    /** Internal output — Create's {@code POWERING}. */
    protected boolean powering;
    protected int state;
    /** Ticks until the pending latch update (vanilla scheduled tick); 0 = none. Server-only. */
    private int inputDelay;
    /** Last folded input. Server-only, refolded from the edges on load. */
    private boolean rawInput;

    protected BrassDiodeBehaviour(FactoryControllerBlockEntity controller, VirtualComponentPosition position, Item item) {
        super(controller, position, item);
    }

    protected interface Factory {
        BrassDiodeBehaviour create(FactoryControllerBlockEntity controller, VirtualComponentPosition pos, Item item);
    }

    protected static Type type(String id, String order, ResourceLocation item, int color, Factory factory) {
        return new Type() {
            @Override public String id() { return id; }
            @Override public String order() { return order; }
            @Override public List<ResourceLocation> items() { return List.of(item); }
            @Override public int color() { return color; }
            @Override public boolean isRequireNetwork() { return false; }

            @Override
            public VirtualComponentBehaviour create(FactoryControllerBlockEntity controller, VirtualComponentPosition pos,
                                                    Item item, UUID networkId) {
                return factory.create(controller, pos, item);
            }

            @Override
            public VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller, CompoundTag tag,
                                                     HolderLookup.Provider registries) {
                BrassDiodeBehaviour diode = factory.create(controller,
                        VirtualComponentPosition.fromNBT(tag.getCompound("Pos")),
                        BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("Item"))));
                diode.readNBT(tag);
                return diode;
            }

            @Override
            public VirtualComponentBehaviour fromClient(RegistryFriendlyByteBuf buf) {
                VirtualComponentPosition pos = SyncCodecs.readPos(buf);
                BrassDiodeBehaviour diode = factory.create(null, pos, BuiltInRegistries.ITEM.get(buf.readResourceLocation()));
                diode.redstoneInterval = buf.readVarInt();
                diode.inverted = buf.readBoolean();
                diode.readClientState(buf);
                return diode;
            }
        };
    }

    protected abstract Type type();

    @Override public String typeId() { return type().id(); }

    @Override public int getColor() { return type().color(); }

    protected int defaultInterval() { return 2; }

    /** One counter step, transcribed from Create's {@code updateState}. Writes {@link #powering} only when
     *  {@code server} (Create's {@code level.isClientSide} returns sit at the same spots). */
    protected abstract void updateState(boolean server);

    protected abstract String spriteName();

    @Override public ResourceLocation getTexture() { return SPRITE_ROOT.withSuffix("/" + spriteName()); }

    public boolean outputPowered() { return powering ^ inverted; }

    public boolean isInverted() { return inverted; }

    public int getRedstoneInterval() { return redstoneInterval; }

    public float progress() {
        int max = Math.max(2, redstoneInterval);
        return Mth.clamp(state, 0, max) / (float) max;
    }

    /** Create's {@code BrassDiodeBlockEntity#format}. */
    public static String formatInterval(int value) {
        if (value < 60) return value + "t";
        if (value < 20 * 60) return (value / 20) + "s";
        return (value / 20 / 60) + "m";
    }

    @Override
    public List<Component> infoTooltip() {
        return List.of(
                Component.translatable("createfactorycontroller.gui.diode.interval",
                                Component.literal(formatInterval(redstoneInterval)).withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("createfactorycontroller.gui.diode.output",
                                Component.translatable(inverted
                                                ? "createfactorycontroller.gui.diode.output.inverted"
                                                : "createfactorycontroller.gui.diode.output.normal")
                                        .withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY));
    }

    // ── Connections: REDSTONE only, BOTH role ───────────────────────────────────

    @Override
    public List<ConnectionCapability> ports() {
        return List.of(new ConnectionCapability(RedstoneConnection.TYPE, ConnectionCapability.Role.BOTH, 1.0));
    }

    @Override
    public ValidationResult validateAsSource(Connection.Type type, VirtualComponentBehaviour sink) {
        return ValidationResult.SUCCESS;
    }

    @Override
    public ValidationResult validateAsSink(Connection.Type type, VirtualComponentBehaviour source) {
        return ValidationResult.SUCCESS;
    }

    @Override
    public ConnectionValue outputValue(Connection.Type type) {
        if (!RedstoneConnection.TYPE.equals(type)) return null;
        return outputPowered() ? RedstoneConnection.State.POWERED : RedstoneConnection.State.UNPOWERED;
    }

    // ── Signal ──────────────────────────────────────────────────────────────────

    /** Vanilla {@code DiodeBlock#checkTickOnNeighbor}: arm the latch when the input disagrees and none is pending. */
    @Override
    public void onInputChanged(Connection.Type type) {
        if (controller == null || !RedstoneConnection.TYPE.equals(type)) return;
        boolean any = false;
        for (Connection c : graph().incomingConnections(position, RedstoneConnection.TYPE))
            if (c instanceof RedstoneConnection rc && rc.powered()) { any = true; break; }
        rawInput = any;
        if (rawInput != powered && inputDelay == 0) inputDelay = INPUT_DELAY;
    }

    @Override
    public void onAdded() {
        onInputChanged(RedstoneConnection.TYPE);
    }

    /** Latch step (vanilla {@code DiodeBlock#tick}, which runs before block entities), then the counter step. No settle
     *  here: the controller's settle right after the preTick loop folds the sinks. */
    @Override
    public void preTick() {
        boolean wasPowered = powered, wasPowering = powering, wasOutput = outputPowered();
        if (inputDelay > 0 && --inputDelay == 0) {
            if (powered && !rawInput) {
                powered = false;
            } else if (!powered) {
                powered = true;
                if (!rawInput) inputDelay = INPUT_DELAY;
            }
        }
        updateState(true);
        if (outputPowered() != wasOutput) publish(RedstoneConnection.TYPE);
        // Counter-only steps stay local: the client runs its own counter (clientTick) and re-anchors on these deltas.
        if ((powered != wasPowered || powering != wasPowering) && controller != null) {
            controller.setChanged();
            controller.syncComponentState(position);
        }
    }

    /** Client snapshot: advance the indicator counter between server deltas, as Create's BE ticks on both sides. */
    public void clientTick() {
        updateState(false);
    }

    @Override
    public void tick() {}

    /** Toggle-mode key: flip the output, like right-clicking the Create block. */
    @Override
    public void cycleOperationMode() {
        configure(redstoneInterval, !inverted);
    }

    /** Settings-screen commit. Interval change clamps the counter like Create's {@code onMaxDelayChanged}; the output
     *  itself only moves on an inversion flip. */
    public void configure(int interval, boolean invert) {
        interval = Mth.clamp(interval, MIN_INTERVAL, MAX_INTERVAL);
        boolean changed = false;
        if (interval != redstoneInterval) {
            redstoneInterval = interval;
            state = Mth.clamp(state, 0, interval);
            changed = true;
        }
        if (invert != inverted) {
            inverted = invert;
            publish(RedstoneConnection.TYPE);
            changed = true;
        }
        if (changed && controller != null) {
            controller.settleConnections();   // packet path runs between ticks
            controller.setChanged();
            controller.syncComponentFull(position);
        }
    }

    // ── Client sync ─────────────────────────────────────────────────────────────

    @Override
    public void writeClient(RegistryFriendlyByteBuf buf) {
        SyncCodecs.writePos(buf, position);
        buf.writeResourceLocation(getItemId());
        buf.writeVarInt(redstoneInterval);
        buf.writeBoolean(inverted);
        writeClientState(buf);
    }

    @Override
    public void writeClientState(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(powered);
        buf.writeBoolean(powering);
        buf.writeVarInt(state);
    }

    @Override
    public void readClientState(RegistryFriendlyByteBuf buf) {
        powered = buf.readBoolean();
        powering = buf.readBoolean();
        state = buf.readVarInt();
    }

    // ── NBT ─────────────────────────────────────────────────────────────────────

    @Override
    public CompoundTag toNBT(HolderLookup.Provider registries, NbtProfile profile) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", typeId());
        tag.put("Pos", position.toNBT());
        tag.putString("Item", getItemId().toString());
        if (redstoneInterval != defaultInterval()) tag.putInt("RedstoneInterval", redstoneInterval);
        if (inverted) tag.putBoolean("Inverted", true);
        if (profile.includesRuntime()) {
            if (powered) tag.putBoolean("Powered", true);
            if (powering) tag.putBoolean("Powering", true);
            if (state != 0) tag.putInt("State", state);
            if (inputDelay != 0) tag.putInt("InputDelay", inputDelay);
        }
        return tag;
    }

    /** Fills this instance from {@link #toNBT}; absent keys keep the defaults. */
    private void readNBT(CompoundTag tag) {
        if (tag.contains("RedstoneInterval"))
            redstoneInterval = Mth.clamp(tag.getInt("RedstoneInterval"), MIN_INTERVAL, MAX_INTERVAL);
        inverted = tag.getBoolean("Inverted");
        powered = tag.getBoolean("Powered");
        powering = tag.getBoolean("Powering");
        state = Mth.clamp(tag.getInt("State"), 0, redstoneInterval + 2);
        inputDelay = Mth.clamp(tag.getInt("InputDelay"), 0, INPUT_DELAY);
    }
}
