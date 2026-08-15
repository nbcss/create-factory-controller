package io.github.nbcss.createfactorycontroller.content.gui.screen.controller.states;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.helper.Rect2i;
import org.joml.Vector2d;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * State and board-space calculations for an in-progress rectangular component selection.
 * The origin is stored in world coordinates so camera movement does not move the anchored corner.
 */
public final class DragSelectionState {
    private static final double DRAG_THRESHOLD = 3;

    private final double startWorldX;
    private final double startWorldY;
    private final boolean preserved;
    private boolean moved;

    public DragSelectionState(double startWorldX, double startWorldY, boolean preserved) {
        this.startWorldX = startWorldX;
        this.startWorldY = startWorldY;
        this.preserved = preserved;
    }

    public double startWorldX() {
        return startWorldX;
    }

    public double startWorldY() {
        return startWorldY;
    }

    public boolean isPreserved() {
        return preserved;
    }

    public boolean hasMoved() {
        return moved;
    }

    /** Promotes the press to a drag once the cursor crosses the screen-space threshold. */
    public void updateMovement(Vector2d startScreen, double mouseX, double mouseY) {
        if (moved) return;
        if (Math.abs(mouseX - startScreen.x) > DRAG_THRESHOLD
                || Math.abs(mouseY - startScreen.y) > DRAG_THRESHOLD)
            moved = true;
    }

    /** The inclusive board-cell box between the anchored origin and the current cursor. */
    public Rect2i cellBox(Vector2d currentWorld, int cellSize) {
        VirtualComponentPosition a = cellAt(startWorldX, startWorldY, cellSize);
        VirtualComponentPosition b = cellAt(currentWorld.x, currentWorld.y, cellSize);
        return Rect2i.fromBounds(
                Math.min(a.x(), b.x()),
                Math.min(a.y(), b.y()),
                Math.max(a.x(), b.x()),
                Math.max(a.y(), b.y()));
    }

    /** Returns occupied cells covered by the current selection rectangle, preserving board iteration order. */
    public Set<VirtualComponentPosition> positionsIn(Collection<VirtualComponentPosition> occupied,
                                                      Vector2d currentWorld, int cellSize) {
        Rect2i box = cellBox(currentWorld, cellSize);
        Set<VirtualComponentPosition> result = new LinkedHashSet<>();
        for (VirtualComponentPosition position : occupied)
            if (box.contains(position.x(), position.y(), Rect2i.Boundary.INCLUSIVE))
                result.add(position);
        return result;
    }

    /** Computes the selection shown while dragging without mutating the persistent selection. */
    public Set<VirtualComponentPosition> effectiveSelection(Set<VirtualComponentPosition> selection,
                                                             Collection<VirtualComponentPosition> occupied,
                                                             Vector2d currentWorld, int cellSize) {
        Set<VirtualComponentPosition> result = preserved
                ? new LinkedHashSet<>(selection)
                : new LinkedHashSet<>();
        result.addAll(positionsIn(occupied, currentWorld, cellSize));
        return result;
    }

    /** Applies the completed rectangle using either additive or replacement semantics. */
    public void applyTo(Set<VirtualComponentPosition> selection,
                        Collection<VirtualComponentPosition> occupied,
                        Vector2d currentWorld, int cellSize) {
        if (!preserved) selection.clear();
        selection.addAll(positionsIn(occupied, currentWorld, cellSize));
    }

    private static VirtualComponentPosition cellAt(double worldX, double worldY, int cellSize) {
        return new VirtualComponentPosition(
                (int) Math.floor(worldX / cellSize),
                (int) Math.floor(worldY / cellSize));
    }
}
