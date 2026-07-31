package io.github.nbcss.createfactorycontroller.content.render;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiSpriteManager;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.concurrent.ExecutionException;

/** Queues colored, textured quads in a texture-specific render batch. */
@OnlyIn(Dist.CLIENT)
public class BatchedBlitter {

    private static final RenderStateShard.ShaderStateShard POSITION_TEX_COLOR_SHADER =
            new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader);

    private static final Cache<ResourceLocation, RenderType> RENDER_TYPES =
            CacheBuilder.newBuilder().weakValues().build();

    private static RenderType createRenderType(ResourceLocation texture) {
        return RenderType.create("create_factory_controller_blit",
                DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, RenderType.TRANSIENT_BUFFER_SIZE,
                RenderType.CompositeState.builder()
                        .setShaderState(POSITION_TEX_COLOR_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .createCompositeState(false));
    }

    private final RenderType renderType;
    private final int textureWidth;
    private final int textureHeight;
    private final float initialU0;
    private final float initialV0;
    private final float initialU1;
    private final float initialV1;
    private int color = 0xFFFFFFFF;
    private int blitOffset;

    protected BatchedBlitter(ResourceLocation texture, int textureWidth, int textureHeight) {
        this(texture, textureWidth, textureHeight, 0, 0, 1, 1);
    }

    protected BatchedBlitter(ResourceLocation texture, int textureWidth, int textureHeight,
                             float u0, float v0, float u1, float v1) {
        try {
            renderType = RENDER_TYPES.get(texture, () -> createRenderType(texture));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.initialU0 = u0;
        this.initialV0 = v0;
        this.initialU1 = u1;
        this.initialV1 = v1;
    }

    public static BatchedBlitter forTexture(ResourceLocation texture, int textureWidth, int textureHeight) {
        return new BatchedBlitter(texture, textureWidth, textureHeight);
    }

    public static BatchedBlitter forSprite(ResourceLocation sprite) {
        GuiSpriteManager spriteManager = Minecraft.getInstance().getGuiSprites();
        var atlasSprite = spriteManager.getSprite(sprite);
        int uWidth = atlasSprite.contents().width();
        int vHeight = atlasSprite.contents().height();
        int textureWidth = Math.round(uWidth / (atlasSprite.getU1() - atlasSprite.getU0()));
        int textureHeight = Math.round(vHeight / (atlasSprite.getV1() - atlasSprite.getV0()));
        return new BatchedBlitter(atlasSprite.atlasLocation(), textureWidth, textureHeight,
                atlasSprite.getU0(), atlasSprite.getV0(), atlasSprite.getU1(), atlasSprite.getV1());
    }

    public BatchedBlitter setColorRGB(int color) {
        return setColorARGB(color & (0xFF << 24));
    }

    public BatchedBlitter setColorARGB(int color) {
        this.color = color;
        return this;
    }

    public BatchedBlitter setBlitOffset(int blitOffset) {
        this.blitOffset = blitOffset;
        return this;
    }

    /**
     * Blit the texture or sprite.
     */
    public void blit(MultiBufferSource bufferSource, PoseStack pose, int x, int y, int width, int height) {
        blit(bufferSource, pose, x, y, width, height, initialU0, initialV0, initialU1, initialV1);
    }

    /**
     * Blit a portion of the texture or sprite.
     */
    public void blit(MultiBufferSource bufferSource, PoseStack pose, int x, int y, int width, int height, int uOffset, int vOffset) {
        blit(bufferSource, pose, x, y, width, height, uOffset, vOffset, width, height);
    }

    /**
     * Blit a portion of the texture or sprite.
     */
    public void blit(MultiBufferSource bufferSource, PoseStack pose, int x, int y, int width, int height, int uOffset, int vOffset, int uWidth, int vHeight) {
        float u0 = (float) uOffset / textureWidth + initialU0;
        float v0 = (float) vOffset / textureHeight + initialV0;
        float u1 = (float) uWidth / textureWidth + u0;
        float v1 = (float) vHeight / textureHeight + v0;
        blit(bufferSource, pose, x, y, width, height, u0, v0, u1, v1);
    }

    private void blit(MultiBufferSource bufferSource, PoseStack pose, int x, int y, int width, int height,
                      float u0, float v0, float u1, float v1) {
        int x1 = x + width;
        int y1 = y + height;

        VertexConsumer buffer = bufferSource.getBuffer(renderType);
        buffer.addVertex(pose.last(), x , y , blitOffset).setColor(color).setUv(u0, v0);
        buffer.addVertex(pose.last(), x , y1, blitOffset).setColor(color).setUv(u0, v1);
        buffer.addVertex(pose.last(), x1, y1, blitOffset).setColor(color).setUv(u1, v1);
        buffer.addVertex(pose.last(), x1, y , blitOffset).setColor(color).setUv(u1, v0);
    }

    public void endBatch(MultiBufferSource.BufferSource bufferSource) {
        bufferSource.endBatch(renderType);
    }
}
