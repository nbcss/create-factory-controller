package io.github.nbcss.createfactorycontroller.content.component.gauge;

import io.github.nbcss.createfactorycontroller.content.compat.gas.GasCompat;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

public interface GaugeFilterResolver {
    boolean acceptsFilter(ItemStack filter);
    boolean supportsIgnoreData();
    boolean acceptsItemDrop();
    boolean acceptsFluidDrop();
    /** Whether the filter is a gas (set from a held gas container); see {@code GasCompat}. */
    default boolean acceptsGas() { return false; }
    ItemStack fromCarried(ItemStack carried, int mouseButton);
    ItemStack fromFluid(FluidStack fluid);

    /**
     * The filter for an ingredient dragged from a recipe viewer (an {@link ItemStack}, {@link FluidStack} or CCB gas
     * stack), or EMPTY when this gauge can't take it.
     */
    default ItemStack fromIngredient(Object ingredient) {
        ItemStack key;
        if (ingredient instanceof ItemStack stack) key = acceptsItemDrop() ? stack.copyWithCount(1) : ItemStack.EMPTY;
        else if (ingredient instanceof FluidStack fluid) key = acceptsFluidDrop() && !fluid.isEmpty() ? fromFluid(fluid) : ItemStack.EMPTY;
        else key = acceptsGas() ? GasCompat.gasFromStack(ingredient) : ItemStack.EMPTY;
        return !key.isEmpty() && acceptsFilter(key) ? key : ItemStack.EMPTY;
    }
}
