package io.github.nbcss.createfactorycontroller.content.component.arithmetic;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.AbstractVirtualComponent;
import io.github.nbcss.createfactorycontroller.content.component.SyncCodecs;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionCapability;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionKey;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionValue;
import io.github.nbcss.createfactorycontroller.content.component.connection.NumberConnection;
import io.github.nbcss.createfactorycontroller.content.component.connection.RedstoneConnection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ValidationResult;
import io.github.nbcss.createfactorycontroller.content.packet.ConfigureArithmeticInputPacket;
import io.github.nbcss.createfactorycontroller.registry.CFCItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * An Arithmetic Tube on the controller board.
 */
public class ArithmeticTubeBehaviour extends AbstractVirtualComponent {

    // ── Input model ─────────────────────────────────────────────────────────────

    public sealed interface NumberInput permits ConnectionInput, ConstantInput {
        /** This operand's current value for {@code tube}. */
        double getValue(ArithmeticTubeBehaviour tube);

        CompoundTag toNBT();

        void writeClient(RegistryFriendlyByteBuf buf);

        static @Nullable NumberInput fromNBT(CompoundTag tag) {
            return switch (tag.getString("Kind")) {
                case ConnectionInput.KIND_NAME -> new ConnectionInput(VirtualComponentPosition.fromNBT(tag.getCompound("Source")));
                case ConstantInput.KIND_NAME -> new ConstantInput(tag.getDouble("Value"));
                default -> null;
            };
        }

        static @Nullable NumberInput fromClient(RegistryFriendlyByteBuf buf) {
            return switch (buf.readByte()) {
                case ConnectionInput.CLIENT_TAG -> new ConnectionInput(SyncCodecs.readPos(buf));
                case ConstantInput.CLIENT_TAG -> new ConstantInput(buf.readDouble());
                default -> null;
            };
        }
    }

    /** An operand read from the incoming NUMBER edge from {@code source} */
    public record ConnectionInput(VirtualComponentPosition source) implements NumberInput {
        public static final byte CLIENT_TAG = 0;
        public static final String KIND_NAME = "connection";

        @Override
        public double getValue(ArithmeticTubeBehaviour tube) {
            Connection e = tube.incomingConnection(source, NumberConnection.TYPE);
            return e instanceof NumberConnection nc ? nc.doubleValue() : Double.NaN;
        }

        @Override
        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.putString("Kind", KIND_NAME);
            t.put("Source", source.toNBT());
            return t;
        }

