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
import io.github.nbcss.createfactorycontroller.content.component.operator.OperatorArity;
import io.github.nbcss.createfactorycontroller.content.helper.NumberFormatter;
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
 * An Arithmetic Tube on the controller board. A networkless, NUMBER-only component: it reads its incoming NUMBER
 * wires (and constants), applies a selectable {@link ArithmeticOperator}, and drives its outgoing NUMBER wires with
 * the result — with a one-tick delay (same {@code nextOutput}→{@link #preTick}→{@code output} indirection the
 * {@link LogicalTubeBehaviour} uses). Being NUMBER-only, a gauge/link/tube pair shares only NUMBER, so the resolver
 * selects it directly (no type picker needed).
 *
 * <p>Inputs are ordered: a {@code primaryInputs} list plus a single optional {@code secondaryInput}. Each is a
 * {@link WireInput} (value read from the incoming NUMBER edge) or a {@link ConstantInput} (literal — no GUI to create
 * one yet). The operator's {@link OperatorArity} governs how many of each are allowed and how a new wire is routed.</p>
 */
public class ArithmeticTubeBehaviour extends AbstractVirtualComponent {

    // ── Input model ─────────────────────────────────────────────────────────────

    /** One operand of the tube: a live NUMBER wire or a user-entered constant. */
    public sealed interface InputRef permits WireInput, ConstantInput {
        /** This operand's current value for {@code tube}. */
        double getValue(ArithmeticTubeBehaviour tube);

        CompoundTag toNBT();

        void writeClient(RegistryFriendlyByteBuf buf);

        static InputRef fromNBT(CompoundTag tag) {
            return "C".equals(tag.getString("K"))
                    ? new ConstantInput(tag.getDouble("Val"))
                    : new WireInput(VirtualComponentPosition.fromNBT(tag.getCompound("Src")));
        }

        static InputRef fromClient(RegistryFriendlyByteBuf buf) {
            return buf.readByte() == 1
                    ? new ConstantInput(buf.readDouble())
                    : new WireInput(SyncCodecs.readPos(buf));
        }
    }

    /** An operand read from the incoming NUMBER edge from {@code source}; a missing edge reads 0 (self-heals on the
     *  next {@link #reconcileInputs}). */
    public record WireInput(VirtualComponentPosition source) implements InputRef {
        @Override
        public double getValue(ArithmeticTubeBehaviour tube) {
            Connection e = tube.incomingConnection(source, NumberConnection.TYPE);
            return e instanceof NumberConnection nc ? nc.doubleValue() : 0.0;
        }

        @Override
        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.putString("K", "W");
            t.put("Src", source.toNBT());
            return t;
        }

        @Override
        public void writeClient(RegistryFriendlyByteBuf buf) {
            buf.writeByte(0);
            SyncCodecs.writePos(buf, source);
        }
    }

    /** A literal operand. No GUI creates these yet — scaffolding for the deferred configuration screen. */
    public record ConstantInput(double value) implements InputRef {
        @Override
        public double getValue(ArithmeticTubeBehaviour tube) {
            return value;
        }

        @Override
        public CompoundTag toNBT() {
            CompoundTag t = new CompoundTag();
            t.putString("K", "C");
            t.putDouble("Val", value);
            return t;
        }

        @Override
        public void writeClient(RegistryFriendlyByteBuf buf) {
            buf.writeByte(1);
            buf.writeDouble(value);
        }
    }

    // ── Type ─────────────────────────────────────────────────────────────────────

    public static final VirtualComponentBehaviour.Type TYPE = new VirtualComponentBehaviour.Type() {
        @Override public String id() { return "ARITHMETIC_TUBE"; }
        @Override public List<ResourceLocation> items() { return List.of(CreateFactoryController.ARITHMETIC_TUBE.getId()); }
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
            t.operator = BuiltinOperator.byId(buf.readUtf());
            int n = buf.readVarInt();
            for (int i = 0; i < n; i++) t.primaryInputs.add(InputRef.fromClient(buf));
            if (buf.readBoolean()) t.secondaryInput = InputRef.fromClient(buf);
            t.readClientState(buf);
            return t;
        }
    };

    @Override public String typeId() { return TYPE.id(); }

    // ── State ──────────────────────────────────────────────────────────────────

    private ArithmeticOperator operator = BuiltinOperator.SUM;   // default
    private final List<InputRef> primaryInputs = new ArrayList<>();
    @Nullable private InputRef secondaryInput;

    /** Emitted output — drives outgoing NUMBER edges, rendered, synced. Only ever changed in {@link #preTick}. */
    private double output = 0.0;
    /** Target = {@code operator(inputs)}, kept current by {@link #onInputChanged}; committed to {@link #output} on the
     *  next {@link #preTick} (the one-tick delay). Not serialized. */
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

    public double getOutput() { return output; }

    public String getOutputLabel() {
        return NumberFormatter.formatCompact(output);
    }

    public String getOutputText() {
        return NumberFormatter.format(output);
    }

    // ── Connections: NUMBER only, BOTH role ─────────────────────────────────────

    @Override
    public List<ConnectionCapability> ports() {
        return List.of(new ConnectionCapability(NumberConnection.TYPE, ConnectionCapability.Role.BOTH));
    }

    @Override
    public ValidationResult validateAsSource(Connection.Type type, VirtualComponentBehaviour sink) {
        return ValidationResult.SUCCESS;   // a tube is always a valid number source
    }

    @Override
    public ValidationResult validateAsSink(Connection.Type type, VirtualComponentBehaviour source) {
        return canAcceptMoreInput()
                ? ValidationResult.SUCCESS
                : ValidationResult.fail(() -> Component.translatable(
                        "createfactorycontroller.arithmetic_tube.inputs_full").withStyle(ChatFormatting.RED));
    }

    /** Whether the current operator has a free input slot for another wire/constant (counts constants too). */
    @Override
    public boolean canAcceptMoreInput() {
        return switch (operator.arity()) {
            case UNARY -> primaryInputs.isEmpty();
            case BINARY -> primaryInputs.isEmpty() || secondaryInput == null;
            case NARY -> true;
        };
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
        double[] primaries = new double[primaryInputs.size()];
        for (int i = 0; i < primaryInputs.size(); i++) primaries[i] = primaryInputs.get(i).getValue(this);
        OptionalDouble secondary = secondaryInput == null
                ? OptionalDouble.empty() : OptionalDouble.of(secondaryInput.getValue(this));
        double raw = (primaries.length == 0 && secondary.isEmpty())
                ? 0.0                                     // nothing connected → 0
                : operator.apply(primaries, secondary);
        nextOutput = Double.isFinite(raw) ? raw : 0.0;    // no invalid output: NaN and ±∞ (÷0) both become 0
    }

    /** Commit last tick's computed output. Runs at the very start of the tick, before any settle → deterministic
     *  one-tick delay (breaks tube→tube cycles). */
    @Override
    public void preTick() {
        if (Double.compare(output, nextOutput) == 0) return;
        output = nextOutput;
        publish(NumberConnection.TYPE);
        if (controller != null) { controller.setChanged(); controller.syncComponentState(position); }
    }

    @Override
    public void tick() {
        // All work is in preTick (commit) + onInputChanged (compute).
    }

    // ── Operator switching ──────────────────────────────────────────────────────

    /** The operation-mode key: advance to the next operator (and prune inputs that no longer fit). */
    @Override
    public void cycleOperationMode() {
        setOperator(operator instanceof BuiltinOperator b ? b.next() : BuiltinOperator.SUM);
    }

    /**
     * Switch operator and drop any input that no longer fits the new arity. A dropped {@link WireInput} also loses its
     * graph edge; a dropped {@link ConstantInput} just vanishes. Positional: keep the first {@code maxPrimary}
     * primaries; keep the secondary only if the new arity allows one (drop, never migrate).
     */
    public void setOperator(ArithmeticOperator next) {
        if (next.id().equals(operator.id())) return;
        operator = next;
        OperatorArity arity = next.arity();
        while (primaryInputs.size() > arity.maxPrimary) dropInput(primaryInputs.removeLast());
        if (!arity.allowsSecondary && secondaryInput != null) {
            dropInput(secondaryInput);
            secondaryInput = null;
        }
        recomputeNext();
        if (controller != null) { controller.setChanged(); controller.syncComponentFull(position); }
    }

    /** Removes a dropped input's graph edge (constants have none). */
    private void dropInput(InputRef ref) {
        if (!(ref instanceof WireInput w) || controller == null) return;
        Connection e = incomingConnection(w.source(), NumberConnection.TYPE);
        if (e == null) return;
        controller.connectionGraph().remove(position, w.source(), NumberConnection.TYPE);
        controller.syncConnectionRemoved(ConnectionKey.of(e));
    }

    // ── Ordered-input reconciliation (mirrors the gauge's recipe slots) ─────────

    /**
     * Keeps {@link #primaryInputs}/{@link #secondaryInput} consistent with the live incoming NUMBER edges: drops wire
     * refs whose edge is gone, then routes each not-yet-referenced edge into the next legal slot (primary while it has
     * room, else — for a binary operator — the secondary). Constants are untouched.
     */
    private void reconcileInputs() {
        primaryInputs.removeIf(r -> r instanceof WireInput w && !isWired(w.source()));
        if (secondaryInput instanceof WireInput w && !isWired(w.source())) secondaryInput = null;

        for (Connection c : incomingConnections(NumberConnection.TYPE)) {
            VirtualComponentPosition src = c.from;
            if (references(src)) continue;
            OperatorArity arity = operator.arity();
            if (arity == OperatorArity.NARY || primaryInputs.isEmpty()) {
                if (primaryInputs.size() < arity.maxPrimary) primaryInputs.add(new WireInput(src));
            } else if (arity.allowsSecondary && secondaryInput == null) {
                secondaryInput = new WireInput(src);
            }
            // else: no slot — validateAsSink prevents this for a fresh wire; ignore defensively.
        }
    }

    @Override
    public boolean onConnectionSetChanged(Connection.Type type) {
        if (type != NumberConnection.TYPE) return false;
        reconcileInputs();
        return true;
    }

    /** Re-keys ordered wire inputs without changing their operand assignments. */
    @Override
    public void onComponentsRelocated(UnaryOperator<VirtualComponentPosition> remap) {
        primaryInputs.replaceAll(r -> r instanceof WireInput w ? new WireInput(remap.apply(w.source())) : r);
        if (secondaryInput instanceof WireInput w) secondaryInput = new WireInput(remap.apply(w.source()));
    }

    /** Whether {@code source} feeds the (single) secondary slot — used by the widget to colour the connected face
     *  (secondary → blue, primary → red, both on one face → both). Any other incoming wire is a primary input. */
    public boolean isSecondarySource(VirtualComponentPosition source) {
        return secondaryInput instanceof WireInput w && w.source().equals(source);
    }

    private boolean isWired(VirtualComponentPosition src) {
        return incomingConnection(src, NumberConnection.TYPE) instanceof NumberConnection;
    }

    private boolean references(VirtualComponentPosition src) {
        for (InputRef r : primaryInputs) if (r instanceof WireInput w && w.source().equals(src)) return true;
        return secondaryInput instanceof WireInput w && w.source().equals(src);
    }

    // ── Client sync ─────────────────────────────────────────────────────────────

    @Override
    public void writeClient(RegistryFriendlyByteBuf buf) {
        SyncCodecs.writePos(buf, position);
        buf.writeResourceLocation(getItemId());
        buf.writeUtf(operator.id());
        buf.writeVarInt(primaryInputs.size());
        for (InputRef r : primaryInputs) r.writeClient(buf);
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
        tag.putString("Operator", operator.id());
        ListTag prim = new ListTag();
        for (InputRef r : primaryInputs) prim.add(r.toNBT());
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
        b.operator = BuiltinOperator.byId(tag.getString("Operator"));
        ListTag prim = tag.getList("PrimaryInputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < prim.size(); i++) b.primaryInputs.add(InputRef.fromNBT(prim.getCompound(i)));
        if (tag.contains("SecondaryInput", Tag.TAG_COMPOUND))
            b.secondaryInput = InputRef.fromNBT(tag.getCompound("SecondaryInput"));
        b.output = tag.getDouble("Output");
        b.nextOutput = b.output;
        return b;
    }
}
