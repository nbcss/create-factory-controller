package io.github.nbcss.createfactorycontroller.content.gui.widget;

import io.github.nbcss.createfactorycontroller.content.helper.Rect2i;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.github.nbcss.createfactorycontroller.content.helper.Rect2i.Boundary.HALF_OPEN;

/**
 * A vertical scrolling viewport that presents content-space coordinates to its contents.
 * Rendering is clipped and translated by the current scroll, and mouse coordinates passed to content hooks are
 * translated by the same amount.
 */
public class VerticalScrollView extends AbstractWidget {
    private static final int SCROLL_STEP = 18;
    private static final int THUMB_MIN_HEIGHT = 12;
    private static final int SCROLLBAR_WIDTH = 3;
    private static final int SCROLLBAR_RIGHT_MARGIN = 3;
    private static final int TRACK_COLOR = 0x503D3C48;
    private static final int THUMB_COLOR = 0xFFC6C6C6;
    private static final int THUMB_HOVER_COLOR = 0xFFE2E2E2;

    private final LayoutElement content;
    private final LerpedFloat scroll = LerpedFloat.linear().startWithValue(0);
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;

    public VerticalScrollView(int x, int y, int width, int height, LayoutElement content) {
        super(x, y, width, height, Component.empty());
        this.content = Objects.requireNonNull(content);
    }

    /** Return the scroll position to the top of the content. */
    public void reset() {
        scroll.setValue(0);
        scroll.chase(0, 0.5, Chaser.EXP);
    }

    /** Advance and clamp the smooth scroll animation. */
    public void tick() {
        scroll.tickChaser();
        float clamped = Mth.clamp(scroll.getChaseTarget(), 0, (float) maxScroll());
        if (clamped != scroll.getChaseTarget()) scroll.chase(clamped, 0.5, Chaser.EXP);
        if (maxScroll() == 0) scroll.setValue(0);
        if (Math.abs(scroll.getValue() - scroll.getChaseTarget()) < 0.5F)
            scroll.setValue(scroll.getChaseTarget());
    }

    /** Get the current scroll amount. */
    public float getScroll() {
        return Mth.clamp(scroll.getValue(), 0, (float) maxScroll());
    }

    /** Get the render-interpolated scroll amount. */
    private float getInterpolatedScroll(float partialTick) {
        return Mth.clamp(scroll.getValue(partialTick), 0, (float) maxScroll());
    }

    /** Get the largest valid scroll offset. */
    public int maxScroll() {
        return Math.max(0, content.getHeight() - getHeight());
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        float currentScroll = getInterpolatedScroll(partialTick);

        Rect2i viewport = viewportBounds();
        gfx.enableScissor(viewport.minX(), viewport.minY(), viewport.maxX(), viewport.maxY());
        gfx.pose().pushPose();
        gfx.pose().translate(0, -currentScroll, 0);
        content.visitWidgets(widget -> widget.render(gfx, mouseX, contentMouseY(mouseY, currentScroll), partialTick));
        gfx.pose().popPose();
        gfx.disableScissor();

        renderScrollbar(gfx, overScrollbar(mouseX, mouseY), currentScroll);
    }

