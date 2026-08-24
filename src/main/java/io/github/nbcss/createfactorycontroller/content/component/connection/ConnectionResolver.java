package io.github.nbcss.createfactorycontroller.content.component.connection;

import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.component.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The single, side-agnostic validator for a connection between two components. Used identically by the hover preview,
 * the client commit, and the server apply, so the server can never be more permissive than the UI.
 */
public final class ConnectionResolver {

    /** Outcome of {@link #resolve}: the resolved type + directed endpoints, and the {@link ValidationResult}
     *  (carrying a lazy success or failure message). */
    public record Result(@Nullable Connection.Type type,
                         @Nullable VirtualComponentPosition source,
                         @Nullable VirtualComponentPosition sink,
                         ValidationResult validation) {
        public boolean ok() { return type != null && validation.isSuccess(); }

        static Result fail(Supplier<Component> message) {
            return new Result(null, null, null, ValidationResult.fail(message));
        }
    }

    /**
     * Resolves wiring {@code a} and {@code b}. {@code creationSink} is the component whose GUI started the
     * connection (the user's intended target) — the direction fallback when neither end is decisive (e.g. gauge →
     * gauge). Order of {@code a}/{@code b} is otherwise irrelevant.
     */
    public static Result resolve(@Nullable VirtualComponentBehaviour a,
                                 @Nullable VirtualComponentBehaviour b,
                                 @Nullable VirtualComponentBehaviour creationSink) {
        if (a == null || b == null || a.position().equals(b.position())) return Result.fail(ConnectionResolver::aborted);

        for (Connection.Type type : Connection.Type.values()) {
            ConnectionCapability.Role ca = capabilityOf(a, type);
            ConnectionCapability.Role cb = capabilityOf(b, type);
            if (ca == null || cb == null) continue;             // not a shared type — try the next
            return orientShared(type, ca, cb, a, b, creationSink, true);   // first shared type decides (short-circuit)
        }
        return Result.fail(() -> cannotConnect(a, b));   // no shared type
    }

    /**
     * Resolves wiring {@code a} and {@code b} constrained to one explicit {@code type} (the type-override UI). Considers
     * only that type — a failure here never falls through to another type. Its orientation and validation match the
     * server's {@link #validate}, so a picked type that {@link Result#ok()}s here will pass the server re-check.
     */
    public static Result resolveAs(@Nullable VirtualComponentBehaviour a,
                                   @Nullable VirtualComponentBehaviour b,
                                   @Nullable VirtualComponentBehaviour creationSink,
                                   Connection.Type type) {
        return resolveAs(a, b, creationSink, type, true);
    }

    private static Result resolveAs(@Nullable VirtualComponentBehaviour a,
                                    @Nullable VirtualComponentBehaviour b,
                                    @Nullable VirtualComponentBehaviour creationSink,
                                    Connection.Type type,
                                    boolean rejectExisting) {
        if (a == null || b == null || a.position().equals(b.position())) return Result.fail(ConnectionResolver::aborted);
        ConnectionCapability.Role ca = capabilityOf(a, type);
        ConnectionCapability.Role cb = capabilityOf(b, type);
        if (ca == null || cb == null) return Result.fail(() -> cannotConnect(a, b));
        return orientShared(type, ca, cb, a, b, creationSink, rejectExisting);
    }

    /** Every possible type for this pair in registry (priority) order, including types already connected. */
    public static List<Connection.Type> possibleTypes(@Nullable VirtualComponentBehaviour a,
                                                      @Nullable VirtualComponentBehaviour b,
                                                      @Nullable VirtualComponentBehaviour creationSink) {
        List<Connection.Type> out = new ArrayList<>();
        for (Connection.Type type : Connection.Type.values())
            if (resolveAs(a, b, creationSink, type, false).ok()) out.add(type);
        return out;
    }

