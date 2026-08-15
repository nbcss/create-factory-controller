package io.github.nbcss.logisticscontrol;

import com.simibubi.create.AllCreativeModeTabs;
import io.github.nbcss.logisticscontrol.content.block.FilterLinkBlock;
import io.github.nbcss.logisticscontrol.content.block.FilterLinkBlockEntity;
import io.github.nbcss.logisticscontrol.content.helper.NonCraftGroups;
import io.github.nbcss.logisticscontrol.content.helper.PackageFilter;
import io.github.nbcss.logisticscontrol.content.item.FilterLinkBlockItem;
import io.github.nbcss.logisticscontrol.content.packet.NetworkHandler;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CreateLogisticsControl.MODID)
public class CreateLogisticsControl {

    public static final String MODID = "createlogisticscontrol";

    // ── Blocks ─────────────────────────────────────────────────────────────
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredBlock<FilterLinkBlock> FILTER_LINK =
        BLOCKS.register("filter_link", () ->
            new FilterLinkBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.PODZOL)
                .strength(1.5f)
                .sound(SoundType.WOOD)
                .noOcclusion()));

    // ── Items ──────────────────────────────────────────────────────────────
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredItem<FilterLinkBlockItem> FILTER_LINK_ITEM =
        ITEMS.register("filter_link", () ->
            new FilterLinkBlockItem(FILTER_LINK.get(), new Item.Properties()));

    // ── Data Components ─────────────────────────────────────────────────────
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
        DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PackageFilter>> PACKAGE_FILTER =
        DATA_COMPONENTS.register("package_filter", () ->
            DataComponentType.<PackageFilter>builder()
                .persistent(PackageFilter.CODEC)
                .networkSynchronized(PackageFilter.STREAM_CODEC)
                .build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NonCraftGroups>> NONCRAFT_GROUPS =
        DATA_COMPONENTS.register("noncraft_groups", () ->
            DataComponentType.<NonCraftGroups>builder()
                .persistent(NonCraftGroups.CODEC)
                .networkSynchronized(NonCraftGroups.STREAM_CODEC)
                .build());

    // ── Block Entity Types ─────────────────────────────────────────────────
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FilterLinkBlockEntity>> FILTER_LINK_BE =
        BLOCK_ENTITY_TYPES.register("filter_link", () ->
            BlockEntityType.Builder.of(FilterLinkBlockEntity::new, FILTER_LINK.get()).build(null));

    public CreateLogisticsControl(IEventBus modEventBus, ModContainer modContainer) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);

        modEventBus.addListener((RegisterPayloadHandlersEvent event) ->
            NetworkHandler.register(event.registrar(MODID)));
        modEventBus.addListener(this::addCreativeTabContents);

        modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
    }

    private void addCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey())
            event.accept(FILTER_LINK_ITEM);
    }
}
