package io.github.nbcss.createfactorycontroller.content.gui.screen.recipe;

import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.GaugeWorkMode;
import io.github.nbcss.createfactorycontroller.content.ThresholdUnit;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.RecipeSlot;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.render.FluidGuiRender;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * REGULAR work mode: each ingredient connection is a single total; the 3×3 grid is derived on demand
 * (full stacks first, then a partial), a slot click disconnects the whole connection, and a slot scroll
 * adjusts that connection's total. Output is a freely-scrollable produced count.
 */
class RegularEditor extends GaugeWorkModeEditor {

    RegularEditor(ConfigureRecipeScreen screen) { super(screen); }

    @Override
    List<Component> renderInputArea(GuiGraphics gfx, int mouseX, int mouseY) {
        s.patternHovered = false;
        List<Component> tooltip = null;
        // Scaled layout while the multiplier bar is hovered (previewScale = 1 otherwise → unchanged).
        List<ConfigureRecipeScreen.InputSlot> slots = s.layoutInputSlots(s.previewScale(mouseX, mouseY));
        for (int i = 0; i < slots.size(); i++) {
            ConfigureRecipeScreen.InputSlot slot = slots.get(i);
            int ix = cellX(i), iy = cellY(i);
            boolean fluidIng = s.isFluidConn(slot.connectionIndex());
            ItemStack stack = s.ingredientOf(s.inputConnections.get(slot.connectionIndex()));
            FluidGuiRender.filterIcon(gfx, stack, ix, iy);
            if (!stack.isEmpty()) {
                s.drawItemCount(gfx, stack, ix, iy, fluidIng
                        ? ConfigureRecipeScreen.formatFluidShort(slot.amount()) : String.valueOf(slot.amount()));
            }
            if (in(mouseX, mouseY, ix, iy, 16, 16)) {
                // Every slot of a connection shows that connection's TOTAL, not the slot's own count.
                int total = Math.max(1, s.inputTotals.get(slot.connectionIndex()));
                String totalLabel = fluidIng ? ThresholdUnit.formatFluidAmount(total) : String.valueOf(total);
                boolean srcIgnore = s.getMenu().componentAt(s.inputConnections.get(slot.connectionIndex()))
                        instanceof VirtualGaugeBehaviour src && src.ignoreData;
                MutableComponent inHeader = CreateLang.translate("gui.factory_panel.sending_item",
                        FluidCompat.filterName(stack).getString() + " x" + totalLabel)
                        .color(ScrollInput.HEADER_RGB).component();
                if (!fluidIng && total > ConfigureRecipeScreen.stackSizeOf(stack))
                    inHeader.append(ConfigureRecipeScreen.stackBreakdown(total, ConfigureRecipeScreen.stackSizeOf(stack)));
                tooltip = stack.isEmpty()
                    ? List.of(
                        CreateLang.translate("gui.factory_panel.empty_panel").color(ScrollInput.HEADER_RGB).component(),
                        Component.translatable("createfactorycontroller.gui.action_disconnect")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
                    : ConfigureRecipeScreen.withIgnoreDataLine(List.of(
                        inHeader,
                        CreateLang.translate("gui.factory_panel.scroll_to_change_amount")
                            .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component(),
                        Component.translatable("createfactorycontroller.gui.action_disconnect")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)),
                        srcIgnore);
            }
        }
        if (s.inputConnections.isEmpty() && in(mouseX, mouseY, s.panelX + 68, s.panelY + 28, 58, 58))
            tooltip = List.of(
                CreateLang.translate("gui.factory_panel.unconfigured_input").color(ScrollInput.HEADER_RGB).component(),
                CreateLang.translate("gui.factory_panel.unconfigured_input_tip").style(ChatFormatting.GRAY).component(),
                CreateLang.translate("gui.factory_panel.unconfigured_input_tip_1").style(ChatFormatting.GRAY).component());
        return tooltip;
    }

    @Override
    boolean inputAreaClicked(double mouseX, double mouseY, int button) {
        List<ConfigureRecipeScreen.InputSlot> slots = s.layoutInputSlots();
        for (int i = 0; i < slots.size(); i++) {
            if (!in(mouseX, mouseY, cellX(i), cellY(i), 16, 16)) continue;
            // Shift-click disconnects
            if (button == 0 && Screen.hasShiftDown()) s.disconnectInput(slots.get(i).connectionIndex());
            return true;
        }
        return false;
    }

    @Override
    boolean inputAreaScrolled(double mouseX, double mouseY, int dir, int step) {
        List<ConfigureRecipeScreen.InputSlot> slots = s.layoutInputSlots();
        for (int i = 0; i < slots.size(); i++) {
            if (in(mouseX, mouseY, cellX(i), cellY(i), 16, 16)) {
                s.adjustInputTotal(slots.get(i).connectionIndex(), dir, Screen.hasShiftDown(), Screen.hasControlDown());
                ConfigureRecipeScreen.playScrollSound();
                return true;
            }
        }
        return false;
    }

    @Override
    Configuration configuration() {
        return configuration(s.inputTotals, 1, 0, List.of(), List.of());
    }

    @Override
    boolean[] occupiedCells() {
        boolean[] cells = new boolean[ConfigureRecipeScreen.MAX_INPUT_SLOTS];
        // Only called while the bar is hovered, so preview at the full multiplier (scaled spill included).
        List<ConfigureRecipeScreen.InputSlot> slots = s.layoutInputSlots(s.maxRequestMultiplier);
        for (int i = 0; i < slots.size() && i < cells.length; i++) cells[i] = true;
        return cells;
    }

    @Override
    void onChange(GaugeWorkMode previous) {
        if (previous == GaugeWorkMode.CRAFTING) {
            bakeCraftingOutput();
            int batch = s.effectiveBatch();
            for (int c = 0; c < s.inputConnections.size(); c++) {
                ItemStack ing = s.ingredientOf(s.inputConnections.get(c));
                int cells = s.craftingIngredients.stream()
                    .filter(b -> !b.stack.isEmpty() && ItemStack.isSameItemSameComponents(b.stack, ing))
                    .mapToInt(b -> Math.max(1, b.stack.getCount())).sum();
                s.inputTotals.set(c, Math.max(1, cells) * batch);
            }
            clampTotalsToGrid();
        } else if (previous == GaugeWorkMode.CUSTOM) {
            for (int c = 0; c < s.inputConnections.size(); c++) {
                VirtualComponentPosition pos = s.inputConnections.get(c);
                int total = s.customSlots.stream()
                    .filter(sl -> !sl.isEmpty() && pos.equals(sl.source()))
                    .mapToInt(RecipeSlot::count).sum();
                s.inputTotals.set(c, Math.max(1, total));
            }
            clampTotalsToGrid();
        }
    }

    /** Reduces each connection total to what still fits the 9-slot grid (best-effort, order-dependent). */
    private void clampTotalsToGrid() {
        for (int c = 0; c < s.inputConnections.size(); c++) {
            if (s.isFluidConn(c)) {
                s.inputTotals.set(c, Mth.clamp(s.inputTotals.get(c), 1, ConfigureRecipeScreen.FLUID_INGREDIENT_CAP_MB));
            } else {
                int ss = ConfigureRecipeScreen.stackSizeOf(s.ingredientOf(s.inputConnections.get(c)));
                int maxTotal = Math.max(1, ConfigureRecipeScreen.MAX_INPUT_SLOTS - s.slotsUsedExcept(c)) * ss;
                s.inputTotals.set(c, Mth.clamp(s.inputTotals.get(c), 1, maxTotal));
            }
        }
    }
}
