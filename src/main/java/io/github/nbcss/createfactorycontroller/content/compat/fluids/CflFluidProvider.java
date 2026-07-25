package io.github.nbcss.createfactorycontroller.content.compat.fluids;

import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Fluid-filter provider for CreateFluidLogistic (>= 1.2.5)
 */
class CflFluidProvider implements FluidFilterProvider {

    @Override
    public boolean isFluidFilter(ItemStack stack) {
        return stack.is(AllItems.COMPRESSED_STORAGE_TANK.get()) && !CompressedTankItem.getFluid(stack).isEmpty();
    }

    @Override
    public FluidStack getFilterFluid(ItemStack stack) {
        return stack.is(AllItems.COMPRESSED_STORAGE_TANK.get()) ? CompressedTankItem.getFluid(stack) : FluidStack.EMPTY;
    }

    @Override
    public ItemStack makeFluidFilter(FluidStack fluid) {
        ItemStack stack = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluid(stack, fluid.copyWithAmount(1));
        return stack;
    }
}
