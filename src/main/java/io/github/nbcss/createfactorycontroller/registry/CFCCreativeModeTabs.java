package io.github.nbcss.createfactorycontroller.registry;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CFCCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateFactoryController.MODID);

    private CFCCreativeModeTabs() {}

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register("factory_controller", CFCCreativeModeTabs::createTab);
        CREATIVE_MODE_TABS.register(eventBus);
    }

    private static CreativeModeTab createTab() {
        return CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.createfactorycontroller.factory_controller"))
                .icon(() -> new ItemStack(CFCItems.FACTORY_CONTROLLER.get()))
                .displayItems((parameters, output) -> {
                    output.accept(CFCItems.FACTORY_CONTROLLER.get());
                    output.accept(CFCItems.ARITHMETIC_TUBE.get());
                    output.accept(CFCItems.CUT_AMETHYST.get());
                })
                .build();
    }
}
