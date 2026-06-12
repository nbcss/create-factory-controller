package io.github.nbcss.content.factorycontroller.compat.fluids;

import com.adonis.fluid.item.FluidManifestItem;
import com.adonis.fluid.registry.CFItems;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Fluid-filter provider for <a href="https://github.com/adonis-baffin/CreateFluid">CreateFluid</a> (modid
 * {@code fluid}). Its filter is a {@code FLUID_MANIFEST} item carrying a {@code FluidManifestContent} component
 * (fluid id + amount; the amount is normalised to 1 as a type tag, the requested mB amount lives on the gauge).
 *
 * <p>The only class touching CreateFluid's types; instantiated by {@link FluidCompat} solely when CreateFluid is
 * loaded (it's {@code compileOnly}). CreateFluid and CreateFluidLogistic are mutually incompatible, so at most one
 * provider is ever active at runtime.</p>
 */
final class CreateFluidProvider implements FluidFilterProvider {

    @Override
    public boolean isFluidFilter(ItemStack stack) {
        return stack.is(CFItems.FLUID_MANIFEST.get()) && !FluidManifestItem.read(stack).isEmpty();
    }

    @Override
    public FluidStack getFilterFluid(ItemStack stack) {
        return stack.is(CFItems.FLUID_MANIFEST.get()) ? FluidManifestItem.read(stack) : FluidStack.EMPTY;
    }

    @Override
    public ItemStack makeFluidFilter(FluidStack fluid) {
        return FluidManifestItem.of(fluid);   // CreateFluid normalises the manifest amount to 1 (a type tag)
    }
}