    private void renderScrollbar(GuiGraphics gfx, boolean hovered, float currentScroll) {
        if (maxScroll() <= 0) return;
        Rect2i track = scrollbarTrackBounds();
        Rect2i thumb = scrollbarThumbBounds(currentScroll);
        gfx.fill(track.minX(), track.minY(), track.maxX(), track.maxY(), TRACK_COLOR);
        gfx.fill(thumb.minX(), thumb.minY(), thumb.maxX(), thumb.maxY(), hovered ? THUMB_HOVER_COLOR : THUMB_COLOR);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) return false;
        float currentScroll = getScroll();
        if (button == 0 && overScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            Rect2i thumb = scrollbarThumbBounds(currentScroll);
            boolean onThumb = thumb.contains(mouseX, mouseY, HALF_OPEN);
            scrollbarGrabOffset = onThumb ? mouseY - thumb.y() : thumb.h() / 2.0;
            if (!onThumb) dragScrollbarTo(mouseY);
            return true;
        }
        return dispatchToWidgets(widget -> widget.mouseClicked(mouseX, contentMouseY(mouseY, currentScroll), button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!active || !visible) return false;
        if (button == 0 && draggingScrollbar) {
            dragScrollbarTo(mouseY);
            return true;
        }
        float currentScroll = getScroll();
        return isMouseOver(mouseX, mouseY)
                && dispatchToWidgets(widget -> widget.mouseDragged(mouseX, contentMouseY(mouseY, currentScroll), button, dragX, dragY));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!active || !visible) return false;
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        float currentScroll = getScroll();
        return dispatchToWidgets(widget -> widget.mouseReleased(mouseX, contentMouseY(mouseY, currentScroll), button));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) return false;
        float currentScroll = getScroll();
        if (dispatchToWidgets(widget -> widget.mouseScrolled(mouseX, contentMouseY(mouseY, currentScroll), scrollX, scrollY)))
            return true;
        if (maxScroll() <= 0) return false;
        double target = Mth.clamp(scroll.getChaseTarget() - scrollY * SCROLL_STEP, 0, maxScroll());
        scroll.chase(target, 0.5, Chaser.EXP);
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        content.visitWidgets(widget -> widget.setFocused(focused));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return dispatchToWidgets(widget -> widget.keyPressed(keyCode, scanCode, modifiers));
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return dispatchToWidgets(widget -> widget.keyReleased(keyCode, scanCode, modifiers));
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return dispatchToWidgets(widget -> widget.charTyped(codePoint, modifiers));
    }

    private int scrollbarThumbHeight() {
        int contentHeight = content.getHeight();
        if (contentHeight <= 0) return getHeight();
        return Math.max(THUMB_MIN_HEIGHT, (int) (getHeight() * (getHeight() / (double) contentHeight)));
    }

    /** Get the vertical range the scrollbar thumb can travel. */
    private int scrollbarTravel() {
        return Math.max(0, getHeight() - scrollbarThumbHeight());
    }

    private void dragScrollbarTo(double mouseY) {
        double max = maxScroll();
        Rect2i track = scrollbarTrackBounds();
        int travel = scrollbarTravel();
        if (max <= 0 || travel <= 0) return;
        double thumbTop = Mth.clamp(mouseY - scrollbarGrabOffset, track.y(), track.y() + travel);
        float value = (float) ((thumbTop - track.y()) / travel * max);
        scroll.setValue(value);
        scroll.chase(value, 0.5, Chaser.EXP);
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        return maxScroll() > 0 && scrollbarTrackBounds().contains(mouseX, mouseY, HALF_OPEN);
    }

    private Rect2i viewportBounds() {
        return Rect2i.fromXYWH(getX(), getY(), getWidth(), getHeight());
    }

    private Rect2i scrollbarTrackBounds() {
        return Rect2i.fromXYWH(
                getRight() - SCROLLBAR_RIGHT_MARGIN - SCROLLBAR_WIDTH, getY(),
                SCROLLBAR_WIDTH, getHeight());
    }

    private Rect2i scrollbarThumbBounds(float currentScroll) {
        double maxScroll = maxScroll();
        Rect2i scrollbarTrackBounds = scrollbarTrackBounds();
        int thumbY = maxScroll <= 0 ? 0 : (int) Math.round(scrollbarTravel() * (currentScroll / maxScroll));
        return Rect2i.fromXYWH(
                scrollbarTrackBounds.x(), scrollbarTrackBounds.y() + thumbY,
                SCROLLBAR_WIDTH, scrollbarThumbHeight());
    }

    private int contentMouseY(double mouseY, float currentScroll) {
        return (int) (mouseY + currentScroll);
    }

    /** Send an event to content widgets until one handles it. */
    private boolean dispatchToWidgets(Function<AbstractWidget, Boolean> event) {
        var visitor = new Consumer<AbstractWidget>() {
            boolean handled;
            @Override public void accept(AbstractWidget widget) {
                if (!handled) handled = event.apply(widget);
            }
        };
        content.visitWidgets(visitor);
        return visitor.handled;
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {}
}
