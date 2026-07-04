package io.github.nbcss.createfactorycontroller.content.gui.widget;

import io.github.nbcss.createfactorycontroller.content.block.ComponentHolder;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import io.github.nbcss.createfactorycontroller.content.render.VirtualConnectionRenderer;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side view of a single connection, holding its resolved cell-space path and providing
 * hit-testing, highlight, and draw methods used by the layered connection render pass.
 */
@OnlyIn(Dist.CLIENT)
public class ConnectionWidget {

    private static final int CELL = 16;
    /** Half the 4 px strip width: the strip spans [centre-HALF, centre+HALF] perpendicular to its run. */
    private static final int HALF = 2;
    /** How far the strip reaches into an endpoint (source/sink) cell — the drawn line stub is 2 px deep. */
    private static final int STUB = 2;
    private static final int HIGHLIGHT_COLOR = 0xCCFFFFFF;   // 80% white
    private static final int FLASH_OK   = 0xEAF2EC;
    private static final int FLASH_FAIL = 0xE5654B;
    private static final float FLASH_DECAY = 8f;

    public final Connection connection;
    final List<Vector2i> path;   // resolved cell-space waypoints, package-visible for tests

    /** Disjoint {@code {x0,y0,x1,y1}} rectangles (canvas-world px) covering the 4 px-wide path strip exactly once.
     *  Shared by {@link #hitTest} and {@link #renderHighlight} so the hover region and the drawn outline always match,
     *  and the highlight never double-paints at <1 alpha. Built once — the path is immutable for this widget. */
    private final List<int[]> stripRects;

    public ConnectionWidget(Connection connection, List<Vector2i> path) {
        this.connection = connection;
        this.path = path;
        this.stripRects = buildStripRects(path);
    }

    /**
     * Decomposes the path into non-overlapping rectangles: one 4×4 node box at each interior waypoint (turn point),
     * and one 4 px-wide link box per segment trimmed to abut those node boxes (no overlap) and to reach only
     * {@link #STUB} px into the two path-endpoint cells (so the strip never bleeds into the source/sink component).
     */
    private static List<int[]> buildStripRects(List<Vector2i> path) {
        List<int[]> rects = new ArrayList<>();
        int n = path.size();
        // Node boxes at interior waypoints (corners), each with its convex (outer) corner pixel trimmed.
        for (int i = 1; i < n - 1; i++) {
            addCornerRects(rects, path.get(i - 1), path.get(i), path.get(i + 1));
        }
        // Link boxes between consecutive waypoints.
        for (int i = 0; i < n - 1; i++) {
            Vector2i a = path.get(i), b = path.get(i + 1);
            boolean aEndpoint = (i == 0), bEndpoint = (i == n - 2);
            if (a.x == b.x) {
                int cx = a.x;
                int yLo = Math.min(a.y, b.y), yHi = Math.max(a.y, b.y);
                boolean loEnd = (a.y < b.y) ? aEndpoint : bEndpoint;   // is the lo-y waypoint a path endpoint?
                boolean hiEnd = (a.y < b.y) ? bEndpoint : aEndpoint;
                int y0 = loEnd ? yLo * CELL + (CELL - STUB) : yLo * CELL + (CELL/2 + HALF);
                int y1 = hiEnd ? yHi * CELL + STUB          : yHi * CELL + (CELL/2 - HALF);
                rects.add(new int[]{ cx * CELL + (CELL/2 - HALF), y0, cx * CELL + (CELL/2 + HALF), y1 });
            } else {
                int cy = a.y;
                int xLo = Math.min(a.x, b.x), xHi = Math.max(a.x, b.x);
                boolean loEnd = (a.x < b.x) ? aEndpoint : bEndpoint;
                boolean hiEnd = (a.x < b.x) ? bEndpoint : aEndpoint;
                int x0 = loEnd ? xLo * CELL + (CELL - STUB) : xLo * CELL + (CELL/2 + HALF);
                int x1 = hiEnd ? xHi * CELL + STUB          : xHi * CELL + (CELL/2 - HALF);
                rects.add(new int[]{ x0, cy * CELL + (CELL/2 - HALF), x1, cy * CELL + (CELL/2 + HALF) });
            }
        }
        return rects;
    }

