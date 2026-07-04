package io.github.nbcss.createfactorycontroller.content.component;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public interface GaugeFilterResolver {
    boolean acceptsFilter(ItemStack filter);
    boolean supportsIgnoreData();
    boolean acceptsItemDrop();
    boolean acceptsFluidDrop();
    ItemStack fromCarried(ItemStack carried, int mouseButton);
    ItemStack fromFluid(FluidStack fluid);
}
