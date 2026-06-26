package io.github.nbcss.createfactorycontroller.content.component;

import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionCapability;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionGraph;
import io.github.nbcss.createfactorycontroller.content.component.connection.ValidationResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Base for all virtual components. Holds the connection graph and its mechanics — identical for
 * gauges, links, and any future component — plus the reused-from-Deployer connection wiring.
 *
 * <p>Mirrors the role {@code AbstractPanelBehaviour}/{@code AbstractPanelSupportBehaviour} play in
 * Deployer (both fill a {@code PanelConnectionBuilder} in their constructor), but binds to a
 * {@link FactoryControllerBlockEntity} + {@link VirtualComponentPosition} instead of a block entity +
 * slot, and never touches a world.</p>
 */
public abstract class AbstractVirtualComponent implements VirtualComponentBehaviour {

    protected final FactoryControllerBlockEntity controller; // null on the client snapshot
    protected VirtualComponentPosition position;
    protected final Item item;

    /** Resolves a sibling component by board position for cross-component checks (connection validation). The server
     *  uses the controller's live map; the client menu injects its snapshot, since client behaviours carry no
     *  controller. See {@link #siblingAt}. */
    private Function<VirtualComponentPosition, VirtualComponentBehaviour> siblingLookup;

    /** The connection store backing this component when it has no controller (client snapshot); the server reads the
     *  controller's graph instead. See {@link #graph}. */
    private ConnectionGraph injectedGraph;

    protected AbstractVirtualComponent(FactoryControllerBlockEntity controller,
                                       VirtualComponentPosition position, Item item) {
        this.controller = controller;
        this.position = position;
        this.item = item;
    }

    // ── Identity ─────────────────────────────────────────────────────────────

    @Override public VirtualComponentPosition position() { return position; }
    @Override public void setPosition(VirtualComponentPosition pos) { this.position = pos; }
    @Override public Item getItem() { return item; }

    // ── Connection graph ──────────────────────────────────────────────────────

    @Override public Map<VirtualComponentPosition, Connection> targetedBy() { return graph().targetedBy(position); }
    @Override public Set<VirtualComponentPosition> targeting() { return graph().targeting(position); }

    /** The connection store backing this component: the controller's (server) or the injected one (client snapshot). */
    protected ConnectionGraph graph() {
        return controller != null ? controller.connectionGraph() : injectedGraph;
    }

    @Override public void setGraph(ConnectionGraph graph) { this.injectedGraph = graph; }

    @Override
    public void setSiblingLookup(Function<VirtualComponentPosition, VirtualComponentBehaviour> lookup) {
        this.siblingLookup = lookup;
    }

    /** The sibling at {@code pos}: the controller's live map on the server, else the injected client lookup. */
    protected VirtualComponentBehaviour siblingAt(VirtualComponentPosition pos) {
        if (controller != null) return controller.components.get(pos);
        return siblingLookup == null ? null : siblingLookup.apply(pos);
    }

    // No connection capabilities by default; gauges and links override. Validation is FAIL-CLOSED: a component must
    // explicitly permit a connection (by overriding the validateAs* below), so forgetting rules never silently allows
    // an unintended wire. (The ports() declaration gates which channels are even possible; these refine them.)
    @Override public List<ConnectionCapability> ports() { return List.of(); }
    @Override public ValidationResult validateAsSource(Connection.Type channel, VirtualComponentBehaviour sink) { return rejectByDefault(); }
    @Override public ValidationResult validateAsSink(Connection.Type channel, VirtualComponentBehaviour source) { return rejectByDefault(); }

    private static ValidationResult rejectByDefault() {
        return ValidationResult.fail(() -> CreateLang.translate("factory_panel.connection_aborted")
                .style(ChatFormatting.WHITE).component());
    }

    @Override
    public void disconnectAll() {
        List<Connection> affected = graph().connections().stream()
                .filter(c -> position.equals(c.from) || position.equals(c.to))
                .toList();
        graph().disconnect(position);
        for (Connection conn : affected) {
            VirtualComponentBehaviour source = position.equals(conn.from) ? this : siblingAt(conn.from);
            VirtualComponentBehaviour sink = position.equals(conn.to) ? this : siblingAt(conn.to);
            if (source != null) source.onDisconnectAsSource(conn);
            if (sink != null) sink.onDisconnectAsSink(conn);
        }
        if (controller != null) { controller.setChanged(); controller.sendData(); }
    }

    @Override
    public void onInteract() {
        int sharedMode = -1;
        for (VirtualComponentPosition targetPos : targeting()) {
            VirtualComponentBehaviour target = controller.components.get(targetPos);
            if (target == null) continue;
            Connection conn = target.targetedBy().get(position);
            if (conn == null) continue;
            if (sharedMode == -1)
                sharedMode = (conn.arrowBendMode + 1) % 4;
            conn.arrowBendMode = sharedMode;
        }
        if (sharedMode != -1) {
            controller.playWrenchRotateSound();
            controller.setChanged();
            controller.sendData();
        }
    }
}
