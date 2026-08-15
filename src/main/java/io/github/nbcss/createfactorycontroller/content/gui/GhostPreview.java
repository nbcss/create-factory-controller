package io.github.nbcss.createfactorycontroller.content.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nbcss.createfactorycontroller.content.block.ComponentHolder;
import io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionGraph;
import io.github.nbcss.createfactorycontroller.content.gui.screen.ConnectionPathResolver;
import io.github.nbcss.createfactorycontroller.content.gui.widget.ComponentWidgetRegistry;
import io.github.nbcss.createfactorycontroller.content.gui.widget.VirtualComponentWidget;
import io.github.nbcss.createfactorycontroller.content.render.ComponentRenderingHelper;
import io.github.nbcss.createfactorycontroller.content.render.VirtualConnectionRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector2i;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A non-ticking, client-only preview "layer": fully-reconstructed virtual components plus their wires, drawn
 * translucently under a movable offset. Drives the blueprint placement preview (a blueprint's components + internal
 * wires) and the batch-move / single-relocate previews (the moving components + every wire touching them, including
 * real&rarr;ghost edges to components that stay put).
 *
 * <p>Because components are reconstructed <em>whole</em> (through the ordinary {@link ComponentRegistry#fromNBT}),
 * their configured content — a gauge's filter, a link's frequencies, a tube's mode — renders through the normal
 * widget path, with no ghost-specific hooks on the component types. The layer is a {@link ComponentHolder} over its
 * own {@link ConnectionGraph} so a reconstructed component's {@code targetedBy()} resolves (an active gauge reads it
 * while rendering).</p>
 */
@OnlyIn(Dist.CLIENT)
public final class GhostPreview implements ComponentHolder {

    private static final int CELL = 16;
    /** A blueprint stores a gauge's Network as an int placeholder; the ghost never uses a network, so any UUID does. */
    private static final UUID GHOST_NETWORK = new UUID(0L, 0L);
    private static final int GHOST_ALPHA = 0x80;

    /** A previewed wire. An endpoint is a ghost (moves with the render offset) or a fixed real component. */
    public record GhostWire(VirtualComponentPosition from, boolean fromGhost,
                            VirtualComponentPosition to, boolean toGhost,
                            int color, int arrowBendMode) {}

    private final ConnectionGraph graph = new ConnectionGraph();
    private final Map<VirtualComponentPosition, VirtualComponentBehaviour> components = new LinkedHashMap<>();
    private final Map<VirtualComponentPosition, VirtualComponentWidget> widgets = new LinkedHashMap<>();
    private final List<GhostWire> wires = new ArrayList<>();
    /** Whether these ghosts relocate <em>from</em> real board cells (a move) — so those cells free up and must drop out
     *  of the wire-routing obstacle set. False for a blueprint, whose ghost bases are local coords, not board cells. */
    private final boolean basesVacate;

    private GhostPreview(boolean basesVacate) {
        this.basesVacate = basesVacate;
    }

    @Override
    public VirtualComponentBehaviour componentAt(VirtualComponentPosition position) {
        return components.get(position);
    }

    // ── Construction ────────────────────────────────────────────────────────────

    /** A blueprint's components + internal wires at their blueprint-local positions (render offset = the anchor). An
     *  unreadable payload yields an empty preview — the screen still draws its placement reticles. */
    public static GhostPreview fromBlueprint(byte[] payload, @Nullable HolderLookup.Provider registries) {
        GhostPreview preview = new GhostPreview(false);   // newly placed components vacate nothing
        if (registries == null) return preview;
        CompoundTag root;
        try {
            root = NbtIo.readCompressed(new ByteArrayInputStream(payload), NbtAccounter.unlimitedHeap());
        } catch (IOException | RuntimeException e) {
            return preview;
        }
        ListTag componentList = root.getList("Components", Tag.TAG_COMPOUND);
        for (int i = 0; i < componentList.size(); i++) {
            CompoundTag tag = componentList.getCompound(i);
            if (tag.contains("Item", Tag.TAG_STRING)) preview.addGhost(reconstruct(tag, registries));
        }
        ListTag connectionList = root.getList("Connections", Tag.TAG_COMPOUND);
        for (int i = 0; i < connectionList.size(); i++) {
            Connection conn = Connection.fromNBT(connectionList.getCompound(i));
            if (conn == null || !preview.components.containsKey(conn.from) || !preview.components.containsKey(conn.to))
                continue;
            preview.graph.add(conn);
            preview.wires.add(new GhostWire(conn.from, true, conn.to, true, conn.type.color(), conn.arrowBendMode));
        }
        return preview;
    }

    /** The moving components + every wire touching them, at their live board positions (render offset = the move
     *  delta). A wire endpoint on a moving component is a ghost; one on a component staying put is real. */
    public static GhostPreview fromSelection(Collection<VirtualComponentBehaviour> moving,
                                             Set<VirtualComponentPosition> movingCells,
                                             HolderLookup.Provider registries) {
        GhostPreview preview = new GhostPreview(true);   // the moving components free their current cells
        for (VirtualComponentBehaviour b : moving)
            preview.addGhost(reconstruct(b.toNBT(registries, VirtualComponentBehaviour.NbtProfile.EXPORT), registries));
        Set<String> seen = new HashSet<>();
        for (VirtualComponentBehaviour b : moving) {
            List<Connection> touching = new ArrayList<>(b.targetedBy().values());
            touching.addAll(b.outgoingConnections());
            for (Connection conn : touching) {
                if (!seen.add(conn.type.name() + ":" + conn.from + "->" + conn.to)) continue;
                preview.wires.add(new GhostWire(conn.from, movingCells.contains(conn.from),
                        conn.to, movingCells.contains(conn.to), conn.type.color(), conn.arrowBendMode));
                Connection copy = Connection.fromNBT(conn.toNBT());   // detached from the live graph
                if (copy != null) preview.graph.add(copy);
            }
        }
        return preview;
    }

    private static VirtualComponentBehaviour reconstruct(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag t = tag.copy();
        if (t.contains("Network", Tag.TAG_INT)) {   // blueprint placeholder int → any UUID (unused for rendering)
            t.remove("Network");
            t.putUUID("Network", GHOST_NETWORK);
        }
        return ComponentRegistry.fromNBT(null, t, registries);
    }

    private void addGhost(@Nullable VirtualComponentBehaviour behaviour) {
        if (behaviour == null) return;   // a component type from a mod no longer present
        components.put(behaviour.position(), behaviour);
        behaviour.setHolder(this);
        behaviour.setGraph(graph);
        VirtualComponentWidget widget = ComponentWidgetRegistry.create(behaviour);
        if (widget != null) widgets.put(behaviour.position(), widget);
    }

    // ── Render ──────────────────────────────────────────────────────────────────

    /**
     * Draws the layer translucently at {@code offset} (added to every ghost element's base cell): the placement
     * anchor for a blueprint, or the move delta for a relocate. {@code realOccupied} is the board's occupied cells so
     * previewed wires route around real components just as live ones do.
     */
    public void render(GuiGraphics graphics, VirtualComponentPosition offset,
                       ComponentRenderingHelper helper, Set<VirtualComponentPosition> realOccupied) {
        // Wires first (beneath the sprites, as on the live board): resolved in absolute board space, each endpoint
        // offset per its ghost flag, routing around occupied cells (real components + the ghost destinations).
        if (!wires.isEmpty()) {
            // Obstacle set = the board as it WILL be: movers leave their old cells (else the preview routes around
            // phantom obstacles the committed path won't see), then every ghost lands on its offset cell.
            Set<VirtualComponentPosition> occupied = new HashSet<>(realOccupied);
            if (basesVacate) occupied.removeAll(widgets.keySet());
            for (VirtualComponentPosition base : widgets.keySet()) occupied.add(shift(base, offset));
            for (GhostWire wire : wires) {
                VirtualComponentPosition from = wire.fromGhost() ? shift(wire.from(), offset) : wire.from();
                VirtualComponentPosition to = wire.toGhost() ? shift(wire.to(), offset) : wire.to();
                List<Vector2i> path = ConnectionPathResolver.resolvePath(from, to, wire.arrowBendMode(), occupied);
                if (path != null)
                    VirtualConnectionRenderer.create(path, (GHOST_ALPHA << 24) | (wire.color() & 0xFFFFFF), false)
                            .drawPath(graphics);
            }
            graphics.flush();   // composite the wire strips before the sprites draw over them
        }

        // Components: one pose translate carries them (and their batched filter/frequency icons) to the offset cell.
        graphics.pose().pushPose();
        graphics.pose().translate((float) offset.x() * CELL, (float) offset.y() * CELL, 0);
        RenderSystem.enableBlend();
        graphics.setColor(1f, 1f, 1f, 0.5f);
        for (VirtualComponentWidget widget : widgets.values())
            widget.renderGhost(helper.params(graphics, new Vector2d(Double.NaN, Double.NaN), false));
        helper.flushBuffers(graphics);   // flush while the alpha + translate are still active
        graphics.setColor(1f, 1f, 1f, 1f);
        graphics.pose().popPose();
    }

    private static VirtualComponentPosition shift(VirtualComponentPosition base, VirtualComponentPosition offset) {
        return new VirtualComponentPosition(base.x() + offset.x(), base.y() + offset.y());
    }
}
