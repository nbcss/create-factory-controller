package io.github.nbcss.createfactorycontroller.content.component;

import io.github.nbcss.createfactorycontroller.content.RequestMode;
import io.github.nbcss.createfactorycontroller.content.ThresholdUnit;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.compat.RepackagedCompat;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public class FluidGaugeBehaviour extends VirtualGaugeBehaviour {

    private static final ResourceLocation FRONT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/fluid_gauge/front");

    public static final VirtualComponentBehaviour.Type TYPE = new VirtualComponentBehaviour.Type(){

        @Override
        public String id() {
            return "FLUID_GAUGE";
        }

        @Override
        public List<ResourceLocation> items() {
            return List.of(RepackagedCompat.FLUID_GAUGE);
        }

        @Override
        public boolean isRequireNetwork() {
            return true;
        }

        @Override
        public VirtualComponentBehaviour create(FactoryControllerBlockEntity controller,
                                                VirtualComponentPosition pos,
                                                ResourceLocation itemId,
                                                UUID networkId) {
            return new FluidGaugeBehaviour(controller, pos, networkId, itemId);
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
                               ResourceLocation gaugeItemId) {
        super(controller, position, networkId, gaugeItemId);
    }

    @Override
    public void forceClearPromise(UUID networkId, ItemStack filter) {
        FluidCompat.repackagedForceClearFluid(networkId, filter);
    }

    @Override
    public void addPromise(UUID networkId, ItemStack filter, boolean ignoreData, int amount) {
        FluidCompat.repackagedAddFluidPromise(networkId, filter, amount);
    }

    @Override
    protected int networkStockOf(ItemStack stack) {
        return stack.isEmpty() ? 0 : FluidCompat.repackagedFluidStock(networkId, stack);
    }

    @Override
    public int getPromised() {
        return filter.isEmpty() ? 0 : FluidCompat.repackagedFluidPromised(networkId, filter, getPromiseExpiryTimeInTicks());
    }

    @Override
    public boolean isFluidGauge() {
        return true;
    }

    @Override
    public boolean supportsIgnoreData() {
        return false;
    }

    @Override
    protected VirtualComponentBehaviour.Type componentType() {
        return TYPE;
    }

    @Override
    public ResourceLocation getFrontTexture() {
        return FRONT_TEXTURE;
    }

    public static VirtualGaugeBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                                CompoundTag tag,
                                                net.minecraft.core.HolderLookup.Provider registries) {
        VirtualComponentPosition pos = VirtualComponentPosition.fromNBT(tag.getCompound("Pos"));
        ResourceLocation gaugeItemId = ResourceLocation.parse(tag.getString("GaugeItem"));
        UUID networkId = tag.getUUID("Network");

        FluidGaugeBehaviour b = new FluidGaugeBehaviour(controller, pos, networkId, gaugeItemId);
        b.filter = ItemStack.parseOptional(registries, tag.getCompound("Filter"));
        b.ignoreData = tag.getBoolean("IgnoreData");
        b.count = tag.getInt("Count");
        b.unit = ThresholdUnit.fromName(tag.getString("Unit"));
        if (tag.contains("RequestMode"))
            b.requestMode = RequestMode.fromName(tag.getString("RequestMode"));
        else   // legacy migration from the old Passive boolean
            b.requestMode = tag.getBoolean("Passive")
                    ? RequestMode.PASSIVE : RequestMode.NORMAL;
        if (tag.hasUUID("PatternId"))
            b.patternId = tag.getUUID("PatternId");
        b.stockLevel = tag.getInt("Stock");
        b.promisedCount = tag.getInt("Promised");
        b.lastRequestTick = tag.getLong("LastRequestTick");

        b.satisfied = tag.getBoolean("Satisfied");
        b.promisedSatisfied = tag.getBoolean("PromisedSatisfied");
        b.waitingForNetwork = tag.getBoolean("Waiting");
        b.redstonePowered = tag.getBoolean("RedstonePowered");
        b.controllerPowered = tag.getBoolean("ControllerPowered");
        b.timer = tag.getInt("Timer");
        b.recipeAddress = tag.getString("RecipeAddress");
        b.recipeOutput = tag.getInt("RecipeOutput");
        b.craftBatch = Math.max(1, tag.getInt("CraftBatch"));   // absent (legacy data) → 1
        b.craftDimension = Math.max(0, tag.getInt("CraftDimension"));   // 0 = not a large recipe / unset
        b.promiseClearingInterval = tag.getInt("PromiseClearingInterval");
        // Connections are loaded centrally by the controller / menu, not from the component tag.

        // Server-side load (placement via setup, or chunk reload): delay the first request by a full interval.
        // A freshly loaded gauge's network stock summary can read 0 for a tick or two before it populates, and
        // with timer==0 (setup strips it) tickRequests would fire immediately even though stock is sufficient.
        // Throttling the first attempt gives tickStorageMonitor time to read real stock and mark it satisfied.
        // (controller is null only on the client snapshot, which never ticks — leave its timer as-is.)
        if (controller != null) b.timer = b.getConfigRequestIntervalInTicks();

        return b;
    }
}
