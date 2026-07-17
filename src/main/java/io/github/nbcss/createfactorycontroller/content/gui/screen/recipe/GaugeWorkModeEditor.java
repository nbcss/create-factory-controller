package io.github.nbcss.createfactorycontroller.content.gui.screen.recipe;

import io.github.nbcss.createfactorycontroller.content.GaugeWorkMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Strategy for the ingredient-grid + output behaviour of a {@link ConfigureRecipeScreen}, one per
 * {@link io.github.nbcss.createfactorycontroller.content.GaugeWorkMode work mode}. The screen owns the
 * shared chrome (threshold row, address box, promise boxes, buttons, connection state) and delegates the
 * parts that differ by mode to the active editor, so a new mode is a new subclass rather than another
 * {@code if (craftingActive)} branch in every method.
 *
 * <p>Editors are behaviour-only: the mutable state (connections, crafting arrangement, output count) lives
 * on the {@link ConfigureRecipeScreen} and is reached through {@link #s}.</p>
 */
abstract class GaugeWorkModeEditor {

    protected final ConfigureRecipeScreen s;

    protected GaugeWorkModeEditor(ConfigureRecipeScreen screen) {
        this.s = screen;
    }

    /** Top-left X of ingredient grid cell {@code i} (row-major 3×3), in screen coords. */
    protected int cellX(int i) { return s.panelX + 68 + (i % 3) * 20; }
    /** Top-left Y of ingredient grid cell {@code i}. */
    protected int cellY(int i) { return s.panelY + 28 + (i / 3) * 20; }

    /** Point-in-rect test (shared by every editor's hit-testing). */
    protected static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** The 3×3 grid cell (0–8) under {@code (mx, my)}, or {@code -1} if none. */
    protected int slotAt(double mx, double my) {
        for (int i = 0; i < ConfigureRecipeScreen.MAX_INPUT_SLOTS; i++)
            if (in(mx, my, cellX(i), cellY(i), 16, 16)) return i;
        return -1;
    }

    // ── Mode-specific behaviour ──────────────────────────────────────────────

    /** Draws the 3×3 ingredient area; returns the hover tooltip (or {@code null}). May set
     *  {@link ConfigureRecipeScreen#patternHovered}. */
    abstract List<Component> renderInputArea(GuiGraphics gfx, int mouseX, int mouseY);

    /** The produced-count number shown on the output slot. Default: the free (non-recipe-locked) count. */
    int producedCount() { return s.outputCount; }

    /** Handles a click inside the ingredient grid; {@code true} if consumed. */
    abstract boolean inputAreaClicked(double mouseX, double mouseY, int button);

    /** Handles a scroll inside the ingredient grid (not the output slot); {@code true} if consumed. */
    abstract boolean inputAreaScrolled(double mouseX, double mouseY, int dir, int step);

    /** Handles a scroll on the output slot; {@code true} if consumed. Default: freely tune the produced
     *  count (item stack/snap steps, or fluid steps for a fluid output); crafting locks this to the recipe. */
    boolean outputScrolled(double mouseX, double mouseY, int dir, int step) {
        if (!in(mouseX, mouseY, s.panelX + 160, s.panelY + 48, 16, 16)) return false;
        if (s.fluidMode) {
            s.outputCount = ConfigureRecipeScreen.adjustFluidAmount(s.outputCount, dir,
                    Screen.hasShiftDown(), Screen.hasControlDown(), 1, ConfigureRecipeScreen.FLUID_OUTPUT_CAP_MB);
        } else if (Screen.hasControlDown()) {
            s.outputCount = Mth.clamp(ConfigureRecipeScreen.snapToStack(s.outputCount, dir, s.outputStackSize()),
                    1, s.maxItemOutput());
        } else {
            int next = Screen.hasShiftDown()
                    ? ConfigureRecipeScreen.shiftStep(s.outputCount, dir, s.outputStackSize()) : s.outputCount + dir;
            s.outputCount = Mth.clamp(next, 1, s.maxItemOutput());
        }
        ConfigureRecipeScreen.playScrollSound();
        return true;
    }

    /** Seeds this editor's representation when the screen switches INTO its mode from {@code previous} */
    void onChange(GaugeWorkMode previous) {}

    /** Bakes the produced output to its batch-multiplied value ({@code outputCount × batch}) */
    protected void bakeCraftingOutput() {
        s.outputCount = Mth.clamp(s.outputCount * s.effectiveBatch(), 1,
                s.fluidMode ? ConfigureRecipeScreen.FLUID_OUTPUT_CAP_MB : s.maxItemOutput());
    }

    /** Handles a mouse release (for drag gestures); {@code true} if consumed. Default: no drag. */
    boolean gridReleased(double mouseX, double mouseY, int button) { return false; }

    /** Drawn on top of everything (after the grid + labels) for drag previews. Default: nothing. */
    void renderOverlay(GuiGraphics gfx, int mouseX, int mouseY) {}

    /** Grid cells (row-major, 0–8) currently showing an ingredient — for the request-multiplier hover highlight.
     *  Default: none. */
    boolean[] occupiedCells() { return new boolean[ConfigureRecipeScreen.MAX_INPUT_SLOTS]; }

    /** Fills each {@link #occupiedCells() occupied} ingredient cell with {@code color}; the caller sets the z-layer. */
    void fillOccupiedCells(GuiGraphics gfx, int color) {
        boolean[] cells = occupiedCells();
        for (int i = 0; i < cells.length; i++)
            if (cells[i]) gfx.fill(cellX(i), cellY(i), cellX(i) + 16, cellY(i) + 16, color);
    }
}
