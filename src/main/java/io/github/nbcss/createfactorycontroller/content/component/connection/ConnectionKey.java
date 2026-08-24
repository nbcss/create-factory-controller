package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.component.SyncCodecs;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.Nullable;

/**
 * Wire identity for delta sync. A {@link ConnectionGraph} holds at most one connection per
 * {@code (from, to, type)} triple, so the endpoint pair <b>plus its type</b> addresses an edge — the pair alone
 * no longer does, since a directed pair may carry several typed wires at once. Captured eagerly ({@link #of})
 * because a {@link Connection}'s endpoints are mutable (reverse / relocation re-key them) — the key must pin the
 * endpoints as they were at mark time.
 */
public record ConnectionKey(VirtualComponentPosition from, VirtualComponentPosition to, @Nullable Connection.Type type) {

    public static ConnectionKey of(Connection conn) {
        return new ConnectionKey(conn.from, conn.to, conn.type);
    }

    public void write(RegistryFriendlyByteBuf buf) {
        SyncCodecs.writePos(buf, from);
        SyncCodecs.writePos(buf, to);
        buf.writeUtf(type == null ? "" : type.name());
    }

    public static ConnectionKey read(RegistryFriendlyByteBuf buf) {
        VirtualComponentPosition from = SyncCodecs.readPos(buf);
        VirtualComponentPosition to = SyncCodecs.readPos(buf);
        return new ConnectionKey(from, to, Connection.Type.get(buf.readUtf()));   // null for an unknown/absent type name
    }
}
