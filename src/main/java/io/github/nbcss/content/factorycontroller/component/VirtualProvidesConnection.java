package io.github.nbcss.content.factorycontroller.component;

import net.liukrast.deployer.lib.logistics.board.connection.PanelConnection;
import net.liukrast.deployer.lib.logistics.board.connection.PanelConnectionBuilder;

import java.util.Optional;
import java.util.Set;

/**
 * Virtual-state mirror of Deployer's {@code ProvidesConnection}.
 *
 * <p>Deployer's interface is sound, but its static helpers ({@code getValue},
 * {@code getAllValuesWithSource}) dereference {@code getWorld()} / {@code FactoryPanelBehaviour}
 * and return a record nested inside {@code AbstractPanelBehaviour} — all of which assume a real
 * block entity in a loaded world. The virtual panel has no world, so we re-declare the same
 * contract here while reusing Deployer's genuinely decoupled types: {@link PanelConnection}
 * (typed connection token + wire color) and {@link PanelConnectionBuilder} (pure map filler).</p>
 *
 * <p>This keeps semantic compatibility with Deployer connection types (redstone, numbers,
 * stock, …) so a component can declare the same I/O a real gauge would, while the value
 * aggregation across the graph is implemented virtually in {@link AbstractVirtualComponent}.</p>
 */
public interface VirtualProvidesConnection {

    /**
     * Declares this component's connection inputs/outputs into the builder.
     * Mirrors {@code AbstractPanelBehaviour#addConnections}. Called once during construction.
     */
    void addConnections(PanelConnectionBuilder builder);

    /** Ordered set of connection types this component can consume. */
    Set<PanelConnection<?>> getInputConnections();

    /** Ordered set of connection types this component can provide. */
    Set<PanelConnection<?>> getOutputConnections();

    /** Current value this component outputs for {@code connection}, or empty if it provides none. */
    <T> Optional<T> getConnectionValue(PanelConnection<T> connection);
}
