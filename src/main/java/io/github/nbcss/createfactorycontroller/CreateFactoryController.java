package io.github.nbcss.createfactorycontroller;

import com.mojang.serialization.Codec;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import io.github.nbcss.createfactorycontroller.content.helper.ArrangementUnpackingHandler;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlock;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.compat.RepackagedCompat;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.display.FactoryControllerDisplaySource;
import io.github.nbcss.createfactorycontroller.content.gui.screen.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.item.FactoryControllerBlockItem;
import io.github.nbcss.createfactorycontroller.content.item.ProductionPatternItem;
import io.github.nbcss.createfactorycontroller.content.item.ProductionTarget;
import io.github.nbcss.createfactorycontroller.content.packet.NetworkHandler;
import io.github.nbcss.createfactorycontroller.content.production.OrderableGaugeRegistry;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrderManager;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
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
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CreateFactoryController.MODID)
public class CreateFactoryController {

    public static final String MODID = "createfactorycontroller";

    // ── Blocks ─────────────────────────────────────────────────────────────
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredBlock<Block> FACTORY_CONTROLLER =
        BLOCKS.register("factory_controller", () ->
            new FactoryControllerBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.TERRACOTTA_YELLOW)
                .strength(300.0f, 1200.0f)
                .sound(SoundType.WOOD)
                .requiresCorrectToolForDrops()
                .noOcclusion()));

    // ── Items ──────────────────────────────────────────────────────────────
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<FactoryControllerBlockItem> FACTORY_CONTROLLER_ITEM =
        ITEMS.register("factory_controller", () ->
            new FactoryControllerBlockItem(FACTORY_CONTROLLER.get(), new Item.Properties()));

    /** Unobtainable, just for Stock Keeper GUI */
    public static final DeferredItem<ProductionPatternItem> PRODUCTION_PATTERN =
        ITEMS.register("production_pattern", () -> new ProductionPatternItem(new Item.Properties()));

    /** Unobtainable sentinel a Custom-Arrangement order ends with — its namespaced identity is what flags the
     *  package for positional unpacking (see {@code ArrangementUnpackingHandler}). Never pulled or shipped. */
    public static final DeferredItem<Item> ARRANGEMENT_MARKER_ITEM =
        ITEMS.register("arrangement_marker", () -> new Item(new Item.Properties()));

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

    // ── Block Entity Types ─────────────────────────────────────────────────
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FactoryControllerBlockEntity>> FACTORY_CONTROLLER_BE =
        BLOCK_ENTITY_TYPES.register("factory_controller", () ->
            BlockEntityType.Builder.of(FactoryControllerBlockEntity::new, FACTORY_CONTROLLER.get()).build(null));

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
        RepackagedCompat.register(ITEMS, DATA_COMPONENTS);

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        DISPLAY_SOURCES.register(modEventBus);

        modEventBus.addListener((RegisterPayloadHandlersEvent event) ->
            NetworkHandler.register(event.registrar(MODID)));
        modEventBus.addListener(this::addCreativeTabContents);
        modEventBus.addListener(this::commonSetup);

        ProductionOrderManager.registerEvents();
        OrderableGaugeRegistry.registerEvents();

        ArrangementUnpackingHandler.register();

        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::registerScreens);
            modEventBus.addListener(this::registerShaders);
        }

        Connection.Type.registerConnections();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DisplaySource.BY_BLOCK.add(FACTORY_CONTROLLER.get(), FACTORY_CONTROLLER_PENDING_ORDERS.get());
            FluidCompat.onRegistriesComplete();
        });
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey())
            event.accept(FACTORY_CONTROLLER_ITEM);
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(FACTORY_CONTROLLER_MENU.get(), FactoryControllerScreen::new);
    }

    private void registerShaders(RegisterShadersEvent event) {
        TiledSpriteRenderer.registerShaders(event);
    }
}
