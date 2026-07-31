package io.github.nbcss.createfactorycontroller.content.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;

/**
 * Wrapper for rendering an item or fluid icon.
 */
public final class ResourceIconRenderer {

    static final Batch BATCH = new Batch();

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

    /** Collects resource icons with their GUI poses and replays them using the vanilla buffer source. */
    public static final class Batch {

        private record BatchRenderedItem(ItemStack stack, PoseStack.Pose pose, FluidStack fluid, BakedModel model) {}

        // Classify once while queueing so each lighting pass only visits icons it can render.
        private final ArrayList<BatchRenderedItem> flatItems = new ArrayList<>();
        private final ArrayList<BatchRenderedItem> blockLitItems = new ArrayList<>();

        /** Captures the current pose with the icon centred at {@code (x, y)}. */
        public void add(PoseStack pose, ItemStack stack, int x, int y) {
            if (stack.isEmpty()) return;
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.getProfiler().push("ResourceIconRenderer");

            PoseStack.Pose itemPose = pose.last().copy();
            itemPose.pose().translate(x + 8, y + 8, 0);

            FluidStack fluid = FluidCompat.getFilterFluid(stack);
            BakedModel model = fluid.isEmpty()
                    ? minecraft.getItemRenderer().getModel(stack, minecraft.level, minecraft.player, 0) : null;
            (model != null && model.usesBlockLight() ? blockLitItems : flatItems)
                    .add(new BatchRenderedItem(stack, itemPose, fluid, model));

            minecraft.getProfiler().pop();
        }

        /** Replays flat and block-lit icons as separate lighting passes. */
        public void flush(GuiGraphics graphics) {
            Minecraft.getInstance().getProfiler().push("ResourceIconRenderer");

            Lighting.setupForFlatItems();
            render(graphics, flatItems);
            graphics.flush();
            Lighting.setupFor3DItems();
            render(graphics, blockLitItems);
            graphics.flush();
            flatItems.clear();
            blockLitItems.clear();

            Minecraft.getInstance().getProfiler().pop();
        }

        private void render(GuiGraphics graphics, ArrayList<BatchRenderedItem> items) {
            PoseStack pose = new PoseStack();

            for (BatchRenderedItem item : items) {
                pose.last().pose().set(item.pose().pose());
                pose.last().normal().set(item.pose().normal());
                ItemStack stack = item.stack();

                try {
                    if (item.model() != null) {
                        pose.translate(0, 0, 150);
                        pose.scale(16, -16, 16);
                        Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.GUI, false, pose, graphics.bufferSource(),
                                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, item.model());
                    } else {
                        pose.translate(0, 0, 100);
                        pose.scale(16, -16, 16);
                        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(
                                item.fluid(), -0.5f, -0.5f, -1 / 32f, 0.5f, 0.5f, 0, graphics.bufferSource(), pose,
                                LightTexture.FULL_BRIGHT, true, false);
                    }

                } catch (Throwable throwable) {
                    CrashReport crashreport = CrashReport.forThrowable(throwable, "Rendering item");
                    CrashReportCategory crashreportcategory = crashreport.addCategory("Item being rendered");
                    crashreportcategory.setDetail("Item Type", () -> String.valueOf(stack.getItem()));
                    crashreportcategory.setDetail("Item Components", () -> String.valueOf(stack.getComponents()));
                    crashreportcategory.setDetail("Item Foil", () -> String.valueOf(stack.hasFoil()));
                    throw new ReportedException(crashreport);
                }
            }
        }
    }
}
