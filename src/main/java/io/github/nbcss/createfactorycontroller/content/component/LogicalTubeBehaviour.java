package io.github.nbcss.createfactorycontroller.content.component;

import com.simibubi.create.AllItems;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionCapability;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionValue;
import io.github.nbcss.createfactorycontroller.content.component.connection.RedstoneConnection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ValidationResult;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.UUID;

/**
 * A Logic Tube placed on the controller board. A networkless, REDSTONE-only component that combines any number of
 * incoming redstone wires with a boolean {@link Mode} and drives its outgoing redstone wires with the result.
 *
 * <p><b>Sequential (one-tick delay).</b> Unlike gauges/links (combinational, settled within the tick), the tube's
 * output always lags its input by exactly one controller tick: an input change recomputes {@link #nextValue} (via
 * {@link #onInputChanged}), and {@link #preTick} commits it into {@link #value} at the start of the <em>next</em>
 * tick — before any settle, so the delay is one tick regardless of component order. See {@code LOGIC_TUBE_PLAN.md}.</p>
 */
public class LogicalTubeBehaviour extends AbstractVirtualComponent {

    /** Boolean gate applied to the folded inputs. The fold starts at the identities ({@code all=true, any=false}), so
     *  with no input wires AND and NOR are true, OR and NAND are false. */
    public enum Mode {
        OR, AND, NOR, NAND;

        public boolean apply(boolean any, boolean all) {
            return switch (this) {
                case AND  -> all;
                case OR   -> any;
                case NAND -> !all;
                case NOR  -> !any;
            };
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Mode fromName(String name) {
            try {
                return Mode.valueOf(name);
            } catch (IllegalArgumentException e) {
                return OR;
            }
        }
    }

    public static final VirtualComponentBehaviour.Type TYPE = new VirtualComponentBehaviour.Type() {
        @Override public String id() { return "LOGICAL_TUBE"; }
        @Override public List<ResourceLocation> items() { return List.of(AllItems.ELECTRON_TUBE.getId()); }
        @Override public boolean isRequireNetwork() { return false; }

        @Override
        public VirtualComponentBehaviour create(FactoryControllerBlockEntity controller, VirtualComponentPosition pos,
                                                Item item, UUID networkId) {
            return new LogicalTubeBehaviour(controller, pos, item);
        }

        @Override
        public VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller, CompoundTag tag,
                                                 HolderLookup.Provider registries) {
            return LogicalTubeBehaviour.fromNBT(controller, tag, registries);
        }
    };

    public static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "factory_controller/logical_tube");

    public Mode mode = Mode.OR;
    /** Emitted output — drives outgoing redstone edges, rendered, synced. Only ever changed in {@link #preTick}. */
    private boolean value = false;
    /** Combinational target {@code = mode(folded input)}, kept current by {@link #onInputChanged}; committed to
     *  {@link #value} on the next {@link #preTick} (this indirection is the one-tick delay). Not serialized. */
    private boolean nextValue = false;

    public LogicalTubeBehaviour(FactoryControllerBlockEntity controller, VirtualComponentPosition position, Item item) {
        super(controller, position, item);
    }

    @Override public ResourceLocation getTexture() { return TEXTURE; }

    @Override public int getColor() { return 0xFC688D; }

    @Override
    public Component getName() {
        return Component.translatable("createfactorycontroller.component.logical_tube");
    }

    /** Info line: the current logic mode. */
    @Override
    public List<Component> infoTooltip() {
        return List.of(Component.translatable("createfactorycontroller.gui.mode_prefix",
                Component.translatable("createfactorycontroller.component.logical_tube.mode." + mode.name().toLowerCase())
                        .withStyle(net.minecraft.ChatFormatting.WHITE)).withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    public Mode getMode() { return mode; }
    public boolean isPowered() { return value; }

    // ── Connections: REDSTONE only, BOTH role (accepts any redstone partner) ────

    @Override
    public List<ConnectionCapability> ports() {
        return List.of(new ConnectionCapability(Connection.Type.REDSTONE, ConnectionCapability.Role.BOTH));
    }

    @Override
    public ValidationResult validateAsSource(Connection.Type type, VirtualComponentBehaviour sink) {
        return ValidationResult.SUCCESS;
    }

    @Override
    public ValidationResult validateAsSink(Connection.Type type, VirtualComponentBehaviour source) {
        return ValidationResult.SUCCESS;
    }

    // ── Signal: compute target on input change, commit on preTick ───────────────

    @Override
    public ConnectionValue outputValue(Connection.Type type) {
        if (!Connection.Type.REDSTONE.equals(type)) return null;
        return value ? RedstoneConnection.State.POWERED : RedstoneConnection.State.UNPOWERED;
    }

    @Override
    public void onInputChanged(Connection.Type type) {
        if (Connection.Type.REDSTONE.equals(type)) recomputeNext();
    }

    private void recomputeNext() {
        boolean any = false, all = true;
        for (Connection c : graph().incomingConnections(position, Connection.Type.REDSTONE))
            if (c instanceof RedstoneConnection rc) { boolean p = rc.powered(); any |= p; all &= p; }
        nextValue = mode.apply(any, all);
    }

    /** Commit last tick's computed output. The controller calls this on every component at the very start of the tick,
     *  before any settle — so this only ever commits the {@link #nextValue} left by the previous tick's settles. */
    @Override
    public void preTick() {
        if (value == nextValue) return;
        value = nextValue;
        publish(Connection.Type.REDSTONE);
        if (controller != null) { controller.setChanged(); controller.sendData(); }
    }

    @Override
    public void tick() {
        // No per-tick work: the logic runs in preTick (commit) + onInputChanged (compute).
    }

    /** Cycle to the next mode (NONE→AND→OR→NOR→NAND→NONE); the output follows one tick later (via preTick). */
    @Override
    public void cycleOperationMode() {
        setMode(mode.next());
    }

    /** Set the operation mode directly (settings GUI). The output follows one tick later (via preTick). */
    public void setMode(Mode mode) {
        if (this.mode == mode) return;
        this.mode = mode;
        recomputeNext();
        if (controller != null) {
            controller.setChanged();
            controller.sendData();
        }
    }

    // ── NBT ─────────────────────────────────────────────────────────────────────

    @Override
    public CompoundTag toNBT(HolderLookup.Provider registries, NbtProfile profile) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", TYPE.id());
        tag.put("Pos", position.toNBT());
        tag.putString("Item", getItemId().toString());
        tag.putString("Mode", mode.name());
        if (profile.includesRuntime())
            tag.putBoolean("Value", value);
        // nextValue is recomputed from the loaded edges by bindConnectionHooks → onInputChanged.
        return tag;
    }

    public static LogicalTubeBehaviour fromNBT(FactoryControllerBlockEntity controller, CompoundTag tag,
                                               HolderLookup.Provider registries) {
        VirtualComponentPosition pos = VirtualComponentPosition.fromNBT(tag.getCompound("Pos"));
        ResourceLocation itemId = ResourceLocation.parse(tag.getString("Item"));
        Item item = BuiltInRegistries.ITEM.get(itemId);
        LogicalTubeBehaviour b = new LogicalTubeBehaviour(controller, pos, item);
        b.mode = Mode.fromName(tag.getString("Mode"));
        b.value = tag.getBoolean("Value");
        b.nextValue = b.value;
        return b;
    }
}
