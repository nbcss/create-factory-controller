package io.github.nbcss.createfactorycontroller.content.component;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionCapability;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionKey;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionValue;
import io.github.nbcss.createfactorycontroller.content.component.connection.NumberConnection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ValidationResult;
import io.github.nbcss.createfactorycontroller.content.component.operator.ArithmeticOperator;
import io.github.nbcss.createfactorycontroller.content.component.operator.BuiltinOperator;
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

    public sealed interface NumberInput permits ConnectionInput, ConstantInput, LoopInput {
        /** Client-sync discriminators, kept in step with the NBT {@code "Kind"} strings. */
        byte TAG_CONNECTION = 0, TAG_CONSTANT = 1, TAG_LOOP = 2;

        /** This operand's current value for {@code tube}. */
        double getValue(ArithmeticTubeBehaviour tube);

        CompoundTag toNBT();

        void writeClient(RegistryFriendlyByteBuf buf);

        static NumberInput fromNBT(CompoundTag tag) {
            return switch (tag.getString("Kind")) {
                case "constant" -> new ConstantInput(tag.getDouble("Value"));
                case "loop" -> new LoopInput();
                default -> new ConnectionInput(VirtualComponentPosition.fromNBT(tag.getCompound("Source")));
            };
        }

        static NumberInput fromClient(RegistryFriendlyByteBuf buf) {
            return switch (buf.readByte()) {
                case TAG_CONSTANT -> new ConstantInput(buf.readDouble());
                case TAG_LOOP -> new LoopInput();
                default -> new ConnectionInput(SyncCodecs.readPos(buf));
            };
        }
    }

    /** An operand read from the incoming NUMBER edge from {@code source} */
    public record ConnectionInput(VirtualComponentPosition source) implements NumberInput {
        @Override
        public double getValue(ArithmeticTubeBehaviour tube) {
            Connection e = tube.incomingConnection(source, NumberConnection.TYPE);
            return e instanceof NumberConnection nc ? nc.doubleValue() : Double.NaN;
        }

        @Override
        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.putString("Kind", "connection");
            t.put("Source", source.toNBT());
            return t;
        }

        @Override
        public void writeClient(RegistryFriendlyByteBuf buf) {
            buf.writeByte(TAG_CONNECTION);
            SyncCodecs.writePos(buf, source);
        }
    }

    /** A literal operand. No GUI creates these yet — scaffolding for the deferred configuration screen. */
    public record ConstantInput(double value) implements NumberInput {
        @Override
        public double getValue(ArithmeticTubeBehaviour tube) {
            return value;
        }

        @Override
        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.putString("Kind", "constant");
            t.putDouble("Value", value);
            return t;
        }

        @Override
        public void writeClient(RegistryFriendlyByteBuf buf) {
            buf.writeByte(TAG_CONSTANT);
            buf.writeDouble(value);
        }
    }

    /** The self-feedback operand: reads the tube's own output — from the PREVIOUS tick, thanks to the one-tick output
     *  delay, so it forms a stable feedback loop. Valid only on a multi-input operator; at most one per tube. */
    public record LoopInput() implements NumberInput {
        @Override
        public double getValue(ArithmeticTubeBehaviour tube) {
            return tube.output;
        }

        @Override
        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.putString("Kind", "loop");
            return t;
        }

        @Override
        public void writeClient(RegistryFriendlyByteBuf buf) {
            buf.writeByte(TAG_LOOP);
        }
    }

    // ── Type ─────────────────────────────────────────────────────────────────────

    public static final VirtualComponentBehaviour.Type TYPE = new VirtualComponentBehaviour.Type() {
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
            for (int i = 0; i < n; i++) t.primaryInputs.add(NumberInput.fromClient(buf));
            if (buf.readBoolean()) t.secondaryInput = NumberInput.fromClient(buf);
            t.readClientState(buf);
            return t;
        }
    };

    @Override public String typeId() { return TYPE.id(); }

    // ── State ──────────────────────────────────────────────────────────────────

    private ArithmeticOperator operator = BuiltinOperator.SUM;   // default
    private final List<NumberInput> primaryInputs = new ArrayList<>();
    @Nullable private ArithmeticTubeBehaviour.NumberInput secondaryInput;
    /** GUI hint: the slot the next created wire should fill (true = primary, false = secondary). One-shot — consumed
     *  by the next reconcile; null = auto-route. Server-only (not persisted/synced). */
    @Nullable private Boolean pendingWireIsPrimary;

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

    /** Whether the primary list currently holds the feedback {@link LoopInput}. */
    public boolean hasLoopInput() {
        for (NumberInput r : primaryInputs) if (r instanceof LoopInput) return true;
        return false;
    }

    /** Loop/feedback is only meaningful for a multi-input (order-independent) operator. */
    public boolean canLoop() { return operator.arity() == ArithmeticOperator.Arity.N_ARY; }

    public boolean hasConstant(boolean primary) {
        if (primary) {
            for (NumberInput r : primaryInputs) if (r instanceof ConstantInput) return true;
            return false;
        }
        return secondaryInput instanceof ConstantInput;
    }

    public double getOutput() { return output; }

    // ── Connections: NUMBER only, BOTH role ─────────────────────────────────────

    @Override
    public List<ConnectionCapability> ports() {
        return List.of(new ConnectionCapability(NumberConnection.TYPE, ConnectionCapability.Role.BOTH));
    }

    @Override
    public ValidationResult validateAsSource(Connection.Type type, VirtualComponentBehaviour sink) {
        return ValidationResult.SUCCESS;   // always a valid number source
    }

    @Override
    public ValidationResult validateAsSink(Connection.Type type, VirtualComponentBehaviour source) {
        return canAcceptMoreInput()
                ? ValidationResult.SUCCESS
                : ValidationResult.fail(() -> Component.translatable(
                        "createfactorycontroller.arithmetic_tube.inputs_full").withStyle(ChatFormatting.RED));
    }

    /** Whether the current operator has a free input slot for another input. */
    @Override
    public boolean canAcceptMoreInput() {
        return switch (operator.arity()) {
            case UNARY -> primaryInputs.isEmpty();
            case BINARY -> primaryInputs.isEmpty() || secondaryInput == null;
            case N_ARY -> true;
        };
    }

    /** Whether switching to {@code op} would keep every current input — i.e. its arity can hold the present
     *  primary/secondary connections without dropping any. The operator picker disables operators that fail this
     *  (the player must disconnect the offending input first, e.g. a red/secondary wire blocks a unary operator). */
    public boolean canSwitchTo(ArithmeticOperator op) {
        ArithmeticOperator.Arity a = op.arity();
        long primaryCount = primaryInputs.stream().filter(r -> !(r instanceof LoopInput)).count();
        return primaryCount <= a.maxPrimary && (secondaryInput == null || a.allowsSecondary);
    }

    // ── Signal: compute target on input change, commit on preTick ───────────────

    @Override
    public ConnectionValue outputValue(Connection.Type type) {
        return NumberConnection.TYPE.equals(type) ? new NumberConnection.NumberValue(output) : null;
    }

    @Override
    public void onInputChanged(Connection.Type type) {
        if (NumberConnection.TYPE.equals(type)) recomputeNext();
    }

    private void recomputeNext() {
        if (controller == null) return;
        double[] primaries = new double[primaryInputs.size()];
        for (int i = 0; i < primaries.length; i++) primaries[i] = primaryInputs.get(i).getValue(this);
        OptionalDouble secondary = secondaryInput == null
                ? OptionalDouble.empty() : OptionalDouble.of(secondaryInput.getValue(this));
        nextOutput = operator.apply(primaries, secondary);
    }

    /**
     * Commit last tick's computed value to {@link #output} (the one-tick delay). A {@link LoopInput} feeds our own
     * output back in, so an output change means the loop must re-fold for the next tick — but the early-return then
     * halts the iteration the moment it settles ({@code nextOutput == output}), so a settled loop costs nothing. Every
     * other recompute is event-driven ({@link #onInputChanged}, {@link #onConnectionSetChanged}, {@link #afterInputChange}).
     */
    @Override
    public void preTick() {
        if (Double.compare(output, nextOutput) == 0) return;
        output = nextOutput;
        publish(NumberConnection.TYPE);
        if (controller != null) { controller.setChanged(); controller.syncComponentState(position); }
        if (hasLoopInput()) recomputeNext();
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
        pendingWireIsPrimary = null;
        if (arity != ArithmeticOperator.Arity.N_ARY)
            primaryInputs.removeIf(r -> r instanceof LoopInput);
        while (primaryInputs.size() > arity.maxPrimary) dropInput(primaryInputs.removeLast());
        if (!arity.allowsSecondary && secondaryInput != null) {
            dropInput(secondaryInput);
            secondaryInput = null;
        }
        recomputeNext();
        if (controller != null) { controller.setChanged(); controller.syncComponentFull(position); }
    }

    private void dropInput(NumberInput input) {
        if (!(input instanceof ConnectionInput w) || controller == null) return;
        Connection e = incomingConnection(w.source(), NumberConnection.TYPE);
        if (e == null) return;
        controller.connectionGraph().remove(position, w.source(), NumberConnection.TYPE);
        controller.syncConnectionRemoved(ConnectionKey.of(e));
    }

    // ── Ordered-input reconciliation (mirrors the gauge's recipe slots) ─────────

    /**
     * Keeps {@link #primaryInputs}/{@link #secondaryInput} consistent with the live incoming NUMBER edges
     */
    private void reconcileInputs() {
        primaryInputs.removeIf(r -> r instanceof ConnectionInput w && incomingConnection(w.source(), NumberConnection.TYPE) == null);
        if (secondaryInput instanceof ConnectionInput w && incomingConnection(w.source(), NumberConnection.TYPE) == null) secondaryInput = null;

        for (Connection c : incomingConnections(NumberConnection.TYPE)) {
            VirtualComponentPosition src = c.from;
            if (references(src)) continue;
            ArithmeticOperator.Arity arity = operator.arity();
            boolean toSecondary = Boolean.FALSE.equals(pendingWireIsPrimary)   // GUI "add secondary connection"
                    && arity.allowsSecondary && secondaryInput == null;
            pendingWireIsPrimary = null;   // one-shot: consumed by the first newly-seen wire
            if (toSecondary) {
                secondaryInput = new ConnectionInput(src);
            } else if (arity == ArithmeticOperator.Arity.N_ARY || primaryInputs.isEmpty()) {
                if (primaryInputs.size() < arity.maxPrimary) {
                    int at = primaryInputs.size();
                    while (at > 0 && primaryInputs.get(at - 1) instanceof ConstantInput) at--;
                    primaryInputs.add(at, new ConnectionInput(src));
                }
            } else if (arity.allowsSecondary && secondaryInput == null) {
                secondaryInput = new ConnectionInput(src);
            }
        }
    }

    // ── GUI-driven input edits ──

    /** Sets which slot the next created wire fills (the add-connection buttons); one-shot, cleared on reconcile. */
    public void prepareWire(boolean primary) { pendingWireIsPrimary = primary; }

    /** Add or remove the feedback {@link LoopInput} (multi-input operators only; at most one). Kept at the front of the
     *  primary list so it renders at the top. */
    public void setLoopEnabled(boolean enabled) {
        if (enabled == hasLoopInput()) return;
        if (enabled) {
            if (!canLoop()) return;
            primaryInputs.addFirst(new LoopInput());
        } else {
            primaryInputs.removeIf(r -> r instanceof LoopInput);
        }
        afterInputChange();
    }

    /** Swaps the primary and secondary operands (binary only) */
    public void swapInputs() {
        if (operator.arity() != ArithmeticOperator.Arity.BINARY) return;
        NumberInput p = primaryInputs.isEmpty() ? null : primaryInputs.get(0);
        NumberInput s = secondaryInput;
        primaryInputs.clear();
        if (s != null) primaryInputs.add(s);
        secondaryInput = p;
        afterInputChange();
    }

    public void addConstant(boolean primary, double value) {
        if (primary) {
            if (primaryInputs.size() >= operator.arity().maxPrimary) return;
            primaryInputs.add(new ConstantInput(value));
        } else {
            if (!operator.arity().allowsSecondary || secondaryInput != null) return;
            secondaryInput = new ConstantInput(value);
        }
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
        if (ref instanceof ConnectionInput w) {
            if (controller != null) controller.removeConnection(w.source(), position, NumberConnection.TYPE);
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

    @Override
    public boolean onConnectionSetChanged(Connection.Type type) {
        if (type != NumberConnection.TYPE) return false;
        reconcileInputs();
        recomputeNext();
        return true;
    }

    /** Re-keys ordered wire inputs without changing their operand assignments. */
    @Override
    public void onComponentsRelocated(UnaryOperator<VirtualComponentPosition> remap) {
        primaryInputs.replaceAll(r -> r instanceof ConnectionInput w ? new ConnectionInput(remap.apply(w.source())) : r);
        if (secondaryInput instanceof ConnectionInput w) secondaryInput = new ConnectionInput(remap.apply(w.source()));
    }

    /** Whether {@code source} feeds the (single) secondary slot — used by the widget to colour the connected face
     *  (secondary → blue, primary → red, both on one face → both). Any other incoming wire is a primary input. */
    public boolean isSecondarySource(VirtualComponentPosition source) {
        return secondaryInput instanceof ConnectionInput w && w.source().equals(source);
    }

    /** Whether one of our input slots already points at {@code src}. Distinct from {@link #incomingConnection} (which
     *  answers whether an edge exists): reconcile iterates the live edges and needs to know which aren't yet assigned
     *  to a slot — a question only the ordered ref list can answer, so it can't be derived from the graph. */
    private boolean references(VirtualComponentPosition src) {
        for (NumberInput r : primaryInputs) if (r instanceof ConnectionInput w && w.source().equals(src)) return true;
        return secondaryInput instanceof ConnectionInput w && w.source().equals(src);
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
        if (profile.includesRuntime())
            tag.putDouble("Output", output);
        return tag;
    }

    public static ArithmeticTubeBehaviour fromNBT(FactoryControllerBlockEntity controller, CompoundTag tag,
                                                  HolderLookup.Provider registries) {
        VirtualComponentPosition pos = VirtualComponentPosition.fromNBT(tag.getCompound("Pos"));
        ResourceLocation itemId = ResourceLocation.parse(tag.getString("Item"));
        Item item = BuiltInRegistries.ITEM.get(itemId);
        ArithmeticTubeBehaviour b = new ArithmeticTubeBehaviour(controller, pos, item);
        b.operator = BuiltinOperator.byName(tag.getString("Operator"));
        ListTag prim = tag.getList("PrimaryInputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < prim.size(); i++) b.primaryInputs.add(NumberInput.fromNBT(prim.getCompound(i)));
        if (tag.contains("SecondaryInput", Tag.TAG_COMPOUND))
            b.secondaryInput = NumberInput.fromNBT(tag.getCompound("SecondaryInput"));
        b.output = tag.getDouble("Output");
        b.nextOutput = b.output;
        return b;
    }
}
