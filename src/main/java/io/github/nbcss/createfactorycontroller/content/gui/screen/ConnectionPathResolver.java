package io.github.nbcss.createfactorycontroller.content.gui.screen;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
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
    public static boolean spanVisible(VirtualComponentPosition a, VirtualComponentPosition b,
                                      int minX, int minY, int maxX, int maxY) {
        int x0 = Math.min(a.x(), b.x()) * CELL;
        int y0 = Math.min(a.y(), b.y()) * CELL;
        int x1 = (Math.max(a.x(), b.x()) + 1) * CELL;
        int y1 = (Math.max(a.y(), b.y()) + 1) * CELL;
        return x0 < maxX && x1 > minX && y0 < maxY && y1 > minY;
    }

    /**
     * Resolves the cell-space path for {@code conn}, or {@code null} if the endpoints are identical.
     * Auto bend (-1) tries the four modes in canonical order and picks the first clear path.
     */
    @Nullable
    public static List<Vector2i> resolvePath(Connection conn, Set<VirtualComponentPosition> occupied) {
        return resolvePath(conn.from, conn.to, conn.arrowBendMode, occupied);
    }

    /** As {@link #resolvePath(Connection, Set)} but from raw endpoints + bend mode, for previewing a wire that has not
     *  been created yet. {@code arrowBendMode} {@code -1} auto-picks the first clear bend. */
    @Nullable
    public static List<Vector2i> resolvePath(VirtualComponentPosition from, VirtualComponentPosition to,
                                             int arrowBendMode, Set<VirtualComponentPosition> occupied) {
        if (from.equals(to)) return null;

        assert Minecraft.getInstance().level != null;

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
