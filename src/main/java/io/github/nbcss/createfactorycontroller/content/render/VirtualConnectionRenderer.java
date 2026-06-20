package io.github.nbcss.createfactorycontroller.content.render;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.LogisticsConnection;
import io.github.nbcss.createfactorycontroller.content.component.RedstoneConnection;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualRedstoneLinkBehaviour;
import io.github.nbcss.createfactorycontroller.content.VirtualPanelConnection;
import io.github.nbcss.createfactorycontroller.content.VirtualPanelPosition;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
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
        Map<VirtualPanelPosition, VirtualComponentBehaviour> byPos = new HashMap<>();
        for (VirtualComponentBehaviour c : menu.components) { occupied.add(c.position()); byPos.put(c.position(), c); }
        for (VirtualComponentBehaviour target : menu.components) {
            VirtualPanelPosition toPos = target.position();
            for (Map.Entry<VirtualPanelPosition, VirtualPanelConnection> e : target.targetedBy().entrySet()) {
                drawConnection(gfx, e.getKey(), toPos, e.getValue(), target, byPos.get(e.getKey()), occupied);
            }
        }
    }

    /**
     * Draws one grid-following connection. For gauge sources the arrowhead enters {@code to} (the consumer). For a
     * redstone-link source the direction follows the link's mode: RECEIVE keeps link → gauge (arrow into the gauge),
     * SEND reverses to gauge → link (arrow into the link).
     */
    private static void drawConnection(GuiGraphics gfx,
                                       VirtualPanelPosition from, VirtualPanelPosition to,
                                       VirtualPanelConnection conn, VirtualComponentBehaviour target,
                                       VirtualComponentBehaviour source,
                                       Set<VirtualPanelPosition> occupied) {
        if (from.equals(to)) return;

        // The path is built in the stable storage direction (from = source, to = target) and its bend uses the stored
        // arrowBendMode, so toggling a redstone link's send/receive never re-routes it (matches Create: the path is
        // fixed, only the arrowhead end moves). A RECEIVE link points the arrow back into the gauge, which we do by
        // reversing the cell ORDER (identical shape) so the arrowhead lands on the source end instead of the target.
        boolean reverse = target instanceof VirtualRedstoneLinkBehaviour link && link.receive;

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
        if (reverse)
            for (int i = 0, j = cells.length - 1; i < j; i++, j--) { int[] t = cells[i]; cells[i] = cells[j]; cells[j] = t; }

        int color = 0x888898;
        boolean flowing = false;
        if (target instanceof VirtualRedstoneLinkBehaviour link && conn instanceof RedstoneConnection rc) {
            color = linkConnectionColor(link, rc, source);
        } else if (target instanceof VirtualGaugeBehaviour gauge) {
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
                    boolean success = conn instanceof LogisticsConnection lc && lc.success;
                    color = Color.mixColors(color, success ? FLASH_OK : FLASH_FAIL, p);
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

    // Redstone-link connection colours — exact values from Create's FactoryPanelRenderer.renderPath (redstone-link
    // branch): gray 0x888898 (no target), red 0xEF0000 (powered), dark red 0x580101 (valid but unpowered).
    private static final int LINK_INVALID = 0x888898;
    private static final int LINK_POWERED = 0xEF0000;
    private static final int LINK_UNPOWERED = 0x580101;

    /**
     * Connection colour for a redstone-link source. Gray only when the link is in SEND mode and its connected gauge
     * has no target amount (an invalid drive); otherwise light-red while the link is powered, dark-red while idle.
     */
    private static int linkConnectionColor(VirtualRedstoneLinkBehaviour link, RedstoneConnection conn,
                                           VirtualComponentBehaviour source) {
        boolean gaugeHasTarget = source instanceof VirtualGaugeBehaviour g && g.count != 0;
        if (!link.receive && !gaugeHasTarget) return LINK_INVALID;   // SEND with no driving target
        return conn.powered ? LINK_POWERED : LINK_UNPOWERED;
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
