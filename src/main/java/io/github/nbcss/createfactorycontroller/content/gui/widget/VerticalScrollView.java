package io.github.nbcss.createfactorycontroller.content.gui.widget;

import io.github.nbcss.createfactorycontroller.content.helper.Rect2i;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2i;

import java.util.List;
import java.util.Objects;

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

    private final Content content;
    private final LerpedFloat scroll = LerpedFloat.linear().startWithValue(0);
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;

    public VerticalScrollView(int x, int y, int width, int height, Content content) {
        super(x, y, width, height, Component.empty());
        this.content = Objects.requireNonNull(content);
    }

    public void reset() {
        scroll.setValue(0);
        scroll.chase(0, 0.5, Chaser.EXP);
    }

    public void tick() {
        scroll.tickChaser();
        float clamped = Mth.clamp(scroll.getChaseTarget(), 0, (float) maxScroll());
        if (clamped != scroll.getChaseTarget()) scroll.chase(clamped, 0.5, Chaser.EXP);
        if (maxScroll() == 0) scroll.setValue(0);
        if (Math.abs(scroll.getValue() - scroll.getChaseTarget()) < 0.5F)
            scroll.setValue(scroll.getChaseTarget());
    }

    public float getScroll() {
        return currentScroll();
    }

    public double maxScroll() {
        return Math.max(0, content.getHeight() - getHeight());
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        float currentScroll = currentScroll(partialTick);

        Rect2i viewport = viewportBounds();
        gfx.enableScissor(viewport.minX(), viewport.minY(), viewport.maxX(), viewport.maxY());
        gfx.pose().pushPose();
        gfx.pose().translate(0, -currentScroll, 0);
        content.render(gfx, mouseX, contentMouseY(mouseY, currentScroll), partialTick);
        gfx.pose().popPose();
        gfx.disableScissor();

        renderScrollbar(gfx, overScrollbar(mouseX, mouseY), currentScroll);
        renderTooltip(mouseX, mouseY, currentScroll);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) return false;
        float currentScroll = currentScroll();
        if (button == 0 && overScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            Rect2i thumb = scrollbarThumbBounds(currentScroll);
            boolean onThumb = thumb.contains(mouseX, mouseY, HALF_OPEN);
            scrollbarGrabOffset = onThumb ? mouseY - thumb.y() : thumb.h() / 2.0;
            if (!onThumb) dragScrollbarTo(mouseY);
            return true;
        }
        return content.mouseClicked(mouseX, contentMouseY(mouseY, currentScroll), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!active || !visible) return false;
        if (button == 0 && draggingScrollbar) {
            dragScrollbarTo(mouseY);
            return true;
        }
        float currentScroll = currentScroll();
        return isMouseOver(mouseX, mouseY)
                && content.mouseDragged(mouseX, contentMouseY(mouseY, currentScroll), button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!active || !visible) return false;
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        float currentScroll = currentScroll();
        return content.mouseReleased(mouseX, contentMouseY(mouseY, currentScroll), button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!active || !visible || !isMouseOver(mouseX, mouseY)) return false;
        float currentScroll = currentScroll();
        if (content.mouseScrolled(mouseX, contentMouseY(mouseY, currentScroll), scrollX, scrollY)) return true;
        if (maxScroll() <= 0) return false;
        double target = Mth.clamp(scroll.getChaseTarget() - scrollY * SCROLL_STEP, 0, maxScroll());
        scroll.chase(target, 0.5, Chaser.EXP);
        return true;
    }

    private void renderTooltip(int mouseX, int mouseY, float currentScroll) {
        if (!isHovered() || overScrollbar(mouseX, mouseY)) return;
        List<FormattedCharSequence> lines = content.tooltip(mouseX, contentMouseY(mouseY, currentScroll));
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null && lines != null && !lines.isEmpty())
            screen.setTooltipForNextRenderPass(lines, DefaultTooltipPositioner.INSTANCE, false);
    }

    private void renderScrollbar(GuiGraphics gfx, boolean hovered, float currentScroll) {
        if (maxScroll() <= 0) return;
        Rect2i track = scrollbarTrackBounds();
        Rect2i thumb = scrollbarThumbBounds(currentScroll);
        gfx.fill(track.minX(), track.minY(), track.maxX(), track.maxY(), TRACK_COLOR);
        gfx.fill(thumb.minX(), thumb.minY(), thumb.maxX(), thumb.maxY(), hovered ? THUMB_HOVER_COLOR : THUMB_COLOR);
    }

    private int scrollbarThumbHeight() {
        int contentHeight = content.getHeight();
        if (contentHeight <= 0) return getHeight();
        return Math.max(THUMB_MIN_HEIGHT, (int) (getHeight() * (getHeight() / (double) contentHeight)));
    }

    private int scrollbarTravel() {
        return Math.max(0, getHeight() - scrollbarThumbHeight());
    }

    private int scrollbarThumbY(float currentScroll) {
        double max = maxScroll();
        return max <= 0 ? getY() : getY() + (int) Math.round(scrollbarTravel() * (currentScroll / max));
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
                new Vector2i(getRight() - SCROLLBAR_RIGHT_MARGIN - SCROLLBAR_WIDTH, getY()),
                new Vector2i(SCROLLBAR_WIDTH, getHeight()));
    }

    private Rect2i scrollbarThumbBounds(float currentScroll) {
        return Rect2i.fromXYWH(
                scrollbarTrackBounds().x(), scrollbarThumbY(currentScroll),
                SCROLLBAR_WIDTH, scrollbarThumbHeight());
    }

    private float currentScroll() {
        return Mth.clamp(scroll.getValue(), 0, (float) maxScroll());
    }

    private float currentScroll(float partialTick) {
        return Mth.clamp(scroll.getValue(partialTick), 0, (float) maxScroll());
    }

    private int contentMouseY(double mouseY, float currentScroll) {
        return (int) (mouseY + currentScroll);
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        content.setFocused(focused);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return content.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return content.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return content.charTyped(codePoint, modifiers);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {}

    public interface Content extends Renderable, GuiEventListener {

        int getHeight();

        default List<FormattedCharSequence> tooltip(int mouseX, int mouseY) {
            return List.of();
        }

        @Override
        default void setFocused(boolean focused) {}

        @Override
        default boolean isFocused() {
            return false;
        }
    }
}
