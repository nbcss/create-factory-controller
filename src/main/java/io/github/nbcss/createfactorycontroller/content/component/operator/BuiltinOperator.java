package io.github.nbcss.createfactorycontroller.content.component.operator;

import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * The built-in {@link ArithmeticOperator}s, in three arity groups. Registration/cycle order is the enum order.
 */
public enum BuiltinOperator implements ArithmeticOperator {
    // ── N inputs, order-independent (no secondary) ──
    SUM(OperatorArity.NARY, "Σ", (p, s) -> Arrays.stream(p).sum()),
    PRODUCT(OperatorArity.NARY, "∏", (p, s) -> { double r = 1; for (double v : p) r *= v; return r; }),
    MAX(OperatorArity.NARY, "max", (p, s) -> Arrays.stream(p).max().orElse(0)),
    MIN(OperatorArity.NARY, "min", (p, s) -> Arrays.stream(p).min().orElse(0)),
    // ── 2 inputs, order-sensitive: primary OP secondary (an absent operand contributes the identity) ──
    ADD(OperatorArity.BINARY, "+", (p, s) -> first(p, 0) + s.orElse(0)),
    SUB(OperatorArity.BINARY, "-", (p, s) -> first(p, 0) - s.orElse(0)),
    MUL(OperatorArity.BINARY, "×", (p, s) -> first(p, 1) * s.orElse(1)),
    DIV(OperatorArity.BINARY, "÷", (p, s) -> first(p, 1) / s.orElse(1)),
    // ── 1 input ──
    ROUND(OperatorArity.UNARY, "rnd", (p, s) -> (double) Math.round(first(p, 0))),   // half-up
    FLOOR(OperatorArity.UNARY, "flr", (p, s) -> Math.floor(first(p, 0))),
    CEIL(OperatorArity.UNARY, "cei", (p, s) -> Math.ceil(first(p, 0))),
    ABS(OperatorArity.UNARY, "abs", (p, s) -> Math.abs(first(p, 0)));

    @FunctionalInterface
    private interface Compute {
        double apply(double[] primaries, OptionalDouble secondary);
    }

    private final OperatorArity arity;
    private final String symbol;
    private final Compute compute;

    BuiltinOperator(OperatorArity arity, String symbol, Compute compute) {
        this.arity = arity;
        this.symbol = symbol;
        this.compute = compute;
    }

    /** {@code primaries[0]} if present, else {@code identity} — an absent operand of a binary/unary op contributes
     *  the operator's identity, so a half-wired operator degrades to its present operand. */
    private static double first(double[] primaries, double identity) {
        return primaries.length > 0 ? primaries[0] : identity;
    }

    @Override public String id() { return name(); }

    @Override
    public Component displayName() {
        return Component.translatable("createfactorycontroller.arithmetic_tube.operator." + name().toLowerCase(Locale.ROOT));
    }

    @Override public String symbol() { return symbol; }

    @Override public OperatorArity arity() { return arity; }

    @Override
    public double apply(double[] primaries, OptionalDouble secondary) {
        return compute.apply(primaries, secondary);
    }

    /** The next operator in enum order (wraps) — the operation-mode cycle key. */
    public BuiltinOperator next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Operator for {@code id}, or {@link #SUM} if unknown (forward-compatible read). */
    public static ArithmeticOperator byId(String id) {
        try {
            return valueOf(id);
        } catch (IllegalArgumentException e) {
            return SUM;
        }
    }
}
