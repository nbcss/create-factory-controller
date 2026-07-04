package io.github.nbcss.createfactorycontroller.content.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A button with custom graphics: Each graphic layer a sprite or a solid color, drawn on normal state or hover.
 * Each layer on top of previous layers.
 */
@OnlyIn(Dist.CLIENT)
public class GraphicButton extends AbstractWidget {

    public static final int DISPLAY_NORMAL = 0b01;
    public static final int DISPLAY_HOVER = 0b10;
    public static final int DISPLAY_BOTH = 0b11;

    private record GraphicLayer(
            int displayedState,
            @Nullable ResourceLocation resource, int color,
            int x, int y, int w, int h
    ) {}

    private final ArrayList<GraphicLayer> graphicLayers = new ArrayList<>();
    private final Supplier<Boolean> onClick;
    @Nullable private List<FormattedCharSequence> tooltip;

    public GraphicButton(int x, int y, int width, int height, Supplier<Boolean> onClick) {
        super(x, y, width, height, Component.empty());
        this.onClick = onClick;
    }

    public GraphicButton withTooltip(@Nullable Component tooltip) {
        this.tooltip = tooltip == null ? null : List.of(tooltip.getVisualOrderText());
        return this;
    }

    public GraphicButton withTooltip(@Nullable List<Component> tooltip) {
        this.tooltip = tooltip == null ? null : tooltip.stream().map(Component::getVisualOrderText).toList();
        return this;
    }

    @Nullable
    public List<FormattedCharSequence> getTooltipText() {
        return tooltip;
    }

    public GraphicButton addGraphic(int displayedState, ResourceLocation resource, int color, int x, int y, int w, int h) {
        graphicLayers.add(new GraphicLayer(displayedState, resource, wrapOpaqueColor(color), x, y, w, h));
        return this;
    }
    public GraphicButton addGraphic(int displayedState, ResourceLocation resource, int x, int y, int w, int h) {
        graphicLayers.add(new GraphicLayer(displayedState, resource, 0xFFFFFFFF, x, y, w, h));
        return this;
    }
    public GraphicButton addGraphic(int displayedState, int color, int x, int y, int w, int h) {
        graphicLayers.add(new GraphicLayer(displayedState, null, wrapOpaqueColor(color), x, y, w, h));
        return this;
    }
    public GraphicButton addGraphic(int displayedState, ResourceLocation resource, int color) {
        return addGraphic(displayedState, resource, color, 0, 0, this.width, this.height);
    }
    public GraphicButton addGraphic(int displayedState, ResourceLocation resource) {
        return addGraphic(displayedState, resource, 0xFFFFFFFF, 0, 0, this.width, this.height);
    }
    public GraphicButton addGraphic(int displayedState, int color) {
        return addGraphic(displayedState, null, color, 0, 0, this.width, this.height);
    }

    private int wrapOpaqueColor(int color) {
        return color < (1 << 24) ? (0xFF << 24) | color : color;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        final int currentState = isHovered() ? DISPLAY_HOVER : DISPLAY_NORMAL;
        for (var layer : graphicLayers) {
            if ((layer.displayedState & currentState) == 0) continue;
            if (layer.resource != null) {
                if (layer.color != 0xFFFFFFFF)
                    gfx.setColor(
                            ((layer.color >> 16) & 0xFF) / 255f,
                            ((layer.color >> 8) & 0xFF) / 255f,
                            (layer.color & 0xFF) / 255f,
                            ((layer.color >> 24) & 0xFF) / 255f);
                gfx.blitSprite(layer.resource, this.getX() + layer.x, this.getY() + layer.y, layer.w, layer.h);
                gfx.setColor(1f, 1f, 1f, 1f);
            } else {
                gfx.fill(
                        this.getX() + layer.x, this.getY() + layer.y,
                        this.getX() + layer.x + layer.w, this.getY() + layer.y + layer.h,
                        layer.color);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isValidClickButton(button) || !clicked(mouseX, mouseY)) return false;
        boolean handled = onClick.get();
        if (handled) playDownSound(Minecraft.getInstance().getSoundManager());
        return handled;
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {}
}
