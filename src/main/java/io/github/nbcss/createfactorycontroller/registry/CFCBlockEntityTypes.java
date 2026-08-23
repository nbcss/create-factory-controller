package io.github.nbcss.createfactorycontroller.registry;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class CFCBlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateFactoryController.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FactoryControllerBlockEntity>>
            FACTORY_CONTROLLER = BLOCK_ENTITY_TYPES.register("factory_controller", () ->
                    BlockEntityType.Builder.of(FactoryControllerBlockEntity::new,
                            CFCBlocks.FACTORY_CONTROLLER.get()).build(null));

    private CFCBlockEntityTypes() {}

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
