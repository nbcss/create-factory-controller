package io.github.nbcss.createfactorycontroller.content.component.operator;

/**
 * The input-shape group of an {@link ArithmeticOperator}. Governs how many operands an Arithmetic Tube accepts and
 * how a new input wire is routed (primary vs. secondary):
 *
 * <ul>
 *   <li>{@link #UNARY} — up to one primary input, no secondary. </li>
 *   <li>{@link #BINARY} — up to one primary input plus an optional secondary; order matters. </li>
 *   <li>{@link #NARY} — any number of primary inputs, no secondary; order irrelevant. </li>
 * </ul>
 */
public enum OperatorArity {
    UNARY(1, false),
    BINARY(1, true),
    NARY(Integer.MAX_VALUE, false);

    /** Maximum number of entries the tube's {@code primaryInputs} list may hold under this arity. */
    public final int maxPrimary;
    /** Whether this arity uses the tube's single optional {@code secondaryInput} slot. */
    public final boolean allowsSecondary;

    OperatorArity(int maxPrimary, boolean allowsSecondary) {
        this.maxPrimary = maxPrimary;
        this.allowsSecondary = allowsSecondary;
    }
}
