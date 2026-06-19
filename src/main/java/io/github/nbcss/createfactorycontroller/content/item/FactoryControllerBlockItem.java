package io.github.nbcss.createfactorycontroller.content.item;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Block item for the Factory Controller. When it carries a preserved board ({@link CreateFactoryController#CONTROLLER_SETUP}),
 * it shows a gold "Configured Controller" tooltip line and glows — mirroring Create's tuned Factory Gauge item.
 */
public class FactoryControllerBlockItem extends BlockItem {

    public FactoryControllerBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** True once the item carries a preserved board — drives the enchantment glow. */
    public static boolean isConfigured(ItemStack stack) {
        return stack.has(CreateFactoryController.CONTROLLER_SETUP.get());
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return isConfigured(stack) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (isConfigured(stack))
            tooltip.add(Component.translatable("createfactorycontroller.tooltip.configured_controller")
                .withStyle(ChatFormatting.GOLD));
    }
}
