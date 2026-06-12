package io.github.nbcss.content.factorycontroller;

import io.github.nbcss.content.factorycontroller.compat.FluidCompat;
import net.liukrast.deployer.lib.logistics.board.connection.PanelConnection;
import net.liukrast.deployer.lib.logistics.board.connection.PanelConnectionBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Base for all virtual components. Holds the connection graph and its mechanics — identical for
 * gauges, links, and any future component — plus the reused-from-Deployer connection wiring.
 *
 * <p>Mirrors the role {@code AbstractPanelBehaviour}/{@code AbstractPanelSupportBehaviour} play in
 * Deployer (both fill a {@link PanelConnectionBuilder} in their constructor), but binds to a
 * {@link FactoryControllerBlockEntity} + {@link VirtualPanelPosition} instead of a block entity +
 * slot, and never touches a world.</p>
 */
public abstract class AbstractVirtualComponent implements VirtualComponentBehaviour {

    /** Max incoming connections, matching Create's factory panel limit. */
    protected static final int MAX_INCOMING = 9;

    protected final FactoryControllerBlockEntity controller; // null on the client snapshot
    protected VirtualPanelPosition position;
    protected final ResourceLocation itemId;

    // Connection graph
    protected final Map<VirtualPanelPosition, VirtualPanelConnection> targetedBy = new LinkedHashMap<>();
    protected final Set<VirtualPanelPosition> targeting = new LinkedHashSet<>();

    // Reused Deployer connection model (filled once via addConnections)
    protected final Map<PanelConnection<?>, Supplier<?>> outputs = new LinkedHashMap<>();
    protected final Set<PanelConnection<?>> inputs = new LinkedHashSet<>();

    protected AbstractVirtualComponent(FactoryControllerBlockEntity controller,
                                       VirtualPanelPosition position, ResourceLocation itemId) {
        this.controller = controller;
        this.position = position;
        this.itemId = itemId;
        addConnections(new PanelConnectionBuilder(outputs, inputs));
    }

    // ── Identity ─────────────────────────────────────────────────────────────

    @Override public VirtualPanelPosition position() { return position; }
    @Override public void setPosition(VirtualPanelPosition pos) { this.position = pos; }
    @Override public ResourceLocation getItemId() { return itemId; }

    // ── Connection declarations (Deployer-compatible) ─────────────────────────

    /** Default: declares nothing. Subclasses register their I/O types. */
    @Override public void addConnections(PanelConnectionBuilder builder) {}

    @Override public Set<PanelConnection<?>> getInputConnections() { return inputs; }
    @Override public Set<PanelConnection<?>> getOutputConnections() { return outputs.keySet(); }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getConnectionValue(PanelConnection<T> connection) {
        Supplier<?> supplier = outputs.get(connection);
        return supplier == null ? Optional.empty() : Optional.ofNullable((T) supplier.get());
    }

    // ── Connection graph ──────────────────────────────────────────────────────

    @Override public Map<VirtualPanelPosition, VirtualPanelConnection> targetedBy() { return targetedBy; }
    @Override public Set<VirtualPanelPosition> targeting() { return targeting; }

    @Override
    public void addConnection(VirtualPanelPosition fromPos) {
        if (targetedBy.containsKey(fromPos)) return;
        if (targetedBy.size() >= MAX_INCOMING) return;

        VirtualComponentBehaviour source = controller.components.get(fromPos);
        if (source == null) return;

        // A fluid ingredient is measured in millibuckets, so start it at one bucket (1000 mB) like the output
        // default; item ingredients start at 1.
        int defaultAmount = source instanceof VirtualGaugeBehaviour g && FluidCompat.isFluidFilter(g.filter)
            ? 1000 : 1;
        source.targeting().add(position);
        targetedBy.put(fromPos, new VirtualPanelConnection(fromPos, defaultAmount));
        controller.setChanged();
        controller.sendData();
    }

    @Override
    public void removeConnection(VirtualPanelPosition fromPos) {
        VirtualPanelConnection conn = targetedBy.remove(fromPos);
        if (conn == null) return;
        VirtualComponentBehaviour source = controller.components.get(fromPos);
        if (source != null) source.targeting().remove(position);
        controller.setChanged();
        controller.sendData();
    }

    @Override
    public void disconnectAll() {
        for (VirtualPanelConnection conn : targetedBy.values()) {
            VirtualComponentBehaviour source = controller.components.get(conn.from);
            if (source != null) {
                source.targeting().remove(position);
                controller.sendData();
            }
        }
        for (VirtualPanelPosition targetPos : targeting) {
            VirtualComponentBehaviour target = controller.components.get(targetPos);
            if (target != null) {
                target.targetedBy().remove(position);
                controller.sendData();
            }
        }
        targetedBy.clear();
        targeting.clear();
    }

    @Override
    public void cycleArrowBend() {
        int sharedMode = -1;
        for (VirtualPanelPosition targetPos : targeting) {
            VirtualComponentBehaviour target = controller.components.get(targetPos);
            if (target == null) continue;
            VirtualPanelConnection conn = target.targetedBy().get(position);
            if (conn == null) continue;
            if (sharedMode == -1)
                sharedMode = (conn.arrowBendMode + 1) % 4;
            conn.arrowBendMode = sharedMode;
        }
        if (sharedMode != -1) {
            controller.playWrenchRotateSound();
            controller.setChanged();
            controller.sendData();
        }
    }
}
