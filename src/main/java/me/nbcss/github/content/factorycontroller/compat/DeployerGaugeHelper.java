package me.nbcss.github.content.factorycontroller.compat;

import net.liukrast.deployer.lib.registry.DeployerRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class DeployerGaugeHelper {

    private DeployerGaugeHelper() {}

    public static boolean isRegisteredGauge(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return isRegisteredGaugeId(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static boolean isRegisteredGaugeId(ResourceLocation id) {
        return DeployerRegistries.PANEL.containsKey(id);
    }

    /** Returns the gauge item for a registered panel type id. */
    public static Item getItemForGaugeId(ResourceLocation gaugeId) {
        return BuiltInRegistries.ITEM.get(gaugeId);
    }
}
