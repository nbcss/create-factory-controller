package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.component.SyncCodecs;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * Wire identity for delta sync. The {@link ConnectionGraph} holds at most one connection per
 * {@code (from, to)} pair (adding overwrites the pair regardless of kind), so the endpoint pair alone
 * addresses an edge. Captured eagerly ({@link #of}) because a {@link Connection}'s endpoints are mutable
 * (reverse / relocation re-key them) — the key must pin the endpoints as they were at mark time.
 */
public record ConnectionKey(VirtualComponentPosition from, VirtualComponentPosition to) {

    public static ConnectionKey of(Connection conn) {
        return new ConnectionKey(conn.from, conn.to);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        SyncCodecs.writePos(buf, from);
        SyncCodecs.writePos(buf, to);
    }

    public static ConnectionKey read(RegistryFriendlyByteBuf buf) {
        return new ConnectionKey(SyncCodecs.readPos(buf), SyncCodecs.readPos(buf));
    }
}
