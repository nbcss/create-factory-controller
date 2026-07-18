package io.github.nbcss.createfactorycontroller.content.block;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Accumulates what changed on a controller's board since the last menu-sync flush, at the granularity the
 * delta packet ships: per component (STATE = only the runtime tail of the sync body, FULL = the whole body),
 * per connection, plus the header (name / controller power) and the network-settings list.
 *
 * <p>{@link #everything()} escalates the next flush to a full board snapshot — the always-correct fallback
 * for mutation paths where precise marking is impractical (relocations, setup restore) or not yet done
 * (anything still going through {@code sendData()}). Pure bookkeeping: no networking, no component
 * knowledge; the controller builds the packet from these marks and {@link #clear()}s once per tick.</p>
 */
public class PanelDeltaTracker {

    /** How much of a component's sync body the next flush must carry. */
    public enum Level {
        /** Only the runtime tail ({@code writeClientState}) — cheap, applied onto the client's existing instance. */
        STATE,
        /** The whole body ({@code writeClient}) — config changed or the component is new/replaced. */
        FULL
    }

    private final Map<VirtualComponentPosition, Level> components = new LinkedHashMap<>();
    private final Set<VirtualComponentPosition> removedComponents = new LinkedHashSet<>();
    private final Set<ConnectionKey> connections = new LinkedHashSet<>();
    private final Set<ConnectionKey> removedConnections = new LinkedHashSet<>();
    private boolean header = false;
    private boolean networks = false;
    private boolean everything = false;

    /** Runtime-only change (stock/promise counts, satisfied flags, powered, …). Never downgrades a FULL mark. */
    public void componentState(VirtualComponentPosition pos) {
        components.putIfAbsent(pos, Level.STATE);
    }

    /** Config change or a new/replaced component — ship the whole body. */
    public void componentFull(VirtualComponentPosition pos) {
        components.put(pos, Level.FULL);
    }

    /** Component gone; any pending upsert for it is moot (a re-add in the same tick marks FULL again, and the
     *  packet applies removals before upserts, so remove-then-add stays correct). */
    public void componentRemoved(VirtualComponentPosition pos) {
        components.remove(pos);
        removedComponents.add(pos);
    }

    /** A wire's payload changed (value/success/amount/bend) or the wire is new — ship the whole edge (edges
     *  are small; there is no per-edge STATE split). */
    public void connection(ConnectionKey key) {
        connections.add(key);
    }

    public void connectionRemoved(ConnectionKey key) {
        connections.remove(key);
        removedConnections.add(key);
    }

    /** Controller name / redstone-powered changed. */
    public void header() {
        header = true;
    }

    /** The known-network list or any network's shared settings changed. */
    public void networks() {
        networks = true;
    }

    /** Escalate the next flush to a full snapshot (supersedes every precise mark). */
    public void everything() {
        everything = true;
    }

    public boolean isEmpty() {
        return !everything && !header && !networks
            && components.isEmpty() && removedComponents.isEmpty()
            && connections.isEmpty() && removedConnections.isEmpty();
    }

    public boolean isEverything() { return everything; }
    public boolean isHeaderDirty() { return header; }
    public boolean isNetworksDirty() { return networks; }

    public Map<VirtualComponentPosition, Level> components() { return Collections.unmodifiableMap(components); }
    public Set<VirtualComponentPosition> removedComponents() { return Collections.unmodifiableSet(removedComponents); }
    public Set<ConnectionKey> connections() { return Collections.unmodifiableSet(connections); }
    public Set<ConnectionKey> removedConnections() { return Collections.unmodifiableSet(removedConnections); }

    public void clear() {
        components.clear();
        removedComponents.clear();
        connections.clear();
        removedConnections.clear();
        header = false;
        networks = false;
        everything = false;
    }
}
