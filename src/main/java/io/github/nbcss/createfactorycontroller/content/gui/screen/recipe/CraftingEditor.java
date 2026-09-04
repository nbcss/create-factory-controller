package io.github.nbcss.createfactorycontroller.content.gui.screen.recipe;

import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.GaugeWorkMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * CRAFTING work mode: the inputs form a crafting recipe, so the grid shows the recipe's cells (or, for a
 * &gt;3×3 recipe, one aggregated slot per distinct ingredient type). Slots aren't individually editable —
 * scrolling tunes the crafts-per-request batch, or (Ctrl) the square crafter-grid size. Output is locked
 * to the recipe yield × batch.
 */
class CraftingEditor extends GaugeWorkModeEditor {

    CraftingEditor(ConfigureRecipeScreen screen) { super(screen); }

    @Override
    void onChange(GaugeWorkMode previous) {
        s.craftBatch = 1;
        s.applyCraftingResolution();
    }

    @Override
    void renderInputArea(GuiGraphics gfx, int mouseX, int mouseY) {
        s.patternHovered = false;
        int scale = s.previewScale(mouseX, mouseY);   // multiplier preview while its bar is hovered, else 1

        if (s.craftingIsLarge()) {
            // >3×3: aggregate to one slot per distinct ingredient type (count = cells × batch, no overflow)
            // and offer the real N×M layout in a Ctrl-held tooltip (drawn in render()).
            List<ConfigureRecipeScreen.CraftSlot> slots = s.craftingDisplaySlots();
            for (int i = 0; i < ConfigureRecipeScreen.MAX_INPUT_SLOTS; i++) {
                int ix = cellX(i), iy = cellY(i);
                if (i < slots.size()) {
                    ConfigureRecipeScreen.CraftSlot slot = slots.get(i);
                    gfx.renderItem(slot.stack(), ix, iy);
                    s.drawItemCount(gfx, slot.stack(), ix, iy, String.valueOf(slot.amount() * scale));
                }
            }
        } else {
            // ≤3×3: render the recipe's own cells straight into the grid (top-left 3×3 of the dim×dim square).
            int dim = s.effectiveCraftDimension();
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                int ix = cellX(row * 3 + col);
                int iy = cellY(row * 3 + col);
                int idx = row * dim + col;
                ItemStack stack = (col < dim && row < dim && idx < s.craftingIngredients.size())
                    ? s.craftingIngredients.get(idx).stack : ItemStack.EMPTY;
                gfx.renderItem(stack, ix, iy);
                int dispBatch = s.effectiveBatch() * scale;
                if (!stack.isEmpty() && dispBatch > 1)
                    s.drawItemCount(gfx, stack, ix, iy, String.valueOf(Math.max(1, stack.getCount()) * dispBatch));
            }
        }
    }

    @Override
    List<Component> inputTooltip(int mouseX, int mouseY) {
        int slot = slotAt(mouseX, mouseY);
        if (slot < 0) return List.of();
        if (Screen.hasControlDown()) {
            s.patternHovered = true;
            return List.of();
        }

        ItemStack hovered = ItemStack.EMPTY;
        if (s.craftingIsLarge()) {
            List<ConfigureRecipeScreen.CraftSlot> slots = s.craftingDisplaySlots();
            if (slot < slots.size()) hovered = slots.get(slot).stack();
        } else {
            int dim = s.effectiveCraftDimension();
            int row = slot / 3;
            int col = slot % 3;
            int index = row * dim + col;
            if (col < dim && row < dim && index < s.craftingIngredients.size())
                hovered = s.craftingIngredients.get(index).stack;
        }

        int dim = s.effectiveCraftDimension();
        return ConfigureRecipeScreen.withIgnoreDataLine(List.of(
                        CreateLang.translate("gui.factory_panel.crafting_input")
                                .color(ScrollInput.HEADER_RGB).component(),
                        Component.translatable("createfactorycontroller.gui.crafting_unpacked")
                                .withStyle(ChatFormatting.GRAY),
                        Component.translatable("createfactorycontroller.gui.crafting_crafters", dim, dim)
                                .withStyle(ChatFormatting.GRAY),
                        Component.translatable("createfactorycontroller.gui.crafting_hold_ctrl_dim")
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)),
                s.craftingCellIgnoresData(hovered));
    }

    @Override
    int producedCount() { return s.outputCount * s.effectiveBatch(); }

    @Override
    boolean inputAreaClicked(double mouseX, double mouseY, int button) {
        return false;   // crafting cells aren't individually editable
    }

    @Override
    boolean inputAreaScrolled(double mouseX, double mouseY, int dir, int step) {
        // Ctrl over the recipe's ingredients resizes the square crafter grid; otherwise tune the batch.
        if (Screen.hasControlDown()) s.adjustCraftDimension(dir);
        else s.adjustCraftBatch(dir * step);
        return true;
    }

    @Override
    boolean outputScrolled(int dir, int step) {
        // Per-craft yield is fixed, so scrolling the output changes how many crafts ride one request.
        s.adjustCraftBatch(dir * step);
        return true;
    }

    @Override
    Configuration configuration() {
        List<Integer> amounts = s.inputConnections.stream().map(pos -> {
            ItemStack ingredient = s.ingredientOf(pos);
            return (int) s.craftingIngredients.stream()
                .filter(entry -> !entry.stack.isEmpty()
                    && ItemStack.isSameItemSameComponents(entry.stack, ingredient))
                .count();
        }).toList();
        List<ItemStack> arrangement = s.craftingIngredients.stream().map(entry -> entry.stack).toList();
        return configuration(amounts, s.effectiveBatch(), s.effectiveCraftDimension(), arrangement, List.of());
    }

    @Override
    boolean[] occupiedCells() {
        boolean[] cells = new boolean[ConfigureRecipeScreen.MAX_INPUT_SLOTS];
        if (s.craftingIsLarge()) {   // one aggregated slot per distinct ingredient, packed from cell 0
            int count = s.craftingDisplaySlots().size();
            for (int i = 0; i < count && i < cells.length; i++) cells[i] = true;
        } else {                     // recipe cells laid into the top-left dim×dim of the grid
            int dim = s.effectiveCraftDimension();
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                int idx = row * dim + col;
                if (col < dim && row < dim && idx < s.craftingIngredients.size()
                        && !s.craftingIngredients.get(idx).stack.isEmpty())
                    cells[row * 3 + col] = true;
            }
        }
        return cells;
    }
}
