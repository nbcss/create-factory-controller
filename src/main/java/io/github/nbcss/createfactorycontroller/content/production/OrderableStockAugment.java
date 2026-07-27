package io.github.nbcss.createfactorycontroller.content.production;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import io.github.nbcss.createfactorycontroller.content.item.ProductionPatternItem;
import io.github.nbcss.createfactorycontroller.content.item.ProductionTarget;

import java.util.UUID;


public final class OrderableStockAugment {

    private OrderableStockAugment() {}

    /**
     * Appends one infinite Production Blueprint to {@code summary} for each orderable gauge on {@code network}
     * (none if there are none). Mutates in place: the single caller is the {@code createSummaryOfNetwork} loader
     * injection, which owns the freshly built summary before it is cached or read, so mutating it directly both
     * preserves the cache object's identity (the gauge refresh signal) and avoids a per-request copy.
     */
    public static void augmentInPlace(InventorySummary summary, UUID network, long now) {
        for (OrderableGaugeRegistry.Entry e : OrderableGaugeRegistry.patternFor(network, now)) {
            ProductionTarget target = new ProductionTarget(e.network(), e.gaugeId(), e.display(),
                e.ingredients(), e.address());
            summary.add(new BigItemStack(ProductionPatternItem.of(target), BigItemStack.INF));
        }
    }
}
