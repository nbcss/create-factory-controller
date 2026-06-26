package io.github.nbcss.createfactorycontroller.content.compat.fluids;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.UUID;

/**
 * A mod-specific way to recognise and build a "fluid filter" — the virtual item, carrying a fluid, that a fluid
 * logistics addon (CreateFluidLogistic, CreateFluid, …) puts in a factory-panel filter slot. Each implementation
 * references exactly one addon's classes and is only instantiated when that addon is loaded, so the dispatcher
 * ({@link FluidCompat}) can support whichever addon(s) are present without hard-linking any of them.
 *
 * <p>All addons share the same integration architecture (a virtual fluid item riding Create's item logistics in
 * millibuckets), so only this thin recognise/build seam differs between them; everything else in the mod is
 * addon-agnostic.</p>
 */
public interface FluidFilterProvider {

    /** Whether {@code stack} is THIS addon's fluid filter (its virtual item, carrying a non-empty fluid). */
    boolean isFluidFilter(ItemStack stack);

    /** The fluid in {@code stack} if it is THIS addon's filter, else {@link FluidStack#EMPTY}. */
    FluidStack getFilterFluid(ItemStack stack);

    /** Builds a fluid filter for {@code fluid} using THIS addon's representation. */
    ItemStack makeFluidFilter(FluidStack fluid);

    /** Whether this provider's filter rides Create's item logistics and can be used by item gauges. */
    default boolean usesCreateItemLogistics() {
        return true;
    }

    default int stock(UUID network, ItemStack filter) {
        return LogisticsManager.getStockOf(network, filter, null);
    }

    default int promised(UUID network, ItemStack filter, int expiry) {
        RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(network);
        return promises == null ? 0 : promises.getTotalPromisedAndRemoveExpired(filter, expiry);
    }

    default void addPromise(UUID network, ItemStack filter, int amount) {
        RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(network);
        if (promises != null) promises.add(new com.simibubi.create.content.logistics.packagerLink.RequestPromise(
                new BigItemStack(filter.copy(), amount)));
    }

    default void forceClear(UUID network, ItemStack filter) {
        RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(network);
        if (promises != null) promises.forceClear(filter);
    }

    default boolean dispatch(UUID network, ItemStack filter, int amount, String address,
                             int orderId, int linkIndex, boolean finalLink) {
        PackageOrderWithCrafts order = PackageOrderWithCrafts.simple(List.of(new BigItemStack(filter.copy(), amount)));
        return LogisticsManager.broadcastPackageRequest(network, RequestType.RESTOCK, order, null, address);
    }
}
