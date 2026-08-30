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
    SUM(OperatorArity.NARY, "add", (p, s) -> Arrays.stream(p).sum()),
    PRODUCT(OperatorArity.NARY, "multiply", (p, s) -> { double r = 1; for (double v : p) r *= v; return r; }),
    MAX(OperatorArity.NARY, "max", (p, s) -> Arrays.stream(p).max().orElse(0)),
    MIN(OperatorArity.NARY, "min", (p, s) -> Arrays.stream(p).min().orElse(0)),
    // ── 2 inputs, order-sensitive (both operands required — an absent one yields 0, see apply) ──
    SUB(OperatorArity.BINARY, "subtract", (p, s) -> p[0] - s),
    DIV(OperatorArity.BINARY, "divide", (p, s) -> p[0] / s),
    POWER(OperatorArity.BINARY, "power", (p, s) -> Math.pow(p[0], s)),
    ROOT(OperatorArity.BINARY, "root", (p, s) -> Math.pow(p[0], 1.0 / s)),
    LOG(OperatorArity.BINARY, "log", (p, s) -> Math.log(p[0]) / Math.log(s)),
    // ── 1 input ──
    ABS(OperatorArity.UNARY, "abs", (p, s) -> Math.abs(first(p))),
    CEIL(OperatorArity.UNARY, "ceil", (p, s) -> Math.ceil(first(p))),
    FLOOR(OperatorArity.UNARY, "floor", (p, s) -> Math.floor(first(p))),
    SIGN(OperatorArity.UNARY, "sign", (p, s) -> Math.signum(first(p))),
    SIN(OperatorArity.UNARY, "sin", (p, s) -> Math.sin(first(p))),        // radians
    COS(OperatorArity.UNARY, "cos", (p, s) -> Math.cos(first(p))),
    TAN(OperatorArity.UNARY, "tan", (p, s) -> Math.tan(first(p)));

    @FunctionalInterface
    private interface Compute {
        double apply(double[] primaries, double secondary);
    }

    private final OperatorArity arity;
    private final String iconName;
    private final Compute compute;

    BuiltinOperator(OperatorArity arity, String iconName, Compute compute) {
        this.arity = arity;
        this.iconName = iconName;
        this.compute = compute;
    }

    /** {@code primaries[0]} if present, else {@code identity} — an absent operand of a unary op degrades to the
     *  identity (binary ops require both operands; see {@link #apply}). */
    private static double first(double[] primaries) {
        return primaries.length > 0 ? primaries[0] : (double) 0;
    }

    @Override public String id() { return name(); }

    @Override
    public Component displayName() {
        return Component.translatable("createfactorycontroller.arithmetic_tube.operator." + name().toLowerCase(Locale.ROOT));
    }

    @Override public String iconName() { return iconName; }

    @Override public OperatorArity arity() { return arity; }

    @Override
    public double apply(double[] primaries, OptionalDouble secondary) {
        if (arity == OperatorArity.BINARY && (primaries.length == 0 || secondary.isEmpty())) return 0.0;
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

    public static ArithmeticOperator byId(String id) {
        try {
            return valueOf(id);
        } catch (IllegalArgumentException e) {
            return SUM;
        }
    }
}
