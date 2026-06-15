package io.github.nbcss.content.factorycontroller.production;

import java.util.ArrayList;
import java.util.List;

/** Client-side cache of the open production orders for the keeper currently being viewed. */
public final class ProductionOrdersClient {

    private static volatile List<ProductionOrderView> orders = List.of();
    /** Wall-clock time the current snapshot arrived, so the mm:ss timer advances smoothly between 1s re-syncs. */
    private static volatile long receivedAtMs = 0L;

    private ProductionOrdersClient() {}

    public static void update(List<ProductionOrderView> views) {
        orders = views;
        receivedAtMs = System.currentTimeMillis();
    }

    public static List<ProductionOrderView> get() {
        return orders;
    }

    /** The order's age in ticks, extrapolated from the last sync so the timer keeps counting between syncs. */
    public static int ageTicksNow(ProductionOrderView view) {
        long elapsedMs = Math.max(0, System.currentTimeMillis() - receivedAtMs);
        return view.ageTicks() + (int) (elapsedMs / 50);   // 50ms per tick
    }

    /** Optimistically drop an order locally (e.g. right after the player hits its cancel button). */
    public static void removeLocally(int orderId) {
        List<ProductionOrderView> next = new ArrayList<>(orders);
        next.removeIf(o -> o.orderId() == orderId);
        orders = next;
    }
}
