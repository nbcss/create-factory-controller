package io.github.nbcss.createfactorycontroller.content.component.connection;

import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The single, side-agnostic validator for a connection between two components. Used identically by the hover preview,
 * the client commit, and the server apply, so the server can never be more permissive than the UI.
 *
 * <p>Algorithm: pick the first shared {@link Connection.Type} (enum order) whose ports admit a valid orientation →
 * resolve direction from the endpoints' {@link VirtualComponentBehaviour#liveRole live roles} (decisive beats
 * deferring; both defer → the caller's creation intent) → run {@code source.validateAsSource} then
 * {@code sink.validateAsSink} → check the type's uniqueness policy. On success it also attaches the green
 * confirmation message, so the screen renders {@code result.validation().message()} the same way for both outcomes
 * (the message is a lazy supplier — the preview never builds it). See {@code CONNECTION_REWORK_PLAN.md}.</p>
 */
public final class ConnectionValidator {

    /** Outcome of {@link #validate}: the resolved type + directed endpoints, and the {@link ValidationResult}
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
     * Validates wiring {@code a} and {@code b}. {@code creationSink} is the component whose GUI started the
     * connection (the user's intended target) — the direction fallback when neither end is decisive (e.g. gauge →
     * gauge). Order of {@code a}/{@code b} is otherwise irrelevant.
     */
    public static Result validate(@Nullable VirtualComponentBehaviour a, @Nullable VirtualComponentBehaviour b,
                                  @Nullable VirtualComponentBehaviour creationSink) {
        if (a == null || b == null || a.position().equals(b.position())) return Result.fail(ConnectionValidator::aborted);

        for (Connection.Type ch : Connection.Type.values()) {
            ConnectionCapability.Role ca = capabilityOf(a, ch);
            ConnectionCapability.Role cb = capabilityOf(b, ch);
            if (ca == null || cb == null) continue;   // not a shared type — try the next

            // First shared type wins. Resolve which end is the source.
            boolean abValid = ca.canSource() && cb.canSink();   // a → b
            boolean baValid = cb.canSource() && ca.canSink();   // b → a
            VirtualComponentBehaviour source, sink;
            if (abValid && !baValid)      { source = a; sink = b; }
            else if (baValid && !abValid) { source = b; sink = a; }
            else if (!abValid)            { return Result.fail(ConnectionValidator::aborted); }   // no valid orientation
            else { boolean aSource = resolveDirection(a, b, ch, creationSink);
                   source = aSource ? a : b; sink = aSource ? b : a; }

            ValidationResult vr = source.validateAsSource(ch, sink);
            if (vr.isSuccess()) vr = sink.validateAsSink(ch, source);
            if (vr.isSuccess() && alreadyConnected(ch, source, sink))
                vr = ValidationResult.fail(() -> CreateLang.translate("factory_panel.already_connected")
                        .style(ChatFormatting.RED).component());
            if (vr.isSuccess())
                vr = new ValidationResult(true, () -> successMessage(ch, source, sink));
            return new Result(ch, source.position(), sink.position(), vr);
        }
        return Result.fail(ConnectionValidator::aborted);   // no shared type
    }

    /** Whether {@code a} is the source (else {@code b}) when both orientations are role-valid. */
    private static boolean resolveDirection(VirtualComponentBehaviour a, VirtualComponentBehaviour b,
                                            Connection.Type ch, @Nullable VirtualComponentBehaviour creationSink) {
        ConnectionCapability.Role la = a.liveRole(ch), lb = b.liveRole(ch);
        if (la == ConnectionCapability.Role.SOURCE) return true;
        if (la == ConnectionCapability.Role.SINK)   return false;
        if (lb == ConnectionCapability.Role.SOURCE) return false;
        if (lb == ConnectionCapability.Role.SINK)   return true;
        // Both defer (BOTH) → a is the source unless a is the intended sink.
        return creationSink != a;
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
        if (ch == Connection.Type.REDSTONE) {
            VirtualComponentBehaviour link = source instanceof VirtualRedstoneLinkBehaviour ? source : sink;
            String linkName = new ItemStack(BuiltInRegistries.ITEM.get(link.getItemId())).getHoverName().getString();
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
