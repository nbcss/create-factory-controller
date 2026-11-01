package io.github.nbcss.createfactorycontroller.content.compat.fluids;

import com.yision.fluidlogistics.content.logistics.fluidPackage.FluidPackageResourceType;
import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Fluid-filter provider for CreateFluidLogistic (>= 1.2.5)
 */
final class CflFluidProvider implements FluidFilterProvider {

    @Override
    public boolean isFluidFilter(ItemStack stack) {
        if (stack.is(AllItems.COMPRESSED_STORAGE_TANK.get())) return !CompressedTankItem.getFluid(stack).isEmpty();
        return !FluidPackageResourceType.getFluid(stack).isEmpty();
    }

    /** Also reads CFL 1.3+'s per-fluid proxy keys ({@code fluid_proxy/<ns>/<path>}), which its own gauges store as filter. */
    @Override
    public FluidStack getFilterFluid(ItemStack stack) {
        return stack.is(AllItems.COMPRESSED_STORAGE_TANK.get())
                ? CompressedTankItem.getFluid(stack) : FluidPackageResourceType.getFluid(stack);
    }

    @Override
    public ItemStack makeFluidFilter(FluidStack fluid) {
        ItemStack stack = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluid(stack, fluid.copyWithAmount(1));
        return stack;
    }
}
