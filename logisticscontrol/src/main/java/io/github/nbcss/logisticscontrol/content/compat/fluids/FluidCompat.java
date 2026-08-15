package io.github.nbcss.logisticscontrol.content.compat.fluids;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Minimal fluid-filter detection. A fluid represented as a virtual item (CreateFluidLogistics / CreateFluid) is not a
 * valid package-output filter — detected by item id so this mod needs no compile dependency on those addons. (Factory
 * Controller's full provider-based FluidCompat is duplicated down to just this check here; see the split design.)
 */
public final class FluidCompat {

    private static final Set<ResourceLocation> FLUID_FILTER_ITEMS = Set.of(
        ResourceLocation.fromNamespaceAndPath("fluidlogistics", "compressed_storage_tank"),
        ResourceLocation.fromNamespaceAndPath("fluid", "fluid_manifest"));

    private FluidCompat() {}

    /** True when {@code stack} is a fluid-as-virtual-item filter from a supported fluid-logistics addon. */
    public static boolean isFluidFilter(ItemStack stack) {
        return !stack.isEmpty() && FLUID_FILTER_ITEMS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
