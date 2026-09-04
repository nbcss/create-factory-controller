package io.github.nbcss.createfactorycontroller.content.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Invisible rectangular control with a deferred vanilla tooltip and optional mouse handlers. */
public class InteractiveAreaWidget extends AbstractWidget {
    private final TooltipProvider tooltipProvider;
    @Nullable private PositionedClickHandler clickHandler;
    @Nullable private PositionedScrollHandler scrollHandler;
    @Nullable private ReleaseHandler releaseHandler;

    public InteractiveAreaWidget(int x, int y, int width, int height,
                                 Supplier<List<Component>> tooltipSupplier) {
        this(x, y, width, height, (mouseX, mouseY) -> tooltipSupplier.get());
        Objects.requireNonNull(tooltipSupplier);
    }

    public InteractiveAreaWidget(int x, int y, int width, int height,
                                 TooltipProvider tooltipProvider) {
        super(x, y, width, height, Component.empty());
        this.tooltipProvider = Objects.requireNonNull(tooltipProvider);
    }

    public InteractiveAreaWidget onClick(ClickHandler handler) {
        Objects.requireNonNull(handler);
        clickHandler = (mouseX, mouseY, button) -> handler.handle(button);
        return this;
    }

    public InteractiveAreaWidget onClick(PositionedClickHandler handler) {
        clickHandler = Objects.requireNonNull(handler);
        return this;
    }

    public InteractiveAreaWidget onScroll(ScrollHandler handler) {
        Objects.requireNonNull(handler);
        scrollHandler = (mouseX, mouseY, scrollX, scrollY) -> handler.handle(scrollX, scrollY);
        return this;
    }

    public InteractiveAreaWidget onScroll(PositionedScrollHandler handler) {
        scrollHandler = Objects.requireNonNull(handler);
        return this;
    }

    public InteractiveAreaWidget onRelease(ReleaseHandler handler) {
        releaseHandler = Objects.requireNonNull(handler);
        return this;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        List<Component> lines = isHovered() ? tooltipProvider.get(mouseX, mouseY) : List.of();
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && lines != null && !lines.isEmpty())
            screen.setTooltipForNextRenderPass(lines.stream().map(Component::getVisualOrderText).toList(),
                    DefaultTooltipPositioner.INSTANCE, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return active && visible && isMouseOver(mouseX, mouseY)
                && clickHandler != null && clickHandler.handle(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return active && visible && releaseHandler != null && releaseHandler.handle(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return active && visible && isMouseOver(mouseX, mouseY)
                && scrollHandler != null && scrollHandler.handle(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {}

    @FunctionalInterface
    public interface ClickHandler {
        boolean handle(int button);
    }

    @FunctionalInterface
    public interface PositionedClickHandler {
        boolean handle(double mouseX, double mouseY, int button);
    }

    @FunctionalInterface
    public interface ScrollHandler {
        boolean handle(double scrollX, double scrollY);
    }

    @FunctionalInterface
    public interface PositionedScrollHandler {
        boolean handle(double mouseX, double mouseY, double scrollX, double scrollY);
    }

    @FunctionalInterface
    public interface ReleaseHandler {
        boolean handle(double mouseX, double mouseY, int button);
    }

    @FunctionalInterface
    public interface TooltipProvider {
        List<Component> get(int mouseX, int mouseY);
    }
}
