package io.github.nbcss.createfactorycontroller.content.component.connection;

import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.component.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
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
     *
     * <p>The default type is the highest-scoring shared, orientable type (score = {@code source.sourceOrder ×
     * sink.sinkOrder}, ties broken by registry order).
     */
    public static Result resolve(@Nullable VirtualComponentBehaviour a,
                                 @Nullable VirtualComponentBehaviour b,
                                 @Nullable VirtualComponentBehaviour creationSink) {
        if (a == null || b == null || a.position().equals(b.position())) return Result.fail(ConnectionResolver::aborted);
        List<Candidate> candidates = candidates(a, b, creationSink);
        if (candidates.isEmpty()) return Result.fail(() -> cannotConnect(a, b));   // no shared/orientable type
        Candidate top = candidates.get(0);
        return result(top.type(), top.source(), top.sink(), true);
    }

    /**
     * Resolves wiring {@code a} and {@code b} constrained to one explicit {@code type} (the type-override UI).
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
        ConnectionCapability pa = portOf(a, type);
        ConnectionCapability pb = portOf(b, type);
        if (pa == null || pb == null) return Result.fail(() -> cannotConnect(a, b));
        Oriented o = orient(pa.role(), pb.role(), a, b, creationSink);
        if (o == null) return Result.fail(() -> cannotConnect(a, b));
        return result(type, o.source(), o.sink(), rejectExisting);
    }

    /** Every valid type for this pair in score (priority) order. */
    public static List<Connection.Type> possibleTypes(@Nullable VirtualComponentBehaviour a,
                                                      @Nullable VirtualComponentBehaviour b,
                                                      @Nullable VirtualComponentBehaviour creationSink) {
        List<Connection.Type> out = new ArrayList<>();
        for (Candidate c : candidates(a, b, creationSink))
            if (validate(c.type(), c.source(), c.sink(), false).isSuccess()) out.add(c.type());
        return out;
    }

    /** A shared type oriented to its one legal direction, tagged with its priority {@link #score}. */
    private record Candidate(Connection.Type type, VirtualComponentBehaviour source,
                             VirtualComponentBehaviour sink, double score) {}

    /** The oriented endpoints of a wire: which component ends up the source, which the sink. */
    private record Oriented(VirtualComponentBehaviour source, VirtualComponentBehaviour sink) {}

    /** Every shared, orientable type for the pair, highest score first (stable → ties keep registry order). */
    private static List<Candidate> candidates(@Nullable VirtualComponentBehaviour a,
                                              @Nullable VirtualComponentBehaviour b,
                                              @Nullable VirtualComponentBehaviour creationSink) {
        List<Candidate> out = new ArrayList<>();
        if (a == null || b == null || a.position().equals(b.position())) return out;
        for (Connection.Type type : Connection.Type.values()) {
            ConnectionCapability pa = portOf(a, type);
            ConnectionCapability pb = portOf(b, type);
            if (pa == null || pb == null) continue;                      // not a shared type
            Oriented o = orient(pa.role(), pb.role(), a, b, creationSink);
            if (o == null) continue;                                     // shared but no legal direction
            ConnectionCapability srcPort = o.source() == a ? pa : pb;
            ConnectionCapability sinkPort = o.sink() == a ? pa : pb;
            out.add(new Candidate(type, o.source(), o.sink(), srcPort.sourceOrder() * sinkPort.sinkOrder()));
        }
        out.sort(Comparator.comparingDouble(Candidate::score).reversed());   // stable: equal scores keep registry order
        return out;
    }

    /** Picks the one legal direction for a KNOWN-shared type: a decisive role wins, else the {@code creationSink}
     *  tiebreak. Returns {@code null} if neither direction's capabilities line up (e.g. both source-only). */
    @Nullable
    private static Oriented orient(ConnectionCapability.Role ca, ConnectionCapability.Role cb,
                                   VirtualComponentBehaviour a, VirtualComponentBehaviour b,
                                   @Nullable VirtualComponentBehaviour creationSink) {
        boolean abValid = ca.canSource() && cb.canSink();   // a → b
        boolean baValid = cb.canSource() && ca.canSink();   // b → a
        if (!abValid && !baValid) return null;
        if (abValid && !baValid) return new Oriented(a, b);
        if (!abValid) return new Oriented(b, a);
        return creationSink == a ? new Oriented(b, a) : new Oriented(a, b);
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
        ConnectionCapability sourcePort = portOf(source, type);
        ConnectionCapability sinkPort = portOf(sink, type);
        if (sourcePort == null || sinkPort == null || !sourcePort.role().canSource() || !sinkPort.role().canSink())
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

    /** {@code c}'s port (role + order scores) for {@code type}, or null if it has no such port. */
    @Nullable
    private static ConnectionCapability portOf(VirtualComponentBehaviour c, Connection.Type type) {
        for (ConnectionCapability p : c.ports())
            if (type.equals(p.type())) return p;
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
