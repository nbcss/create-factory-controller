package me.nbcss.github.content.factorycontroller;

import me.nbcss.github.content.factorycontroller.compat.DeployerGaugeHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class GaugeHelper {

    private GaugeHelper() {}

    /** Any item registered in DeployerRegistries.PANEL is a valid gauge — tuning is not required. */
    public static boolean isValidGauge(ItemStack stack) {
        return DeployerGaugeHelper.isRegisteredGauge(stack);
    }

    public static ResourceLocation getGaugeItemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}
