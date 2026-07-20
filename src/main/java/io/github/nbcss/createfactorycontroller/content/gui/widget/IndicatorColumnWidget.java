package io.github.nbcss.createfactorycontroller.content.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nbcss.createfactorycontroller.ClientConfig;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.gui.widget.indicator.ControllerIndicator;
import io.github.nbcss.createfactorycontroller.content.gui.widget.indicator.LinkMissingIndicator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** Invisible layout container for the controller's vertically stacked 21x13 indicator tabs. */
public class IndicatorColumnWidget extends AbstractWidget {
    private static final int INDICATOR_GAP = 2;

    private final FactoryControllerMenu menu;
    private List<ControllerIndicator> indicators = List.of();

    public IndicatorColumnWidget(int x, int y, FactoryControllerMenu menu) {
        super(x, y, ControllerIndicator.WIDTH, 0, Component.empty());
        this.menu = menu;
        refresh();
    }

    public void setPosition(int x, int y) {
        setX(x);
        setY(y);
    }

    /** Rebuilds the visible indicator tabs from the latest synchronized menu state. */
    public void refresh() {
        List<ControllerIndicator> next = new ArrayList<>();
        if (!ClientConfig.hideMissingLinkWarning())
            menu.missingLinkStatuses.stream()
                    .filter(status -> menu.knownNetworks.contains(status.network()))
                    .forEach(status -> next.add(new LinkMissingIndicator(menu, status)));
        indicators = List.copyOf(next);
        height = indicators.size() * ControllerIndicator.HEIGHT
                + Math.max(0, indicators.size() - 1) * INDICATOR_GAP;
        visible = !indicators.isEmpty();
    }

    public List<Component> getTooltipLines(double mouseX, double mouseY) {
        ControllerIndicator indicator = indicatorAt(mouseX, mouseY);
        return indicator == null ? List.of() : indicator.tooltip();
    }

    private ControllerIndicator indicatorAt(double mouseX, double mouseY) {
        if (!visible || mouseX < getX() || mouseX >= getX() + ControllerIndicator.WIDTH
                || mouseY < getY() || mouseY >= getY() + height) return null;
        int relativeY = (int) (mouseY - getY());
        int stride = ControllerIndicator.HEIGHT + INDICATOR_GAP;
        int index = relativeY / stride;
        if (relativeY % stride >= ControllerIndicator.HEIGHT) return null;
        return index >= 0 && index < indicators.size() ? indicators.get(index) : null;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return indicatorAt(mouseX, mouseY) != null;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        for (int i = 0; i < indicators.size(); i++)
            gfx.blitSprite(indicators.get(i).icon(), getX(),
                    getY() + i * (ControllerIndicator.HEIGHT + INDICATOR_GAP),
                    ControllerIndicator.WIDTH, ControllerIndicator.HEIGHT);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {}
}
