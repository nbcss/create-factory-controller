package io.github.nbcss.content.factorycontroller;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.GuiSpriteManager;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.io.IOException;

@OnlyIn(Dist.CLIENT)
public abstract class TiledSpriteRenderer {

    // Define a custom vertex format and shader.

    static final VertexFormat VERTEX_FORMAT = VertexFormat.builder()
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

    private static ShaderInstance getShader() {
        return shader;
    }


    // Polymorphic factory.

    public static TiledSpriteRenderer create(ResourceLocation spriteLocation) {
        GuiSpriteManager spriteManager = Minecraft.getInstance().getGuiSprites();
        var sprite = spriteManager.getSprite(spriteLocation);
        var spriteScaling = spriteManager.getSpriteScaling(sprite);
        return switch (spriteScaling.type()) {
            case TILE -> new TiledSpriteRenderer.Tile(sprite, (GuiSpriteScaling.Tile) spriteScaling);
            case NINE_SLICE -> new TiledSpriteRenderer.NineSlice(sprite, (GuiSpriteScaling.NineSlice) spriteScaling);
            default -> throw new UnsupportedOperationException("Only sprites of type tile and nine_slice are supported");
        };
    }


    protected final TextureAtlasSprite sprite;

    protected TiledSpriteRenderer(TextureAtlasSprite sprite) {
        this.sprite = sprite;
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


    public static class Tile extends TiledSpriteRenderer {

        protected final GuiSpriteScaling.Tile spriteScaling;

        protected Tile(TextureAtlasSprite sprite, GuiSpriteScaling.Tile spriteScaling) {
            super(sprite);
            this.spriteScaling = spriteScaling;
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int x, int y, int blitOffset, int width, int height) {
            RenderSystem.setShaderTexture(0, sprite.atlasLocation());
            RenderSystem.setShader(TiledSpriteRenderer::getShader);
            Matrix4f pose = graphics.pose().last().pose();
            BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, VERTEX_FORMAT);
            buildQuad(bufferBuilder, pose, blitOffset,
                    x, y, x + width, y + height,
                    sprite.getX(), sprite.getY(), sprite.getX() + spriteScaling.width(), sprite.getY() + spriteScaling.height());
            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        }
    }

    public static class NineSlice extends TiledSpriteRenderer {

        protected final GuiSpriteScaling.NineSlice spriteScaling;

        protected NineSlice(TextureAtlasSprite sprite, GuiSpriteScaling.NineSlice spriteScaling) {
            super(sprite);
            this.spriteScaling = spriteScaling;
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int x, int y, int blitOffset, int width, int height) {
            RenderSystem.setShaderTexture(0, sprite.atlasLocation());
            RenderSystem.setShader(TiledSpriteRenderer::getShader);
            Matrix4f pose = graphics.pose().last().pose();
            BufferBuilder bufferBuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, VERTEX_FORMAT);

            var border = spriteScaling.border();

            int[] xs = {x, x + border.left(), x + width - border.right(), x + width};
            int[] ys = {y, y + border.top(), y + height - border.bottom(), y + height};
            int[] us = {
                    sprite.getX(), sprite.getX() + border.left(),
                    sprite.getX() + spriteScaling.width() - border.right(), sprite.getX() + spriteScaling.width()
            };
            int[] vs = {
                    sprite.getY(), sprite.getY() + border.top(),
                    sprite.getY() + spriteScaling.height() - border.bottom(), sprite.getY() + spriteScaling.height()
            };

             for (int i = 0; i < 9; i++) {
                 buildQuad(bufferBuilder, pose, blitOffset,
                         xs[i % 3], ys[i / 3], xs[i % 3 + 1], ys[i / 3 + 1],
                         us[i % 3], vs[i / 3], us[i % 3 + 1], vs[i / 3 + 1]);
             }

            BufferUploader.drawWithShader(bufferBuilder.buildOrThrow());
        }
    }
}
