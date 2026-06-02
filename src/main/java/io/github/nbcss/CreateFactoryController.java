package io.github.nbcss;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllCreativeModeTabs;
import io.github.nbcss.content.factorycontroller.*;
import io.github.nbcss.content.factorycontroller.gui.FactoryControllerScreen;
import io.github.nbcss.content.factorycontroller.packet.*;
import net.minecraft.core.registries.Registries;
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
import org.slf4j.Logger;

@Mod(CreateFactoryController.MODID)
public class CreateFactoryController {

    public static final String MODID = "createfactorycontroller";
    public static final Logger LOGGER = LogUtils.getLogger();

    // ── Blocks ─────────────────────────────────────────────────────────────
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredBlock<Block> FACTORY_CONTROLLER =
        BLOCKS.register("factory_controller", () ->
            // Mirror Create's brass casing: stone strength (1.5/6.0), wood sound, requires the correct
            // tool — and noOcclusion so it renders as a transparent, non-full block. The axe/pickaxe
            // tool tags (mineable/axe + mineable/pickaxe, see data tags) let a wooden pickaxe OR axe mine it.
            new FactoryControllerBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.TERRACOTTA_YELLOW)
                .strength(3.0f, 6.0f)
                .sound(SoundType.WOOD)
                .requiresCorrectToolForDrops()
                .noOcclusion()));

    // ── Items ──────────────────────────────────────────────────────────────
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> FACTORY_CONTROLLER_ITEM =
        ITEMS.registerSimpleBlockItem("factory_controller", FACTORY_CONTROLLER);

    // ── Block Entity Types ─────────────────────────────────────────────────
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FactoryControllerBlockEntity>> FACTORY_CONTROLLER_BE =
        BLOCK_ENTITY_TYPES.register("factory_controller", () ->
            BlockEntityType.Builder.of(FactoryControllerBlockEntity::new, FACTORY_CONTROLLER.get()).build(null));

    // ── Menu Types ─────────────────────────────────────────────────────────
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
        DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredHolder<MenuType<?>, MenuType<FactoryControllerMenu>> FACTORY_CONTROLLER_MENU =
        MENU_TYPES.register("factory_controller", () ->
            IMenuTypeExtension.create(
                (syncId, inv, buf) -> new FactoryControllerMenu(syncId, inv, buf)));

    // ── Constructor ────────────────────────────────────────────────────────
    public CreateFactoryController(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        MENU_TYPES.register(modEventBus);

        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::addCreativeTabContents);

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
        registrar.playToServer(ConfigureGaugePacket.TYPE, ConfigureGaugePacket.STREAM_CODEC, ConfigureGaugePacket::handle);
        registrar.playToServer(AddConnectionPacket.TYPE, AddConnectionPacket.STREAM_CODEC, AddConnectionPacket::handle);
        registrar.playToServer(RemoveConnectionPacket.TYPE, RemoveConnectionPacket.STREAM_CODEC, RemoveConnectionPacket::handle);
        registrar.playToServer(CycleArrowBendPacket.TYPE, CycleArrowBendPacket.STREAM_CODEC, CycleArrowBendPacket::handle);
        registrar.playToClient(SyncPanelStatePacket.TYPE, SyncPanelStatePacket.STREAM_CODEC, SyncPanelStatePacket::handle);
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(FACTORY_CONTROLLER_MENU.get(), FactoryControllerScreen::new);
    }

    private void registerShaders(RegisterShadersEvent event) {
        TiledSpriteRenderer.registerShaders(event);
    }
}
