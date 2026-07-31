package io.github.nbcss.createfactorycontroller.content.render;

import io.github.nbcss.createfactorycontroller.content.gui.widget.VirtualComponentWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2d;

public class ComponentRenderingHelper {

    /** Parameters passed to {@link VirtualComponentWidget} rendering methods. */
    public record RenderingParameters(
            GuiGraphics graphics,
            Vector2d mousePosition,
            boolean mouseOver,
            ResourceIconRenderer.Batch batchRenderedItems

    ) implements VirtualComponentWidget.RenderingParameters {

        @Override
        public void addBatchRenderedItem(ItemStack stack, int x, int y) {
            batchRenderedItems.add(graphics.pose(), stack, x, y);
        }

    }

    public RenderingParameters params(GuiGraphics graphics, Vector2d mousePosition, boolean mouseOver) {
        return new RenderingParameters(graphics, mousePosition, mouseOver, ResourceIconRenderer.BATCH);
    }

    public void flushBuffers(GuiGraphics graphics) {
        graphics.flush();
        ResourceIconRenderer.BATCH.flush(graphics);
    }
}
