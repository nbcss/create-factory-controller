package io.github.nbcss.createfactorycontroller.content.item;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A virtual, <b>unobtainable</b> item that represents a standing promise to produce the output of a specific
 * passive gauge. It never exists as a real world/inventory item: a Stock Keeper on the gauge's network shows
 * it (in infinite supply) so the player can order it, and ordering it spawns a tracked Promise Order instead
 * of moving any physical item (see the promise package).
 *
 * <p>The bound gauge + produced item live in the {@link ProductionTarget} data component. The blueprint reads
 * its name and tooltip from the produced item so it looks like "the item, promised"; it's rendered as a
 * blueprint background with that item drawn on top (client renderer).</p>
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
        return display.isEmpty() ? super.getName(stack) : display.getHoverName();
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
        if (!display.isEmpty())
            display.getItem().appendHoverText(display, context, tooltip, flag);
    }
}
