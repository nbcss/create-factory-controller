package io.github.nbcss.createfactorycontroller.content.component;

import io.github.nbcss.createfactorycontroller.content.VirtualPanelConnection;
import io.github.nbcss.createfactorycontroller.content.VirtualPanelPosition;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
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

    protected final FactoryControllerBlockEntity controller; // null on the client snapshot
    protected VirtualPanelPosition position;
    protected final ResourceLocation itemId;

    // Connection graph
    protected final Map<VirtualPanelPosition, VirtualPanelConnection> targetedBy = new LinkedHashMap<>();
    protected final Set<VirtualPanelPosition> targeting = new LinkedHashSet<>();

    protected AbstractVirtualComponent(FactoryControllerBlockEntity controller,
                                       VirtualPanelPosition position, ResourceLocation itemId) {
        this.controller = controller;
        this.position = position;
        this.itemId = itemId;
    }

    // ── Identity ─────────────────────────────────────────────────────────────

    @Override public VirtualPanelPosition position() { return position; }
    @Override public void setPosition(VirtualPanelPosition pos) { this.position = pos; }
    @Override public ResourceLocation getItemId() { return itemId; }

    // ── Connection graph ──────────────────────────────────────────────────────

    @Override public Map<VirtualPanelPosition, VirtualPanelConnection> targetedBy() { return targetedBy; }
    @Override public Set<VirtualPanelPosition> targeting() { return targeting; }

    /**
     * Adds an incoming connection from {@code fromPos}. Uncapped at this level — only {@link VirtualGaugeBehaviour}
     * limits its ingredient inputs (a redstone link holds unlimited gauge connections).
     */
    @Override
    public void addConnection(VirtualPanelPosition fromPos) {
        if (targetedBy.containsKey(fromPos)) return;

        VirtualComponentBehaviour source = controller.components.get(fromPos);
        if (source == null) return;

        source.targeting().add(position);
        targetedBy.put(fromPos, createConnection(fromPos, source));
        controller.setChanged();
        controller.sendData();
    }

    /** Creates the connection kind this component holds (a gauge → {@code LogisticsConnection}, a redstone link →
     *  {@code RedstoneConnection}). {@code source} is the wired-in component (for kind-specific defaults). */
    protected abstract VirtualPanelConnection createConnection(VirtualPanelPosition from,
                                                               VirtualComponentBehaviour source);

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
    public void onInteract() {
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
