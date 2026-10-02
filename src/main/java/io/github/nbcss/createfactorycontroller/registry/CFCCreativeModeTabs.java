package io.github.nbcss.createfactorycontroller.registry;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CFCCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateFactoryController.MODID);

    private CFCCreativeModeTabs() {}

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register("factory_controller", CFCCreativeModeTabs::createTab);
        CREATIVE_MODE_TABS.register(eventBus);
        eventBus.addListener(CFCCreativeModeTabs::addToVanillaTabs);
    }

    private static void addToVanillaTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.INGREDIENTS) return;
        ItemStack cutAmethyst = new ItemStack(CFCItems.CUT_AMETHYST.get());
        try {
            event.insertAfter(new ItemStack(Items.AMETHYST_SHARD), cutAmethyst,
                    CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } catch (IllegalArgumentException e) {
            event.accept(cutAmethyst);
        }
    }

    private static CreativeModeTab createTab() {
        return CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.createfactorycontroller.factory_controller"))
                .icon(() -> new ItemStack(CFCItems.FACTORY_CONTROLLER.get()))
                .displayItems((parameters, output) -> {
                    output.accept(CFCItems.FACTORY_CONTROLLER.get());
                    output.accept(CFCItems.FACTORY_CONTROLLER_TERMINAL.get());
                    output.accept(CFCItems.ARITHMETIC_TUBE.get());
                })
                .build();
    }
}
