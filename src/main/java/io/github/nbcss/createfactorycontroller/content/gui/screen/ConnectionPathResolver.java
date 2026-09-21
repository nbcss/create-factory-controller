package io.github.nbcss.createfactorycontroller.content.gui.screen;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.helper.Rect2i;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Resolves connection paths on the controller's 2D component grid. Paths are axis-aligned: a single straight segment
 * when both cells share an axis, otherwise an L-path or staircase selected by {@link Connection#arrowBendMode}.
 */
public final class ConnectionPathResolver {

    private static final int CELL = 16;

    private ConnectionPathResolver() {}

    /** Whether the cell-bounding rectangle of the two connection ends overlaps the visible canvas rectangle. */
    public static boolean spanVisible(VirtualComponentPosition a, VirtualComponentPosition b, Rect2i visibleArea) {
        if (a.equals(b)) {
            return Rect2i.fromBounds(
                    (a.x() - 1) * CELL, (a.y() - 1) * CELL,
                    (a.x() + 2) * CELL, (a.y() + 2) * CELL
            ).intersects(visibleArea, Rect2i.Boundary.EXCLUSIVE);
        }
        return Rect2i.fromBounds(
                Math.min(a.x(), b.x()) * CELL,
                Math.min(a.y(), b.y()) * CELL,
                (Math.max(a.x(), b.x()) + 1) * CELL,
                (Math.max(a.y(), b.y()) + 1) * CELL
        ).intersects(visibleArea, Rect2i.Boundary.EXCLUSIVE);
    }

    /** Resolves the cell-space path for {@code conn}. Auto mode picks the first clear bend or loop quadrant. */
    public static List<Vector2i> resolvePath(Connection conn, Set<VirtualComponentPosition> occupied) {
        return resolvePath(conn.from, conn.to, conn.arrowBendMode, occupied);
    }

    /** As {@link #resolvePath(Connection, Set)} but from raw endpoints + bend mode, for previewing a wire that has not
     *  been created yet. {@code arrowBendMode} {@code -1} auto-picks the first clear bend. */
    public static List<Vector2i> resolvePath(VirtualComponentPosition from, VirtualComponentPosition to,
                                             int arrowBendMode, Set<VirtualComponentPosition> occupied) {
        if (from.equals(to))
            return buildLoopPath(from, arrowBendMode < 0 ? autoLoopOrientation(from, occupied) : (arrowBendMode & 7));

        int mode;
        if (arrowBendMode < 0) {
            boolean swap = from.x() > to.x() || (from.x() == to.x() && from.y() > to.y());
            VirtualComponentPosition pa = swap ? to : from, pb = swap ? from : to;
            int m = 0;
            for (int k = 0; k < 4; k++) {
                if (pathClear(buildCellPath(pa, pb, k), occupied, pa, pb)) { m = k; break; }
            }
            mode = !swap ? m : (m == 0 ? 1 : m == 1 ? 0 : m);
        } else {
            mode = arrowBendMode % 4;
        }
        return new ArrayList<>(buildCellPath(from, to, mode));
    }

    /**
     * Grid-following cell waypoints from source to target for a bend mode (Create's order):
     * <ul><li>0 - V->H (vertical-first L)</li>
     *     <li>1 - H->V (horizontal-first L)</li>
     *     <li>2 - H->V->H staircase (falls back to H->V if it cannot fit)</li>
     *     <li>3 - V->H->V staircase (falls back to V->H if it cannot fit)</li></ul>
     * A staircase needs a cell strictly between source and target on its stepping axis ({@code |d|>=2}).
     */
    private static List<Vector2i> buildCellPath(VirtualComponentPosition from, VirtualComponentPosition to, int mode) {
        int fx = from.x(), fy = from.y(), tx = to.x(), ty = to.y();
        if (fx == tx || fy == ty)
            return List.of(new Vector2i(fx, fy), new Vector2i(tx, ty));

        int dx = tx - fx, dy = ty - fy;
        return switch (mode) {
            case 1 -> List.of(new Vector2i(fx, fy), new Vector2i(tx, fy), new Vector2i(tx, ty));
            case 2 -> {
                if (Math.abs(dx) >= 2) {
                    int mx = fx + dx / 2;
                    yield List.of(new Vector2i(fx, fy), new Vector2i(mx, fy), new Vector2i(mx, ty),
                            new Vector2i(tx, ty));
                }
                yield List.of(new Vector2i(fx, fy), new Vector2i(tx, fy), new Vector2i(tx, ty));
            }
            case 3 -> {
                if (Math.abs(dy) >= 2) {
                    int my = fy + dy / 2;
                    yield List.of(new Vector2i(fx, fy), new Vector2i(fx, my), new Vector2i(tx, my),
                            new Vector2i(tx, ty));
                }
                yield List.of(new Vector2i(fx, fy), new Vector2i(fx, ty), new Vector2i(tx, ty));
            }
            default -> List.of(new Vector2i(fx, fy), new Vector2i(fx, ty), new Vector2i(tx, ty));
        };
    }

    /** CW-first arm of each loop quadrant: top-right, bottom-right, bottom-left, top-left. */
    private static final Vector2i[] QUADRANT_ARMS = {
            new Vector2i(0, -1), new Vector2i(1, 0), new Vector2i(0, 1), new Vector2i(-1, 0)
    };

    public static List<Vector2i> buildLoopPath(VirtualComponentPosition cell, int orientation) {
        Vector2i a = QUADRANT_ARMS[(orientation >> 1) & 3];
        Vector2i b = new Vector2i(-a.y, a.x);              // 90° CW from a — the quadrant's other arm
        Vector2i exit = (orientation & 1) == 0 ? a : b;    // direction picks the exit arm (same square either way)
        Vector2i back = (orientation & 1) == 0 ? b : a;
        Vector2i c = new Vector2i(cell.x(), cell.y());
        List<Vector2i> path = new ArrayList<>(5);
        path.add(new Vector2i(c));
        path.add(new Vector2i(c).add(exit));
        path.add(new Vector2i(c).add(exit).add(back));
        path.add(new Vector2i(c).add(back));
        path.add(new Vector2i(c));
        return path;
    }

    /**
     * Picks the first loop orientation whose three decorative cells are all
     * in-board and unoccupied, or {@code 0} if none is clear.
     */
    public static int autoLoopOrientation(VirtualComponentPosition cell, Set<VirtualComponentPosition> occupied) {
        for (int quadrant = 0; quadrant < QUADRANT_ARMS.length; quadrant++) {
            Vector2i a = QUADRANT_ARMS[quadrant];
            Vector2i b = new Vector2i(-a.y, a.x);
            if (loopCellClear(cell, a.x, a.y, occupied)
                    && loopCellClear(cell, a.x + b.x, a.y + b.y, occupied)
                    && loopCellClear(cell, b.x, b.y, occupied))
                return quadrant * 2;   // auto starts with direction 0; direction 1 occupies the same cells
        }
        return 0;
    }

    private static boolean loopCellClear(VirtualComponentPosition cell, int dx, int dy,
                                         Set<VirtualComponentPosition> occupied) {
        VirtualComponentPosition p = new VirtualComponentPosition(cell.x() + dx, cell.y() + dy);
        return !occupied.contains(p) && !FactoryControllerBlockEntity.isOutBoard(p);
    }

    /** True if the cell-space polyline passes through no occupied cell other than its endpoints. */
    private static boolean pathClear(List<Vector2i> path, Set<VirtualComponentPosition> occupied,
                                     VirtualComponentPosition from, VirtualComponentPosition to) {
        for (int i = 0; i < path.size() - 1; i++) {
            Vector2i a = path.get(i), b = path.get(i + 1);
            int stepX = Integer.signum(b.x - a.x), stepY = Integer.signum(b.y - a.y);
            Vector2i c = new Vector2i(a);
            while (true) {
                VirtualComponentPosition p = new VirtualComponentPosition(c.x, c.y);
                if (!p.equals(from) && !p.equals(to) && occupied.contains(p)) return false;
                if (c.x == b.x && c.y == b.y) break;
                c.x += stepX;
                c.y += stepY;
            }
        }
        return true;
    }
}
