package io.github.nbcss.content.factorycontroller.compat.fluids;

import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Fluid-filter provider for <a href="https://github.com/yision1/CreateFluidLogistic">CreateFluidLogistic</a>
 * (modid {@code fluidlogistics}). Its filter is a virtual {@code COMPRESSED_STORAGE_TANK} item carrying a
 * {@code FluidTankContent} component (the fluid is a type tag; the requested mB amount lives on the gauge).
 *
 * <p>This class is the only place that references CFL's types, and it's instantiated by {@link FluidCompat} solely
 * when CFL is loaded, so the JVM never resolves a CFL class when the mod is absent (CFL is {@code compileOnly}).</p>
 */
final class CflFluidProvider implements FluidFilterProvider {

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
        CompressedTankItem.setFluidVirtual(stack, fluid.copyWithAmount(1));
        return stack;
    }
}
