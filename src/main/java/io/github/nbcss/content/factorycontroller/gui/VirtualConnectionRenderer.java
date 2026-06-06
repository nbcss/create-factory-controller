package io.github.nbcss.content.factorycontroller.gui;

import io.github.nbcss.content.factorycontroller.FactoryControllerMenu;
import io.github.nbcss.content.factorycontroller.VirtualComponentBehaviour;
import io.github.nbcss.content.factorycontroller.VirtualGaugeBehaviour;
import io.github.nbcss.content.factorycontroller.VirtualPanelConnection;
import io.github.nbcss.content.factorycontroller.VirtualPanelPosition;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
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

    // Flash mix targets (Create's renderPath): on each request attempt the line flashes toward whitish
    // if the source can supply, or red if it can't. The flash decays over FLASH_DECAY ticks from the
    // gauge's last-attempt tick, so it pulses once per request (cooldown), not continuously.
    private static final int FLASH_OK   = 0xEAF2EC;
    private static final int FLASH_FAIL = 0xE5654B;
    private static final float FLASH_DECAY = 8f;   // ticks for the attempt flash to fade out

    // The flowing animation scrolls the flat line strip ({@link #LINE_V}/{@link #LINE_H}) toward the
    // target — a moving brightness wave along a flat line (the strip's own 8-px-period ripple), not
    // marching chevrons. Idle connections draw the same strip without scrolling.
    private static final int SCROLL_PERIOD = 8;       // strip length (world px)
    private static final float SCROLL_SPEED = 0.4f;   // px per render tick

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
                drawConnection(gfx, e.getKey(), toPos, e.getValue(), target, occupied);
            }
        }
    }

    /** Draws one grid-following connection. Flow is {@code from → to}; the arrowhead enters {@code to}. */
    private static void drawConnection(GuiGraphics gfx,
                                       VirtualPanelPosition from, VirtualPanelPosition to,
                                       VirtualPanelConnection conn, VirtualComponentBehaviour target,
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

        // State drives appearance, mirroring Create's FactoryPanelRenderer#renderPath: the line is
        // tinted by the *target* gauge's ingredient-status colour. While the gauge is actively
        // requesting (not satisfied / waiting / missing-address / powered) it (a) pulses the whole
        // line toward white if its source can supply (conn.success) or red if it cannot — the "no
        // ingredients" flash — and (b) scrolls the chevron strip toward the target (the progressing
        // wave). Idle connections draw the plain line strip. One arrowhead enters the target cell.
        int color = 0x888898;
        boolean flowing = false;
        if (target instanceof VirtualGaugeBehaviour gauge) {
            color = gauge.getConnectionColor();
            flowing = !gauge.isMissingAddress() && !gauge.waitingForNetwork
                    && !gauge.satisfied && !gauge.redstonePowered;
            if (flowing) {
                // Flash once per request attempt, decaying from the gauge's last-attempt tick.
                float age = Minecraft.getInstance().level.getGameTime() - gauge.lastRequestTick
                        + AnimationTickHolder.getPartialTicks();
                float glow = Mth.clamp(1f - age / FLASH_DECAY, 0f, 1f);
                if (glow > 0f) {
                    float p = 1f - (1f - glow) * (1f - glow);
                    color = Color.mixColors(color, conn.success ? FLASH_OK : FLASH_FAIL, p);
                }
            }
        }
        gfx.setColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, 1f);

        // World-pixel waypoints (cell centres); pull the last one back to the target's edge so the
        // arrowhead fills the remaining half-cell.
        float[][] pts = new float[cells.length][2];
        for (int i = 0; i < cells.length; i++)
            pts[i] = new float[] { cellCenter(cells[i][0]), cellCenter(cells[i][1]) };

        int n = pts.length;
        float[] corner = pts[n - 2];
        float tx = pts[n - 1][0], ty = pts[n - 1][1];
        boolean approachVertical = Math.abs(corner[0] - tx) < 0.5f;
        float half = CELL * 0.5f;
        pts[n - 1] = approachVertical
                ? new float[] { tx, ty + (ty < corner[1] ? half : -half) }
                : new float[] { tx + (tx < corner[0] ? half : -half), ty };

        // The line: plain strip when idle, or the scrolling chevron strip while flowing.
        for (int i = 0; i < n - 1; i++)
            drawLineSegment(gfx, pts[i], pts[i + 1], flowing);

        // Arrowhead entering the target cell.
        if (approachVertical) {
            boolean destAbove = ty < corner[1];
            float arrowTop = destAbove ? pts[n - 1][1] - ARROW_LEN : pts[n - 1][1];
            blit(gfx, destAbove ? ARROW_N : ARROW_S, Math.round(tx) - THICKNESS / 2, Math.round(arrowTop), THICKNESS, ARROW_LEN);
        } else {
            boolean destLeft = tx < corner[0];
            float arrowLeft = destLeft ? pts[n - 1][0] - ARROW_LEN : pts[n - 1][0];
            blit(gfx, destLeft ? ARROW_W : ARROW_E, Math.round(arrowLeft), Math.round(ty) - THICKNESS / 2, ARROW_LEN, THICKNESS);
        }
        gfx.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * Draws one axis-aligned line segment {@code a → b} in the current tint by tiling the flat line
     * strip over its 8-px period. While flowing, the strip scrolls toward the target so its ripple
     * reads as a progressing wave along a flat line; idle, it is static.
     */
    private static void drawLineSegment(GuiGraphics gfx, float[] a, float[] b, boolean flowing) {
        boolean horizontal = a[1] == b[1];
        int dir = (int) Math.signum(horizontal ? b[0] - a[0] : b[1] - a[1]);
        // Scroll so the ripple advances toward the target; integer px keeps the pixel art crisp.
        int off = flowing ? -dir * Math.round(AnimationTickHolder.getRenderTime() * SCROLL_SPEED) : 0;
        if (horizontal) {
            int y = Math.round(a[1]) - THICKNESS / 2;
            int xa = Math.round(Math.min(a[0], b[0])), xb = Math.round(Math.max(a[0], b[0]));
            for (int x = xa; x < xb; ) {
                int u = Math.floorMod(x + off, SCROLL_PERIOD);
                int run = Math.min(SCROLL_PERIOD - u, xb - x);
                gfx.blit(TEX, x, y, run, THICKNESS, LINE_H[0] + u, LINE_H[1], run, LINE_H[3], TEX_SIZE, TEX_SIZE);
                x += run;
            }
        } else {
            int x = Math.round(a[0]) - THICKNESS / 2;
            int ya = Math.round(Math.min(a[1], b[1])), yb = Math.round(Math.max(a[1], b[1]));
            for (int y = ya; y < yb; ) {
                int v = Math.floorMod(y + off, SCROLL_PERIOD);
                int run = Math.min(SCROLL_PERIOD - v, yb - y);
                gfx.blit(TEX, x, y, LINE_V[2], run, LINE_V[0], LINE_V[1] + v, LINE_V[2], run, TEX_SIZE, TEX_SIZE);
                y += run;
            }
        }
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
