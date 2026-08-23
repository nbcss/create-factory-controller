package io.github.nbcss.createfactorycontroller.registry;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.item.FactoryControllerBlockItem;
import io.github.nbcss.createfactorycontroller.content.item.ProductionPatternItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CFCItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CreateFactoryController.MODID);

    public static final DeferredItem<FactoryControllerBlockItem> FACTORY_CONTROLLER =
            ITEMS.register("factory_controller", () ->
                    new FactoryControllerBlockItem(CFCBlocks.FACTORY_CONTROLLER.get(), new Item.Properties()));

    public static final DeferredItem<Item> POLISHED_AMETHYST =
            ITEMS.register("polished_amethyst", () -> new Item(new Item.Properties()));

    public static final DeferredItem<Item> ARITHMETIC_TUBE =
            ITEMS.register("arithmetic_tube", () -> new Item(new Item.Properties()));

    public static final DeferredItem<ProductionPatternItem> PRODUCTION_PATTERN =
            ITEMS.register("production_pattern", () -> new ProductionPatternItem(new Item.Properties()));

    public static final DeferredItem<Item> ARRANGEMENT_MARKER =
            ITEMS.register("arrangement_marker", () -> new Item(new Item.Properties()));

    private CFCItems() {}

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
