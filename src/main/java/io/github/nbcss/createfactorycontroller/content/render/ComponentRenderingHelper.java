package io.github.nbcss.createfactorycontroller.content.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import io.github.nbcss.createfactorycontroller.content.gui.widget.VirtualComponentWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector2d;

public class ComponentRenderingHelper {

    public MultiBufferSource.BufferSource bufferSourceForFlatItems =
            MultiBufferSource.immediate(new ByteBufferBuilder(786432));
    public MultiBufferSource.BufferSource bufferSourceFor3DItems =
            MultiBufferSource.immediate(new ByteBufferBuilder(786432));

    /** Parameters passed to {@link VirtualComponentWidget} rendering methods. */
    public record RenderingParameters(
            GuiGraphics graphics,
            Vector2d mousePosition,
            boolean mouseOver,
            MultiBufferSource bufferSourceForFlatItems,
            MultiBufferSource bufferSourceFor3DItems
    ) implements VirtualComponentWidget.RenderingParameters {
    }

    public RenderingParameters params(GuiGraphics graphics, Vector2d mousePosition, boolean mouseOver) {
        return new RenderingParameters(graphics, mousePosition, mouseOver, bufferSourceForFlatItems, bufferSourceFor3DItems);
    }

    public void flushBuffers() {
        Lighting.setupForFlatItems();
        bufferSourceForFlatItems.endBatch();
        Lighting.setupFor3DItems();
        bufferSourceFor3DItems.endBatch();
    }

}
