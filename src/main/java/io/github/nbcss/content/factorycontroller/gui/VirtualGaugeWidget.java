package io.github.nbcss.content.factorycontroller.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nbcss.content.factorycontroller.VirtualGaugeBehaviour;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renders a single gauge on the canvas. All drawing is in canvas-world coordinates — the screen
 * pushes a world→screen pose around the whole canvas, so a cell is just {@code CELL} world px at
 * {@code (position * CELL)} and this class never deals with zoom/pan directly.
 */
@OnlyIn(Dist.CLIENT)
public class VirtualGaugeWidget extends AbstractWidget {

    private static final int CELL = 16;

    /** Status indicator light, drawn top-right within the cell and tinted by gauge state. */
    private static final ResourceLocation INDICATOR =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/gauge_indicator");

    private final VirtualGaugeBehaviour behaviour;

    public VirtualGaugeWidget(VirtualGaugeBehaviour behaviour) {
        super(0, 0, CELL, CELL, Component.empty());
        this.behaviour = behaviour;
    }

    public VirtualGaugeBehaviour getBehaviour() {
        return behaviour;
    }

    /**
     * Back layer: the gauge's {@code back} sprite. Rendered before connections so the arrows
     * (whose heads tuck into the cell) sit above the back but below the front.
     */
    public void renderBack(GuiGraphics gfx) {
        int x0 = behaviour.position().x() * CELL;
        int y0 = behaviour.position().y() * CELL;
        gfx.blitSprite(behaviour.getTexture().withSuffix("/back"), x0, y0, CELL, CELL);
    }

    /**
     * Front layer: the gauge's {@code front} sprite plus the status dot and hover/selection
     * outline. Rendered after connections so the front frame covers the arrowheads.
     */
    public void renderFront(GuiGraphics gfx, boolean hovered, boolean selected) {
        int x0 = behaviour.position().x() * CELL;
        int y0 = behaviour.position().y() * CELL;

        gfx.blitSprite(behaviour.getTexture().withSuffix("/front"), x0, y0, CELL, CELL);

        // Indicator light — only shown once a target count is set (matches Create: an unconfigured
        // gauge has no bulb). Tinted by the gauge's status color.
        if (behaviour.count != 0) {
            RenderSystem.enableBlend();
            int color = behaviour.getIngredientStatusColor();
            gfx.setColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, 0.9f);
            gfx.blitSprite(INDICATOR, x0, y0, CELL, CELL);
            gfx.setColor(1f, 1f, 1f, 1f);
        }

        // Selection / hover outline, on top so the highlight is always visible.
        if (selected) {
            gfx.renderOutline(x0 - 1, y0 - 1, CELL + 2, CELL + 2, 0xFFFFAA00);
        } else if (hovered) {
            gfx.renderOutline(x0 - 1, y0 - 1, CELL + 2, CELL + 2, 0xFFFFFFFF);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {}

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
