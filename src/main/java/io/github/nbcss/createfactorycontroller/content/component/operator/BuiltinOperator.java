package io.github.nbcss.createfactorycontroller.content.component.operator;

import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * The built-in {@link ArithmeticOperator}s
 */
public enum BuiltinOperator implements ArithmeticOperator {
    // ── N inputs, order-independent ──
    SUM(Arity.N_ARY, "add", (p, s) -> Arrays.stream(p).sum()),
    PRODUCT(Arity.N_ARY, "multiply", (p, s) -> { double r = 1; for (double v : p) r *= v; return r; }),
    MAX(Arity.N_ARY, "max", (p, s) -> Arrays.stream(p).max().orElse(Double.NaN)),
    MIN(Arity.N_ARY, "min", (p, s) -> Arrays.stream(p).min().orElse(Double.NaN)),
    // ── 2 inputs, order-sensitive (both operands required — an absent one yields NaN, see apply) ──
    SUB(Arity.BINARY, "subtract", (p, s) -> p[0] - s),
    DIV(Arity.BINARY, "divide", (p, s) -> p[0] / s),
    POWER(Arity.BINARY, "power", (p, s) -> Math.pow(p[0], s)),
    ROOT(Arity.BINARY, "root", (p, s) -> Math.pow(p[0], 1.0 / s)),
    LOG(Arity.BINARY, "log", (p, s) -> Math.log(p[0]) / Math.log(s)),
    // ── 1 input ──
    ABS(Arity.UNARY, "abs", (p, s) -> Math.abs(first(p))),
    CEIL(Arity.UNARY, "ceil", (p, s) -> Math.ceil(first(p))),
    FLOOR(Arity.UNARY, "floor", (p, s) -> Math.floor(first(p))),
    SIGN(Arity.UNARY, "sign", (p, s) -> Math.signum(first(p))),
    SIN(Arity.UNARY, "sin", (p, s) -> Math.sin(first(p))),
    COS(Arity.UNARY, "cos", (p, s) -> Math.cos(first(p))),
    TAN(Arity.UNARY, "tan", (p, s) -> Math.tan(first(p)));

    @FunctionalInterface
    private interface Compute {
        double apply(double[] primaries, double secondary);
    }

    private final Arity arity;
    private final String iconName;
    private final Compute compute;

    BuiltinOperator(Arity arity, String iconName, Compute compute) {
        this.arity = arity;
        this.iconName = iconName;
        this.compute = compute;
    }

    /** {@code primaries[0]} if present, else {@code NaN} — an absent operand of a unary op makes the result undefined,
     *  which propagates as NaN through the computation (binary ops require both operands; see {@link #apply}). */
    private static double first(double[] primaries) {
        return primaries.length > 0 ? primaries[0] : Double.NaN;
    }

    @Override
    public Component displayName() {
        return Component.translatable("createfactorycontroller.arithmetic_tube.operator." + name().toLowerCase(Locale.ROOT));
    }

    @Override public String iconName() { return iconName; }

    @Override public Arity arity() { return arity; }

    @Override
    public double apply(double[] primaries, OptionalDouble secondary) {
        if (arity == Arity.BINARY && (primaries.length == 0 || secondary.isEmpty())) return Double.NaN;
        return compute.apply(primaries, secondary.orElse(Double.NaN));
    }

    public BuiltinOperator nextInSameArity() {
        BuiltinOperator[] vals = values();
        for (int i = 1; i <= vals.length; i++) {
            BuiltinOperator cand = vals[(ordinal() + i) % vals.length];
            if (cand.arity == arity) return cand;
        }
        return this;
    }

    public static ArithmeticOperator byName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return SUM;
        }
    }
}
