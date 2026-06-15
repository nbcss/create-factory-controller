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
 * Renders a {@link ProductionPatternItem}. With no bound target (e.g. the Stock Keeper tab icon) it draws the
 * standalone {@code production_order} icon; with a target it draws the blueprint background and the bound item on
 * top. Used everywhere the stack is drawn (the Stock Keeper list, the monitoring tab, tooltips), because the item
 * model is {@code builtin/entity} and delegates here.
 */
public class ProductionPatternRenderer extends BlockEntityWithoutLevelRenderer {

    /** Blueprint frame drawn behind a bound target item. */
    private static final ResourceLocation PATTERN_BG =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "textures/item/production_pattern.png");
    /** Standalone icon shown when the pattern has no target (also the Stock Keeper tab icon). */
    private static final ResourceLocation ORDER_ICON =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "textures/item/production_order.png");

    public ProductionPatternRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
              Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        ItemStack display = ProductionPatternItem.displayOf(stack);
        boolean hasTarget = !display.isEmpty() && !(display.getItem() instanceof ProductionPatternItem);

        // Background: the unit square [0,1], centred in the slot in this post-transform space. No target → the
        // standalone production-order icon; with a target → the blueprint frame (item drawn on top below).
        // Use a TRANSLUCENT render type, not cutout: cutout does a binary alpha test (no blending), so manual
        // RenderSystem.enableBlend() has no effect — the translucent type actually alpha-blends the texture.
        ResourceLocation tex = hasTarget ? PATTERN_BG : ORDER_ICON;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(tex));
        PoseStack.Pose p = pose.last();
        Matrix4f m = p.pose();
        vertex(vc, m, p, 0f, 1f, 0f, 0f, light, overlay);
        vertex(vc, m, p, 1f, 1f, 1f, 0f, light, overlay);
        vertex(vc, m, p, 1f, 0f, 1f, 1f, light, overlay);
        vertex(vc, m, p, 0f, 0f, 0f, 1f, light, overlay);

        if (!hasTarget) return;

        // Flush the background and clear the depth buffer so the item draws cleanly on top instead of z-fighting
        // the background at the same plane (same idea as the board's flush before layering an overlay).
        if (buffers instanceof MultiBufferSource.BufferSource bs) bs.endBatch();
        RenderSystem.clear(256, Minecraft.ON_OSX);   // 256 = GL_DEPTH_BUFFER_BIT

        // Bound target item on top. renderStatic centres the item at the pose origin, which here is the slot corner,
        // so shift +0.5,+0.5 to centre it like the background.
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0f);
        RenderSystem.enableBlend();
        ItemRenderer ir = Minecraft.getInstance().getItemRenderer();
        ir.renderStatic(display, context, light, overlay, pose, buffers, Minecraft.getInstance().level, 0);
        pose.popPose();
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
