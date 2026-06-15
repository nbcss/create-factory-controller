package io.github.nbcss.content.factorycontroller.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.item.ProductionPatternItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Renders a {@link ProductionPatternItem}: a flat blueprint background with the
 * pattern item drawn on top. Used everywhere the stack is drawn (the Stock Keeper list, the monitoring tab,
 * tooltips), because the item model is {@code builtin/entity} and delegates here.
 */
public class ProductionPatternRenderer extends BlockEntityWithoutLevelRenderer {

    private static final ResourceLocation BACKGROUND =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "textures/item/production_pattern.png");

    public ProductionPatternRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
              Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        // Background: the unit square [0,1], centred in the slot in this post-transform space.
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(BACKGROUND));
        PoseStack.Pose p = pose.last();
        Matrix4f m = p.pose();
        vertex(vc, m, p, 0f, 1f, 0f, 1f, light, overlay);
        vertex(vc, m, p, 1f, 1f, 1f, 1f, light, overlay);
        vertex(vc, m, p, 1f, 0f, 1f, 0f, light, overlay);
        vertex(vc, m, p, 0f, 0f, 0f, 0f, light, overlay);

        // Flush the background and clear the depth buffer so the item draws cleanly on top instead of z-fighting
        // the background at the same plane (same idea as the board's flush before layering an overlay).
        if (buffers instanceof MultiBufferSource.BufferSource bs) bs.endBatch();
        RenderSystem.clear(256, Minecraft.ON_OSX);   // 256 = GL_DEPTH_BUFFER_BIT

        // Promised item on top. renderStatic centres the item at the pose origin, which here is the slot corner,
        // so shift +0.5,+0.5 to centre it like the background.
        ItemStack display = ProductionPatternItem.displayOf(stack);
        if (!display.isEmpty() && !(display.getItem() instanceof ProductionPatternItem)) {
            pose.pushPose();
            pose.translate(0.5f, 0.5f, 0f);
            ItemRenderer ir = Minecraft.getInstance().getItemRenderer();
            ir.renderStatic(display, context, light, overlay, pose, buffers, Minecraft.getInstance().level, 0);
            pose.popPose();
        }
    }

    private void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose,
                        float x, float y, float u, float v, int light, int overlay) {
        vc.addVertex(m, x, y, 0f)
          .setColor(0xFFFFFFFF)
          .setUv(u, v)
          .setOverlay(overlay)
          .setLight(light)
          .setNormal(pose, 0f, 0f, 1f);
    }
}
