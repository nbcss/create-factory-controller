package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The controller-level store of every connection on the board (Phase 2 — connections live here, not on each
 * component). A connection is owned by one component (the link for a gauge↔link wire; the consumer for a gauge→gauge
 * wire) and keyed there by its source position, exactly as the old per-component {@code targetedBy} map was.
 *
 * <p>Two indexes are kept in lock-step: {@link #targetedBy} (sink → source → payload, the live "incoming" view a
 * component exposes) and {@link #targeting} (source → sink, the reverse "outgoing" view). Both server (controller)
 * and client (menu) hold one; components are stateless views over it. The reverse index is derived, never serialized.</p>
 */
public class ConnectionGraph {

    private final Map<VirtualComponentPosition, LinkedHashMap<VirtualComponentPosition, Connection>> incoming = new LinkedHashMap<>();
    private final Map<VirtualComponentPosition, LinkedHashSet<VirtualComponentPosition>> outgoing = new LinkedHashMap<>();

    // ── Live views (what components return from targetedBy()/targeting()) ───────

    /** Live map of the connections {@code sink} holds, keyed by source position. Created on demand. */
    public Map<VirtualComponentPosition, Connection> targetedBy(VirtualComponentPosition sink) {
        return incoming.computeIfAbsent(sink, k -> new LinkedHashMap<>());
    }

    /** Live set of the components {@code source} points at (sinks that hold a wire from it). Created on demand. */
    public Set<VirtualComponentPosition> targeting(VirtualComponentPosition source) {
        return outgoing.computeIfAbsent(source, k -> new LinkedHashSet<>());
    }

    // ── Mutation (both indexes kept in sync) ────────────────────────────────────

    /** Adds {@code conn}. Overwrites any existing wire for its from/to pair. */
    public void add(Connection conn) {
        incoming.computeIfAbsent(conn.to, k -> new LinkedHashMap<>()).put(conn.from, conn);
        outgoing.computeIfAbsent(conn.from, k -> new LinkedHashSet<>()).add(conn.to);
    }

    /** Removes the wire {@code sink} holds from {@code source}, if any. */
    public void remove(VirtualComponentPosition sink, VirtualComponentPosition source) {
        Map<VirtualComponentPosition, Connection> in = incoming.get(sink);
        if (in != null) { in.remove(source); if (in.isEmpty()) incoming.remove(sink); }
        Set<VirtualComponentPosition> out = outgoing.get(source);
        if (out != null) { out.remove(sink); if (out.isEmpty()) outgoing.remove(source); }
    }

    /** Removes every wire touching {@code pos} (as sink or as source) — called when its component is removed. */
    public void disconnect(VirtualComponentPosition pos) {
        Map<VirtualComponentPosition, Connection> ownedHere = incoming.remove(pos);
        if (ownedHere != null)
            for (VirtualComponentPosition source : ownedHere.keySet()) {
                Set<VirtualComponentPosition> out = outgoing.get(source);
                if (out != null) { out.remove(pos); if (out.isEmpty()) outgoing.remove(source); }
            }
        Set<VirtualComponentPosition> pointsAt = outgoing.remove(pos);
        if (pointsAt != null)
            for (VirtualComponentPosition sink : pointsAt) {
                Map<VirtualComponentPosition, Connection> in = incoming.get(sink);
                if (in != null) { in.remove(pos); if (in.isEmpty()) incoming.remove(sink); }
            }
    }

    /** Re-keys every wire touching {@code from} to {@code to} (a component relocation); updates each moved wire's
     *  {@code from} when {@code from} was the source. {@code to} must be empty. */
    public void rename(VirtualComponentPosition from, VirtualComponentPosition to) {
        // As sink: move its incoming map; repoint each source's outgoing set from→to.
        LinkedHashMap<VirtualComponentPosition, Connection> owned = incoming.remove(from);
        if (owned != null) {
            incoming.put(to, owned);
            for (VirtualComponentPosition source : owned.keySet()) {
                Set<VirtualComponentPosition> out = outgoing.get(source);
                if (out != null) { out.remove(from); out.add(to); }
            }
        }
        // As SOURCE: move its outgoing set; re-key the matching entry (and its conn.from) in each sink's incoming map.
        LinkedHashSet<VirtualComponentPosition> pointsAt = outgoing.remove(from);
        if (pointsAt != null) {
            outgoing.put(to, pointsAt);
            for (VirtualComponentPosition sink : pointsAt) {
                Map<VirtualComponentPosition, Connection> in = incoming.get(sink);
                if (in == null) continue;
                Connection conn = in.remove(from);
                if (conn != null) { conn.from = to; in.put(to, conn); }
            }
        }
    }

    /** Remaps every sink/source position (and each wire's {@code from}) through {@code f} in one atomic pass — the
     *  batch relocate. The reverse index is rebuilt from the remapped incoming map. */
    public void remap(java.util.function.Function<VirtualComponentPosition, VirtualComponentPosition> f) {
        Map<VirtualComponentPosition, LinkedHashMap<VirtualComponentPosition, Connection>> remapped = new LinkedHashMap<>();
        for (Map.Entry<VirtualComponentPosition, LinkedHashMap<VirtualComponentPosition, Connection>> e : incoming.entrySet()) {
            LinkedHashMap<VirtualComponentPosition, Connection> m = new LinkedHashMap<>();
            for (Connection conn : e.getValue().values()) {
                conn.from = f.apply(conn.from);
                m.put(conn.from, conn);
            }
            remapped.put(f.apply(e.getKey()), m);
        }
        incoming.clear();
        incoming.putAll(remapped);
        outgoing.clear();
        for (Map.Entry<VirtualComponentPosition, LinkedHashMap<VirtualComponentPosition, Connection>> e : incoming.entrySet())
            for (VirtualComponentPosition source : e.getValue().keySet())
                outgoing.computeIfAbsent(source, k -> new LinkedHashSet<>()).add(e.getKey());
    }

    public void clear() { incoming.clear(); outgoing.clear(); }

    // ── Persistence (flat edge list; the reverse index is rebuilt on read) ──────

    public ListTag toNBT() {
        ListTag list = new ListTag();
        for (var e : incoming.entrySet()) {
            for (Connection conn : e.getValue().values()) {
                list.add(conn.toNBT());
            }
        }
        return list;
    }

    /** Replaces all edges from a {@link #toNBT} list, dispatching each payload by its stored type. */
    public void readNBT(ListTag list) {
        clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            Connection conn = Connection.fromNBT(tag);
            if (conn != null) add(conn);
        }
    }

    /** The edges as a plain {@code List<CompoundTag>} (for menu/packet sync, which serializes tag lists). */
    public List<CompoundTag> toTagList() {
        List<CompoundTag> list = new ArrayList<>();
        for (var e : incoming.entrySet())
            for (Connection conn : e.getValue().values())
                list.add(conn.toNBT());
        return list;
    }

    /** Replaces all edges from a {@link #toTagList} list (client sync). */
    public void readTagList(List<CompoundTag> list) {
        clear();
        for (CompoundTag tag : list) {
            Connection conn = Connection.fromNBT(tag);
            if (conn != null) add(conn);
        }
    }
}
