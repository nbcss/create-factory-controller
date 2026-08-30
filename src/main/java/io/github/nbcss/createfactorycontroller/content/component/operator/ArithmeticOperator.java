package io.github.nbcss.createfactorycontroller.content.component.operator;

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
    String id();

    /** Human-readable name */
    Component displayName();

    /** Sprite base name under {@code arithmetic_tube/operators/} */
    String iconName();

    /** Input-shape group */
    OperatorArity arity();

    /** Icon sprite */
    @Nullable
    default ResourceLocation icon() {
        return null;
    }

    /**
     * Computes the result from the resolved operand values.
     */
    double apply(double[] primaries, OptionalDouble secondary);
}
