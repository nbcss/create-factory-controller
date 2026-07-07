package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The controller-level store of every connection on the board (Phase 2 — connections live here, not on each
 * component). There is no per-connection owner: a wire is a directed {@code source → sink} edge, and its direction is
 * resolved from the endpoints' port roles (see {@code ConnectionResolver}), not from where it happens to be stored.
 *
 * <p>Two indexes are kept in lock-step: {@link #targetedBy} (sink → source → payload, the "incoming" view a component
 * exposes) and {@link #targeting} (source → sink, the reverse "outgoing" view). Both server (controller) and client
 * (menu) hold one; components are stateless views over it. The reverse index is derived, never serialized.</p>
 */
public class ConnectionGraph {

    private final Map<VirtualComponentPosition, LinkedHashMap<VirtualComponentPosition, Connection>> incoming = new LinkedHashMap<>();
    private final Map<VirtualComponentPosition, LinkedHashMap<VirtualComponentPosition, Connection>> outgoing = new LinkedHashMap<>();

    // ── Read-only views (what components return from targetedBy()/targeting()) ──

    /** The connections {@code sink} holds, keyed by source position; empty (and immutable) if it has none. */
    public Map<VirtualComponentPosition, Connection> targetedBy(VirtualComponentPosition sink) {
        Map<VirtualComponentPosition, Connection> in = incoming.get(sink);
        return in == null ? Collections.emptyMap() : in;
    }

    /** The components {@code source} points at (sinks that hold a wire from it); empty (and immutable) if none. */
    public Set<VirtualComponentPosition> targeting(VirtualComponentPosition source) {
        Map<VirtualComponentPosition, Connection> out = outgoing.get(source);
        return out == null ? Collections.emptySet() : out.keySet();
    }

    public Collection<Connection> incomingConnections(VirtualComponentPosition sink) {
        Map<VirtualComponentPosition, Connection> in = incoming.get(sink);
        return in == null ? Collections.emptyList() : in.values();
    }

    public Collection<Connection> outgoingConnections(VirtualComponentPosition source) {
        Map<VirtualComponentPosition, Connection> out = outgoing.get(source);
        return out == null ? Collections.emptyList() : out.values();
    }

    public List<Connection> incomingConnections(VirtualComponentPosition sink, Connection.Type type) {
        return incomingConnections(sink).stream().filter(c -> type.equals(c.type)).collect(Collectors.toList());
    }

    public List<Connection> outgoingConnections(VirtualComponentPosition source, Connection.Type type) {
        return outgoingConnections(source).stream().filter(c -> type.equals(c.type)).collect(Collectors.toList());
    }

    public Connection get(VirtualComponentPosition source, VirtualComponentPosition sink) {
        Map<VirtualComponentPosition, Connection> out = outgoing.get(source);
        return out == null ? null : out.get(sink);
    }

    public Connection get(VirtualComponentPosition source, VirtualComponentPosition sink, Connection.Type type) {
        Connection conn = get(source, sink);
        return conn != null && type.equals(conn.type) ? conn : null;
    }

    public List<Connection> connections() {
        List<Connection> result = new ArrayList<>();
        for (Map<VirtualComponentPosition, Connection> m : incoming.values())
            result.addAll(m.values());
        return result;
    }

    // ── Mutation (both indexes kept in sync) ────────────────────────────────────

    /** Adds {@code conn}. Overwrites any existing wire for its from/to pair. */
    public void add(Connection conn) {
        incoming.computeIfAbsent(conn.to, k -> new LinkedHashMap<>()).put(conn.from, conn);
        outgoing.computeIfAbsent(conn.from, k -> new LinkedHashMap<>()).put(conn.to, conn);
    }

    /** Removes the wire {@code sink} holds from {@code source}, if any. */
    public void remove(VirtualComponentPosition sink, VirtualComponentPosition source) {
        Map<VirtualComponentPosition, Connection> in = incoming.get(sink);
        if (in != null) { in.remove(source); if (in.isEmpty()) incoming.remove(sink); }
        Map<VirtualComponentPosition, Connection> out = outgoing.get(source);
        if (out != null) { out.remove(sink); if (out.isEmpty()) outgoing.remove(source); }
    }

    public void reverse(Connection conn) {
        remove(conn.to, conn.from);
        VirtualComponentPosition oldFrom = conn.from;
        conn.from = conn.to;
        conn.to = oldFrom;
        // Keep the rendered path SHAPE stable across the flip — only the arrowhead should change direction.
        if (conn.arrowBendMode == 0)
            conn.arrowBendMode = 1;
        else if (conn.arrowBendMode == 1)
            conn.arrowBendMode = 0;
        add(conn);
    }

    /** Removes every wire touching {@code pos} (as sink or as source) — called when its component is removed. */
    public void disconnect(VirtualComponentPosition pos) {
        Map<VirtualComponentPosition, Connection> ownedHere = incoming.remove(pos);
        if (ownedHere != null)
            for (VirtualComponentPosition source : ownedHere.keySet()) {
                Map<VirtualComponentPosition, Connection> out = outgoing.get(source);
                if (out != null) { out.remove(pos); if (out.isEmpty()) outgoing.remove(source); }
            }
        Map<VirtualComponentPosition, Connection> pointsAt = outgoing.remove(pos);
        if (pointsAt != null)
            for (VirtualComponentPosition sink : pointsAt.keySet()) {
                Map<VirtualComponentPosition, Connection> in = incoming.get(sink);
                if (in != null) { in.remove(pos); if (in.isEmpty()) incoming.remove(sink); }
            }
    }

    /** Re-keys every wire touching {@code from} to {@code to} (a component relocation); updates each moved wire's
     *  stored endpoints to match. {@code to} must be empty. */
    public void rename(VirtualComponentPosition from, VirtualComponentPosition to) {
        // As sink: move its incoming map; repoint each source's outgoing set from→to.
        LinkedHashMap<VirtualComponentPosition, Connection> owned = incoming.remove(from);
        if (owned != null) {
            incoming.put(to, owned);
            for (Map.Entry<VirtualComponentPosition, Connection> entry : owned.entrySet()) {
                VirtualComponentPosition source = entry.getKey();
                entry.getValue().to = to;
                Map<VirtualComponentPosition, Connection> out = outgoing.get(source);
                if (out != null) {
                    Connection conn = out.remove(from);
                    if (conn != null) out.put(to, conn);
                }
            }
        }
        // As SOURCE: move its outgoing set; re-key the matching entry (and its conn.from) in each sink's incoming map.
        LinkedHashMap<VirtualComponentPosition, Connection> pointsAt = outgoing.remove(from);
        if (pointsAt != null) {
            outgoing.put(to, pointsAt);
            for (VirtualComponentPosition sink : pointsAt.keySet()) {
                Map<VirtualComponentPosition, Connection> in = incoming.get(sink);
                if (in == null) continue;
                Connection conn = in.remove(from);
                if (conn != null) { conn.from = to; in.put(to, conn); }
            }
        }
    }

    /** Remaps every sink/source position (and each wire's stored endpoints) through {@code f} in one atomic pass — the
     *  batch relocate. The reverse index is rebuilt from the remapped incoming map. */
    public void remap(java.util.function.Function<VirtualComponentPosition, VirtualComponentPosition> f) {
        Map<VirtualComponentPosition, LinkedHashMap<VirtualComponentPosition, Connection>> remapped = new LinkedHashMap<>();
        for (Map.Entry<VirtualComponentPosition, LinkedHashMap<VirtualComponentPosition, Connection>> e : incoming.entrySet()) {
            VirtualComponentPosition sink = f.apply(e.getKey());
            LinkedHashMap<VirtualComponentPosition, Connection> m = new LinkedHashMap<>();
            for (Connection conn : e.getValue().values()) {
                conn.from = f.apply(conn.from);
                conn.to = sink;
                m.put(conn.from, conn);
            }
            remapped.put(sink, m);
        }
        incoming.clear();
        incoming.putAll(remapped);
        outgoing.clear();
        for (Map.Entry<VirtualComponentPosition, LinkedHashMap<VirtualComponentPosition, Connection>> e : incoming.entrySet())
            for (Connection conn : e.getValue().values())
                outgoing.computeIfAbsent(conn.from, k -> new LinkedHashMap<>()).put(conn.to, conn);
    }

    public void clear() { incoming.clear(); outgoing.clear(); }

    // ── Persistence (flat edge list; readback lives in the controller so it can validate endpoints) ──

    public ListTag toNBT() {
        ListTag list = new ListTag();
        for (var e : incoming.entrySet()) {
            for (Connection conn : e.getValue().values()) {
                list.add(conn.toNBT());
            }
        }
        return list;
    }

}
