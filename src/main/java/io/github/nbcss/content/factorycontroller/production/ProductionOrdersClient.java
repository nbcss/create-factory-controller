package io.github.nbcss.content.factorycontroller.production;

import java.util.List;

/** Client-side cache of the open promise orders for the keeper currently being viewed. */
public final class ProductionOrdersClient {

    private static volatile List<ProductionOrderView> orders = List.of();

    private ProductionOrdersClient() {}

    public static void update(List<ProductionOrderView> views) {
        orders = views;
    }

    public static List<ProductionOrderView> get() {
        return orders;
    }
}
