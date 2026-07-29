package io.github.nbcss.createfactorycontroller.content.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
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
public final class VirtualConnectionRenderer {

    private static final int CELL = 16;

    // Create's connection sprite (16×16).
    private static final ResourceLocation TEX_STATIC =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/connection/factory_panel_connections.png");
    private static final ResourceLocation TEX_ANIMATED =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/connection/factory_panel_connections_animated.png");
    private static final RenderType RENDER_TYPE_STATIC = createRenderType(TEX_STATIC);
    private static final RenderType RENDER_TYPE_ANIMATED = createRenderType(TEX_ANIMATED);

    private static final int FRAME_SIZE = 16; // pixels
    private static final float FRAME_TIME = 2f; // ticks per frame
    private static final int N_FRAMES = 8;

    private static RenderType createRenderType(ResourceLocation texture) {
        return RenderType.create("create_factory_controller_connection",
                DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, RenderType.TRANSIENT_BUFFER_SIZE,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.POSITION_TEX_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .createCompositeState(false));
    }

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

    /** Draws a connection {@code path} (cell waypoints) in {@code color} (0xAARRGGBB or 0xRRGGBB).
     * Translate the pose so cell {@code (0,0)} sits at the desired screen origin. */
    public static void drawPath(GuiGraphics gfx, List<Vector2i> path, int color, boolean animated) {
        int alpha = FastColor.ARGB32.alpha(color);
        gfx.setColor(FastColor.ARGB32.red(color) / 255f,
                FastColor.ARGB32.green(color) / 255f,
                FastColor.ARGB32.blue(color) / 255f,
                alpha == 0 ? 1f : alpha / 255f);

        drawPathSegments(gfx.bufferSource(), gfx.pose(), path, animated);
        gfx.bufferSource().endBatch(animated ? RENDER_TYPE_ANIMATED : RENDER_TYPE_STATIC);

        gfx.setColor(1f, 1f, 1f, 1f);
    }

    /** Walks a cell-space polyline and draws the arrow strip per segment (head at the last point, tail at the first).
     *  The caller sets the colour via {@code gfx.setColor} first; cells are {@link #CELL} px, so translate the pose to
     *  position the path. */
    private static void drawPathSegments(MultiBufferSource bufferSource, PoseStack pose, List<Vector2i> path, boolean animated) {
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
                    drawSegment(bufferSource, pose, st0, animated, cell.x, cell.y);
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
                    drawSegment(bufferSource, pose, st1, animated, cell.x, cell.y);
                }
            }
        }
    }

    /**
     * Draw a single segment of arrow graphics. Align sprite origin with cell center.
     * @param x Cell x coordinate.
     * @param y Cell y coordinate.
     */
    private static void drawSegment(MultiBufferSource bufferSource, PoseStack pose, Subtexture st, boolean animated, int x, int y) {
        int frame = !animated ? 0 :
                (int) (AnimationTickHolder.getRenderTime() / FRAME_TIME + st.t) % N_FRAMES;
        int x0 = x * CELL + CELL / 2 - st.ox;
        int y0 = y * CELL + CELL / 2 - st.oy;
        int x1 = x0 + st.w;
        int y1 = y0 + st.h;
        int textureHeight = FRAME_SIZE * (animated ? N_FRAMES : 1);
        float u0 = st.u / (float) FRAME_SIZE;
        float v0 = (st.v + frame * FRAME_SIZE) / (float) textureHeight;
        float u1 = (st.u + st.w) / (float) FRAME_SIZE;
        float v1 = (st.v + frame * FRAME_SIZE + st.h) / (float) textureHeight;

        VertexConsumer buffer = bufferSource.getBuffer(animated ? RENDER_TYPE_ANIMATED : RENDER_TYPE_STATIC);
        buffer.addVertex(pose.last(), x0, y0, 0).setUv(u0, v0);
        buffer.addVertex(pose.last(), x0, y1, 0).setUv(u0, v1);
        buffer.addVertex(pose.last(), x1, y1, 0).setUv(u1, v1);
        buffer.addVertex(pose.last(), x1, y0, 0).setUv(u1, v0);
    }

}
