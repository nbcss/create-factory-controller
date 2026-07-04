package io.github.nbcss.createfactorycontroller.content.production;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import io.github.nbcss.createfactorycontroller.content.item.ProductionPatternItem;
import io.github.nbcss.createfactorycontroller.content.item.ProductionTarget;

import java.util.List;
import java.util.UUID;


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
            ProductionTarget target = new ProductionTarget(e.network(), e.gaugeId(), e.display(),
                e.ingredients(), e.address());
            copy.add(new BigItemStack(ProductionPatternItem.of(target), BigItemStack.INF));
        }
        return copy;
    }
}
