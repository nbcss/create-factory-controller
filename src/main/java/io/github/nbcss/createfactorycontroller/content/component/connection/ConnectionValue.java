package io.github.nbcss.createfactorycontroller.content.component.connection;

/**
 * A value transported along a {@link Connection} of a signal {@link Connection.Type} (REDSTONE today; an INTEGER
 * channel later). A source component computes one via
 * {@link io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour#outputValue},
 * {@code publish} writes it onto the outgoing edges, and the sink reads it back via {@link Connection#value()}. The
 * only contract is value-equality — {@link Connection#setValue} uses it for change detection — so a {@code record}
 * (or, as for redstone, an {@code enum}) is the natural implementor.
 */
public sealed interface ConnectionValue permits RedstoneConnection.State {
}
