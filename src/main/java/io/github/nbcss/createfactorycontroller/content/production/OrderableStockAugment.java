package io.github.nbcss.createfactorycontroller.content.production;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import io.github.nbcss.createfactorycontroller.content.item.ProductionPatternItem;
import io.github.nbcss.createfactorycontroller.content.item.ProductionTarget;

import java.util.List;
import java.util.UUID;

/**
 * Augments a Stock Keeper's stock summary with one infinite {@link ProductionPatternItem} stack per orderable gauge
 * on the keeper's network, so producible items show up in the request GUI alongside real stock. Shared by both the
 * periodic stock-response path and the initial GUI-open summary, so orderable items are present from the first frame.
 */
public final class OrderableStockAugment {

    private OrderableStockAugment() {}

    /**
     * Returns {@code base} unchanged when the network has no orderable gauges, otherwise a {@link InventorySummary#copy()
     * copy} with one {@code BigItemStack.INF} Production Blueprint per orderable gauge appended (the original cached
     * summary is never mutated).
     */
    public static InventorySummary augment(InventorySummary base, UUID network, long now) {
        List<OrderableGaugeRegistry.Entry> patterns = OrderableGaugeRegistry.patternFor(network, now);
        if (patterns.isEmpty()) return base;
        InventorySummary copy = base.copy();
        for (OrderableGaugeRegistry.Entry e : patterns) {
            ProductionTarget target = new ProductionTarget(e.network(), e.patternId(), e.display());
            copy.add(new BigItemStack(ProductionPatternItem.of(target), BigItemStack.INF));
        }
        return copy;
    }
}
