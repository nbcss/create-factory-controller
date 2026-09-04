package io.github.nbcss.createfactorycontroller.content.gui.widget;

import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Create icon button whose supplied tooltip uses vanilla's deferred tooltip pass. */
public class TooltipIconButton extends IconButton {

    private Supplier<List<Component>> tooltipSupplier = List::of;
    private final InteractiveAreaWidget tooltipArea;

    public TooltipIconButton(int x, int y, ScreenElement icon) {
        super(x, y, icon);
        tooltipArea = new InteractiveAreaWidget(x, y, getWidth(), getHeight(), () -> tooltipSupplier.get());
    }

    public TooltipIconButton(int x, int y, int width, int height, ScreenElement icon) {
        super(x, y, width, height, icon);
        tooltipArea = new InteractiveAreaWidget(x, y, width, height, () -> tooltipSupplier.get());
    }

    @Override
    public void setToolTip(Component tooltip) {
        tooltipSupplier = () -> List.of(tooltip);
    }

    public TooltipIconButton withTooltip(Supplier<List<Component>> tooltip) {
        tooltipSupplier = Objects.requireNonNull(tooltip);
        return this;
    }

    @Override
    protected void renderTooltip(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        tooltipArea.setRectangle(getWidth(), getHeight(), getX(), getY());
        tooltipArea.render(gfx, mouseX, mouseY, partialTicks);
    }
}
