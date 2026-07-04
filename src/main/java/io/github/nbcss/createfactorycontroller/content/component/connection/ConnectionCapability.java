package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;

/**
 * A component's declared participation in one {@link Connection.Type}: the type plus its static
 * {@link Role role} ({@code SOURCE}/{@code SINK}/{@code BOTH}). A component returns its ports from
 * {@link VirtualComponentBehaviour#ports()}; {@link ConnectionResolver} reads them to decide whether a wire is
 * possible — no {@code instanceof} pair matrix.
 */
public record ConnectionCapability(Connection.Type type, Role role) {
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
