package io.github.nbcss.content.factorycontroller.gui;

import io.github.nbcss.content.factorycontroller.FactoryControllerMenu;
import io.github.nbcss.content.factorycontroller.VirtualComponentBehaviour;
import io.github.nbcss.content.factorycontroller.VirtualPanelConnection;
import io.github.nbcss.content.factorycontroller.VirtualPanelPosition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Draws connection arrows between virtual components on the canvas, reusing Create's own
 * factory-panel connection sprite ({@code create:block/factory_panel_connections}).
 *
 * <p>Connections follow the 2D grid like Create's panels: each connection is an axis-aligned
 * path — a single straight segment when the two cells share a row/column, otherwise an L-path
 * through one corner (corner side chosen by {@link VirtualPanelConnection#arrowBendMode}). The
 * texture is a pre-oriented atlas: it contains a separate line + arrow strip for each of the four
 * grid directions, so we blit the matching strip directly (no rotation). The arrowhead points
 * into the {@code from} (source) gauge.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class VirtualConnectionRenderer {

    private static final int CELL = 16;

    // Create's connection sprite (16×16). The blocks resolve under assets/create/textures/...
    private static final ResourceLocation TEX =
            ResourceLocation.fromNamespaceAndPath("create", "textures/block/factory_panel_connections.png");
    private static final int TEX_SIZE = 16;
    /** Connection line width (world px); the sprite line strip is 4px wide. */
    private static final int THICKNESS = 4;
    /** Arrowhead length along its pointing axis (world px). */
    private static final int ARROW_LEN = 2;

    // Atlas sub-rects {u, v, w, h}. Create's connection models apply rotation:180 to every face,
    // which we can't replicate with a plain blit — so each strip's raw pixels point OPPOSITE its
    // model name. The constants below are named by the direction they actually point when blitted
    // raw: ARROW_N = the rect that visually points up (Create's "south" rect), etc.
    private static final int[] LINE_V  = {4, 0, 4, 8};   // vertical line   (4w × 8h)
    private static final int[] LINE_H  = {0, 8, 8, 4};   // horizontal line (8w × 4h)
    private static final int[] ARROW_N = {8, 3, 4, 2};   // points up    (model "south" rect)
    private static final int[] ARROW_S = {12, 3, 4, 2};  // points down  (model "north" rect)
    private static final int[] ARROW_E = {11, 12, 2, 4};  // points right (model "west" rect)
    private static final int[] ARROW_W = {11, 8, 2, 4};   // points left  (model "east" rect)

    private VirtualConnectionRenderer() {}

    /**
     * Draws every incoming connection in the panel. Call inside the canvas scissor, before the
     * components are drawn, so the gauge icons sit on top of the line ends.
     */
    public static void renderConnections(GuiGraphics gfx, FactoryControllerMenu menu) {
        Set<VirtualPanelPosition> occupied = new HashSet<>();
        for (VirtualComponentBehaviour c : menu.components) occupied.add(c.position());
        for (VirtualComponentBehaviour target : menu.components) {
            VirtualPanelPosition toPos = target.position();
            for (Map.Entry<VirtualPanelPosition, VirtualPanelConnection> e : target.targetedBy().entrySet()) {
                drawConnection(gfx, e.getKey(), toPos, e.getValue(), occupied);
            }
        }
    }

    /** Draws one grid-following connection. Flow is {@code from → to}; the arrowhead enters {@code to}. */
    private static void drawConnection(GuiGraphics gfx,
                                       VirtualPanelPosition from, VirtualPanelPosition to,
                                       VirtualPanelConnection conn,
                                       Set<VirtualPanelPosition> occupied) {
        if (from.equals(to)) return;

        // Resolve the bend mode. Auto (-1) mirrors Create: try the four modes in order and use the
        // first whose path runs through no other component cell; if all are blocked, fall to V→H.
        int mode;
        if (conn.arrowBendMode < 0) {
            mode = 0;
            for (int m = 0; m < 4; m++) {
                if (pathClear(buildCellPath(from, to, m), occupied, from, to)) { mode = m; break; }
            }
        } else {
            mode = conn.arrowBendMode % 4;
        }

        int[][] cells = buildCellPath(from, to, mode);

        // Tint encodes state; the sprite is grayscale.
        if (conn.success) gfx.setColor(0.27f, 0.87f, 0.33f, 1f);
        else gfx.setColor(0.88f, 0.88f, 0.88f, 1f);

        // World-pixel waypoints (cell centres). The canvas pose maps them to the screen.
        float[][] pts = new float[cells.length][2];
        for (int i = 0; i < cells.length; i++)
            pts[i] = new float[] { cellCenter(cells[i][0]), cellCenter(cells[i][1]) };

        // All segments except the final approach are plain line strips (corners overlap, no gaps).
        for (int i = 0; i < pts.length - 2; i++)
            segment(gfx, pts[i], pts[i + 1]);

        // Final approach into `to`: line to just inside the edge, then the arrowhead inside the cell.
        float[] corner = pts[pts.length - 2];
        drawApproach(gfx, corner[0], corner[1], cellCenter(to.x()), cellCenter(to.y()));

        gfx.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * Grid-following cell waypoints from source to target for a bend mode (Create's order):
     * <ul><li>0 — V→H (vertical-first L)</li>
     *     <li>1 — H→V (horizontal-first L)</li>
     *     <li>2 — H→V→H staircase (falls back to H→V if it can't fit)</li>
     *     <li>3 — V→H→V staircase (falls back to V→H if it can't fit)</li></ul>
     * A staircase needs a cell strictly between source and target on its stepping axis ({@code |d|>=2}).
     */
    private static int[][] buildCellPath(VirtualPanelPosition from, VirtualPanelPosition to, int mode) {
        int fx = from.x(), fy = from.y(), tx = to.x(), ty = to.y();
        if (fx == tx || fy == ty)                                   // aligned → straight run
            return new int[][] {{fx, fy}, {tx, ty}};

        int dx = tx - fx, dy = ty - fy;
        switch (mode) {
            case 1:                                                 // H→V
                return new int[][] {{fx, fy}, {tx, fy}, {tx, ty}};
            case 2:                                                 // H→V→H
                if (Math.abs(dx) >= 2) {
                    int mx = fx + dx / 2;                           // bend column (a cell centre)
                    return new int[][] {{fx, fy}, {mx, fy}, {mx, ty}, {tx, ty}};
                }
                return new int[][] {{fx, fy}, {tx, fy}, {tx, ty}};  // fall back to H→V
            case 3:                                                 // V→H→V
                if (Math.abs(dy) >= 2) {
                    int my = fy + dy / 2;                           // bend row (a cell centre)
                    return new int[][] {{fx, fy}, {fx, my}, {tx, my}, {tx, ty}};
                }
                return new int[][] {{fx, fy}, {fx, ty}, {tx, ty}};  // fall back to V→H
            default:                                                // 0: V→H
                return new int[][] {{fx, fy}, {fx, ty}, {tx, ty}};
        }
    }

    /** True if the cell-space polyline passes through no occupied cell other than its endpoints. */
    private static boolean pathClear(int[][] cells, Set<VirtualPanelPosition> occupied,
                                     VirtualPanelPosition from, VirtualPanelPosition to) {
        for (int i = 0; i < cells.length - 1; i++) {
            int ax = cells[i][0], ay = cells[i][1], bx = cells[i + 1][0], by = cells[i + 1][1];
            int stepX = Integer.signum(bx - ax), stepY = Integer.signum(by - ay);
            int cx = ax, cy = ay;
            while (true) {
                VirtualPanelPosition p = new VirtualPanelPosition(cx, cy);
                if (!p.equals(from) && !p.equals(to) && occupied.contains(p)) return false;
                if (cx == bx && cy == by) break;
                cx += stepX; cy += stepY;
            }
        }
        return true;
    }

    /** Draws one axis-aligned segment between two waypoints. */
    private static void segment(GuiGraphics gfx, float[] a, float[] b) {
        if (a[1] == b[1]) hLine(gfx, a[0], b[0], a[1], THICKNESS);
        else              vLine(gfx, a[0], a[1], b[1], THICKNESS);
    }

    /** Final segment from {@code corner} into the destination {@code (tx,ty)} + the arrowhead. */
    private static void drawApproach(GuiGraphics gfx, float cornerX, float cornerY, float tx, float ty) {
        float half = CELL * 0.5f;
        boolean approachVertical = Math.abs(cornerX - tx) < 0.5f; // x unchanged ⇒ moving vertically
        if (approachVertical) {
            boolean destAbove = ty < cornerY;
            float edge = ty + (destAbove ? half : -half);          // `to` edge the line enters through
            float arrowTop = destAbove ? edge - ARROW_LEN : edge;
            vLine(gfx, cornerX, cornerY, edge, THICKNESS);
            blit(gfx, destAbove ? ARROW_N : ARROW_S,
                    Math.round(tx) - THICKNESS / 2, Math.round(arrowTop), THICKNESS, ARROW_LEN);
        } else {
            boolean destLeft = tx < cornerX;
            float edge = tx + (destLeft ? half : -half);
            float arrowLeft = destLeft ? edge - ARROW_LEN : edge;
            hLine(gfx, cornerX, edge, cornerY, THICKNESS);
            blit(gfx, destLeft ? ARROW_W : ARROW_E,
                    Math.round(arrowLeft), Math.round(ty) - THICKNESS / 2, ARROW_LEN, THICKNESS);
        }
    }

    /** Vertical line strip at world-x {@code x}, between world-y {@code y0} and {@code y1}. */
    private static void vLine(GuiGraphics gfx, float x, float y0, float y1, int t) {
        int ya = Math.round(Math.min(y0, y1)), yb = Math.round(Math.max(y0, y1));
        if (yb > ya) blit(gfx, LINE_V, Math.round(x) - t / 2, ya, t, yb - ya);
    }

    /** Horizontal line strip at world-y {@code y}, between world-x {@code x0} and {@code x1}. */
    private static void hLine(GuiGraphics gfx, float x0, float x1, float y, int t) {
        int xa = Math.round(Math.min(x0, x1)), xb = Math.round(Math.max(x0, x1));
        if (xb > xa) blit(gfx, LINE_H, xa, Math.round(y) - t / 2, xb - xa, t);
    }

    /** Blits an atlas sub-rect {@code {u,v,w,h}}, stretched into the given (world) rectangle. */
    private static void blit(GuiGraphics gfx, int[] uv, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        gfx.blit(TEX, x, y, w, h, uv[0], uv[1], uv[2], uv[3], TEX_SIZE, TEX_SIZE);
    }

    /** World-pixel centre of a cell along one axis. */
    private static float cellCenter(int cell) {
        return (cell + 0.5f) * CELL;
    }
}
