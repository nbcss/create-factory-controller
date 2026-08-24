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

    /** Stable identifier used for NBT/sync and lang-key derivation (e.g. {@code "SUM"}). */
    String id();

    /** Human-readable name (lang key {@code createfactorycontroller.arithmetic_tube.operator.<id-lower>}). */
    Component displayName();

    /** Short glyph rendered on the component while there is no icon (see {@link #icon()}). Placeholder text for now. */
    String symbol();

    /** Input-shape group — decides input capacity and routing. */
    OperatorArity arity();

    /** Icon sprite, or {@code null} until art exists → the widget renders {@link #symbol()} text instead. Declared
     *  now per the interface contract; unused this stage. */
    @Nullable
    default ResourceLocation icon() {
        return null;
    }

    /**
     * Computes the result from the resolved operand values. {@code primaries} holds the current values of the tube's
     * {@code primaryInputs} (in order); {@code secondary} is present only for {@link OperatorArity#BINARY}. The tube
     * guards the all-empty case (→ 0) before calling this, and passes an absent operand as this operator's identity,
     * so N-group folds and unary reads always see at least one value here.
     */
    double apply(double[] primaries, OptionalDouble secondary);
}
