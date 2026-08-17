package io.github.nbcss.createfactorycontroller.content.compat.computercraft;

import dan200.computercraft.api.peripheral.PeripheralCapability;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import net.minecraft.core.Direction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Every reference to CC: Tweaked's API lives in this class and {@link FactoryControllerPeripheral}. Only reached
 * from {@link CreateFactoryController}'s constructor behind {@code CcTweakedCompat.isLoaded()}, so neither is ever
 * classloaded when CC: Tweaked isn't installed.
 */
public final class CcTweakedIntegration {

    private CcTweakedIntegration() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CcTweakedIntegration::registerCapabilities);
    }

    /** Attaches a read-only {@link FactoryControllerPeripheral} to every factory controller block entity.
     *  Registering a capability for our own {@code BlockEntityType} through CC: Tweaked's capability is the
     *  standard pattern for third-party peripherals (CC: Tweaked's own docs attach a peripheral to vanilla's
     *  brewing stand the same way). */
    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(),
            CreateFactoryController.FACTORY_CONTROLLER_BE.get(),
            (FactoryControllerBlockEntity controller, Direction side) -> new FactoryControllerPeripheral(controller));
    }
}
