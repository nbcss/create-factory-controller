package io.github.nbcss.createfactorycontroller.content.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * A minimal sprite-backed button: draws {@code normal} normally and {@code hover} while the cursor is over it,
 * running {@code onPress} on click. It does <b>not</b> draw its own tooltip — the host screen renders that in its
 * {@code render()} pass (after {@code super.render}) so the tooltip stacks above other GUI/JEI overlays rather
 * than under them. Add it via {@code addWidget} for click dispatch and render it manually where wanted.
 */
@OnlyIn(Dist.CLIENT)
public class TexturedButton extends AbstractWidget {

    private final ResourceLocation normal;
    private final ResourceLocation hover;
    private final Runnable onPress;
    @Nullable private Component tooltip;

    public TexturedButton(int x, int y, int width, int height,
                          ResourceLocation normal, ResourceLocation hover, Runnable onPress) {
        super(x, y, width, height, Component.empty());
        this.normal = normal;
        this.hover = hover;
        this.onPress = onPress;
    }

    public TexturedButton withTooltip(@Nullable Component tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Nullable
    public Component getTooltipText() {
        return tooltip;
    }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        gfx.blitSprite(isHovered ? hover : normal, getX(), getY(), getWidth(), getHeight());
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
