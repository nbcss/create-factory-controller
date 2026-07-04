package io.github.nbcss.createfactorycontroller.content.component;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.compat.RepackagedCompat;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.UUID;

public class FluidGaugeBehaviour extends VirtualGaugeBehaviour {

    private static final ResourceLocation FRONT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/fluid_gauge/front");
    private static final GaugeFilterResolver FILTER_RESOLVER = new GaugeFilterResolver() {
        @Override public boolean acceptsFilter(ItemStack filter) { return filter.isEmpty() || FluidCompat.isFluidFilter(filter); }
        @Override public boolean supportsIgnoreData() { return false; }
        @Override public boolean acceptsItemDrop() { return false; }
        @Override public boolean acceptsFluidDrop() { return true; }

        @Override
        public ItemStack fromCarried(ItemStack carried, int mouseButton) {
            FluidStack fluid = FluidCompat.fluidInContainer(carried);
            return fluid.isEmpty() ? ItemStack.EMPTY : FluidCompat.makeFluidGaugeFilter(fluid);
        }

        @Override
        public ItemStack fromFluid(FluidStack fluid) {
            return FluidCompat.makeFluidGaugeFilter(fluid);
        }
    };
    private static final LogisticsControl LOGISTICS = new LogisticsControl() {
        @Override public int stockOf(VirtualGaugeBehaviour gauge, ItemStack stack) {
            return stack.isEmpty() ? 0 : FluidCompat.fluidStock(gauge.networkId, stack);
        }
        @Override public int promised(VirtualGaugeBehaviour gauge) {
            return gauge.filter.isEmpty() ? 0 : FluidCompat.fluidPromised(gauge.networkId, gauge.filter, gauge.getPromiseExpiryTimeInTicks());
        }
        @Override public void forceClearPromise(UUID networkId, ItemStack filter) { FluidCompat.forceClearFluid(networkId, filter); }
        @Override public void addPromise(UUID networkId, ItemStack filter, boolean ignoreData, int amount,
                                         String ownerKey, String targetAddress, int ttl) {
            // A dedicated (Repackaged) fluid backend tags its promises with the minting gauge/address so they count
            // toward the promise limit; other backends fall back to an untagged promise (ttl still rides the backend's
            // own timeout, not our per-owner sentinel — the fluid-side timeout isolation is a future step).
            FluidCompat.addControllerFluidPromise(networkId, filter, amount, ownerKey, targetAddress);
        }
    };

    public static final VirtualComponentBehaviour.Type TYPE = new VirtualComponentBehaviour.Type(){

        @Override
        public String id() {
            return "FLUID_GAUGE";
        }

        @Override
        public List<ResourceLocation> items() {
            return List.of(RepackagedCompat.FLUID_GAUGE);
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
            return new FluidGaugeBehaviour(controller, pos, networkId, item);
        }

        @Override
        public VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                                 CompoundTag tag,
                                                 HolderLookup.Provider registries) {
            return FluidGaugeBehaviour.fromNBT(controller, tag, registries);
        }
    };

    public FluidGaugeBehaviour(FactoryControllerBlockEntity controller,
                               VirtualComponentPosition position,
                               UUID networkId,
                               Item gaugeItem) {
        super(controller, position, networkId, gaugeItem);
    }

    @Override public GaugeFilterResolver filterResolver() { return FILTER_RESOLVER; }
    @Override public LogisticsControl logisticsControl() { return LOGISTICS; }

    /** Fold the fresh promise into the FLUID count cache (not the item {@code PromiseCounts}), so a fluid gauge firing
     *  later this tick sees it. Mirrors the base gauge's item-side fold. */
    @Override
    public void addPromise(UUID networkId, ItemStack filter, boolean ignoreData, int amount) {
        String ownerKey = gaugeId == null ? null : gaugeId.toString();
        logisticsControl().addPromise(networkId, filter, ignoreData, amount, ownerKey, recipeAddress,
                getPromiseExpiryTimeInTicks());
        if (controller != null && controller.getLevel() != null)
            FluidCompat.onFluidPromiseAdded(networkId, filter, ownerKey, recipeAddress,
                    controller.getLevel().getGameTime());
    }

    // Owner scope counts this fluid gauge's own promises from the FLUID backend, instead of the item PromiseCounts the
    // base gauge reads (a gauge only ever mints one kind). Address scope is NOT overridden: the base already sums item
    // + fluid promises to the address, so a fluid gauge and an item gauge sharing an address share one quota.
    @Override
    public int ownedPromiseCount(long now) {
        return FluidCompat.fluidOwnedPromises(networkId, filter, gaugeId == null ? null : gaugeId.toString(), now);
    }

    @Override
    protected VirtualComponentBehaviour.Type componentType() {
        return TYPE;
    }

    @Override
    public ResourceLocation getFrontTexture() {
        return FRONT_TEXTURE;
    }

    @Override public int getColor() { return TYPE.color(); }

    public static FluidGaugeBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                              CompoundTag tag,
                                              net.minecraft.core.HolderLookup.Provider registries) {
        VirtualComponentPosition pos = VirtualComponentPosition.fromNBT(tag.getCompound("Pos"));
        ResourceLocation gaugeItemId = ResourceLocation.parse(tag.getString("Item"));
        Item gaugeItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(gaugeItemId);
        UUID networkId = tag.getUUID("Network");

        FluidGaugeBehaviour b = new FluidGaugeBehaviour(controller, pos, networkId, gaugeItem);
        b.readGaugeNBT(tag, registries);
        return b;
    }
}
