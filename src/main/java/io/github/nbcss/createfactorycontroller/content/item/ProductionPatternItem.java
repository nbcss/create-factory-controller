package io.github.nbcss.createfactorycontroller.content.item;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.ThresholdUnit;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A item that represents a standing promise to produce the output of a specific passive gauge.
 * For use in Stock Keeper GUI
 */
public class ProductionPatternItem extends Item {

    public ProductionPatternItem(Properties properties) {
        super(properties);
    }

    public static boolean isPattern(ItemStack stack) {
        return stack.getItem() instanceof ProductionPatternItem;
    }

    @Nullable
    public static ProductionTarget getTarget(ItemStack stack) {
        return stack.get(CreateFactoryController.PRODUCTION_TARGET.get());
    }

    /** Builds a blueprint stack bound to {@code target}. */
    public static ItemStack of(ProductionTarget target) {
        ItemStack stack = new ItemStack(CreateFactoryController.PRODUCTION_PATTERN.get());
        stack.set(CreateFactoryController.PRODUCTION_TARGET.get(), target);
        return stack;
    }

    /** Reads the produced item this blueprint promises (or empty if the component is missing). */
    public static ItemStack displayOf(ItemStack stack) {
        ProductionTarget target = getTarget(stack);
        return target == null ? ItemStack.EMPTY : target.display();
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        ItemStack display = displayOf(stack);
        // A fluid filter names/draws itself as a wrapper item ("Fluid Filter"/addon wrapper); source the clean fluid
        // name instead (filterName falls back to the item's name for non-fluids), so fluid patterns read correctly.
        return display.isEmpty() ? super.getName(stack) : FluidCompat.filterName(display);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack,
                                @NotNull TooltipContext context,
                                List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        // Read like the promised item: header, then the produced item's own extra tooltip lines.
        tooltip.add(Component.translatable("item.createfactorycontroller.production_pattern")
            .withStyle(ChatFormatting.GRAY));
        ItemStack display = displayOf(stack);
        if (display.isEmpty()) return;
        if (FluidCompat.isFluidFilter(display)) {
            // Fluid filter: the wrapper item's own tooltip is noise; show the fluid's lines (id when advanced + source
            // mod), skipping the leading name line which is already the tooltip title (getName).
            List<Component> lines = FluidCompat.fluidTooltip(FluidCompat.getFilterFluid(display), flag.isAdvanced());
            for (int i = 1; i < lines.size(); i++) tooltip.add(lines.get(i));
        } else {
            display.getItem().appendHoverText(display, context, tooltip, flag);
        }
        appendRecipeInfo(stack, tooltip);
    }

    /** Appends the gauge's recipe (ingredients + target address), baked into {@link ProductionTarget} at heartbeat
     *  time so this reads purely from the item — no server round-trip needed on hover. */
    private static void appendRecipeInfo(ItemStack stack, List<Component> tooltip) {
        ProductionTarget target = getTarget(stack);
        if (target == null) return;
        List<ItemStack> ingredients = target.ingredients();
        if (!ingredients.isEmpty()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("createfactorycontroller.gui.production_output_header")
                    .withColor(0x528FDE));
            {
                ItemStack output = target.display();
                String name = FluidCompat.filterName(output).getString();
                String amount = FluidCompat.isFluidFilter(output)
                        ? ThresholdUnit.formatFluidAmount(output.getCount())
                        : String.valueOf(output.getCount());
                tooltip.add(Component.literal("- " + name + " x" + amount).withStyle(ChatFormatting.GRAY));
            }
            tooltip.add(Component.translatable("createfactorycontroller.gui.production_ingredients_header")
                    .withColor(0x528FDE));
            for (ItemStack ingredient : ingredients) {
                String name = FluidCompat.filterName(ingredient).getString();
                String amount = FluidCompat.isFluidFilter(ingredient)
                    ? ThresholdUnit.formatFluidAmount(ingredient.getCount())
                    : String.valueOf(ingredient.getCount());
                tooltip.add(Component.literal("- " + name + " x" + amount).withStyle(ChatFormatting.GRAY));
            }
        }
        String address = target.address();
        if (!address.isBlank()) {
            tooltip.add(Component.translatable("createfactorycontroller.gui.production_address_header")
                .withColor(0x528FDE));
            tooltip.add(Component.literal("'" + address + "'").withStyle(ChatFormatting.GRAY));
        }
    }
}
