package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;

/**
 * A component's declared participation in one {@link Connection.Type}: the type plus its static
 * {@link Role role} ({@code SOURCE}/{@code SINK}/{@code BOTH}). A component returns its ports from
 * {@link VirtualComponentBehaviour#ports()}; {@link ConnectionValidator} reads them to decide whether a wire is
 * possible — no {@code instanceof} pair matrix.
 */
public record ConnectionCapability(Connection.Type type, Role role) {
    /**
     * A component port's participation in a {@link Connection.Type}. Serves two purposes:
     * <ul>
     *   <li><b>Capability</b> (static, for validity): may this port be a source and/or a sink? {@link #BOTH} = either.</li>
     *   <li><b>Live role</b> (dynamic, for direction): a component's {@link VirtualComponentBehaviour#liveRole} returns a
     *       <i>decisive</i> {@link #SOURCE}/{@link #SINK} when it knows its role, or {@link #BOTH} when it defers. A
     *       decisive role beats {@code BOTH}, so e.g. a redstone link's Send/Receive mode dictates the wire's direction
     *       without any priority/weight.</li>
     * </ul>
     */
    public enum Role {
        SOURCE, SINK, BOTH;

        public boolean canSource() { return this == SOURCE || this == BOTH; }
        public boolean canSink()   { return this == SINK   || this == BOTH; }
    }
}
