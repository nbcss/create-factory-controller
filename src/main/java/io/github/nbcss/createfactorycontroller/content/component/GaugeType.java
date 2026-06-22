package io.github.nbcss.createfactorycontroller.content.component;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * The stock type a {@link VirtualGaugeBehaviour} manages, derived from its gauge item id (see
 * {@code ComponentRegistry#typeOf}) rather than stored, so it costs no extra NBT/sync. {@link #ITEM} is Create's
 * Factory Gauge; {@link #FLUID} is "Create: Repackaged"'s Fluid Gauge, whose promises route through Deployer's fluid
 * system ({@link FluidCompat}) instead of Create's item promise queue. The type also gates a gauge's item-only
 * features (mechanical crafting and {@code ignoreData}) — see {@code VirtualGaugeBehaviour}.
 */
public enum GaugeType {
    ITEM,
    FLUID{
        @Override
        public void forceClearPromise(UUID networkId, ItemStack filter) {
            FluidCompat.repackagedForceClearFluid(networkId, filter);
        }

        @Override
        public void addPromise(UUID networkId, ItemStack filter, boolean ignoreData, int amount) {
            FluidCompat.repackagedAddFluidPromise(networkId, filter, amount);
        }
    };

    public void forceClearPromise(UUID networkId, ItemStack filter) {
        RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(networkId);
        if (promises != null) promises.forceClear(filter);
    }

    public void addPromise(UUID networkId, ItemStack filter, boolean ignoreData, int amount) {
        RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(networkId);
        if (promises != null) {
            ItemStack promiseStack = filter.copy();
            // Ignore-data: mark the promise so the queue clears it when ANY variant of the item type arrives
            // (the produced output may carry data the pure-form filter doesn't). See RequestPromiseQueueMixin.
            if (ignoreData) promiseStack.set(CreateFactoryController.FUZZY_PROMISE.get(), true);
            promises.add(new RequestPromise(new BigItemStack(promiseStack, amount)));
        }
    }
}
