package io.github.nbcss.createfactorycontroller.content.component.gauge;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.compat.gas.GasCompat;
import io.github.nbcss.createfactorycontroller.content.component.SyncCodecs;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.UUID;

/**
 * Create: Crafted Beginning's Gas Factory Gauge ({@code createcraftedbeginning:gas_factory_gauge}). Its gas key is a
 * virtual item counted in GU by gas packagers, so it rides item logistics like a base gauge.
 */
public class CcbGasGaugeBehaviour extends VirtualGaugeBehaviour {

    public static final ResourceLocation ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(GasCompat.MODID, "gas_factory_gauge");

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/gas_gauge");
    private static final ResourceLocation FRONT_TEXTURE = TEXTURE.withSuffix("/front");

    /** Whether CCB's gas factory gauge item is present. */
    public static boolean isAvailable() {
        return BuiltInRegistries.ITEM.containsKey(ITEM_ID) && GasCompat.isLoaded();
    }

    private static final GaugeFilterResolver FILTER_RESOLVER = new GaugeFilterResolver() {
        @Override public boolean acceptsFilter(ItemStack filter) { return filter.isEmpty() || GasCompat.isGasFilter(filter); }
        @Override public boolean supportsIgnoreData() { return false; }
        @Override public boolean acceptsItemDrop() { return false; }
        @Override public boolean acceptsFluidDrop() { return false; }
        @Override public boolean acceptsGas() { return true; }

        @Override
        public ItemStack fromCarried(ItemStack carried, int mouseButton) {
            return GasCompat.gasFromCarried(carried);
        }

        @Override
        public ItemStack fromFluid(FluidStack fluid) {
            return ItemStack.EMPTY;
        }
    };

    public static final VirtualComponentBehaviour.Type TYPE = new VirtualComponentBehaviour.Type() {

        @Override
        public String id() {
            return "GAS_FACTORY_GAUGE";
        }

        @Override
        public String order() {
            return "fg";
        }

        @Override
        public List<ResourceLocation> items() {
            return List.of(ITEM_ID);
        }

        @Override public int color() { return 0xBC8EED; }

        @Override
        public boolean isRequireNetwork() {
            return true;
        }

        @Override
        public VirtualComponentBehaviour create(FactoryControllerBlockEntity controller,
                                                VirtualComponentPosition pos,
                                                Item item,
                                                UUID networkId) {
            return new CcbGasGaugeBehaviour(controller, pos, networkId, item);
        }

        @Override
        public VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                                 CompoundTag tag,
                                                 HolderLookup.Provider registries) {
            VirtualComponentPosition pos = VirtualComponentPosition.fromNBT(tag.getCompound("Pos"));
            Item gaugeItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(tag.getString("Item")));
            CcbGasGaugeBehaviour b = new CcbGasGaugeBehaviour(controller, pos, tag.getUUID("Network"), gaugeItem);
            b.readGaugeNBT(tag, registries);
            return b;
        }

        @Override
        public VirtualComponentBehaviour fromClient(net.minecraft.network.RegistryFriendlyByteBuf buf) {
            VirtualComponentPosition pos = SyncCodecs.readPos(buf);
            Item item = BuiltInRegistries.ITEM.get(buf.readResourceLocation());
            CcbGasGaugeBehaviour g = new CcbGasGaugeBehaviour(null, pos, buf.readUUID(), item);
            g.readClientBody(buf);
            return g;
        }
    };

    public CcbGasGaugeBehaviour(FactoryControllerBlockEntity controller,
                                VirtualComponentPosition position,
                                UUID networkId,
                                Item gaugeItem) {
        super(controller, position, networkId, gaugeItem);
    }

    @Override public GaugeFilterResolver filterResolver() { return FILTER_RESOLVER; }
    // logisticsControl() intentionally NOT overridden -> inherits ITEM_LOGISTICS (a gas key rides item logistics).

    @Override
    protected VirtualComponentBehaviour.Type componentType() {
        return TYPE;
    }

    @Override
    public ResourceLocation getFrontTexture() {
        return FRONT_TEXTURE;
    }

    @Override
    public ResourceLocation getTexture() {
        return TEXTURE;
    }

    @Override public int getColor() { return TYPE.color(); }
}
