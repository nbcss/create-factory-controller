package io.github.nbcss.createfactorycontroller.content.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiSpriteManager;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.io.IOException;

/**
 * Renders a sprite in a tiled or nine-sliced manner. Functionally similar to
 * {@link GuiGraphics#blitSprite(ResourceLocation, int, int, int, int, int) GuiGraphics#blitSprite}
 * where the sprite's scaling type is {@code tile} or {@code nine_slice}, but tiling is done in the shader instead of
 * with for loops issuing a draw call for every tile.
 */
@OnlyIn(Dist.CLIENT)
public abstract class TiledSpriteRenderer {

    // Define a custom vertex format and shader.
    // The fragment shader receives:
    // - uv in pixel coordinates within the sprite bounding box, starts at (0, 0) at top-left and will wrap outside the
    //   sprite size.
    // - Bounding box of the sprite in the texture atlas. The most suitable built-in VertexFormatElement are UV1 and
    //   UV2, making 4 i16's, as integers they are in pixel coordinates.

    protected static final VertexFormat VERTEX_FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV1", VertexFormatElement.UV1)
            .add("UV2", VertexFormatElement.UV2)
            .build();

    private static final ResourceLocation SHADER_LOCATION = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "tiled_sprite");
    private static ShaderInstance shader;

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER_LOCATION, VERTEX_FORMAT), s -> shader = s);
        } catch (IOException exception) {
            throw new RuntimeException("could not reload shaders", exception);
        }
    }

    protected static ShaderInstance getShader() {
        return shader;
    }


    /**
     * Create a renderer for a sprite in "textures/gui/sprites" with an mcmeta file.
     */
    public static @NotNull TiledSpriteRenderer create(ResourceLocation spriteLocation) {
        GuiSpriteManager spriteManager = Minecraft.getInstance().getGuiSprites();
        var sprite = spriteManager.getSprite(spriteLocation);
        var spriteScaling = spriteManager.getSpriteScaling(sprite);
        return switch (spriteScaling.type()) {
            case TILE -> new Tile(
                    sprite.atlasLocation(), sprite.getX(), sprite.getY(), (GuiSpriteScaling.Tile) spriteScaling);
            case NINE_SLICE -> new NineSlice(
                    sprite.atlasLocation(), sprite.getX(), sprite.getY(), (GuiSpriteScaling.NineSlice) spriteScaling);
            default -> throw new UnsupportedOperationException("Only sprites of type tile and nine_slice are supported");
        };
    }

    /**
     * Create a renderer for any texture, provided with offsets and sprite scaling definition.
     * @param uOffset Staring u position of the sprite in pixels.
     * @param vOffset Staring v position of the sprite in pixels.
     */
    public static @NotNull TiledSpriteRenderer create(ResourceLocation atlasLocation, int uOffset, int vOffset, GuiSpriteScaling spriteScaling) {
        return switch (spriteScaling.type()) {
            case TILE -> new Tile(atlasLocation, uOffset, vOffset, (GuiSpriteScaling.Tile) spriteScaling);
            case NINE_SLICE -> new NineSlice(atlasLocation, uOffset, vOffset, (GuiSpriteScaling.NineSlice) spriteScaling);
            default -> throw new UnsupportedOperationException("Only sprites of type tile and nine_slice are supported");
        };
    }


    protected final ResourceLocation atlasLocation;
    protected final int uOffset, vOffset; // Offset of sprite in the atlas

    protected TiledSpriteRenderer(ResourceLocation atlasLocation, int uOffset, int vOffset) {
        this.atlasLocation = atlasLocation;
        this.uOffset = uOffset;
        this.vOffset = vOffset;
    }

    public void render(@NotNull GuiGraphics graphics, int x, int y, int width, int height) {
        render(graphics, x, y, 0, width, height);
    }

    public abstract void render(@NotNull GuiGraphics graphics, int x, int y, int blitOffset, int width, int height);


    // Helper
    protected static void buildQuad(BufferBuilder bufferBuilder, Matrix4f pose, int blitOffset,
                                    int x1, int y1, int x2, int y2,
                                    int u1, int v1, int u2, int v2) {
        int width = x2 - x1, height = y2 - y1;
        bufferBuilder.addVertex(pose, x1, y1, blitOffset)
                .setUv(0, 0)
                .setUv1(u1, v1).setUv2(u2, v2);
        bufferBuilder.addVertex(pose, x1, y2, blitOffset)
                .setUv(0, height)
                .setUv1(u1, v1).setUv2(u2, v2);
        bufferBuilder.addVertex(pose, x2, y2, blitOffset)
                .setUv(width, height)
                .setUv1(u1, v1).setUv2(u2, v2);
        bufferBuilder.addVertex(pose, x2, y1, blitOffset)
                .setUv(width, 0)
                .setUv1(u1, v1).setUv2(u2, v2);
    }


    /**
     * Renders the sprite tiled.
     */
    public static class Tile extends TiledSpriteRenderer {

        protected final GuiSpriteScaling.Tile spriteScaling;

        protected Tile(ResourceLocation atlasLocation, int uOffset, int vOffset, GuiSpriteScaling.Tile spriteScaling) {
            super(atlasLocation, uOffset, vOffset);
            this.spriteScaling = spriteScaling;
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int x, int y, int blitOffset, int width, int height) {
            RenderSystem.setShaderTexture(0, atlasLocation);
            RenderSystem.setShader(TiledSpriteRenderer::getShader);
            Matrix4f pose = graphics.pose().last().pose();
            BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, VERTEX_FORMAT);
            buildQuad(bufferBuilder, pose, blitOffset,
                    x, y, x + width, y + height,
                    uOffset, vOffset, uOffset + spriteScaling.width(), vOffset + spriteScaling.height());
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        }
    }

    /**
     * Renders the sprite in a nine-sliced manner.
     */
    public static class NineSlice extends TiledSpriteRenderer {

        protected final GuiSpriteScaling.NineSlice spriteScaling;

        protected NineSlice(ResourceLocation atlasLocation, int uOffset, int vOffset, GuiSpriteScaling.NineSlice spriteScaling) {
            super(atlasLocation, uOffset, vOffset);
            this.spriteScaling = spriteScaling;
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int x, int y, int blitOffset, int width, int height) {
            RenderSystem.setShaderTexture(0, atlasLocation);
            RenderSystem.setShader(TiledSpriteRenderer::getShader);
            Matrix4f pose = graphics.pose().last().pose();
            BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, VERTEX_FORMAT);

            var border = spriteScaling.border();

            //            x,u
            //       0   1   2   3
            //    0  -------------
            //       | 0 | 1 | 2 |
            // y  1  |---|---|---|
            // ,     | 3 | 4 | 5 |
            // v  2  |---|---|---|
            //       | 6 | 7 | 8 |
            //    3  -------------

            // Slice the sprite with 4x4 vertices.
            int[] xs = {x, x + border.left(), x + width - border.right(), x + width};
            int[] ys = {y, y + border.top(), y + height - border.bottom(), y + height};
            int[] us = {
                    uOffset, uOffset + border.left(),
                    uOffset + spriteScaling.width() - border.right(), uOffset + spriteScaling.width()
            };
            int[] vs = {
                    vOffset, vOffset + border.top(),
                    vOffset + spriteScaling.height() - border.bottom(), vOffset + spriteScaling.height()
            };

            // Build the 9 quads.
             for (int i = 0; i < 9; i++) {
                 buildQuad(bufferBuilder, pose, blitOffset,
                         xs[i % 3], ys[i / 3], xs[i % 3 + 1], ys[i / 3 + 1],
                         us[i % 3], vs[i / 3], us[i % 3 + 1], vs[i / 3 + 1]);
             }

            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        }
    }
}
