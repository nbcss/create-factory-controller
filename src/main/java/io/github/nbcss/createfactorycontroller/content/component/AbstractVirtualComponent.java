package io.github.nbcss.createfactorycontroller.content.component;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionCapability;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionGraph;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionResolver;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionValue;
import io.github.nbcss.createfactorycontroller.content.component.connection.ValidationResult;
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
    @Override public java.util.Collection<Connection> outgoingConnections() { return graph().outgoingConnections(position); }

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
    // an unintended wire. (The ports() declaration gates which types are even possible; these refine them.)
    @Override public List<ConnectionCapability> ports() { return List.of(); }

    /** Default wire-endpoint label: the component's item name (a link shows "Redstone Link"). Gauges override to
     *  name their ingredient instead. */
    @Override public ValidationResult validateAsSource(Connection.Type type, VirtualComponentBehaviour sink) { return rejectByDefault(this, sink); }
    @Override public ValidationResult validateAsSink(Connection.Type type, VirtualComponentBehaviour source) { return rejectByDefault(source, this); }

    private static ValidationResult rejectByDefault(VirtualComponentBehaviour source, VirtualComponentBehaviour sink) {
        return ValidationResult.fail(() -> ConnectionResolver.cannotConnect(source, sink));
    }

    // ── Signal propagation ──────────────────────────────────────────────────────
    // Not a source/sink of any type by default; gauges and links override. The generic publisher below is the single
    // place that touches edges — sources call it on a real output change (and on connect/load).

    @Override public ConnectionValue outputValue(Connection.Type type) { return null; }
    @Override public void onInputChanged(Connection.Type type) {}

    @Override
    public final void publish(Connection.Type type) {
        ConnectionValue out = outputValue(type);
        if (out == null) return;                       // I'm not a source of this type
        // Eager edge write, deferred fold: a new edge enters at its fold-identity default (UNPOWERED for the redstone
        // OR), so writing the source's value here and flagging the sink dirty is enough — the sink folds once at the
        // next settle, reading every edge already up to date (no per-source double-fold, no mid-tick glitch).
        for (Connection c : graph().outgoingConnections(position, type))
            if (c.setValue(out) && controller != null)
                controller.markSinkDirty(c.to, type);
    }

    @Override
    public void disconnectAll() {
        List<Connection> affected = graph().connections().stream()
                .filter(c -> position.equals(c.from) || position.equals(c.to))
                .toList();
        graph().disconnect(position);
        // Flag every surviving sink that lost an incoming edge from this component (a source partner needs nothing),
        // then settle once. Off-controller (client) — which never reaches here in practice — folds directly.
        for (Connection conn : affected) {
            if (position.equals(conn.to)) continue;       // we were the sink → the source side is unaffected
            VirtualComponentBehaviour sink = siblingAt(conn.to);
            if (sink == null) continue;
            if (controller != null) controller.markSinkDirty(conn.to, conn.type);
            else sink.onInputChanged(conn.type);
        }
        if (controller != null) { controller.settleConnections(); controller.setChanged(); controller.sendData(); }
    }

    @Override
    public void cycleArrowMode() {
        List<Connection> toCycle = connectionsToCycle();
        if (toCycle.isEmpty()) return;
        int sharedMode = (toCycle.getFirst().arrowBendMode + 1) % 4;   // auto (-1) → 0 on first press
        for (Connection conn : toCycle) conn.arrowBendMode = sharedMode;
        if (controller != null) {
            controller.setChanged();
            controller.sendData();
        }
    }

    @Override
    public void cycleOperationMode() {}

    /** The connections whose arrow-bend this component's cycle-arrow key cycles. By default its outgoing wires; gauges
     *  also include incoming redstone (whose RECEIVE-link end can't cycle — see {@code VirtualGaugeBehaviour}). */
    public List<Connection> connectionsToCycle() {
        List<Connection> result = new java.util.ArrayList<>();
        for (VirtualComponentPosition targetPos : targeting()) {
            Connection conn = graph().get(position, targetPos);
            if (conn != null) result.add(conn);
        }
        return result;
    }
}