    /** Orients a KNOWN-shared {@code type} (both roles non-null): picks the legal direction — a decisive role wins,
     *  else the {@code creationSink} tiebreak — and validates. Fails if neither direction's capabilities line up. */
    private static Result orientShared(Connection.Type type,
                                       ConnectionCapability.Role ca, ConnectionCapability.Role cb,
                                       VirtualComponentBehaviour a, VirtualComponentBehaviour b,
                                       @Nullable VirtualComponentBehaviour creationSink,
                                       boolean rejectExisting) {
        boolean abValid = ca.canSource() && cb.canSink();   // a → b
        boolean baValid = cb.canSource() && ca.canSink();   // b → a
        if (!abValid && !baValid) return Result.fail(() -> cannotConnect(a, b));
        if (abValid && !baValid) return result(type, a, b, rejectExisting);
        if (!abValid) return result(type, b, a, rejectExisting);
        return creationSink == a
                ? result(type, b, a, rejectExisting)
                : result(type, a, b, rejectExisting);
    }

    private static Result result(Connection.Type type, VirtualComponentBehaviour source,
                                 VirtualComponentBehaviour sink, boolean rejectExisting) {
        return new Result(type, source.position(), sink.position(), validate(type, source, sink, rejectExisting));
    }

    /** Validates that the explicit {@code type/source/sink} setup is still legal. Does not resolve alternatives. */
    public static ValidationResult validate(@Nullable Connection.Type type,
                                            @Nullable VirtualComponentBehaviour source,
                                            @Nullable VirtualComponentBehaviour sink) {
        return validate(type, source, sink, true);
    }

    private static ValidationResult validate(@Nullable Connection.Type type,
                                             @Nullable VirtualComponentBehaviour source,
                                             @Nullable VirtualComponentBehaviour sink,
                                             boolean rejectExisting) {
        if (type == null || source == null || sink == null || source.position().equals(sink.position()))
            return ValidationResult.fail(ConnectionResolver::aborted);
        ConnectionCapability.Role sourceCap = capabilityOf(source, type);
        ConnectionCapability.Role sinkCap = capabilityOf(sink, type);
        if (sourceCap == null || sinkCap == null || !sourceCap.canSource() || !sinkCap.canSink())
            return ValidationResult.fail(() -> cannotConnect(source, sink));
        ValidationResult vr = source.validateAsSource(type, sink);
        if (vr.isSuccess()) vr = sink.validateAsSink(type, source);
        if (vr.isSuccess() && rejectExisting && alreadyConnected(source, sink, type))
            vr = ValidationResult.fail(() -> Component.translatable("createfactorycontroller.connection.already_connected")
                    .withStyle(ChatFormatting.RED));
        return vr.isSuccess() ? new ValidationResult(true, () -> type.successMessage(source, sink)) : vr;
    }

    /** Whether the exact directed edge {@code source → sink} of {@code type} already exists. A wire of a <b>different</b>
     *  type on the same pair is allowed (a gauge can feed items and drive a count at once), and the reverse
     *  {@code sink → source} is a separate, independently-allowed wire (so two tubes can point at each other) — the
     *  single direction a redstone link permits is enforced by its decisive capability role, not here. */
    private static boolean alreadyConnected(VirtualComponentBehaviour source, VirtualComponentBehaviour sink, Connection.Type type) {
        return sink.incomingConnection(source.position(), type) != null;
    }

    /** {@code c}'s role for {@code type}, or null if it has no such port. */
    @Nullable
    private static ConnectionCapability.Role capabilityOf(VirtualComponentBehaviour c, Connection.Type type) {
        for (ConnectionCapability p : c.ports())
            if (type.equals(p.type())) return p.role();
        return null;
    }

    private static Component aborted() {
        return CreateLang.translate("factory_panel.connection_aborted").style(ChatFormatting.RED).component();
    }

    /** "{@code a} cannot connect to {@code b}" — the generic failure for two identified, incompatible components
     *  (no shared/matching connection type or direction).*/
    public static Component cannotConnect(VirtualComponentBehaviour a, VirtualComponentBehaviour b) {
        return Component.translatable("createfactorycontroller.connection.cannot_connect", a.getName(), b.getName())
                .withStyle(ChatFormatting.RED);
    }
}
