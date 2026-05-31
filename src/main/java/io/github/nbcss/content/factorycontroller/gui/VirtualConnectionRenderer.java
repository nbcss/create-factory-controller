package io.github.nbcss.content.factorycontroller.gui;

import io.github.nbcss.content.factorycontroller.FactoryControllerMenu;
import io.github.nbcss.content.factorycontroller.VirtualComponentBehaviour;
import io.github.nbcss.content.factorycontroller.VirtualPanelConnection;
import io.github.nbcss.content.factorycontroller.VirtualPanelPosition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;

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

    // Atlas sub-rects {u, v, w, h}. Create's connection models apply rotation:180 to every face,
    // which we can't replicate with a plain blit — so each strip's raw pixels point OPPOSITE its
    // model name. The constants below are named by the direction they actually point when blitted
    // raw: ARROW_N = the rect that visually points up (Create's "south" rect), etc.
    private static final int[] LINE_V  = {4, 0, 4, 8};   // vertical line   (4w × 8h)
    private static final int[] LINE_H  = {0, 8, 8, 4};   // horizontal line (8w × 4h)
    private static final int[] ARROW_N = {8, 0, 4, 8};   // points up    (model "south" rect)
    private static final int[] ARROW_S = {12, 0, 4, 8};  // points down  (model "north" rect)
    private static final int[] ARROW_E = {8, 12, 8, 4};  // points right (model "west" rect)
    private static final int[] ARROW_W = {8, 8, 8, 4};   // points left  (model "east" rect)

    private VirtualConnectionRenderer() {}

    /**
     * Draws every incoming connection in the panel. Call inside the canvas scissor, before the
     * components are drawn, so the gauge icons sit on top of the line ends.
     */
    public static void renderConnections(GuiGraphics gfx, FactoryControllerMenu menu,
                                         int centerX, int centerY,
                                         double viewX, double viewY, double zoom) {
        for (VirtualComponentBehaviour target : menu.components) {
            VirtualPanelPosition toPos = target.position();
            for (Map.Entry<VirtualPanelPosition, VirtualPanelConnection> e : target.targetedBy().entrySet()) {
                drawConnection(gfx, e.getKey(), toPos, e.getValue(), centerX, centerY, viewX, viewY, zoom);
            }
        }
    }

    /** Draws one grid-following connection. Flow is {@code from → to}; the arrowhead enters {@code to}. */
    private static void drawConnection(GuiGraphics gfx,
                                       VirtualPanelPosition from, VirtualPanelPosition to,
                                       VirtualPanelConnection conn,
                                       int centerX, int centerY,
                                       double viewX, double viewY, double zoom) {
        if (from.equals(to)) return;

        // Destination = arrow end; source = plain end (line may start under the source's gauge icon).
        float ax = cellCenterX(to, centerX, viewX, zoom);
        float ay = cellCenterY(to, centerY, viewY, zoom);
        float px = cellCenterX(from, centerX, viewX, zoom);
        float py = cellCenterY(from, centerY, viewY, zoom);

        float half = (float) (CELL * 0.5 * zoom);
        int thickness = Math.max(2, Math.round(4f * (float) zoom));   // sprite line is 4px wide
        int arrowLen = Math.max(4, Math.round(8f * (float) zoom));    // sprite arrow strip is 8px long

        boolean sameCol = from.x() == to.x();
        boolean sameRow = from.y() == to.y();
        boolean horizontalFirst = conn.arrowBendMode < 0 || conn.arrowBendMode % 2 == 0;

        // Route from the plain end (source) through a corner into the arrow end (destination). The
        // final segment runs along one axis into `to`; that axis decides the arrow direction.
        float cornerX, cornerY;
        boolean approachVertical;
        if (sameRow) {
            cornerX = px; cornerY = ay; approachVertical = false;
        } else if (sameCol) {
            cornerX = ax; cornerY = py; approachVertical = true;
        } else if (horizontalFirst) {     // travel along X first, then vertically into `from`
            cornerX = ax; cornerY = py; approachVertical = true;
        } else {                          // travel along Y first, then horizontally into `from`
            cornerX = px; cornerY = ay; approachVertical = false;
        }

        // Tint encodes state; the sprite is grayscale.
        if (conn.success) gfx.setColor(0.27f, 0.87f, 0.33f, 1f);
        else gfx.setColor(0.88f, 0.88f, 0.88f, 1f);

        // Body: source → corner → arrow base. Arrowhead occupies the `arrowLen` next to `to`.
        if (approachVertical) {
            boolean destAbove = ay < cornerY;               // arrow points up if destination is above
            float edge = ay + (destAbove ? half : -half);   // `to` edge facing the gap
            float base = edge + (destAbove ? arrowLen : -arrowLen);
            hLine(gfx, px, cornerX, cornerY, thickness);    // source → corner (horizontal)
            vLine(gfx, cornerX, cornerY, base, thickness);  // corner → arrow base (vertical)
            if (destAbove) blit(gfx, ARROW_N, Math.round(ax) - thickness / 2, Math.round(edge), thickness, arrowLen);
            else           blit(gfx, ARROW_S, Math.round(ax) - thickness / 2, Math.round(base), thickness, arrowLen);
        } else {
            boolean destLeft = ax < cornerX;                // arrow points left if destination is left
            float edge = ax + (destLeft ? half : -half);
            float base = edge + (destLeft ? arrowLen : -arrowLen);
            vLine(gfx, cornerX, py, cornerY, thickness);    // source → corner (vertical)
            hLine(gfx, cornerX, base, cornerY, thickness);  // corner → arrow base (horizontal)
            if (destLeft) blit(gfx, ARROW_W, Math.round(edge), Math.round(ay) - thickness / 2, arrowLen, thickness);
            else          blit(gfx, ARROW_E, Math.round(base), Math.round(ay) - thickness / 2, arrowLen, thickness);
        }

        gfx.setColor(1f, 1f, 1f, 1f);
    }

    /** Vertical line strip at screen-x {@code x}, between screen-y {@code y0} and {@code y1}. */
    private static void vLine(GuiGraphics gfx, float x, float y0, float y1, int t) {
        int ya = Math.round(Math.min(y0, y1)), yb = Math.round(Math.max(y0, y1));
        if (yb > ya) blit(gfx, LINE_V, Math.round(x) - t / 2, ya, t, yb - ya);
    }

    /** Horizontal line strip at screen-y {@code y}, between screen-x {@code x0} and {@code x1}. */
    private static void hLine(GuiGraphics gfx, float x0, float x1, float y, int t) {
        int xa = Math.round(Math.min(x0, x1)), xb = Math.round(Math.max(x0, x1));
        if (xb > xa) blit(gfx, LINE_H, xa, Math.round(y) - t / 2, xb - xa, t);
    }

    /** Blits an atlas sub-rect {@code {u,v,w,h}}, stretched into the given screen rectangle. */
    private static void blit(GuiGraphics gfx, int[] uv, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        gfx.blit(TEX, x, y, w, h, uv[0], uv[1], uv[2], uv[3], TEX_SIZE, TEX_SIZE);
    }

    private static float cellCenterX(VirtualPanelPosition pos, int centerX, double viewX, double zoom) {
        return (float) (centerX + ((pos.x() + 0.5) * CELL - viewX) * zoom);
    }

    private static float cellCenterY(VirtualPanelPosition pos, int centerY, double viewY, double zoom) {
        return (float) (centerY + ((pos.y() + 0.5) * CELL - viewY) * zoom);
    }
}
