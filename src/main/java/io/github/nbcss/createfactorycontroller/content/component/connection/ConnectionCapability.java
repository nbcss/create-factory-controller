package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;

/**
 * A component's declared participation in one {@link Connection.Type}: the type, its static
 * {@link Role role} ({@code SOURCE}/{@code SINK}/{@code BOTH}), and two priority scores. A component returns its ports
 * from {@link VirtualComponentBehaviour#ports()}; {@link ConnectionResolver} reads them to decide whether a wire is
 * possible — no {@code instanceof} pair matrix.
 *
 * <p>{@code sourceOrder} is read when this port is the <b>source</b> end of an oriented wire, {@code sinkOrder} when it
 * is the <b>sink</b> end. For a candidate type on a pair the resolver ranks by the product
 * {@code source.sourceOrder × sink.sinkOrder}; the highest is the default (ties fall back to registry order). Orders
 * are {@code ≥ 0}; a {@code 0} keeps a type reachable in the picker but never auto-default. Leaving both at {@code 1.0}
 * (the two-arg constructor) reproduces the legacy registry-order priority.
 */
public record ConnectionCapability(Connection.Type type, Role role, double sourceOrder, double sinkOrder) {
    /** Port with the neutral order {@code 1.0} in both directions (registry order alone then decides). */
    public ConnectionCapability(Connection.Type type, Role role) {
        this(type, role, 1.0, 1.0);
    }

    /** Port with a single {@code order} applied to whichever direction its role uses (source and sink alike). */
    public ConnectionCapability(Connection.Type type, Role role, double order) {
        this(type, role, order, order);
    }

    /**
     * A component port's static participation in a {@link Connection.Type}: may this port be a source and/or a sink?
     * {@link #BOTH} = either. Dynamic state belongs in {@code validateAsSource}/{@code validateAsSink}, which the
     * resolver uses to infer direction.
     */
    public enum Role {
        SOURCE, SINK, BOTH;

        public boolean canSource() { return this == SOURCE || this == BOTH; }
        public boolean canSink()   { return this == SINK   || this == BOTH; }
    }
}
