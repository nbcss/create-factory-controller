package io.github.nbcss.createfactorycontroller.content.gui.widget;

import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Create icon button whose tooltip is deliberately omitted from the button's normal render pass.
 * Screens call {@link #renderDeferredTooltip(GuiGraphics, Font, int, int)} after their slots and
 * other widgets have rendered, keeping the tooltip above neighbouring controls.
 */
public class TooltipIconButton extends IconButton {

    private Supplier<List<Component>> deferredTooltip = List::of;

    public TooltipIconButton(int x, int y, ScreenElement icon) {
        super(x, y, icon);
    }

    public TooltipIconButton(int x, int y, int width, int height, ScreenElement icon) {
        super(x, y, width, height, icon);
    }

    /** Keeps the familiar IconButton API while preventing AbstractSimiWidget from drawing it immediately. */
    @Override
    public void setToolTip(Component tooltip) {
        deferredTooltip = () -> List.of(tooltip);
    }

    /** Supplies tooltip lines at render time, for state- or modifier-dependent descriptions. */
    public TooltipIconButton withDeferredTooltip(Supplier<List<Component>> tooltip) {
        deferredTooltip = Objects.requireNonNull(tooltip);
        return this;
    }

    /** Renders this button's tooltip in the screen's final tooltip pass. */
    public boolean renderDeferredTooltip(GuiGraphics gfx, Font font, int mouseX, int mouseY) {
        if (!visible || !isMouseOver(mouseX, mouseY)) return false;
        List<Component> lines = deferredTooltip.get();
        if (lines == null || lines.isEmpty()) return false;
        gfx.renderComponentTooltip(font, lines, mouseX, mouseY);
        return true;
    }

    /** Renders at most one hovered tooltip, using argument order as the overlap precedence. */
    public static boolean renderFirstTooltip(GuiGraphics gfx, Font font, int mouseX, int mouseY,
                                             TooltipIconButton... buttons) {
        for (TooltipIconButton button : buttons)
            if (button != null && button.renderDeferredTooltip(gfx, font, mouseX, mouseY)) return true;
        return false;
    }

    /** Collection form used by button groups such as Logical Tube modes. */
    public static boolean renderFirstTooltip(GuiGraphics gfx, Font font, int mouseX, int mouseY,
                                             Iterable<TooltipIconButton> buttons) {
        for (TooltipIconButton button : buttons)
            if (button != null && button.renderDeferredTooltip(gfx, font, mouseX, mouseY)) return true;
        return false;
    }
}
