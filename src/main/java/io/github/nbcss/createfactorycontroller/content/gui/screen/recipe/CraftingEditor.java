package io.github.nbcss.createfactorycontroller.content.gui.screen.recipe;

import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
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
    List<Component> renderInputArea(GuiGraphics gfx, int mouseX, int mouseY) {
        s.patternHovered = false;
        List<Component> tooltip = null;

        if (s.craftingIsLarge()) {
            // >3×3: aggregate to one slot per distinct ingredient type (count = cells × batch, no overflow)
            // and offer the real N×M layout in a Ctrl-held tooltip (drawn in render()).
            List<ConfigureRecipeScreen.CraftSlot> slots = s.craftingDisplaySlots();
            boolean hovering = false;
            for (int i = 0; i < ConfigureRecipeScreen.MAX_INPUT_SLOTS; i++) {
                int ix = cellX(i), iy = cellY(i);
                if (i < slots.size()) {
                    ConfigureRecipeScreen.CraftSlot slot = slots.get(i);
                    gfx.renderItem(slot.stack(), ix, iy);
                    s.drawItemCount(gfx, slot.stack(), ix, iy, String.valueOf(slot.amount()));
                }
                if (in(mouseX, mouseY, ix, iy, 16, 16)) hovering = true;
            }
            if (hovering) {
                if (Screen.hasControlDown())
                    s.patternHovered = true;   // draw the N×M layout grid in render()
                else {
                    int dim = s.effectiveCraftDimension();
                    tooltip = List.of(
                        CreateLang.translate("gui.factory_panel.crafting_input").color(ScrollInput.HEADER_RGB).component(),
                        Component.translatable("createfactorycontroller.gui.crafting_unpacked").withStyle(ChatFormatting.GRAY),
                        Component.translatable("createfactorycontroller.gui.crafting_crafters", dim, dim).withStyle(ChatFormatting.GRAY),
                        Component.translatable("createfactorycontroller.gui.crafting_hold_ctrl_dim").withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC));
                }
            }
        } else {
            // ≤3×3: render the recipe's own cells straight into the grid (top-left 3×3 of the dim×dim square).
            int dim = s.effectiveCraftDimension();
            boolean hovering = false;
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                int ix = s.panelX + 68 + col * 20;
                int iy = s.panelY + 28 + row * 20;
                int idx = row * dim + col;
                ItemStack stack = (col < dim && row < dim && idx < s.craftingIngredients.size())
                    ? s.craftingIngredients.get(idx).stack : ItemStack.EMPTY;
                gfx.renderItem(stack, ix, iy);
                int dispBatch = s.effectiveBatch();
                if (!stack.isEmpty() && dispBatch > 1)
                    s.drawItemCount(gfx, stack, ix, iy, String.valueOf(Math.max(1, stack.getCount()) * dispBatch));
                if (in(mouseX, mouseY, ix, iy, 16, 16)) hovering = true;
            }
            if (hovering) {
                boolean ignoreData = s.craftingUsesIgnoreData();   // grid resizing & Ctrl popup disabled then
                if (Screen.hasControlDown() && !ignoreData)
                    s.patternHovered = true;
                else if (dim > 3)
                    tooltip = List.of(
                            CreateLang.translate("gui.factory_panel.crafting_input").color(ScrollInput.HEADER_RGB).component(),
                            Component.translatable("createfactorycontroller.gui.crafting_unpacked").withStyle(ChatFormatting.GRAY),
                            Component.translatable("createfactorycontroller.gui.crafting_crafters", dim, dim).withStyle(ChatFormatting.GRAY),
                            Component.translatable("createfactorycontroller.gui.crafting_hold_ctrl_dim").withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC));
                else {
                    List<Component> t = new ArrayList<>();
                    t.add(CreateLang.translate("gui.factory_panel.crafting_input").color(ScrollInput.HEADER_RGB).component());
                    t.add(CreateLang.translate("gui.factory_panel.crafting_input_tip").style(ChatFormatting.GRAY).component());
                    t.add(CreateLang.translate("gui.factory_panel.crafting_input_tip_1").style(ChatFormatting.GRAY).component());
                    if (!ignoreData)
                        t.add(Component.translatable("createfactorycontroller.gui.crafting_hold_ctrl_dim")
                                .withStyle(ChatFormatting.DARK_GRAY).withStyle(ChatFormatting.ITALIC));
                    tooltip = t;
                }
            }
        }
        return tooltip;
    }

    @Override
    int producedCount() { return s.outputCount * s.effectiveBatch(); }

    @Override
    boolean inputAreaClicked(double mouseX, double mouseY, int button) {
        return false;   // crafting cells aren't individually editable
    }

    @Override
    boolean inputAreaScrolled(double mouseX, double mouseY, int dir, int step) {
        if (!in(mouseX, mouseY, s.panelX + 68, s.panelY + 28, 58, 58)) return false;
        // Ctrl over the recipe's ingredients resizes the square crafter grid; otherwise tune the batch.
        // Both are disabled when an ignore-data ingredient is present (fixed 3×3, no batching).
        if (!s.craftingUsesIgnoreData()) {
            if (Screen.hasControlDown()) s.adjustCraftDimension(dir);
            else s.adjustCraftBatch(dir * step);
        }
        return true;
    }

    @Override
    boolean outputScrolled(double mouseX, double mouseY, int dir, int step) {
        if (!in(mouseX, mouseY, s.panelX + 160, s.panelY + 48, 16, 16)) return false;
        // Per-craft yield is fixed, so scrolling the output changes how many crafts ride one request.
        if (!s.craftingUsesIgnoreData()) s.adjustCraftBatch(dir * step);
        return true;
    }
}
