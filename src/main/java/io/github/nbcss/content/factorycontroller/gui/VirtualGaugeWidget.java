package io.github.nbcss.content.factorycontroller.gui;

import io.github.nbcss.content.factorycontroller.VirtualGaugeBehaviour;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VirtualGaugeWidget extends AbstractWidget {

    private static final int CELL = 16;

    private final VirtualGaugeBehaviour behaviour;
    private boolean gaugeHovered;
    private boolean gaugeSelected;

    public VirtualGaugeWidget(VirtualGaugeBehaviour behaviour) {
        super(0, 0, CELL, CELL, Component.empty());
        this.behaviour = behaviour;
    }

    public VirtualGaugeBehaviour getBehaviour() {
        return behaviour;
    }

    /**
     * Updates this widget's screen bounds from the current canvas transform.
     * Must be called before {@link #isMouseOver} or {@link #renderOnCanvas}.
     */
    public void applyTransform(int centerX, int centerY, double viewX, double viewY, double zoom) {
        setX((int)(centerX + (behaviour.position().x() * CELL - viewX) * zoom));
        setY((int)(centerY + (behaviour.position().y() * CELL - viewY) * zoom));
        int sw = (int) Math.ceil(CELL * zoom);
        width  = sw;
        height = sw;
    }

    /**
     * Applies the canvas transform and renders the gauge.
     * Delegates to {@link AbstractWidget#render} which calls {@link #renderWidget}.
     */
    public void renderOnCanvas(GuiGraphics gfx,
                               int centerX, int centerY,
                               double viewX, double viewY, double zoom,
                               boolean hovered, boolean selected,
                               int mouseX, int mouseY, float partialTick) {
        applyTransform(centerX, centerY, viewX, viewY, zoom);
        this.gaugeHovered  = hovered;
        this.gaugeSelected = selected;
        render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int sx = getX(), sy = getY(), sw = width;

        // Slot background
        gfx.fill(sx, sy, sx + sw, sy + sw, 0xFF111111);

        // Selection / hover outline
        if (gaugeSelected) {
            gfx.renderOutline(sx - 1, sy - 1, sw + 2, sw + 2, 0xFFFFAA00);
        } else if (gaugeHovered) {
            gfx.renderOutline(sx - 1, sy - 1, sw + 2, sw + 2, 0xFFFFFFFF);
        }

        // Gauge item icon — base size is 16; scale the pose so it fills the (zoomed) cell.
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(behaviour.getItemId()));
        if (!stack.isEmpty()) {
            float scale = sw / 16.0f;
            gfx.pose().pushPose();
            gfx.pose().translate(sx, sy, 0);
            gfx.pose().scale(scale, scale, 1);
            gfx.renderItem(stack, 0, 0);
            gfx.pose().popPose();
        }

        // Status dot (3×3, bottom-right corner)
        int dotColor = behaviour.waitingForNetwork ? 0xFFFFAA00
                     : behaviour.satisfied         ? 0xFF55FF55
                     : behaviour.promisedSatisfied ? 0xFFAAAAFF
                     : 0xFFFF5555;
        gfx.fill(sx + sw - 4, sy + sw - 4, sx + sw - 1, sy + sw - 1, dotColor);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
