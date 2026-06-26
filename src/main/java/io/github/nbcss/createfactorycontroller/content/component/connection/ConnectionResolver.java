package io.github.nbcss.createfactorycontroller.content.component.connection;

import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The single, side-agnostic validator for a connection between two components. Used identically by the hover preview,
 * the client commit, and the server apply, so the server can never be more permissive than the UI.
 *
 * <p>Algorithm: pick the first shared {@link Connection.Type} (enum order), lock source/sink direction from port
 * capabilities (or caller intent when both directions are possible), then validate only that locked direction. On
 * success it also attaches the green
 * confirmation message, so the screen renders {@code result.validation().message()} the same way for both outcomes
 * (the message is a lazy supplier — the preview never builds it). See {@code CONNECTION_REWORK_PLAN.md}.</p>
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
            if (ca == null || cb == null) continue;   // not a shared type — try the next

            boolean abValid = ca.canSource() && cb.canSink();   // a → b
            boolean baValid = cb.canSource() && ca.canSink();   // b → a
            if (!abValid && !baValid) return Result.fail(ConnectionResolver::aborted);

            if (abValid && !baValid) return result(type, a, b);
            if (!abValid) return result(type, b, a);
            return creationSink == a ? result(type, b, a) : result(type, a, b);
        }
        return Result.fail(ConnectionResolver::aborted);   // no shared type
    }

    private static Result result(Connection.Type type, VirtualComponentBehaviour source, VirtualComponentBehaviour sink) {
        return new Result(type, source.position(), sink.position(), validate(type, source, sink));
    }

    /** Validates that the explicit {@code type/source/sink} setup is still legal. Does not resolve alternatives. */
    public static ValidationResult validate(@Nullable Connection.Type type,
                                            @Nullable VirtualComponentBehaviour source,
                                            @Nullable VirtualComponentBehaviour sink) {
        if (type == null || source == null || sink == null || source.position().equals(sink.position()))
            return ValidationResult.fail(ConnectionResolver::aborted);
        ConnectionCapability.Role sourceCap = capabilityOf(source, type);
        ConnectionCapability.Role sinkCap = capabilityOf(sink, type);
        if (sourceCap == null || sinkCap == null || !sourceCap.canSource() || !sinkCap.canSink())
            return ValidationResult.fail(ConnectionResolver::aborted);
        ValidationResult vr = source.validateAsSource(type, sink);
        if (vr.isSuccess()) vr = sink.validateAsSink(type, source);
        if (vr.isSuccess() && alreadyConnected(type, source, sink))
            vr = ValidationResult.fail(() -> CreateLang.translate("factory_panel.already_connected")
                    .style(ChatFormatting.RED).component());
        return vr.isSuccess() ? new ValidationResult(true, () -> successMessage(type, source, sink)) : vr;
    }

    /** This type's uniqueness policy against the current graph (Phase 1: per-component {@code targetedBy}). */
    private static boolean alreadyConnected(Connection.Type ch, VirtualComponentBehaviour source,
                                            VirtualComponentBehaviour sink) {
        return switch (ch.uniqueness()) {
            case DIRECTED -> sink.targetedBy().containsKey(source.position());
            case UNDIRECTED -> source.targetedBy().containsKey(sink.position())
                            || sink.targetedBy().containsKey(source.position());
        };
    }

    /** The green confirmation prompt for a valid connection: a redstone wire names the link; an ingredient wire names
     *  the two gauges' filters. Built lazily (only the client commit invokes it). */
    private static Component successMessage(Connection.Type ch, VirtualComponentBehaviour source,
                                            VirtualComponentBehaviour sink) {
        //fixme don't hardcode success message branch
        if (ch == Connection.Type.REDSTONE) {
            VirtualComponentBehaviour link = source instanceof VirtualRedstoneLinkBehaviour ? source : sink;
            String linkName = new ItemStack(link.getItem()).getHoverName().getString();
            return CreateLang.translate("factory_panel.link_connected", linkName).style(ChatFormatting.GREEN).component();
        }
        Component in  = source instanceof VirtualGaugeBehaviour g ? FluidCompat.filterName(g.filter) : Component.empty();
        Component out = sink   instanceof VirtualGaugeBehaviour g ? FluidCompat.filterName(g.filter) : Component.empty();
        return CreateLang.translate("factory_panel.panels_connected", in, out).style(ChatFormatting.GREEN).component();
    }

    /** {@code c}'s role for {@code ch}, or null if it has no such port. */
    @Nullable
    private static ConnectionCapability.Role capabilityOf(VirtualComponentBehaviour c, Connection.Type ch) {
        for (ConnectionCapability p : c.ports())
            if (p.type() == ch) return p.role();
        return null;
    }

    private static Component aborted() {
        return CreateLang.translate("factory_panel.connection_aborted").style(ChatFormatting.WHITE).component();
    }
}
