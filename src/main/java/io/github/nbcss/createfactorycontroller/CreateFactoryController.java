package io.github.nbcss.createfactorycontroller;

import com.simibubi.create.AllCreativeModeTabs;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlock;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.gui.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.item.ProductionPatternItem;
import io.github.nbcss.createfactorycontroller.content.item.ProductionTarget;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrderManager;
import io.github.nbcss.createfactorycontroller.content.packet.*;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.Item.Properties;
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
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
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
    public static final DeferredItem<io.github.nbcss.createfactorycontroller.content.item.FactoryControllerBlockItem> FACTORY_CONTROLLER_ITEM =
        ITEMS.register("factory_controller", () ->
            new io.github.nbcss.createfactorycontroller.content.item.FactoryControllerBlockItem(
                FACTORY_CONTROLLER.get(), new Properties()));

    /** Virtual, unobtainable Promise Blueprint — intentionally NOT added to any creative tab. */
    public static final DeferredItem<ProductionPatternItem> PRODUCTION_PATTERN =
        ITEMS.register("production_pattern", () ->
            new ProductionPatternItem(new Properties()));

    /** Generic, unobtainable fluid-filter token: an item carrying a {@link #FLUID_CONTENT} fluid, used as a gauge's
     *  filter so the entire item-filter pipeline (rendering, label, set-item / recipe screens) is reused for fluid
     *  gauges. Addon-agnostic — unlike the CFL/CreateFluid wrapper items it rides no item logistics (the fluid stock
     *  backend reads its fluid separately). NOT in any creative tab; only ever drawn as the fluid itself.
     *  <p>Registered ONLY when Create: Repackaged is installed (the only source of fluid gauges) — see the
     *  constructor — so it stays out of the registry on a plain Create install. {@code null} when absent.</p> */
    @org.jetbrains.annotations.Nullable
    public static DeferredItem<net.minecraft.world.item.Item> FLUID_FILTER;

    // ── Data Components ──────────────────────────────────────────────────────
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ProductionTarget>> PRODUCTION_TARGET =
        DATA_COMPONENTS.register("production_target", () ->
            DataComponentType.<ProductionTarget>builder()
                .persistent(ProductionTarget.CODEC)
                .networkSynchronized(ProductionTarget.STREAM_CODEC)
                .build());

    /** Minimal board setup (connections / request amounts / modes) carried by a broken controller item so it
     *  restores on placement. Holds the stripped-down BE NBT (no runtime/bulb state, no patternId). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.nbt.CompoundTag>> CONTROLLER_SETUP =
        DATA_COMPONENTS.register("controller_setup", () ->
            DataComponentType.<net.minecraft.nbt.CompoundTag>builder()
                .persistent(net.minecraft.nbt.CompoundTag.CODEC)
                .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.COMPOUND_TAG)
                .build());

    /** Marker placed on an ignore-data gauge's request promise so the promise queue clears it by item type
     *  (fuzzy), not exact components — the produced output may arrive as any data-variant of the item.
     *  Used only on the internal promise stack; see {@code RequestPromiseQueueMixin}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> FUZZY_PROMISE =
        DATA_COMPONENTS.register("fuzzy_promise", () ->
            DataComponentType.<Boolean>builder()
                .persistent(com.mojang.serialization.Codec.BOOL)
                .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL)
                .build());

    /** The fluid carried by a {@link #FLUID_FILTER} token. Uses {@code SimpleFluidContent} (not a raw {@code FluidStack},
     *  which can't be a data component — it has no equals/hashCode). Registered alongside it (only with Repackaged). */
    @org.jetbrains.annotations.Nullable
    public static DeferredHolder<DataComponentType<?>, DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>> FLUID_CONTENT;

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
            ResourceLocation.fromNamespaceAndPath(MODID, "factory_controller.close")));;
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
        MENU_TYPES.register("factory_controller", () ->
            IMenuTypeExtension.create(
                (syncId, inv, buf) -> new FactoryControllerMenu(syncId, inv, buf)));

    // ── Constructor ────────────────────────────────────────────────────────
    public CreateFactoryController(IEventBus modEventBus, ModContainer modContainer) {
        // Create: Repackaged compat — register the generic fluid-filter token + its fluid component ONLY when the
        // addon is present (its fluid gauges are the only thing that uses them). Done here (not in a field
        // initializer) because ModList is ready by the mod constructor, and before the DeferredRegisters are attached
        // to the bus so the entries make it into the RegisterEvent.
        if (io.github.nbcss.createfactorycontroller.content.compat.RepackagedCompat.isLoaded()) {
            FLUID_FILTER = ITEMS.register("fluid_filter", () -> new net.minecraft.world.item.Item(new Properties()));
            FLUID_CONTENT = DATA_COMPONENTS.register("fluid_content", () ->
                DataComponentType.<net.neoforged.neoforge.fluids.SimpleFluidContent>builder()
                    .persistent(net.neoforged.neoforge.fluids.SimpleFluidContent.CODEC)
                    .networkSynchronized(net.neoforged.neoforge.fluids.SimpleFluidContent.STREAM_CODEC)
                    .build());
        }

        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);

        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::addCreativeTabContents);

        // Drive the production-order manager once per server tick (independent of any loaded controller/keeper).
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) ->
                ProductionOrderManager.get(event.getServer()).tick(event.getServer()));

        // Clear the static orderable-gauge index on server stop so it never bleeds across worlds in one JVM.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
            (net.neoforged.neoforge.event.server.ServerStoppedEvent event) ->
                io.github.nbcss.createfactorycontroller.content.production.OrderableGaugeRegistry.clear());

        // Server config (synced to clients): the per-controller component cap.
        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::registerScreens);
            modEventBus.addListener(this::registerShaders);
        }
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey())
            event.accept(FACTORY_CONTROLLER_ITEM);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MODID);
        registrar.playToServer(AttachComponentPacket.TYPE, AttachComponentPacket.STREAM_CODEC, AttachComponentPacket::handle);
        registrar.playToServer(RemoveComponentPacket.TYPE, RemoveComponentPacket.STREAM_CODEC, RemoveComponentPacket::handle);
        registrar.playToServer(MoveComponentPacket.TYPE, MoveComponentPacket.STREAM_CODEC, MoveComponentPacket::handle);
        registrar.playToServer(BatchMoveComponentPacket.TYPE, BatchMoveComponentPacket.STREAM_CODEC, BatchMoveComponentPacket::handle);
        registrar.playToServer(GaugeSetItemPacket.TYPE, GaugeSetItemPacket.STREAM_CODEC, GaugeSetItemPacket::handle);
        registrar.playToServer(ConfigureRecipePacket.TYPE, ConfigureRecipePacket.STREAM_CODEC, ConfigureRecipePacket::handle);
        registrar.playToServer(AddConnectionPacket.TYPE, AddConnectionPacket.STREAM_CODEC, AddConnectionPacket::handle);
        registrar.playToServer(RemoveConnectionPacket.TYPE, RemoveConnectionPacket.STREAM_CODEC, RemoveConnectionPacket::handle);
        registrar.playToServer(DisconnectIngredientPacket.TYPE, DisconnectIngredientPacket.STREAM_CODEC, DisconnectIngredientPacket::handle);
        registrar.playToServer(DisconnectLinksPacket.TYPE, DisconnectLinksPacket.STREAM_CODEC, DisconnectLinksPacket::handle);
        registrar.playToServer(CycleArrowModePacket.TYPE, CycleArrowModePacket.STREAM_CODEC, CycleArrowModePacket::handle);
        registrar.playToServer(CycleOperationModePacket.TYPE, CycleOperationModePacket.STREAM_CODEC, CycleOperationModePacket::handle);
        registrar.playToServer(ReverseConnectionPacket.TYPE, ReverseConnectionPacket.STREAM_CODEC, ReverseConnectionPacket::handle);
        registrar.playToServer(ConfigureLogicalTubePacket.TYPE, ConfigureLogicalTubePacket.STREAM_CODEC, ConfigureLogicalTubePacket::handle);
        registrar.playToServer(ConfigureRedstoneLinkPacket.TYPE, ConfigureRedstoneLinkPacket.STREAM_CODEC, ConfigureRedstoneLinkPacket::handle);
        registrar.playToServer(RetuneCarriedPacket.TYPE, RetuneCarriedPacket.STREAM_CODEC, RetuneCarriedPacket::handle);
        registrar.playToServer(RenameControllerPacket.TYPE, RenameControllerPacket.STREAM_CODEC, RenameControllerPacket::handle);
        registrar.playToServer(RequestProductionOrdersPacket.TYPE, RequestProductionOrdersPacket.STREAM_CODEC, RequestProductionOrdersPacket::handle);
        registrar.playToServer(RemoveProductionOrderPacket.TYPE, RemoveProductionOrderPacket.STREAM_CODEC, RemoveProductionOrderPacket::handle);
        registrar.playToServer(RequestIngredientCheckPacket.TYPE, RequestIngredientCheckPacket.STREAM_CODEC, RequestIngredientCheckPacket::handle);
        registrar.playToClient(SyncPanelStatePacket.TYPE, SyncPanelStatePacket.STREAM_CODEC, SyncPanelStatePacket::handle);
        registrar.playToClient(SyncProductionOrdersPacket.TYPE, SyncProductionOrdersPacket.STREAM_CODEC, SyncProductionOrdersPacket::handle);
        registrar.playToClient(IngredientCheckResultPacket.TYPE, IngredientCheckResultPacket.STREAM_CODEC, IngredientCheckResultPacket::handle);
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(FACTORY_CONTROLLER_MENU.get(), FactoryControllerScreen::new);
    }

    private void registerShaders(RegisterShadersEvent event) {
        TiledSpriteRenderer.registerShaders(event);
    }
}
