package io.github.nbcss.createfactorycontroller.content.component;

import io.github.nbcss.createfactorycontroller.content.block.ComponentHolder;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionCapability;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionGraph;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionKey;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionResolver;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionValue;
import io.github.nbcss.createfactorycontroller.content.component.connection.ValidationResult;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base implementation for all virtual components.
 */
public abstract class AbstractVirtualComponent implements VirtualComponentBehaviour {

    protected final FactoryControllerBlockEntity controller; // null on the client snapshot
    protected VirtualComponentPosition position;
    protected final Item item;
    private ComponentHolder holder;

    /** The connection store backing this component when it has no controller (client snapshot); the server reads the
     *  controller's graph instead. See {@link #graph}. */
    private ConnectionGraph injectedGraph;

    protected AbstractVirtualComponent(FactoryControllerBlockEntity controller,
                                       VirtualComponentPosition position, Item item) {
        this.controller = controller;
        this.holder = controller;
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
    public void setHolder(ComponentHolder holder) {
        this.holder = holder;
    }

    /** The sibling at {@code pos}: the controller's live map on the server, else the injected client lookup. */
    protected VirtualComponentBehaviour siblingAt(VirtualComponentPosition pos) {
        return holder == null ? null : holder.componentAt(pos);
    }

    // No connection capabilities by default
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
        for (Connection c : graph().outgoingConnections(position, type))
            if (c.setValue(out) && controller != null) {
                controller.markSinkDirty(c.to, type);
                controller.syncConnection(ConnectionKey.of(c));   // the edge's carried value is rendered
            }
    }

    @Override
    public void disconnectAll() {
        List<Connection> affected = graph().connections().stream()
                .filter(c -> position.equals(c.from) || position.equals(c.to))
                .toList();
        graph().disconnect(position);
        for (Connection conn : affected) {
            if (controller != null) controller.syncConnectionRemoved(ConnectionKey.of(conn));
            if (position.equals(conn.to)) continue;
            VirtualComponentBehaviour sink = siblingAt(conn.to);
            if (sink == null) continue;
            if (controller != null) controller.markSinkDirty(conn.to, conn.type);
            else sink.onInputChanged(conn.type);
        }
        // Callers mark the component itself (removed / FULL); this only marks the wires it took down.
        if (controller != null) { controller.settleConnections(); controller.setChanged(); }
    }

    @Override
    public void cycleArrowMode() {
        List<Connection> toCycle = connectionsToCycle();
        if (toCycle.isEmpty()) return;
        int sharedMode = (toCycle.getFirst().arrowBendMode + 1) % 4;   // auto (-1) → 0 on first press
        for (Connection conn : toCycle) conn.arrowBendMode = sharedMode;
        if (controller != null) {
            controller.setChanged();
            for (Connection conn : toCycle) controller.syncConnection(ConnectionKey.of(conn));
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
