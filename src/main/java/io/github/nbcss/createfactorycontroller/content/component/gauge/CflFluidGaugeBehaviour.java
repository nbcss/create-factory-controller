package io.github.nbcss.createfactorycontroller.content.component.gauge;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
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
 * The dedicated fluid gauge introduced by Create: Fluid Logistics 1.2.7+ ({@code fluidlogistics:fluid_factory_gauge}).
 */
public class CflFluidGaugeBehaviour extends VirtualGaugeBehaviour {

    /** CFL's dedicated fluid factory gauge item (1.2.7+). */
    public static final ResourceLocation ITEM_ID =
            ResourceLocation.fromNamespaceAndPath("fluidlogistics", "fluid_factory_gauge");
    /** CFL's fluid-filter carrier item — a gauge filter carrying this item id is a CFL item-logistics fluid filter. */
    public static final ResourceLocation FILTER_ITEM_ID =
            ResourceLocation.fromNamespaceAndPath("fluidlogistics", "compressed_storage_tank");

    private static final ResourceLocation FRONT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/fluid_gauge/front");

    /**
     * Whether CFL's dedicated fluid factory gauge item is present (⇒ CFL 1.2.7+).
     */
    public static boolean isAvailable() {
        return BuiltInRegistries.ITEM.containsKey(ITEM_ID);
    }

    private static final GaugeFilterResolver FILTER_RESOLVER = new GaugeFilterResolver() {
        @Override public boolean acceptsFilter(ItemStack filter) { return filter.isEmpty() || FluidCompat.isFluidFilter(filter); }
        @Override public boolean supportsIgnoreData() { return false; }
        @Override public boolean acceptsItemDrop() { return false; }
        @Override public boolean acceptsFluidDrop() { return true; }

        @Override
        public ItemStack fromCarried(ItemStack carried, int mouseButton) {
            FluidStack fluid = FluidCompat.fluidInContainer(carried);
            return fluid.isEmpty() ? ItemStack.EMPTY : FluidCompat.makeFluidFilter(fluid);
        }

        @Override
        public ItemStack fromFluid(FluidStack fluid) {
            return FluidCompat.makeFluidFilter(fluid);
        }
    };

    public static final VirtualComponentBehaviour.Type TYPE = new VirtualComponentBehaviour.Type(){

        @Override
        public String id() {
            return "FLUID_FACTORY_GAUGE";
        }

        @Override
        public List<ResourceLocation> items() {
            return List.of(ITEM_ID);
        }

        @Override public int color() { return 0xE2816C; }

        @Override
        public boolean isRequireNetwork() {
            return true;
        }

        @Override
        public VirtualComponentBehaviour create(FactoryControllerBlockEntity controller,
                                                VirtualComponentPosition pos,
                                                Item item,
                                                UUID networkId) {
            return new CflFluidGaugeBehaviour(controller, pos, networkId, item);
        }

        @Override
        public VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                                 CompoundTag tag,
                                                 HolderLookup.Provider registries) {
            return CflFluidGaugeBehaviour.fromNBT(controller, tag, registries);
        }

        @Override
        public VirtualComponentBehaviour fromClient(net.minecraft.network.RegistryFriendlyByteBuf buf) {
            VirtualComponentPosition pos = SyncCodecs.readPos(buf);
            Item item = BuiltInRegistries.ITEM.get(buf.readResourceLocation());
            CflFluidGaugeBehaviour g = new CflFluidGaugeBehaviour(null, pos, buf.readUUID(), item);
            g.readClientBody(buf);
            return g;
        }
    };

    public CflFluidGaugeBehaviour(FactoryControllerBlockEntity controller,
                                  VirtualComponentPosition position,
                                  UUID networkId,
                                  Item gaugeItem) {
        super(controller, position, networkId, gaugeItem);
    }

    @Override public GaugeFilterResolver filterResolver() { return FILTER_RESOLVER; }
    // logisticsControl() intentionally NOT overridden -> inherits ITEM_LOGISTICS (a CFL fluid rides item logistics).

    @Override
    protected VirtualComponentBehaviour.Type componentType() {
        return TYPE;
    }

    @Override
    public ResourceLocation getFrontTexture() {
        return FRONT_TEXTURE;
    }

    @Override public int getColor() { return TYPE.color(); }

    public static CflFluidGaugeBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                                 CompoundTag tag,
                                                 HolderLookup.Provider registries) {
        VirtualComponentPosition pos = VirtualComponentPosition.fromNBT(tag.getCompound("Pos"));
        ResourceLocation gaugeItemId = ResourceLocation.parse(tag.getString("Item"));
        Item gaugeItem = BuiltInRegistries.ITEM.get(gaugeItemId);
        UUID networkId = tag.getUUID("Network");

        CflFluidGaugeBehaviour b = new CflFluidGaugeBehaviour(controller, pos, networkId, gaugeItem);
        b.readGaugeNBT(tag, registries);
        return b;
    }
}
