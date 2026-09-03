package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The controller-level store of every connection on the board (Phase 2 — connections live here, not on each
 * component). There is no per-connection owner: a wire is a directed {@code source → sink} edge, and its direction is
 * resolved from the endpoints' port roles (see {@code ConnectionResolver}), not from where it happens to be stored.
 *
 * <p>A directed {@code (source, sink)} pair may hold <b>at most one wire per {@link Connection.Type}</b> — so a gauge
 * can feed another gauge a LOGISTICS ingredient and drive its count over a NUMBER wire at the same time. Two indexes
 * are kept in lock-step: {@link #incoming} (sink → source → type → payload, the "incoming" view a component exposes)
 * and {@link #outgoing} (source → sink → type, the reverse "outgoing" view). Both server (controller) and client
 * (menu) hold one; components are stateless views over it. The reverse index is derived, never serialized.</p>
 */
public class ConnectionGraph {

    /** Per-endpoint bucket of typed wires: the other endpoint's position → (type → wire). */
    private static final class EdgeIndex {
        final Map<VirtualComponentPosition, LinkedHashMap<Connection.Type, Connection>> byEndpoint = new LinkedHashMap<>();
        List<Connection> connections; //lazy cache
    }

    private final Map<VirtualComponentPosition, EdgeIndex> incoming = new LinkedHashMap<>();   // keyed by sink
    private final Map<VirtualComponentPosition, EdgeIndex> outgoing = new LinkedHashMap<>();   // keyed by source

    // ── Read-only views (what components return from incomingConnections()/outgoingConnections()) ──

    /** Every wire {@code sink} holds, across all sources and types; a fresh list (safe to mutate/iterate). */
    public Collection<Connection> incomingConnections(VirtualComponentPosition sink) {
        return flatten(incoming.get(sink));
    }

    /** Every wire {@code source} points at, across all sinks and types; a fresh list. */
    public Collection<Connection> outgoingConnections(VirtualComponentPosition source) {
        return flatten(outgoing.get(source));
    }

    public List<Connection> incomingConnections(VirtualComponentPosition sink, Connection.Type type) {
        return incomingConnections(sink).stream().filter(c -> type.equals(c.type)).collect(Collectors.toList());
    }

    public List<Connection> outgoingConnections(VirtualComponentPosition source, Connection.Type type) {
        return outgoingConnections(source).stream().filter(c -> type.equals(c.type)).collect(Collectors.toList());
    }

    /** The single {@code source → sink} wire of {@code type}, or {@code null} if there is none. */
    public Connection get(VirtualComponentPosition source, VirtualComponentPosition sink, Connection.Type type) {
        EdgeIndex out = outgoing.get(source);
        if (out == null) return null;
        LinkedHashMap<Connection.Type, Connection> byType = out.byEndpoint.get(sink);
        return byType == null ? null : byType.get(type);
    }

    public List<Connection> connections() {
        List<Connection> result = new ArrayList<>();
        for (EdgeIndex index : incoming.values())
            for (LinkedHashMap<Connection.Type, Connection> byType : index.byEndpoint.values())
                result.addAll(byType.values());
        return result;
    }

    private static List<Connection> flatten(EdgeIndex index) {
        if (index == null) return Collections.emptyList();
        if (index.connections != null) return index.connections;
        List<Connection> out = new ArrayList<>();
        for (LinkedHashMap<Connection.Type, Connection> byType : index.byEndpoint.values())
            out.addAll(byType.values());
        return index.connections = Collections.unmodifiableList(out);
    }

    // ── Mutation (both indexes kept in sync) ────────────────────────────────────

    /** Adds {@code conn}. Overwrites only an existing wire of the <b>same type</b> on its from/to pair; wires of other
     *  types between the same pair are left untouched. */
    public void add(Connection conn) {
        put(incoming, conn.to, conn.from, conn);
        put(outgoing, conn.from, conn.to, conn);
    }

    private static void put(Map<VirtualComponentPosition, EdgeIndex> index,
                            VirtualComponentPosition primary, VirtualComponentPosition secondary, Connection conn) {
        EdgeIndex edges = index.computeIfAbsent(primary, k -> new EdgeIndex());
        edges.byEndpoint.computeIfAbsent(secondary, k -> new LinkedHashMap<>()).put(conn.type, conn);
        edges.connections = null;
    }

    /** Removes the {@code type} wire {@code sink} holds from {@code source}, if any. */
    public void remove(VirtualComponentPosition sink, VirtualComponentPosition source, Connection.Type type) {
        removeFrom(incoming, sink, source, type);
        removeFrom(outgoing, source, sink, type);
    }

    private static void removeFrom(Map<VirtualComponentPosition, EdgeIndex> index,
                                   VirtualComponentPosition primary, VirtualComponentPosition secondary, Connection.Type type) {
        EdgeIndex edges = index.get(primary);
        if (edges == null) return;
        LinkedHashMap<Connection.Type, Connection> byType = edges.byEndpoint.get(secondary);
        if (byType == null) return;
        byType.remove(type);
        if (byType.isEmpty()) edges.byEndpoint.remove(secondary);
        if (edges.byEndpoint.isEmpty()) index.remove(primary);
        edges.connections = null;
    }

    public void reverse(Connection conn) {
        remove(conn.to, conn.from, conn.type);
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

    /** Removes every wire touching {@code pos} (as sink or as source, any type) — called when its component is removed. */
    public void disconnect(VirtualComponentPosition pos) {
        List<Connection> affected = new ArrayList<>();
        affected.addAll(incomingConnections(pos));
        affected.addAll(outgoingConnections(pos));
        for (Connection conn : affected) remove(conn.to, conn.from, conn.type);
    }

    /** Re-keys every wire touching {@code from} to {@code to} (a component relocation); updates each moved wire's
     *  stored endpoints to match. {@code to} must be empty. */
    public void rename(VirtualComponentPosition from, VirtualComponentPosition to) {
        List<Connection> affected = new ArrayList<>();
        affected.addAll(incomingConnections(from));   // from as sink
        affected.addAll(outgoingConnections(from));   // from as source (disjoint — no self-loops)
        for (Connection conn : affected) remove(conn.to, conn.from, conn.type);
        for (Connection conn : affected) {
            if (conn.from.equals(from)) conn.from = to;
            if (conn.to.equals(from)) conn.to = to;
            add(conn);
        }
    }

    /** Remaps every endpoint (and each wire's stored endpoints) through {@code f} in one pass — the batch relocate. */
    public void remap(Function<VirtualComponentPosition, VirtualComponentPosition> f) {
        List<Connection> all = connections();
        clear();
        for (Connection conn : all) {
            conn.from = f.apply(conn.from);
            conn.to = f.apply(conn.to);
            add(conn);
        }
    }

    public void clear() { incoming.clear(); outgoing.clear(); }

    // ── Persistence (flat edge list; readback lives in the controller so it can validate endpoints) ──

    public ListTag toNBT() {
        ListTag list = new ListTag();
        for (Connection conn : connections())
            list.add(conn.toNBT());
        return list;
    }

}
