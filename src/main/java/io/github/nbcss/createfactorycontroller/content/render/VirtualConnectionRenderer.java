package io.github.nbcss.createfactorycontroller.content.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector2i;

import java.util.List;

/**
 * Draws connection arrows between virtual components on the canvas, reusing Create's own
 * factory-panel connection sprite ({@code create:block/factory_panel_connections}).
 */
@OnlyIn(Dist.CLIENT)
public class VirtualConnectionRenderer {

    private static final int CELL = 16;
    private static final int FRAME_SIZE = 16; // pixels
    private static final float FRAME_TIME = 2f; // ticks per frame
    private static final int N_FRAMES = 8;

    // Create's connection sprite (16×16).
    private static final ResourceLocation TEX_STATIC =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/connection/factory_panel_connections.png");
    private static final ResourceLocation TEX_ANIMATED =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/connection/factory_panel_connections_animated.png");

    private static final DeferredBlitter BLITTER_STATIC = DeferredBlitter.forTexture(TEX_STATIC, FRAME_SIZE, FRAME_SIZE);
    private static final DeferredBlitter BLITTER_ANIMATED = DeferredBlitter.forTexture(TEX_ANIMATED, FRAME_SIZE, FRAME_SIZE * N_FRAMES);

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

    private final List<Vector2i> path;
    private final int color;
    private final boolean animated;
    private final DeferredBlitter blitter;

    protected VirtualConnectionRenderer(List<Vector2i> path, int color, boolean animated) {
        this.path = path;
        this.color = FastColor.ARGB32.alpha(color) == 0 ? FastColor.ARGB32.opaque(color) : color;
        this.animated = animated;
        this.blitter = animated ? BLITTER_ANIMATED : BLITTER_STATIC;
    }

    public static VirtualConnectionRenderer create(List<Vector2i> path, int color, boolean animated) {
        return new VirtualConnectionRenderer(path, color, animated);
    }

    /** Draws the connection path in its configured color. Immediate. */
    public void drawPath(GuiGraphics gfx) {
        drawPath(gfx.bufferSource(), gfx.pose());
        blitter.endBatch(gfx.bufferSource());
    }

    /** Draws the connection path in its configured color. Deferred. */
    public void drawPath(MultiBufferSource bufferSource, PoseStack pose) {
        blitter.setColorARGB(color);

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
                    drawSegment(bufferSource, pose, st0, cell.x, cell.y);
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
                    drawSegment(bufferSource, pose, st1, cell.x, cell.y);
                }
            }
        }
    }

    /**
     * Draw a single segment of arrow graphics. Align sprite origin with cell center.
     * @param x Cell x coordinate.
     * @param y Cell y coordinate.
     */
    private void drawSegment(MultiBufferSource bufferSource, PoseStack pose, Subtexture st, int x, int y) {
        int frame = !animated ? 0 :
                (int) (AnimationTickHolder.getRenderTime() / FRAME_TIME + st.t) % N_FRAMES;
        blitter.blit(bufferSource, pose,
                x * CELL + CELL / 2 - st.ox, y * CELL + CELL / 2 - st.oy,
                st.w, st.h, st.u, st.v + frame * FRAME_SIZE);
    }

}
