package io.github.nbcss.createfactorycontroller;

import com.mojang.serialization.Codec;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import io.github.nbcss.createfactorycontroller.content.helper.ArrangementUnpackingHandler;
import io.github.nbcss.createfactorycontroller.content.helper.ConfigDataFixer;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.compat.RepackagedCompat;
import io.github.nbcss.createfactorycontroller.content.compat.computercraft.CcTweakedCompat;
import io.github.nbcss.createfactorycontroller.content.compat.computercraft.CcTweakedIntegration;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.displaylink.FactoryControllerDisplaySource;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.item.ProductionTarget;
import io.github.nbcss.createfactorycontroller.content.packet.NetworkHandler;
import io.github.nbcss.createfactorycontroller.content.production.OrderableGaugeRegistry;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrderManager;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import io.github.nbcss.createfactorycontroller.registry.CFCBlocks;
import io.github.nbcss.createfactorycontroller.registry.CFCBlockEntityTypes;
import io.github.nbcss.createfactorycontroller.registry.CFCCreativeModeTabs;
import io.github.nbcss.createfactorycontroller.registry.CFCItems;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CreateFactoryController.MODID)
public class CreateFactoryController {

    public static final String MODID = "createfactorycontroller";

    // ── Data Components ──────────────────────────────────────────────────────
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ProductionTarget>> PRODUCTION_TARGET =
        DATA_COMPONENTS.register("production_target", () ->
            DataComponentType.<ProductionTarget>builder()
                .persistent(ProductionTarget.CODEC)
                .networkSynchronized(ProductionTarget.STREAM_CODEC)
                .build());

    /** Minimal board setup carried by a broken controller item */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> CONTROLLER_SETUP =
        DATA_COMPONENTS.register("controller_setup", () ->
            DataComponentType.<CompoundTag>builder()
                .persistent(CompoundTag.CODEC)
                .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
                .build());

    /** Marker placed on an ignore-data gauge's request promise so the promise queue clears it by item type */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> FUZZY_PROMISE =
        DATA_COMPONENTS.register("fuzzy_promise", () ->
            DataComponentType.<Boolean>builder()
                .persistent(Codec.BOOL)
                .networkSynchronized(ByteBufCodecs.BOOL)
                .build());

    // ── Sound Events ───────────────────────────────────────────────────────
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
        DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    // Factory controller UI open/close
    public static final DeferredHolder<SoundEvent, SoundEvent> CONTROLLER_UI_OPEN =
        SOUND_EVENTS.register("factory_controller.open", () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MODID, "factory_controller.open")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CONTROLLER_UI_CLOSE =
        SOUND_EVENTS.register("factory_controller.close", () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MODID, "factory_controller.close")));
    // Virtual-gauge configuration overlay open/close
    public static final DeferredHolder<SoundEvent, SoundEvent> GAUGE_UI_OPEN =
        SOUND_EVENTS.register("gauge.open", () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MODID, "gauge.open")));
    public static final DeferredHolder<SoundEvent, SoundEvent> GAUGE_UI_CLOSE =
        SOUND_EVENTS.register("gauge.close", () -> SoundEvent.createVariableRangeEvent(
            ResourceLocation.fromNamespaceAndPath(MODID, "gauge.close")));

    // ── Menu Types ─────────────────────────────────────────────────────────
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<FactoryControllerMenu>> FACTORY_CONTROLLER_MENU =
        MENU_TYPES.register("factory_controller", () -> IMenuTypeExtension.create(FactoryControllerMenu::new));

    // ── Display Link sources (registered into Create's display-source registry) ──
    public static final DeferredRegister<DisplaySource> DISPLAY_SOURCES =
        DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, MODID);
    public static final DeferredHolder<DisplaySource, FactoryControllerDisplaySource> FACTORY_CONTROLLER_PENDING_ORDERS =
        DISPLAY_SOURCES.register("factory_controller_pending_orders", FactoryControllerDisplaySource::new);

    // ── Constructor ────────────────────────────────────────────────────────
    public CreateFactoryController(IEventBus modEventBus, ModContainer modContainer) {
        RepackagedCompat.register(CFCItems.ITEMS, DATA_COMPONENTS);

        CFCBlocks.register(modEventBus);
        CFCBlockEntityTypes.register(modEventBus);
        CFCItems.register(modEventBus);
        CFCCreativeModeTabs.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        DISPLAY_SOURCES.register(modEventBus);

        modEventBus.addListener((RegisterPayloadHandlersEvent event) ->
            NetworkHandler.register(event.registrar(MODID)));
        modEventBus.addListener(this::commonSetup);

        ProductionOrderManager.registerEvents();
        OrderableGaugeRegistry.registerEvents();

        ArrangementUnpackingHandler.register();

        // CC: Tweaked (soft dependency — a read-only peripheral on the factory controller; see CcTweakedCompat).
        if (CcTweakedCompat.isLoaded()) {
            CcTweakedIntegration.register(modEventBus);
        }

        modEventBus.addListener(ConfigDataFixer::migrate);
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::registerScreens);
            modEventBus.addListener(this::registerShaders);
        }

        Connection.Type.registerConnections();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DisplaySource.BY_BLOCK.add(CFCBlocks.FACTORY_CONTROLLER.get(), FACTORY_CONTROLLER_PENDING_ORDERS.get());
            FluidCompat.onRegistriesComplete();
        });
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(FACTORY_CONTROLLER_MENU.get(), FactoryControllerScreen::new);
    }

    private void registerShaders(RegisterShadersEvent event) {
        TiledSpriteRenderer.registerShaders(event);
    }
}
