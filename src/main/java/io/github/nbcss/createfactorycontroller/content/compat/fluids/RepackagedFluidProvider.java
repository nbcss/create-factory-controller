package io.github.nbcss.createfactorycontroller.content.compat.fluids;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.UUID;

final class RepackagedFluidProvider implements FluidFilterProvider {

    @Override
    public boolean isFluidFilter(ItemStack stack) {
        return CreateFactoryController.FLUID_FILTER != null && stack.is(CreateFactoryController.FLUID_FILTER.get());
    }

    @Override
    public FluidStack getFilterFluid(ItemStack stack) {
        return isFluidFilter(stack)
                ? stack.getOrDefault(CreateFactoryController.FLUID_CONTENT.get(),
                        net.neoforged.neoforge.fluids.SimpleFluidContent.EMPTY).copy()
                : FluidStack.EMPTY;
    }

    @Override
    public ItemStack makeFluidFilter(FluidStack fluid) {
        if (fluid.isEmpty() || CreateFactoryController.FLUID_FILTER == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(CreateFactoryController.FLUID_FILTER.get());
        stack.set(CreateFactoryController.FLUID_CONTENT.get(),
                net.neoforged.neoforge.fluids.SimpleFluidContent.copyOf(fluid.copyWithAmount(1)));
        return stack;
    }

    @Override
    public boolean usesCreateItemLogistics() {
        return false;
    }

    @Override
    public int stock(UUID network, ItemStack filter) {
        return RepackagedFluidStock.stock(network, getFilterFluid(filter));
    }

    @Override
    public int promised(UUID network, ItemStack filter, int expiry) {
        return RepackagedFluidStock.promised(network, getFilterFluid(filter), expiry);
    }

    @Override
    public void addPromise(UUID network, ItemStack filter, int amount) {
        RepackagedFluidStock.addPromise(network, getFilterFluid(filter), amount);
    }

    @Override
    public void forceClear(UUID network, ItemStack filter) {
        RepackagedFluidStock.forceClear(network, getFilterFluid(filter));
    }

    @Override
    public boolean dispatch(UUID network, ItemStack filter, int amount, String address,
                            int orderId, int linkIndex, boolean finalLink) {
        return RepackagedFluidStock.dispatchWithOrderId(network, getFilterFluid(filter).copyWithAmount(amount),
                address, orderId, linkIndex, finalLink);
    }

    @Override
    public void addControllerPromise(UUID network, ItemStack filter, int amount, String ownerKey, String address) {
        RepackagedFluidStock.addPromise(network, getFilterFluid(filter), amount, ownerKey, address);
    }

    @Override
    public int ownedPromises(UUID network, String ownerKey, long gameTime) {
        return RepackagedFluidStock.owned(network, ownerKey, gameTime);
    }

    @Override
    public int addressPromises(UUID network, String address, long gameTime) {
        return RepackagedFluidStock.address(network, address, gameTime);
    }

    @Override
    public void onPromiseAdded(UUID network, String ownerKey, String address, long gameTime) {
        RepackagedFluidStock.onAdded(network, ownerKey, address, gameTime);
    }
}