        @Override
        public void writeClient(RegistryFriendlyByteBuf buf) {
            buf.writeByte(CLIENT_TAG);
            SyncCodecs.writePos(buf, source);
        }
    }

    /** A literal operand. No GUI creates these yet — scaffolding for the deferred configuration screen. */
    public record ConstantInput(double value) implements NumberInput {
        public static final byte CLIENT_TAG = 1;
        public static final String KIND_NAME = "constant";

        @Override
        public double getValue(ArithmeticTubeBehaviour tube) {
            return value;
        }

        @Override
        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.putString("Kind", KIND_NAME);
            t.putDouble("Value", value);
            return t;
        }

        @Override
        public void writeClient(RegistryFriendlyByteBuf buf) {
            buf.writeByte(CLIENT_TAG);
            buf.writeDouble(value);
        }
    }

    // ── Type ─────────────────────────────────────────────────────────────────────

    public static final Type TYPE = new Type() {
        @Override public String id() { return "ARITHMETIC_TUBE"; }
        @Override public List<ResourceLocation> items() { return List.of(CFCItems.ARITHMETIC_TUBE.getId()); }
        @Override public int color() { return NumberConnection.COLOR; }   // purple, matching its wire
        @Override public boolean isRequireNetwork() { return false; }

        @Override
        public VirtualComponentBehaviour create(FactoryControllerBlockEntity controller, VirtualComponentPosition pos,
                                                Item item, UUID networkId) {
            return new ArithmeticTubeBehaviour(controller, pos, item);
        }

        @Override
        public VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller, CompoundTag tag,
                                                 HolderLookup.Provider registries) {
            return ArithmeticTubeBehaviour.fromNBT(controller, tag, registries);
        }

        @Override
        public VirtualComponentBehaviour fromClient(RegistryFriendlyByteBuf buf) {
            VirtualComponentPosition pos = SyncCodecs.readPos(buf);
            Item item = BuiltInRegistries.ITEM.get(buf.readResourceLocation());
            ArithmeticTubeBehaviour t = new ArithmeticTubeBehaviour(null, pos, item);
            t.operator = BuiltinOperator.byName(buf.readUtf());
            int n = buf.readVarInt();
            for (int i = 0; i < n; i++) {
                NumberInput input = NumberInput.fromClient(buf);
                if (input != null) t.primaryInputs.add(input);
            }
            if (buf.readBoolean()) t.secondaryInput = NumberInput.fromClient(buf);
            t.redstoneMode = SyncCodecs.readEnum(buf, RedstoneMode.values());
            t.overrideValue = buf.readDouble();
            t.outputComparison = SyncCodecs.readEnum(buf, Comparison.values());
            t.outputThreshold = buf.readDouble();
            t.readClientState(buf);
            return t;
        }
    };

    @Override public String typeId() { return TYPE.id(); }

    // ── State ──────────────────────────────────────────────────────────────────

    /** How a powered redstone input gates the numeric output. */
    public enum RedstoneMode { OVERRIDE, HOLD }

    /** Comparison applied to the numeric output to derive the redstone output signal. */
    public enum Comparison {
        GREATER(">"), LESS("<"), AT_LEAST("≥"), AT_MOST("≤"), EQUAL("="), NOT_EQUAL("≠");

        private final String symbol;

        Comparison(String symbol) { this.symbol = symbol; }

        public String symbol() { return symbol; }

        /** Whether {@code output <cmp> threshold} holds, epsilon-tolerant at the boundary so float drift doesn't make
         *  any comparator flip near equality: values within {@link #approxEqual} count as equal, so {@code ≥}/{@code ≤}/
         *  {@code =} are true there and {@code >}/{@code <}/{@code ≠} are false. A NaN output fails every test. */
        public boolean test(double output, double threshold) {
            boolean equal = approxEqual(output, threshold);
            return switch (this) {
                case GREATER   -> output > threshold && !equal;
                case LESS      -> output < threshold && !equal;
                case AT_LEAST  -> output > threshold || equal;
                case AT_MOST   -> output < threshold || equal;
                case EQUAL     -> equal;
                case NOT_EQUAL -> !equal;
            };
        }

        private static boolean approxEqual(double a, double b) {
            if (a == b) return true;   // exact (covers ±∞ and clean integers)
            double diff = Math.abs(a - b);
            return diff <= 1e-9 * Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));   // relative, absolute floor
        }
    }

    private ArithmeticOperator operator = BuiltinOperator.SUM;   // default
    private final List<NumberInput> primaryInputs = new ArrayList<>();
    @Nullable private ArithmeticTubeBehaviour.NumberInput secondaryInput;

    /** Redstone-control config: gate mode + the OVERRIDE constant. HOLD needs no snapshot field — the freeze is just
     *  {@code nextOutput = output} (§5 of the design), and {@code output} itself is the persisted/synced value. */
    private RedstoneMode redstoneMode = RedstoneMode.OVERRIDE;
    private double overrideValue = 0.0;

    /** Redstone-output config: the comparison + threshold that turn the numeric output into a POWERED/UNPOWERED signal
     *  on outgoing REDSTONE edges. Stateless — the signal is recomputed from {@link #output} on demand. */
    private Comparison outputComparison = Comparison.GREATER;
    private double outputThreshold = 0.0;

    private double output = 0.0;
    private double nextOutput = 0.0;

    public ArithmeticTubeBehaviour(FactoryControllerBlockEntity controller, VirtualComponentPosition position, Item item) {
        super(controller, position, item);
    }

    // ── Identity / render data ──────────────────────────────────────────────────

    public static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "factory_controller/arithmetic_tube");

    @Override public ResourceLocation getTexture() { return TEXTURE; }

    @Override public int getColor() { return TYPE.color(); }

    @Override
    public Component getName() {
        return Component.translatable("createfactorycontroller.component.arithmetic_tube");
    }

    @Override
    public List<Component> infoTooltip() {
        return List.of(Component.translatable("createfactorycontroller.arithmetic_tube.operator_prefix",
                operator.displayName().copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
    }

    public ArithmeticOperator getOperator() { return operator; }

    public List<NumberInput> getPrimaryInputs() { return primaryInputs; }

    @Nullable
    public ArithmeticTubeBehaviour.NumberInput getSecondaryInput() { return secondaryInput; }

    public double getOutput() { return output; }

    public RedstoneMode getRedstoneMode() { return redstoneMode; }

    public double getOverrideValue() { return overrideValue; }

    public Comparison getOutputComparison() { return outputComparison; }

    public double getOutputThreshold() { return outputThreshold; }

    // ── Connections: NUMBER only, BOTH role ─────────────────────────────────────

    @Override
    public List<ConnectionCapability> ports() {
        return List.of(new ConnectionCapability(NumberConnection.TYPE, ConnectionCapability.Role.BOTH, 1.0),
                       new ConnectionCapability(RedstoneConnection.TYPE, ConnectionCapability.Role.BOTH, 0.1));
    }

    @Override
    public ValidationResult validateAsSource(Connection.Type type, VirtualComponentBehaviour sink) {
        return ValidationResult.SUCCESS;   // always a valid number source
    }

    @Override
    public ValidationResult validateAsSink(Connection.Type type, VirtualComponentBehaviour source) {
        if (RedstoneConnection.TYPE.equals(type)) return ValidationResult.SUCCESS;   // control input; not an operand slot
        return canAcceptMoreInput()
                ? ValidationResult.SUCCESS
                : ValidationResult.fail(() -> Component.translatable(
                        "createfactorycontroller.arithmetic_tube.inputs_full").withStyle(ChatFormatting.RED));
    }

    /** Whether the current operator has a free operand slot (NUMBER-sink capacity gate + constant-add precondition). */
    private boolean canAcceptMoreInput() {
        return switch (operator.arity()) {
            case UNARY -> primaryInputs.isEmpty();
            case BINARY -> primaryInputs.isEmpty() || secondaryInput == null;
            case N_ARY -> true;
        };
    }

    /** Which slot the next auto-routed input (connection or constant) would fill: {@code true} = primary,
     *  {@code false} = the binary secondary. Mirrors the routing in {@link #addConstant}/{@link #reconcileInputs}. */
    public boolean nextInputIsPrimary() {
        return operator.arity() == ArithmeticOperator.Arity.N_ARY || primaryInputs.isEmpty();
    }

    /** Whether a constant operand can be added: a free slot must exist, and a multi-input (N_ARY) operator is capped at
     *  a single constant (its slots are otherwise unbounded). */
    public boolean canAddConstant() {
        if (!canAcceptMoreInput()) return false;
        if (operator.arity() == ArithmeticOperator.Arity.N_ARY)
            return primaryInputs.stream().noneMatch(r -> r instanceof ConstantInput);
        return true;
    }

    /** Whether switching to {@code op} would keep every current input — i.e. its arity can hold the present
     *  primary/secondary connections without dropping any. The operator picker disables operators that fail this
     *  (the player must disconnect the offending input first, e.g. a red/secondary wire blocks a unary operator). */
    public boolean canSwitchTo(ArithmeticOperator op) {
        ArithmeticOperator.Arity a = op.arity();
        return primaryInputs.size() <= a.maxPrimary && (secondaryInput == null || a.allowsSecondary);
    }

    // ── Signal: compute target on input change, commit on preTick ───────────────

    @Override
    public ConnectionValue outputValue(Connection.Type type) {
        if (NumberConnection.TYPE.equals(type)) return new NumberConnection.NumberValue(output);
        if (RedstoneConnection.TYPE.equals(type))
            return redstoneOutputPowered() ? RedstoneConnection.State.POWERED : RedstoneConnection.State.UNPOWERED;
        return null;
    }

    /** Whether the tube is currently sourcing a POWERED redstone signal (numeric output satisfies the comparison). */
    public boolean redstoneOutputPowered() {
        return outputComparison.test(output, outputThreshold);
    }

    @Override
    public void onInputChanged(Connection.Type type) {
        if (NumberConnection.TYPE.equals(type) || RedstoneConnection.TYPE.equals(type)) recomputeNext();
    }

    /** The raw operator result over the current operands, before the redstone gate. */
    private double rawArithmetic() {
        double[] primaries = new double[primaryInputs.size()];
        for (int i = 0; i < primaries.length; i++) primaries[i] = primaryInputs.get(i).getValue(this);
        OptionalDouble secondary = secondaryInput == null
                ? OptionalDouble.empty() : OptionalDouble.of(secondaryInput.getValue(this));
        return operator.apply(primaries, secondary);
    }

    /** Whether any incoming REDSTONE edge is currently POWERED (INACTIVE / UNPOWERED do not count). */
    public boolean anyRedstonePowered() {
        for (Connection c : incomingConnections(RedstoneConnection.TYPE))
            if (c instanceof RedstoneConnection rc && rc.powered()) return true;
        return false;
    }

    /** Whether the tube has at least one incoming REDSTONE edge (drives the GUI redstone-control row's presence). */
    public boolean hasRedstoneInput() {
        return !incomingConnections(RedstoneConnection.TYPE).isEmpty();
    }

    /** Whether the tube has at least one outgoing REDSTONE edge (drives the GUI redstone-output row's presence). */
    public boolean hasRedstoneOutput() {
        return !outgoingConnections(RedstoneConnection.TYPE).isEmpty();
    }

    private void recomputeNext() {
        if (controller == null) return;
        if (anyRedstonePowered()) {
            // OVERRIDE → drive the constant, ignoring operands; HOLD → keep the current value (preTick's guard no-ops).
            nextOutput = redstoneMode == RedstoneMode.OVERRIDE ? overrideValue : output;
            return;
        }
        nextOutput = rawArithmetic();
    }

    /**
     * Commit last tick's computed value to {@link #output} (the one-tick delay). Recompute is otherwise event-driven
     * ({@link #onInputChanged}, {@link #onConnectionSetChanged}, {@link #afterInputChange}); a self-feedback wire (a
     * NUMBER loop back into this tube) re-folds through the normal edge path — {@code publish} flags this tube's own
     * sink, which {@code settleConnections} then re-folds — so no special loop handling is needed here.
     */
    @Override
    public void preTick() {
        if (Double.compare(output, nextOutput) == 0) return;
        output = nextOutput;
        publish(NumberConnection.TYPE);
        publish(RedstoneConnection.TYPE);   // re-derive the redstone output from the new value (no-op if no sinks)
        if (controller != null) { controller.setChanged(); controller.syncComponentState(position); }
    }

    @Override
    public void onAdded() {
        recomputeNext();
    }

    @Override
    public void tick() {}

    // ── Operator switching ──────────────────────────────────────────────────────

    @Override
    public void cycleOperationMode() {
        setOperator(operator instanceof BuiltinOperator b ? b.nextInSameArity() : BuiltinOperator.SUM);
    }

    public void setOperator(ArithmeticOperator next) {
        if (next.name().equals(operator.name())) return;
        operator = next;
        ArithmeticOperator.Arity arity = next.arity();
        while (primaryInputs.size() > arity.maxPrimary) dropInput(primaryInputs.removeLast());
        if (!arity.allowsSecondary && secondaryInput != null) {
            dropInput(secondaryInput);
            secondaryInput = null;
        }
        recomputeNext();
        if (controller != null) { controller.setChanged(); controller.syncComponentFull(position); }
    }

    private void dropInput(NumberInput input) {
        if (!(input instanceof ConnectionInput(VirtualComponentPosition source)) || controller == null) return;
        Connection e = incomingConnection(source, NumberConnection.TYPE);
        if (e == null) return;
        controller.connectionGraph().remove(position, source, NumberConnection.TYPE);
        controller.syncConnectionRemoved(ConnectionKey.of(e));
    }

    // ── Ordered-input reconciliation (mirrors the gauge's recipe slots) ─────────

    /**
     * Keeps {@link #primaryInputs}/{@link #secondaryInput} consistent with the live incoming NUMBER edges
     */
    private void reconcileInputs() {
        primaryInputs.removeIf(r ->
                r instanceof ConnectionInput(VirtualComponentPosition source) &&
                incomingConnection(source, NumberConnection.TYPE) == null
        );
        if (secondaryInput instanceof ConnectionInput(VirtualComponentPosition source) &&
                incomingConnection(source, NumberConnection.TYPE) == null
        ) secondaryInput = null;

        for (Connection c : incomingConnections(NumberConnection.TYPE)) {
            VirtualComponentPosition src = c.from;
            if (references(src)) continue;
            ArithmeticOperator.Arity arity = operator.arity();
            if (arity == ArithmeticOperator.Arity.N_ARY) {
                if (primaryInputs.size() < arity.maxPrimary) {
                    int at = primaryInputs.size();
                    while (at > 0 && primaryInputs.get(at - 1) instanceof ConstantInput) at--;
                    primaryInputs.add(at, new ConnectionInput(src));
                }
            } else if (primaryInputs.isEmpty()) {
                primaryInputs.add(new ConnectionInput(src));
            } else if (arity.allowsSecondary && secondaryInput == null) {
                secondaryInput = new ConnectionInput(src);
            }
        }
    }

    // ── GUI-driven input edits ──

    /** Applies one settings-GUI edit. */
    public void configureInput(int op, boolean primary, int index, double value) {
        switch (op) {
            case ConfigureArithmeticInputPacket.ADD_CONSTANT -> addConstant(value);
            case ConfigureArithmeticInputPacket.SET_CONSTANT -> setConstant(primary, index, value);
            case ConfigureArithmeticInputPacket.REMOVE -> removeInput(primary, index);
            case ConfigureArithmeticInputPacket.SWAP -> swapInputs();
            case ConfigureArithmeticInputPacket.SET_REDSTONE_MODE -> {
                int i = (int) value;
                if (i >= 0 && i < RedstoneMode.values().length) setRedstoneMode(RedstoneMode.values()[i]);
            }
            case ConfigureArithmeticInputPacket.SET_REDSTONE_VALUE -> setOverrideValue(value);
            case ConfigureArithmeticInputPacket.SET_OUTPUT_COMPARISON -> {
                int i = (int) value;
                if (i >= 0 && i < Comparison.values().length) setOutputComparison(Comparison.values()[i]);
            }
            case ConfigureArithmeticInputPacket.SET_OUTPUT_THRESHOLD -> setOutputThreshold(value);
        }
    }

    /** Swaps the primary and secondary operands (binary only) */
    public void swapInputs() {
        if (operator.arity() != ArithmeticOperator.Arity.BINARY) return;
        NumberInput p = primaryInputs.isEmpty() ? null : primaryInputs.getFirst();
        NumberInput s = secondaryInput;
        primaryInputs.clear();
        if (s != null) primaryInputs.add(s);
        secondaryInput = p;
        afterInputChange();
    }

    /** Adds a literal operand. */
    public void addConstant(double value) {
        if (!canAddConstant()) return;
        if (operator.arity() == ArithmeticOperator.Arity.N_ARY || primaryInputs.isEmpty())
            primaryInputs.add(new ConstantInput(value));
        else
            secondaryInput = new ConstantInput(value);
        afterInputChange();
    }

    /** Sets a constant operand's value */
    public void setConstant(boolean primary, int index, double value) {
        if (primary) {
            if (index < 0 || index >= primaryInputs.size() || !(primaryInputs.get(index) instanceof ConstantInput)) return;
            primaryInputs.set(index, new ConstantInput(value));
        } else if (secondaryInput instanceof ConstantInput) {
            secondaryInput = new ConstantInput(value);
        } else {
            return;
        }
        afterInputChange();
    }

    public void removeInput(boolean primary, int index) {
        NumberInput ref = primary
                ? (index >= 0 && index < primaryInputs.size() ? primaryInputs.get(index) : null)
                : secondaryInput;
        if (ref == null) return;
        if (ref instanceof ConnectionInput(VirtualComponentPosition source)) {
            if (controller != null) controller.removeConnection(source, position, NumberConnection.TYPE);
        } else {
            if (primary)
                primaryInputs.remove(index);
            else
                secondaryInput = null;
            afterInputChange();
        }
    }

    private void afterInputChange() {
        recomputeNext();
        if (controller != null) { controller.setChanged(); controller.syncComponentFull(position); }
    }

    /** Sets the redstone-control gate mode (config). If powered, {@link #recomputeNext} re-pins/re-drives at once. */
    public void setRedstoneMode(RedstoneMode mode) {
        if (mode == redstoneMode) return;
        redstoneMode = mode;
        afterInputChange();
    }

    /** Sets the OVERRIDE constant (config). */
    public void setOverrideValue(double value) {
        if (Double.compare(value, overrideValue) == 0) return;
        overrideValue = value;
        afterInputChange();
    }

    /** Sets the redstone-output comparison (config). Affects only the redstone output, not the number, so it
     *  re-publishes the redstone signal directly rather than recomputing the numeric output. */
    public void setOutputComparison(Comparison cmp) {
        if (cmp == outputComparison) return;
        outputComparison = cmp;
        if (controller != null) {
            controller.setChanged();
            publish(RedstoneConnection.TYPE);
            controller.syncComponentFull(position);
        }
    }

    /** Sets the redstone-output threshold (config). See {@link #setOutputComparison} on the redstone-only publish. */
    public void setOutputThreshold(double value) {
        if (Double.compare(value, outputThreshold) == 0) return;
        outputThreshold = value;
        if (controller != null) {
            controller.setChanged();
            publish(RedstoneConnection.TYPE);
            controller.syncComponentFull(position);
        }
    }

    @Override
    public boolean onConnectionSetChanged(Connection.Type type) {
        if (type == RedstoneConnection.TYPE) {
            recomputeNext();
            return true;
        }
        if (type != NumberConnection.TYPE) return false;
        reconcileInputs();
        recomputeNext();
        return true;
    }

    /** Re-keys ordered wire inputs without changing their operand assignments. */
    @Override
    public void onComponentsRelocated(UnaryOperator<VirtualComponentPosition> remap) {
        primaryInputs.replaceAll(r ->
                r instanceof ConnectionInput(VirtualComponentPosition source) ? new ConnectionInput(remap.apply(source)) : r
        );
        if (secondaryInput instanceof ConnectionInput(VirtualComponentPosition source))
            secondaryInput = new ConnectionInput(remap.apply(source));
    }

    /** Whether {@code source} feeds the (single) secondary slot — used by the widget to colour the connected face
     *  (secondary → blue, primary → red, both on one face → both). Any other incoming wire is a primary input. */
    public boolean isSecondarySource(VirtualComponentPosition source) {
        return secondaryInput instanceof ConnectionInput(VirtualComponentPosition source1) && source1.equals(source);
    }

    /** Whether one of our input slots already points at {@code src}. Distinct from {@link #incomingConnection} (which
     *  answers whether an edge exists): reconcile iterates the live edges and needs to know which aren't yet assigned
     *  to a slot — a question only the ordered ref list can answer, so it can't be derived from the graph. */
    private boolean references(VirtualComponentPosition src) {
        for (NumberInput r : primaryInputs)
            if (r instanceof ConnectionInput(VirtualComponentPosition source) && source.equals(src))
                return true;
        return secondaryInput instanceof ConnectionInput(VirtualComponentPosition source) && source.equals(src);
    }

    // ── Client sync ─────────────────────────────────────────────────────────────

    @Override
    public void writeClient(RegistryFriendlyByteBuf buf) {
        SyncCodecs.writePos(buf, position);
        buf.writeResourceLocation(getItemId());
        buf.writeUtf(operator.name());
        buf.writeVarInt(primaryInputs.size());
        for (NumberInput r : primaryInputs) r.writeClient(buf);
        buf.writeBoolean(secondaryInput != null);
        if (secondaryInput != null) secondaryInput.writeClient(buf);
        SyncCodecs.writeEnum(buf, redstoneMode);
        buf.writeDouble(overrideValue);
        SyncCodecs.writeEnum(buf, outputComparison);
        buf.writeDouble(outputThreshold);
        writeClientState(buf);
    }

    @Override
    public void writeClientState(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(output);
    }

    @Override
    public void readClientState(RegistryFriendlyByteBuf buf) {
        output = buf.readDouble();
        nextOutput = output;
    }

    // ── NBT ─────────────────────────────────────────────────────────────────────

    @Override
    public CompoundTag toNBT(HolderLookup.Provider registries, NbtProfile profile) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", TYPE.id());
        tag.put("Pos", position.toNBT());
        tag.putString("Item", getItemId().toString());
        tag.putString("Operator", operator.name());
        ListTag prim = new ListTag();
        for (NumberInput r : primaryInputs) prim.add(r.toNBT());
        tag.put("PrimaryInputs", prim);
        if (secondaryInput != null) tag.put("SecondaryInput", secondaryInput.toNBT());
        // Omit redstone config at its defaults — fromNBT restores the same values from absence, and skipping them keeps
        // blueprint/board NBT small (most tubes never touch redstone).
        if (redstoneMode != RedstoneMode.OVERRIDE) tag.putString("RedstoneMode", redstoneMode.name());
        if (overrideValue != 0.0) tag.putDouble("OverrideValue", overrideValue);
        if (outputComparison != Comparison.GREATER) tag.putString("OutputComparison", outputComparison.name());
        if (outputThreshold != 0.0) tag.putDouble("OutputThreshold", outputThreshold);
        if (profile.includesRuntime())
            tag.putDouble("Output", output);
        return tag;
    }

    public static ArithmeticTubeBehaviour fromNBT(FactoryControllerBlockEntity controller, CompoundTag tag, HolderLookup.Provider registries) {
        VirtualComponentPosition pos = VirtualComponentPosition.fromNBT(tag.getCompound("Pos"));
        ResourceLocation itemId = ResourceLocation.parse(tag.getString("Item"));
        Item item = BuiltInRegistries.ITEM.get(itemId);
        ArithmeticTubeBehaviour b = new ArithmeticTubeBehaviour(controller, pos, item);
        b.operator = BuiltinOperator.byName(tag.getString("Operator"));
        ListTag prim = tag.getList("PrimaryInputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < prim.size(); i++) {
            NumberInput input = NumberInput.fromNBT(prim.getCompound(i));
            if (input != null) b.primaryInputs.add(input);   // drops stale operands (e.g. the removed loop kind)
        }
        if (tag.contains("SecondaryInput", Tag.TAG_COMPOUND))
            b.secondaryInput = NumberInput.fromNBT(tag.getCompound("SecondaryInput"));
        b.redstoneMode = tag.contains("RedstoneMode")
                ? RedstoneMode.valueOf(tag.getString("RedstoneMode")) : RedstoneMode.OVERRIDE;
        b.overrideValue = tag.getDouble("OverrideValue");
        b.outputComparison = tag.contains("OutputComparison")
                ? Comparison.valueOf(tag.getString("OutputComparison")) : Comparison.GREATER;
        b.outputThreshold = tag.getDouble("OutputThreshold");
        b.output = tag.getDouble("Output");
        b.nextOutput = b.output;
        return b;
    }
}