    /**
     * Adds the corner node box at {@code cur} as two disjoint rects, omitting the single convex (outer) corner pixel
     * so an L-bend shows no redundant highlight pixel diagonally outside the turn (e.g. the top-right pixel of a
     * right→down bend). The links always attach on the concave sides, so trimming the convex corner is safe.
     */
    private static void addCornerRects(List<int[]> rects, Vector2i prev, Vector2i cur, Vector2i next) {
        int x0 = cur.x * CELL + (CELL/2 - HALF), x1 = cur.x * CELL + (CELL/2 + HALF);
        int y0 = cur.y * CELL + (CELL/2 - HALF), y1 = cur.y * CELL + (CELL/2 + HALF);
        // One neighbour shares cur's row (horizontal segment), the other shares its column (vertical segment).
        Vector2i h = (prev.y == cur.y) ? prev : next;
        Vector2i v = (prev.y == cur.y) ? next : prev;
        boolean outerEast  = h.x < cur.x;   // line runs west  → convex corner is on the east
        boolean outerSouth = v.y < cur.y;   // line runs north → convex corner is on the south
        if (outerSouth) {
            rects.add(new int[]{ x0, y0, x1, y1 - 1 });                                 // upper rows, full width
            rects.add(outerEast ? new int[]{ x0, y1 - 1, x1 - 1, y1 }                   // bottom row, drop E pixel
                                : new int[]{ x0 + 1, y1 - 1, x1, y1 });                 // bottom row, drop W pixel
        } else {
            rects.add(new int[]{ x0, y0 + 1, x1, y1 });                                 // lower rows, full width
            rects.add(outerEast ? new int[]{ x0, y0, x1 - 1, y0 + 1 }                   // top row, drop E pixel
                                : new int[]{ x0 + 1, y0, x1, y0 + 1 });                 // top row, drop W pixel
        }
    }

    /**
     * Hover tooltip for this wire: its kind name, plus an overlap selector ({@code □□■□}) and a scroll hint when more
     * than one wire sits under the cursor. {@code overlapCount}/{@code selectedIndex} are supplied by the screen, which
     * owns the set of overlapping wires; {@code overlapCount <= 1} shows the name only.
     */
    public List<Component> getTooltip(int overlapCount, int selectedIndex, boolean arrowLocked) {
        List<Component> lines = new ArrayList<>();
        lines.add(connection.type.displayName());
        if (arrowLocked) {
            // Arrow-mode boxes: the 4 fixed bends, ■ marking the wire's current mode (auto shows none, until first cycle).
            int active = connection.arrowBendMode;   // 0..3; -1 (auto) highlights nothing
            StringBuilder boxes = new StringBuilder();
            for (int i = 0; i < 4; i++) boxes.append(i == active ? '■' : '□');
            lines.add(Component.translatable("createfactorycontroller.connection.cycle_arrow", boxes.toString())
                    .withStyle(ChatFormatting.GRAY));
        } else if (overlapCount > 1) {
            StringBuilder boxes = new StringBuilder();
            for (int i = 0; i < overlapCount; i++) boxes.append(i == selectedIndex ? '■' : '□');   // ■ marks the selected wire
            lines.add(Component.translatable("createfactorycontroller.connection.overlapping", boxes.toString())
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("createfactorycontroller.connection.scroll_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable("createfactorycontroller.gui.action_remove_component")
                .withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    /** Returns true if the canvas-world point lies within the 4 px-wide path strip (segments and turn points). */
    public boolean hitTest(double worldMouseX, double worldMouseY) {
        for (int[] r : stripRects)
            if (worldMouseX >= r[0] && worldMouseX <= r[2] && worldMouseY >= r[1] && worldMouseY <= r[3])
                return true;
        return false;
    }

    /** Draws this connection in its normal (non-hovered) state. */
    public void render(GuiGraphics gfx, ComponentHolder holder) {
        renderWithColor(gfx, holder);
    }

    /**
     * Draws the white highlight strip behind the hovered connection. The {@link #stripRects} are disjoint, so each
     * pixel is painted exactly once — no brighter seams at turn points even at the 80% highlight alpha.
     */
    public void renderHighlight(GuiGraphics gfx) {
        for (int[] r : stripRects)
            gfx.fill(r[0], r[1], r[2], r[3], HIGHLIGHT_COLOR);
    }

    private void renderWithColor(GuiGraphics gfx, ComponentHolder holder) {
        int color = connection.getConnectionColor(holder);
        long animationTick = connection.getAnimationTick(holder);
        boolean animated = animationTick >= 0;
        if (animated) {
            float age = animationTick + AnimationTickHolder.getPartialTicks();
            float glow = Mth.clamp(1f - age / FLASH_DECAY, 0f, 1f);
            if (glow > 0f) {
                float p = 1f - (1f - glow) * (1f - glow);
                boolean success = connection instanceof LogisticsConnection lc && lc.success;
                color = Color.mixColors(color, success ? FLASH_OK : FLASH_FAIL, p);
            }
        }
        gfx.setColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, 1f);
        VirtualConnectionRenderer.drawPathSegments(gfx, path, animated);
        gfx.setColor(1f, 1f, 1f, 1f);
    }
}
