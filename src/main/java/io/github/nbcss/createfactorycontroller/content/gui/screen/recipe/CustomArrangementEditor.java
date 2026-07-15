package io.github.nbcss.createfactorycontroller.content.gui.screen.recipe;

import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.GaugeWorkMode;
import io.github.nbcss.createfactorycontroller.content.ThresholdUnit;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.RecipeSlot;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.render.FluidGuiRender;
import io.github.nbcss.createfactorycontroller.content.render.SpriteNumbersRender;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * CUSTOM work mode: the player hand-places ingredients into an explicit 9-slot grid ({@link
 * ConfigureRecipeScreen#customSlots}). Left-drag moves/swaps a slot (empty gaps allowed), right-drag copies
 * a slot into an empty one (replicate), scroll tunes a single slot's count (floored at 1), and shift-click
 * clears a slot (disconnecting the ingredient if it was its last slot). Output stays the free produced count
 * (the base-class default).
 */
class CustomArrangementEditor extends GaugeWorkModeEditor {

    /** The slot a drag started from (always non-empty), and which button; -1 when not dragging. */
    private int dragFrom = -1;
    private int dragButton = -1;

    CustomArrangementEditor(ConfigureRecipeScreen screen) { super(screen); }

    /** Abandons an in-flight drag — called when the screen re-enters CUSTOM mode, so a drag begun before a
     *  mid-drag mode toggle can't resurface as a phantom pickup. */
    void resetDrag() {
        dragFrom = -1;
        dragButton = -1;
    }

    // ── Mode entry ──────────────────────────────────────────────────────────────

    @Override
    void onChange(GaugeWorkMode previous) {
        resetDrag();   // a drag begun in a previous CUSTOM stint must not survive re-entry
        s.customSlots = new java.util.ArrayList<>(
                java.util.Collections.nCopies(ConfigureRecipeScreen.MAX_INPUT_SLOTS, RecipeSlot.EMPTY));
        if (previous == GaugeWorkMode.CRAFTING) seedFromCrafting();
        else seedFromRegular();
    }

    /** Seeds the 9-slot layout from the current REGULAR-derived grid (full stacks first, then a partial). */
    private void seedFromRegular() {
        List<ConfigureRecipeScreen.InputSlot> slots = s.layoutInputSlots();
        for (int i = 0; i < slots.size() && i < ConfigureRecipeScreen.MAX_INPUT_SLOTS; i++)
            s.customSlots.set(i, new RecipeSlot(s.inputConnections.get(slots.get(i).connectionIndex()), slots.get(i).amount()));
    }

    /** Mirrors the crafting grid currently shown, preserving each cell's position and its batch-multiplied amount
     *  (best-effort: cell counts are capped to one stack). Called while the crafting arrangement/batch are still live. */
    private void seedFromCrafting() {
        bakeCraftingOutput();
        int batch = s.effectiveBatch();
        if (s.craftingIsLarge()) {   // aggregated view
            List<ConfigureRecipeScreen.CraftSlot> slots = s.craftingDisplaySlots();
            for (int i = 0; i < slots.size() && i < ConfigureRecipeScreen.MAX_INPUT_SLOTS; i++)
                placeCraftingCell(i, slots.get(i).stack(), slots.get(i).amount());
        } else {
            int dim = s.effectiveCraftDimension();
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                int idx = row * dim + col;
                if (col < dim && row < dim && idx < s.craftingIngredients.size()) {
                    ItemStack stack = s.craftingIngredients.get(idx).stack;
                    if (!stack.isEmpty())
                        placeCraftingCell(row * 3 + col, stack, Math.max(1, stack.getCount()) * batch);
                }
            }
        }
    }

    /** Places one crafting cell into custom slot {@code cell}: resolves the item to its source wire and clamps the
     *  amount to the cell's capacity (one stack, or the fluid mB cap). Skips items with no matching wire. */
    private void placeCraftingCell(int cell, ItemStack item, int amount) {
        VirtualComponentPosition src = sourceForItem(item);
        if (src == null) return;
        int cap = FluidCompat.isFluidFilter(s.ingredientOf(src)) ? ConfigureRecipeScreen.FLUID_INGREDIENT_CAP_MB
                : ConfigureRecipeScreen.stackSizeOf(s.ingredientOf(src));
        s.customSlots.set(cell, new RecipeSlot(src, Math.clamp(amount, 1, cap)));
    }

    /** The input connection whose ingredient matches {@code item} (exact components), or null. */
    private VirtualComponentPosition sourceForItem(ItemStack item) {
        for (VirtualComponentPosition pos : s.inputConnections)
            if (ItemStack.isSameItemSameComponents(s.ingredientOf(pos), item)) return pos;
        return null;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    List<Component> renderInputArea(GuiGraphics gfx, int mouseX, int mouseY) {
        s.patternHovered = false;
        int hover = slotAt(mouseX, mouseY);
        int scale = s.previewScale(mouseX, mouseY);
        List<Component> tooltip = null;
        for (int i = 0; i < ConfigureRecipeScreen.MAX_INPUT_SLOTS; i++) {
            RecipeSlot draw = slotDisplay(i, hover);
            renderSlotCell(gfx, cellX(i), cellY(i), draw, scale);
            if (dragFrom < 0 && !draw.isEmpty() && in(mouseX, mouseY, cellX(i), cellY(i), 16, 16)) {
                ItemStack stack = s.ingredientOf(draw.source());
                String amount = FluidCompat.isFluidFilter(stack)
                    ? ThresholdUnit.formatFluidAmount(draw.count()) : String.valueOf(draw.count());
                boolean srcIgnore = s.getMenu().componentAt(draw.source())
                        instanceof VirtualGaugeBehaviour src && src.ignoreData;
                tooltip = ConfigureRecipeScreen.withIgnoreDataLine(List.of(
                    CreateLang.translate("gui.factory_panel.sending_item",
                        FluidCompat.filterName(stack).getString() + " x" + amount)
                        .color(ScrollInput.HEADER_RGB).component(),
                    Component.translatable("createfactorycontroller.gui.custom_slot_move")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    Component.translatable("createfactorycontroller.gui.custom_slot_copy")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                    CreateLang.translate("gui.factory_panel.scroll_to_change_amount")
                        .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component(),
                    Component.translatable("createfactorycontroller.gui.action_remove_component")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)), srcIgnore);
            }
        }

        if (s.customSlots.stream().allMatch(RecipeSlot::isEmpty)
                && in(mouseX, mouseY, s.panelX + 68, s.panelY + 28, 58, 58))
            tooltip = List.of(
                CreateLang.translate("gui.factory_panel.unconfigured_input").color(ScrollInput.HEADER_RGB).component(),
                CreateLang.translate("gui.factory_panel.unconfigured_input_tip").style(ChatFormatting.GRAY).component(),
                CreateLang.translate("gui.factory_panel.unconfigured_input_tip_1").style(ChatFormatting.GRAY).component());
        return tooltip;
    }

    /** What a cell shows, accounting for the swap-drag preview (left button only): the source cell shows the
     *  hovered target's item (a fake swap preview), and the hovered target cell itself renders empty — its
     *  own item is "lifted" while the source item (shown following the cursor) is about to land there. */
    private RecipeSlot slotDisplay(int i, int hover) {
        if (dragFrom >= 0 && dragButton == 0) {
            if (i == dragFrom)
                return hover >= 0 && hover != dragFrom ? s.customSlots.get(hover) : RecipeSlot.EMPTY;
            if (hover >= 0 && i == hover)
                return RecipeSlot.EMPTY;
        }
        return i < s.customSlots.size() ? s.customSlots.get(i) : RecipeSlot.EMPTY;
    }

    private void renderSlotCell(GuiGraphics gfx, int ix, int iy, RecipeSlot slot, int scale) {
        if (slot.isEmpty()) return;
        ItemStack stack = s.ingredientOf(slot.source());
        if (stack.isEmpty()) return;
        FluidGuiRender.filterIcon(gfx, stack, ix, iy);
        // scale = 1 unless the multiplier bar is hovered, then each cell previews its scaled count.
        s.drawItemCount(gfx, stack, ix, iy, countLabel(stack, slot.count() * scale));
    }

    @Override
    void renderOverlay(GuiGraphics gfx, int mouseX, int mouseY) {
        if (dragFrom < 0 || dragFrom >= s.customSlots.size()) return;
        int hover = slotAt(mouseX, mouseY);

        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 199);
        // Hovering the source slot itself never draws a box, in either mode.
        if (hover >= 0 && hover != dragFrom) {
            int hx = cellX(hover), hy = cellY(hover);
            boolean targetEmpty = s.customSlots.get(hover).isEmpty();
            // Copy (right-drag): white = valid empty target, red = occupied (can't copy there).
            // Swap (left-drag): white.
            int color = dragButton == 1
                ? (targetEmpty ? 0x80FFFFFF : 0x80FF4040)
                : 0x80FFFFFF;
            gfx.fill(hx, hy, hx + 16, hy + 16, color);
        }
        int fx = cellX(dragFrom), fy = cellY(dragFrom);
        gfx.fill(fx, fy, fx + 16, fy + 16, 0x4485F2A2);   // source slot highlight
        gfx.pose().popPose();

        // The picked-up (source) item follows the cursor, above everything (including every count).
        RecipeSlot src = s.customSlots.get(dragFrom);
        ItemStack stack = src.isEmpty() ? ItemStack.EMPTY : s.ingredientOf(src.source());
        if (!stack.isEmpty()) {
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 250);
            int dx = mouseX - 8, dy = mouseY - 8;
            gfx.renderItem(stack, dx, dy);
            s.drawItemCount(gfx, stack, dx, dy, countLabel(stack, src.count()));
            gfx.pose().popPose();
        }
    }

    private static String countLabel(ItemStack stack, int count) {
        return FluidCompat.isFluidFilter(stack) ? ConfigureRecipeScreen.formatFluidShort(count) : String.valueOf(count);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    boolean inputAreaClicked(double mouseX, double mouseY, int button) {
        int slot = slotAt(mouseX, mouseY);
        if (slot < 0) return false;
        if (button == 0 && Screen.hasShiftDown()) {   // shift-click clears the slot
            clearSlot(slot);
            return true;
        }
        if (s.customSlots.get(slot).isEmpty()) return true;   // nothing to drag from an empty slot
        dragFrom = slot;   // begin a drag (left = move/swap, right = copy-to-empty), applied on release
        dragButton = button;
        return true;
    }

    @Override
    boolean gridReleased(double mouseX, double mouseY, int button) {
        if (dragFrom < 0 || button != dragButton) { dragFrom = -1; return false; }
        int from = dragFrom;
        dragFrom = -1;
        // A move dropped outside the whole panel is a removal, same as shift-click (drag it off entirely).
        if (button == 0 && !s.inPanelBounds(mouseX, mouseY)) {
            clearSlot(from);
            return true;
        }
        int to = slotAt(mouseX, mouseY);
        if (to < 0 || to == from) return true;
        if (button == 0) {   // move / swap
            RecipeSlot a = s.customSlots.get(from);
            s.customSlots.set(from, s.customSlots.get(to));
            s.customSlots.set(to, a);
            ConfigureRecipeScreen.playClickSound();
        } else if (button == 1) {   // copy into an empty slot (replicate the ingredient)
            RecipeSlot src = s.customSlots.get(from);
            if (!src.isEmpty() && s.customSlots.get(to).isEmpty()) {
                s.customSlots.set(to, new RecipeSlot(src.source(), src.count()));
                ConfigureRecipeScreen.playClickSound();
            }
        }
        return true;
    }

    @Override
    boolean inputAreaScrolled(double mouseX, double mouseY, int dir, int step) {
        int slot = dragFrom >= 0 ? dragFrom : slotAt(mouseX, mouseY);
        if (slot < 0) return false;
        RecipeSlot rs = s.customSlots.get(slot);
        if (rs.isEmpty()) return true;   // consume the scroll, but a gap has nothing to tune
        ItemStack ingredient = s.ingredientOf(rs.source());
        boolean ctrl = Screen.hasControlDown();
        boolean fluid = FluidCompat.isFluidFilter(ingredient);
        // Floored at 1 in both branches — clearing is shift-click only.
        int next;
        if (fluid) {   // fluid cell: millibuckets with fluid steps + cap
            next = ConfigureRecipeScreen.adjustFluidAmount(rs.count(), dir,
                Screen.hasShiftDown(), ctrl, 1, ConfigureRecipeScreen.FLUID_INGREDIENT_CAP_MB);
        } else if (ctrl) {   // ctrl-scroll a solid slot: fine ±1 step, levels every other solid slot to match
            next = Mth.clamp(rs.count() + dir, 1, Math.max(1, ConfigureRecipeScreen.stackSizeOf(ingredient)));
        } else {
            next = Mth.clamp(rs.count() + dir * step, 1, Math.max(1, ConfigureRecipeScreen.stackSizeOf(ingredient)));
        }
        if (next != rs.count()) {
            s.customSlots.set(slot, new RecipeSlot(rs.source(), next));
            ConfigureRecipeScreen.playScrollSound();
            if (ctrl && !fluid) {
                for (int i = 0; i < s.customSlots.size(); i++) {
                    if (i == slot) continue;
                    RecipeSlot other = s.customSlots.get(i);
                    if (other.isEmpty()) continue;
                    ItemStack otherItem = s.ingredientOf(other.source());
                    if (FluidCompat.isFluidFilter(otherItem)) continue;
                    int capped = Mth.clamp(next, 1, Math.max(1, ConfigureRecipeScreen.stackSizeOf(otherItem)));
                    if (capped != other.count())
                        s.customSlots.set(i, new RecipeSlot(other.source(), capped));
                }
            }
        }
        return true;
    }

    @Override
    boolean[] occupiedCells() {
        boolean[] cells = new boolean[ConfigureRecipeScreen.MAX_INPUT_SLOTS];
        for (int i = 0; i < cells.length && i < s.customSlots.size(); i++)
            cells[i] = !s.customSlots.get(i).isEmpty();
        return cells;
    }

    /** Clears slot {@code i}; if it was that ingredient's last slot, disconnects the wire too. */
    private void clearSlot(int i) {
        RecipeSlot slot = s.customSlots.get(i);
        if (slot.isEmpty()) return;
        VirtualComponentPosition source = slot.source();
        s.customSlots.set(i, RecipeSlot.EMPTY);
        boolean stillUsed = s.customSlots.stream().anyMatch(sl -> !sl.isEmpty() && source.equals(sl.source()));
        if (!stillUsed) {
            int c = s.inputConnections.indexOf(source);
            if (c >= 0) { s.disconnectInput(c); return; }   // disconnectInput plays the click sound
        }
        ConfigureRecipeScreen.playClickSound();
    }
}
