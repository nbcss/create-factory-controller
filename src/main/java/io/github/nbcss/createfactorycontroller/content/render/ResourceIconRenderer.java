package io.github.nbcss.createfactorycontroller.content.render;

import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Wrapper for rendering an item or fluid icon.
 */
public final class ResourceIconRenderer {

    private ResourceIconRenderer() {}

    public static void render(GuiGraphics gfx, ItemStack stack, int x, int y) {
        Minecraft.getInstance().getProfiler().push("ResourceIconRenderer");

        FluidStack fluid = FluidCompat.getFilterFluid(stack);
        if (fluid.isEmpty())
            gfx.renderItem(stack, x, y);
        else
            FluidGuiRender.icon(gfx, fluid, x, y, 16);

        Minecraft.getInstance().getProfiler().pop();
    }
}
