package io.github.nbcss.createfactorycontroller.content.component.arithmetic;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalDouble;

/**
 * A numeric operation an Arithmetic Tube can apply to its inputs. Kept an interface (not just the {@link
 * BuiltinOperator} enum) so an addon could contribute more operators later, mirroring the connection-type registry.
 */
public interface ArithmeticOperator {

    /** Stable identifier used for NBT/sync and lang-key derivation */
    String name();

    /** Human-readable name */
    Component displayName();

    /** Sprite base name under {@code arithmetic_tube/operators/} */
    String iconName();

    /** Input-shape group */
    Arity arity();

    /** Icon sprite */
    @Nullable
    default ResourceLocation icon() {
        return null;
    }

    /**
     * Computes the result from the resolved operand values.
     */
    double apply(double[] primaries, OptionalDouble secondary);

    /**
     * The input-shape group of an {@link ArithmeticOperator}. Governs how many operands an Arithmetic Tube accepts and
     * how a new input wire is routed (primary vs. secondary):
     *
     * <ul>
     *   <li>{@link #UNARY} — up to one primary input, no secondary. </li>
     *   <li>{@link #BINARY} — up to one primary input plus an optional secondary; order matters. </li>
     *   <li>{@link #N_ARY} — any number of primary inputs, no secondary; order irrelevant. </li>
     * </ul>
     */
    enum Arity {
        UNARY(1, false),
        BINARY(1, true),
        N_ARY(Integer.MAX_VALUE, false);

        /** Maximum number of entries the tube's {@code primaryInputs} list may hold under this arity. */
        public final int maxPrimary;
        /** Whether this arity uses the tube's single optional {@code secondaryInput} slot. */
        public final boolean allowsSecondary;

        Arity(int maxPrimary, boolean allowsSecondary) {
            this.maxPrimary = maxPrimary;
            this.allowsSecondary = allowsSecondary;
        }
    }
}
