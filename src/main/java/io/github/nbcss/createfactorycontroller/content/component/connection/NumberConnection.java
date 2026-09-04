package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.block.ComponentHolder;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.helper.NumberFormatter;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A number connection carrying a double {@link #value}.
 */
public class NumberConnection extends Connection {
    public static final int COLOR = 0xB265E6;
    private static final int INACTIVE_COLOR = 0x888898;

    public static final Type TYPE = new Type("NUMBER", COLOR) {
        @Override
        public Connection create(VirtualComponentBehaviour source, VirtualComponentBehaviour sink) {
            return new NumberConnection(source.position(), sink.position());
        }

        @Override
        public Connection fromNBT(CompoundTag tag) {
            return NumberConnection.fromNBT(tag);
        }

        @Override
        public Connection fromClient(RegistryFriendlyByteBuf buf,
                                     VirtualComponentPosition from, VirtualComponentPosition to, int arrowBendMode) {
            NumberConnection c = new NumberConnection(from, to);
            c.arrowBendMode = arrowBendMode;
            c.value = buf.readDouble();
            return c;
        }
    };

    /** The transported value (source → sink). Runtime state, wrapped as {@link NumberValue} on the signal bus. */
    public record NumberValue(double value) implements ConnectionValue {}

    private double value = 0.0;

    public NumberConnection(VirtualComponentPosition from, VirtualComponentPosition to) {
        super(TYPE, from, to);
    }

    /** The primitive value on this edge ({@link #value()} wraps it for the generic bus). */
    public double doubleValue() {
        return value;
    }

    @Override
    public int getConnectionColor(ComponentHolder holder) {
        return Double.isNaN(value) ? INACTIVE_COLOR : COLOR;
    }

    @Override
    public List<Component> getInfoTooltip(ComponentHolder holder) {
        return List.of(Component.translatable("createfactorycontroller.connection.info.value",
                        Component.literal(NumberFormatter.format(value)).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean setValue(ConnectionValue value) {
        double next = ((NumberValue) value).value();
        if (Double.compare(next, this.value) == 0) return false;   // exact-equal dedup → no needless sink-dirty / sync
        this.value = next;
        return true;
    }

    @Override
    public ConnectionValue value() {
        return new NumberValue(value);
    }

    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        tag.putDouble("Value", value);
        return tag;
    }

    @Override
    public CompoundTag toExportNBT() {
        return super.toNBT();   // the carried value is runtime, not blueprint config
    }

    @Override
    protected void writeClientExtra(RegistryFriendlyByteBuf buf) {
        buf.writeDouble(value);
    }

    private NumberConnection(CompoundTag tag) {
        super(tag);
        this.value = tag.getDouble("Value");
    }

    public static NumberConnection fromNBT(CompoundTag tag) {
        return new NumberConnection(tag);
    }

    // ── Float precision ────────────────────────────────────────────────────────

    /**
     * Floors {@code x} with a tolerance that absorbs float error near an integer (so a value that should be
     * {@code 15} but arrives as {@code 14.9999999} floors to {@code 15}) without swallowing a genuine fraction.
     * The absolute term covers small magnitudes (redstone 0-15); the ULP term absorbs arithmetic error at larger
     * magnitudes without growing enough to swallow a real fractional base unit.
     */
    public static long floorTolerant(double x) {
        if (Double.isNaN(x)) return 0;                                  // undefined → 0
        if (x == Double.POSITIVE_INFINITY) return Long.MAX_VALUE;       // saturated ±∞ → the long bound (sinks clamp)
        if (x == Double.NEGATIVE_INFINITY) return Long.MIN_VALUE;
        double eps = Math.max(1e-6, Math.ulp(x) * 8);
        return (long) Math.floor(x + eps);
    }
}
