package io.github.nbcss.createfactorycontroller.content.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * GUI rendering for fluids.
 */
@OnlyIn(Dist.CLIENT)
public final class FluidGuiRender {

    private FluidGuiRender() {}

    /** Slot icon for a filter stack: a fluid filter draws the fluid; anything else its normal item model. */
    public static void filterIcon(GuiGraphics gfx, ItemStack stack, int x, int y) {
        FluidStack fluid = FluidCompat.getFilterFluid(stack);
        if (fluid.isEmpty()) gfx.renderItem(stack, x, y);
        else icon(gfx, fluid, x, y, 16);
    }

    /**
     * A flat, front-facing fluid icon filling a {@code size}px square at {@code (x, y)} — a thin slab so the fluid's
     * animated still sprite faces the viewer, like CreateFluidLogistic's own slot icon.
     */
    public static void icon(GuiGraphics gfx, FluidStack fluid, int x, int y, int size) {
        if (fluid.isEmpty()) return;
        PoseStack ps = gfx.pose();
        ps.pushPose();
        ps.translate(x + size / 2f, y + size / 2f, 100);            // centre the quad on the slot
        ps.scale(size, -size, size);                                // GUI px → model units (flip Y for GUIs)

        Lighting.setupForFlatItems();                               // even front lighting (no diffuse darkening)
        MultiBufferSource.BufferSource buffer = gfx.bufferSource();
        // Thin slab (z ∈ [-1/32, 0]) so only the viewer-facing +Z face shows: a flat fluid icon.
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(
            fluid, -0.5f, -0.5f, -1 / 32f, 0.5f, 0.5f, 0f, buffer, ps, LightTexture.FULL_BRIGHT, true, false);
        buffer.endBatch();
        ps.popPose();
    }

    /**
     * The recipe-screen preview: {@code fluid} as a 3D block, oriented like the gauge block beside it. catnip's
     * renderFluidBox emits the fluid faces (animated still sprite + tint + flow); we only set up the isometric GUI
     * pose and 3D-item lighting around it.
     */
    public static void cube(GuiGraphics gfx, FluidStack fluid, int x, int y, float px) {
        if (fluid.isEmpty()) return;
        PoseStack ps = gfx.pose();
        ps.pushPose();
        ps.translate(x + px / 2f, y + px / 2f, 250);                 // centre the block on the slot
        ps.scale(px, -px, px);                                       // GUI px → model units (flip Y for GUIs)
        ps.mulPose(Axis.XP.rotationDegrees(30));                     // vanilla block-in-inventory GUI orientation
        ps.mulPose(Axis.YP.rotationDegrees(225));                    // (matches how the gauge block beside it is lit)
        ps.translate(-0.5f, -0.5f, -0.5f);                          // origin → cube centre

        Lighting.setupFor3DItems();                                  // directional shading, like a 3D block item
        MultiBufferSource.BufferSource buffer = gfx.bufferSource();
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(
            fluid, 0, 0, 0, 1, 1, 1, buffer, ps, LightTexture.FULL_BRIGHT, true, true);
        buffer.endBatch();
        ps.popPose();
        Lighting.setupForFlatItems();                                // restore the GUI's flat lighting
    }
}
