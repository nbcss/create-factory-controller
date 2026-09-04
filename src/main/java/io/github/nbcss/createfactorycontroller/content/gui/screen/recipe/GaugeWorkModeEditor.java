package io.github.nbcss.createfactorycontroller.content.gui.screen.recipe;

import io.github.nbcss.createfactorycontroller.content.GaugeWorkMode;
import io.github.nbcss.createfactorycontroller.content.component.gauge.RecipeSlot;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.gui.widget.InteractiveAreaWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

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

    private static final int GRID_X = 68;
    private static final int GRID_Y = 28;
    private static final int GRID_SIZE = 58;
    private static final int CELL_STEP = 20;
    private static final int CELL_SIZE = 16;

    /** The mode-specific values written by {@link ConfigureRecipeScreen} when its edits are committed. */
    record Configuration(int craftBatch, int craftDimension,
                         List<VirtualComponentPosition> inputPositions, List<Integer> inputAmounts,
                         List<ItemStack> craftingArrangement, List<RecipeSlot> recipeSlots) {
        Configuration {
            inputPositions = List.copyOf(inputPositions);
            inputAmounts = inputAmounts.stream().map(amount -> Math.max(1, amount)).toList();
            craftingArrangement = List.copyOf(craftingArrangement);
            recipeSlots = List.copyOf(recipeSlots);
        }
    }

    protected final ConfigureRecipeScreen s;

    protected GaugeWorkModeEditor(ConfigureRecipeScreen screen) {
        this.s = screen;
    }

    /** Top-left X of ingredient grid cell {@code i} (row-major 3×3), in screen coords. */
    protected int cellX(int i) { return s.panelX + GRID_X + (i % 3) * CELL_STEP; }
    /** Top-left Y of ingredient grid cell {@code i}. */
    protected int cellY(int i) { return s.panelY + GRID_Y + (i / 3) * CELL_STEP; }

    /** The 3×3 grid cell (0–8) under {@code (mx, my)}, or {@code -1} if none. */
    protected int slotAt(double mx, double my) {
        int x = Mth.floor(mx) - (s.panelX + GRID_X);
        int y = Mth.floor(my) - (s.panelY + GRID_Y);
        if (x < 0 || x >= GRID_SIZE || y < 0 || y >= GRID_SIZE) return -1;
        int col = x / CELL_STEP;
        int row = y / CELL_STEP;
        return x % CELL_STEP < CELL_SIZE && y % CELL_STEP < CELL_SIZE ? row * 3 + col : -1;
    }

    InteractiveAreaWidget createInputAreaWidget() {
        return new InteractiveAreaWidget(s.panelX + GRID_X, s.panelY + GRID_Y, GRID_SIZE, GRID_SIZE,
                this::inputTooltip)
                .onClick((mouseX, mouseY, button) -> {
                    if (button == 0 && Screen.hasControlDown() && s.workMode != GaugeWorkMode.CRAFTING) {
                        VirtualComponentPosition source = ingredientSourceAt(mouseX, mouseY);
                        if (source != null) {
                            if (!s.multiplierExcludedInputs.add(source)) s.multiplierExcludedInputs.remove(source);
                            s.maxRequestMultiplier = Mth.clamp(
                                    s.maxRequestMultiplier, 1, s.structuralMultiplierCap());
                            ConfigureRecipeScreen.playClickSound();
                            return true;
                        }
                    }
                    return inputAreaClicked(mouseX, mouseY, button);
                })
                .onScroll((mouseX, mouseY, scrollX, scrollY) -> inputAreaScrolled(
                        mouseX, mouseY, (int) Math.signum(scrollY),
                        Screen.hasControlDown() ? 100 : Screen.hasShiftDown() ? 10 : 1))
                .onRelease(this::gridReleased);
    }

    // ── Mode-specific behaviour ──────────────────────────────────────────────

    /** Draws the 3×3 ingredient area. */
    abstract void renderInputArea(GuiGraphics gfx, int mouseX, int mouseY);

    /** Supplies the ingredient tooltip under the cursor. May set
     *  {@link ConfigureRecipeScreen#patternHovered}. */
    abstract List<Component> inputTooltip(int mouseX, int mouseY);

    /** The produced-count number shown on the output slot. Default: the free (non-recipe-locked) count. */
    int producedCount() { return s.outputCount; }

    /** Handles a click inside the ingredient grid; {@code true} if consumed. */
    abstract boolean inputAreaClicked(double mouseX, double mouseY, int button);

    /** Source ingredient under the cursor for Ctrl-click multiplier exclusion; null when none/not supported. */
    @javax.annotation.Nullable
    VirtualComponentPosition ingredientSourceAt(double mouseX, double mouseY) { return null; }

    /** Handles a scroll inside the ingredient grid (not the output slot); {@code true} if consumed. */
    abstract boolean inputAreaScrolled(double mouseX, double mouseY, int dir, int step);

    /** Provides the packet-facing values whose representation differs between work modes. */
    abstract Configuration configuration();

    /** Builds a configuration using the current connection order shared by every work mode. */
    protected Configuration configuration(List<Integer> inputAmounts, int craftBatch, int craftDimension,
                                          List<ItemStack> craftingArrangement, List<RecipeSlot> recipeSlots) {
        return new Configuration(craftBatch, craftDimension, s.inputConnections, inputAmounts,
            craftingArrangement, recipeSlots);
    }

    /** Handles a scroll on the output slot; {@code true} if consumed. Default: freely tune the produced
     *  count (item stack/snap steps, or fluid steps for a fluid output); crafting locks this to the recipe. */
    boolean outputScrolled(int dir, int step) {
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
            if (cells[i]) gfx.fill(cellX(i), cellY(i), cellX(i) + CELL_SIZE, cellY(i) + CELL_SIZE, color);
    }
}
