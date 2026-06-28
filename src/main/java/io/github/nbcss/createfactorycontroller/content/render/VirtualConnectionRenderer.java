package io.github.nbcss.createfactorycontroller.content.render;

import io.github.nbcss.createfactorycontroller.content.block.ComponentHolder;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import io.github.nbcss.createfactorycontroller.content.component.connection.RedstoneConnection;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualRedstoneLinkBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws connection arrows between virtual components on the canvas, reusing Create's own
 * factory-panel connection sprite ({@code create:block/factory_panel_connections}).
 *
 * <p>Connections follow the 2D grid like Create's panels: each connection is an axis-aligned
 * path — a single straight segment when the two cells share a row/column, otherwise an L-path
 * through one corner (corner side chosen by {@link Connection#arrowBendMode}). The
 * texture is a pre-oriented atlas: it contains a separate line + arrow strip for each of the four
 * grid directions, so we blit the matching strip directly (no rotation). The arrowhead points
 * into the {@code from} (source) gauge.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class VirtualConnectionRenderer {

    private static final int CELL = 16;

    // Create's connection sprite (16×16).
    private static final ResourceLocation TEX_STATIC =
            ResourceLocation.fromNamespaceAndPath("create", "textures/block/factory_panel_connections.png");
    private static final ResourceLocation TEX_ANIMATED =
            ResourceLocation.fromNamespaceAndPath("create", "textures/block/factory_panel_connections_animated.png");

    private static final int FRAME_SIZE = 16; // pixels
    private static final float FRAME_TIME = 2f; // ticks per frame
    private static final int N_FRAMES = 8;

    private enum Direction { UP, DOWN, LEFT, RIGHT }

    // Subtexture rect coordinates in a frame.
    // (ox, oy) = origin which is aligned with the center of a cell
    // t = starting frame
    // LINE_*_0 = line that fills the earlier half of the cell in this direction
    // LINE_*_1 = line that fills the latter half of the cell in this direction
    private record Subtexture(int u, int v, int w, int h, int ox, int oy, int t) {
        public static final Subtexture LINE_UP_0    = of( 0,  0,  4,  8,  2,  0, 0);
        public static final Subtexture LINE_UP_1    = of( 0,  0,  4,  8,  2,  8, 0);
        public static final Subtexture LINE_DOWN_0  = of( 4,  0,  4,  8,  2,  8, 0);
        public static final Subtexture LINE_DOWN_1  = of( 4,  0,  4,  8,  2,  0, 0);
        public static final Subtexture LINE_LEFT_0  = of( 0,  8,  8,  4,  0,  2, 0);
        public static final Subtexture LINE_LEFT_1  = of( 0,  8,  8,  4,  8,  2, 0);
        public static final Subtexture LINE_RIGHT_0 = of( 0, 12,  8,  4,  8,  2, 0);
        public static final Subtexture LINE_RIGHT_1 = of( 0, 12,  8,  4,  0,  2, 0);
        public static final Subtexture HEAD_UP      = of( 8,  3,  4,  2,  2, -6, 4);
        public static final Subtexture HEAD_DOWN    = of(12,  3,  4,  2,  2,  8, 4);
        public static final Subtexture HEAD_LEFT    = of(11,  8,  2,  4, -6,  2, 4);
        public static final Subtexture HEAD_RIGHT   = of(11, 12,  2,  4,  8,  2, 4);
        public static final Subtexture TAIL_UP      = of( 0,  0,  4,  2,  2,  8, 0);
        public static final Subtexture TAIL_DOWN    = of( 4,  6,  4,  2,  2, -6, 0);
        public static final Subtexture TAIL_LEFT    = of( 0,  8,  2,  4,  8,  2, 0);
        public static final Subtexture TAIL_RIGHT   = of( 6, 12,  2,  4, -6,  2, 0);

        public static Subtexture of(int u, int v, int w, int h, int ox, int oy, int t) {
            return new Subtexture(u, v, w, h, ox, oy, t);
        }
    }

    // Flash mix targets (Create's renderPath): on each request attempt the line flashes toward whitish
    // if the source can supply, or red if it can't. The flash decays over FLASH_DECAY ticks from the
    // gauge's last-attempt tick, so it pulses once per request (cooldown), not continuously.
    private static final int FLASH_OK   = 0xEAF2EC;
    private static final int FLASH_FAIL = 0xE5654B;
    private static final float FLASH_DECAY = 8f;   // ticks for the attempt flash to fade out

    /**
     * Draws every incoming connection in the panel. Call inside the canvas scissor, before the
     * components are drawn, so the gauge icons sit on top of the line ends.
     *
     * <p>A connection whose source/target cells span a rectangle that doesn't overlap the visible canvas rectangle
     * {@code [minX,maxX]×[minY,maxY]} (canvas-world px) is skipped — its whole grid path (any bend included) stays
     * inside that rectangle, so it can contribute nothing on screen.</p>
     */
    public static void renderConnections(GuiGraphics gfx, FactoryControllerMenu menu,
                                         int minX, int minY, int maxX, int maxY) {
        Set<VirtualComponentPosition> occupied = new HashSet<>();
        Map<VirtualComponentPosition, VirtualComponentBehaviour> byPos = new HashMap<>();
        for (VirtualComponentBehaviour c : menu.components) {
            occupied.add(c.position());
            byPos.put(c.position(), c);
        }
        for (VirtualComponentBehaviour sink : menu.components) {
            for (Connection conn : sink.targetedBy().values()) {
                if (!spanVisible(conn.from, conn.to, minX, minY, maxX, maxY)) continue;
                drawConnection(gfx, menu, conn, byPos.get(conn.from), byPos.get(conn.to), occupied);
            }
        }
    }

    /** Whether the cell-bounding rectangle of the two connection ends overlaps the visible canvas rectangle. */
    private static boolean spanVisible(VirtualComponentPosition a, VirtualComponentPosition b,
                                       int minX, int minY, int maxX, int maxY) {
        int x0 = Math.min(a.x(), b.x()) * CELL;
        int y0 = Math.min(a.y(), b.y()) * CELL;
        int x1 = (Math.max(a.x(), b.x()) + 1) * CELL;
        int y1 = (Math.max(a.y(), b.y()) + 1) * CELL;
        return x0 < maxX && x1 > minX && y0 < maxY && y1 > minY;
    }

    /**
     * Draws one grid-following connection. The stored {@code conn.from -> conn.to} direction is the signal/item flow;
     * redstone link mode changes rewrite that direction in the graph instead of being special-cased here.
     */
    private static void drawConnection(GuiGraphics gfx,
                                       ComponentHolder holder, Connection conn,
                                       VirtualComponentBehaviour source,
                                       VirtualComponentBehaviour sink,
                                       Set<VirtualComponentPosition> occupied) {
        VirtualComponentPosition from = conn.from;
        VirtualComponentPosition to = conn.to;
        if (from.equals(to)) return;

        // Resolve the bend mode. Auto (-1) mirrors Create: try the four modes in order and use the
        // first whose path runs through no other component cell; if all are blocked, fall to V→H.
        int mode;
        if (conn.arrowBendMode < 0) {
            // Resolve auto on a canonical (flow-direction-independent) endpoint order, then translate back to from→to,
            // so flipping a redstone wire's direction doesn't reshape its path — only the arrowhead flips.
            boolean swap = from.x() > to.x() || (from.x() == to.x() && from.y() > to.y());
            VirtualComponentPosition pa = swap ? to : from, pb = swap ? from : to;
            int m = 0;
            for (int k = 0; k < 4; k++) {
                if (pathClear(buildCellPath(pa, pb, k), occupied, pa, pb)) { m = k; break; }
            }
            mode = !swap ? m : (m == 0 ? 1 : m == 1 ? 0 : m);   // translate canonical mode → from→to orientation
        } else {
            mode = conn.arrowBendMode % 4;
        }

        List<Vector2i> path = new ArrayList<>(buildCellPath(from, to, mode));

        assert Minecraft.getInstance().level != null;

        int color = conn.getConnectionColor(holder);
        long animationTick = conn.getAnimationTick(holder);
        boolean animated = animationTick >= 0;
        if (animated) {
            // Flash once per request attempt, decaying from the gauge's last-attempt tick.
            float age = animationTick + AnimationTickHolder.getPartialTicks();
            float glow = Mth.clamp(1f - age / FLASH_DECAY, 0f, 1f);
            if (glow > 0f) {
                float p = 1f - (1f - glow) * (1f - glow);
                boolean success = conn instanceof LogisticsConnection lc && lc.success;
                color = Color.mixColors(color, success ? FLASH_OK : FLASH_FAIL, p);
            }
        }
        gfx.setColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, 1f);
        drawPathSegments(gfx, path, animated);
        gfx.setColor(1f, 1f, 1f, 1f);
    }

    /** Draws a static connection {@code path} (cell waypoints) in {@code color} (0xRRGGBB) — for the Logical Tube
     *  settings grids. Translate the pose so cell {@code (0,0)} sits at the desired screen origin. */
    public static void drawGuiPath(GuiGraphics gfx, List<Vector2i> path, int color) {
        gfx.setColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, 1f);
        drawPathSegments(gfx, path, false);
        gfx.setColor(1f, 1f, 1f, 1f);
    }

    /** Walks a cell-space polyline and blits the arrow strip per segment (head at the last point, tail at the first).
     *  The caller sets the colour via {@code gfx.setColor} first; cells are {@link #CELL} px, so translate the pose to
     *  position the path. */
    public static void drawPathSegments(GuiGraphics gfx, List<Vector2i> path, boolean animated) {
        // Walk every line of the path and blit.
        for (int i = 0; i < path.size() - 1; i++) {
            Vector2i a = path.get(i), b = path.get(i + 1);
            int length = (int) a.gridDistance(b);

            Direction direction;
            if      (a.y > b.y) direction = Direction.UP;
            else if (a.y < b.y) direction = Direction.DOWN;
            else if (a.x > b.x) direction = Direction.LEFT;
            else if (a.x < b.x) direction = Direction.RIGHT;
            else continue;

            // Walk cell by cell along this line, blitting the body strip in each cell.
            for (int j = 0; j <= length; j++) {
                Vector2i cell = switch (direction) {
                    case UP    -> new Vector2i(a.x,     a.y - j);
                    case DOWN  -> new Vector2i(a.x,     a.y + j);
                    case LEFT  -> new Vector2i(a.x - j, a.y    );
                    case RIGHT -> new Vector2i(a.x + j, a.y    );
                };

                // Earlier half of the cell in the current direction
                if (j != 0) {
                    var st0 = (i == path.size() - 2 && j == length)
                    ? switch (direction) {
                        case UP    -> Subtexture.HEAD_UP;
                        case DOWN  -> Subtexture.HEAD_DOWN;
                        case LEFT  -> Subtexture.HEAD_LEFT;
                        case RIGHT -> Subtexture.HEAD_RIGHT;
                    } : switch (direction) {
                        case UP    -> Subtexture.LINE_UP_0;
                        case DOWN  -> Subtexture.LINE_DOWN_0;
                        case LEFT  -> Subtexture.LINE_LEFT_0;
                        case RIGHT -> Subtexture.LINE_RIGHT_0;
                    };
                    drawSegment(gfx, st0, animated, cell.x, cell.y);
                }
                // Latter half of the cell in the current direction
                if (j != length) {
                    var st1 = (i == 0 && j == 0)
                    ? switch (direction) {
                        case UP    -> Subtexture.TAIL_UP;
                        case DOWN  -> Subtexture.TAIL_DOWN;
                        case LEFT  -> Subtexture.TAIL_LEFT;
                        case RIGHT -> Subtexture.TAIL_RIGHT;
                    } : switch (direction) {
                        case UP    -> Subtexture.LINE_UP_1;
                        case DOWN  -> Subtexture.LINE_DOWN_1;
                        case LEFT  -> Subtexture.LINE_LEFT_1;
                        case RIGHT -> Subtexture.LINE_RIGHT_1;
                    };
                    drawSegment(gfx, st1, animated, cell.x, cell.y);
                }
            }
        }
    }

    /**
     * Draw a single segment of arrow graphics. Align sprite origin with cell center.
     * @param x Cell x coordinate.
     * @param y Cell y coordinate.
     */
    private static void drawSegment(GuiGraphics gfx, Subtexture st, boolean animated, int x, int y) {
        int frame = !animated ? 0 :
                (int) (AnimationTickHolder.getRenderTime() / FRAME_TIME + st.t) % N_FRAMES;
        gfx.blit(
                animated ? TEX_ANIMATED : TEX_STATIC,
                x * CELL + CELL/2 - st.ox, y * CELL + CELL/2 - st.oy,
                st.u, st.v + frame * FRAME_SIZE, st.w, st.h,
                FRAME_SIZE, FRAME_SIZE * (animated ? N_FRAMES : 1));
    }

    /**
     * Grid-following cell waypoints from source to target for a bend mode (Create's order):
     * <ul><li>0 — V→H (vertical-first L)</li>
     *     <li>1 — H→V (horizontal-first L)</li>
     *     <li>2 — H→V→H staircase (falls back to H→V if it can't fit)</li>
     *     <li>3 — V→H→V staircase (falls back to V→H if it can't fit)</li></ul>
     * A staircase needs a cell strictly between source and target on its stepping axis ({@code |d|>=2}).
     */
    private static List<Vector2i> buildCellPath(VirtualComponentPosition from, VirtualComponentPosition to, int mode) {
        int fx = from.x(), fy = from.y(), tx = to.x(), ty = to.y();
        if (fx == tx || fy == ty)                                   // aligned → straight run
            return List.of(new Vector2i(fx, fy), new Vector2i(tx, ty));

        int dx = tx - fx, dy = ty - fy;
        return switch (mode) {
            case 1 ->                                                 // H→V
                    List.of(new Vector2i(fx, fy), new Vector2i(tx, fy), new Vector2i(tx, ty));
            case 2 -> {
                if (Math.abs(dx) >= 2) {
                    int mx = fx + dx / 2;                           // bend column (a cell centre)
                    yield List.of(new Vector2i(fx, fy), new Vector2i(mx, fy), new Vector2i(mx, ty), new Vector2i(tx, ty));
                }
                yield List.of(new Vector2i(fx, fy), new Vector2i(tx, fy), new Vector2i(tx, ty));
            }
            case 3 -> {
                if (Math.abs(dy) >= 2) {
                    int my = fy + dy / 2;                           // bend row (a cell centre)
                    yield List.of(new Vector2i(fx, fy), new Vector2i(fx, my), new Vector2i(tx, my), new Vector2i(tx, ty));
                }
                yield List.of(new Vector2i(fx, fy), new Vector2i(fx, ty), new Vector2i(tx, ty));
            }
            default ->                                                // 0: V→H
                    List.of(new Vector2i(fx, fy), new Vector2i(fx, ty), new Vector2i(tx, ty));
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
                c.x += stepX; c.y += stepY;
            }
        }
        return true;
    }

}
