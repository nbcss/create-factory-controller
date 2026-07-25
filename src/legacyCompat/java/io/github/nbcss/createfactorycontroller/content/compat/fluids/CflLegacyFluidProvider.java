package io.github.nbcss.createfactorycontroller.content.compat.fluids;

import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Fluid-filter provider for CreateFluidLogistic < 1.2.5
 */
final class CflLegacyFluidProvider extends CflFluidProvider {

    @Override
    public ItemStack makeFluidFilter(FluidStack fluid) {
        ItemStack stack = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluidVirtual(stack, fluid.copyWithAmount(1));
        return stack;
    }
}
