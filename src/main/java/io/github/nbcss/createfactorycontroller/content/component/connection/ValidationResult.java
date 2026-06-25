package io.github.nbcss.createfactorycontroller.content.component.connection;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The outcome of a connection validation: success, or failure with a <b>lazy</b> error message. The message is a
 * {@link Supplier} so a check that only needs {@link #isSuccess()} (e.g. the hover preview, run every frame) never
 * builds the {@link Component}. Returned by {@code validateAsSource}/{@code validateAsSink} and carried by
 * {@link ConnectionValidator.Result}.
 */
public record ValidationResult(boolean isSuccess, Supplier<@Nullable Component> message) {

    public static final ValidationResult SUCCESS = new ValidationResult(true);

    public ValidationResult(boolean isSuccess) {
        this(isSuccess, () -> null);
    }

    public static ValidationResult fail(Supplier<Component> message) {
        return new ValidationResult(false, message);
    }
}
