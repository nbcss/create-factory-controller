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

    // ── Promise-limit support (dedicated fluid backends only) ───────────────────
    // Whether this backend's promises can be counted per-gauge / per-address (the promise-limit feature). Only a
    // dedicated fluid backend (Repackaged) whose promises don't ride Create's item queue overrides these; the default
    // is "unsupported" (a gauge on this backend behaves as if no limit is set), which is correct for the item-queue
    // backends whose promises are already counted on the item side.

    /** Promises the fluid {@code filter} while tagging it with the minting gauge/address, so it counts toward the
     *  promise limit. Default: an untagged promise (uncounted). */
    default void addControllerPromise(UUID network, ItemStack filter, int amount, String ownerKey, String address) {
        addPromise(network, filter, amount);
    }

    /** Active tagged promises minted by gauge {@code ownerKey} on {@code network} this tick (0 if unsupported). */
    default int ownedPromises(UUID network, String ownerKey, long gameTime) {
        return 0;
    }

    /** Active tagged promises targeting {@code address} on {@code network} this tick (0 if unsupported). */
    default int addressPromises(UUID network, String address, long gameTime) {
        return 0;
    }

    /** Folds a just-dispatched promise into this tick's count cache (no-op if unsupported). */
    default void onPromiseAdded(UUID network, String ownerKey, String address, long gameTime) {
    }
}
