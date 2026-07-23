package io.github.nbcss.createfactorycontroller.content.compat.fluids;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * The seam between this mod and the optional fluid-logistics addons.
 */
public final class FluidCompat {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String CFL_PROVIDER = "io.github.nbcss.createfactorycontroller.content.compat.fluids.CflFluidProvider";
    private static final String CREATEFLUID_PROVIDER = "io.github.nbcss.createfactorycontroller.content.compat.fluids.CreateFluidProvider";
    private static final String REPACKAGED_PROVIDER = "io.github.nbcss.createfactorycontroller.content.compat.fluids.RepackagedFluidProvider";

    private record Addon(String modid, @Nullable String filterItem, String providerClass) {}

    private static final List<Addon> ADDONS = List.of(
        new Addon("fluidlogistics", "compressed_storage_tank", CFL_PROVIDER),
        new Addon("fluid", "fluid_manifest", CREATEFLUID_PROVIDER),
        // Repackaged's filter is this mod's own item, and its provider already null-guards it.
        new Addon("repackaged", null, REPACKAGED_PROVIDER));

    private static final List<FluidFilterProvider> PROVIDERS = new ArrayList<>();
    private static boolean providersInitialized = false;
    private static volatile boolean registriesReady = false;

    private FluidCompat() {}

    /**
     * Resolves the providers, from common setup.
     */
    public static void onRegistriesComplete() {
        registriesReady = true;
        providers();
    }

    private static List<FluidFilterProvider> providers() {
        if (providersInitialized) return PROVIDERS;
        ModList list = ModList.get();
        if (list == null || !registriesReady) return PROVIDERS;
        providersInitialized = true;
        for (Addon addon : ADDONS) {
            if (!list.isLoaded(addon.modid())) continue;
            if (!hasFilterItem(addon)) {
                LOGGER.warn("{} is installed but registers no '{}' item, so it predates fluid-package support; "
                    + "fluid filters are disabled. Update it to use fluid gauges.", addon.modid(), addon.filterItem());
                continue;
            }
            addProvider(PROVIDERS, addon.providerClass());
        }
        return PROVIDERS;
    }

    private static boolean hasFilterItem(Addon addon) {
        return addon.filterItem() == null || BuiltInRegistries.ITEM.containsKey(
            ResourceLocation.fromNamespaceAndPath(addon.modid(), addon.filterItem()));
    }

    private static void addProvider(List<FluidFilterProvider> providers, String className) {
        try {
            Class<?> cls = Class.forName(className);
            providers.add((FluidFilterProvider) cls.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException | LinkageError e) {
            // Provider class not in this build
        }
    }

    /** Whether any supported fluid-logistics addon is installed; gates all fluid-filter behaviour. */
    public static boolean isLoaded() {
        return providers().stream().anyMatch(FluidFilterProvider::usesCreateItemLogistics);
    }

    public static boolean usesCreateItemLogistics(ItemStack stack) {
        FluidFilterProvider provider = providerOf(stack);
        return provider == null || provider.usesCreateItemLogistics();
    }

    /** True when {@code stack} is a fluid filter from any installed provider. */
    public static boolean isFluidFilter(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (FluidFilterProvider provider : providers())
            if (provider.isFluidFilter(stack)) return true;
        return false;
    }

    /** The fluid carried by a fluid-filter stack, or {@link FluidStack#EMPTY} if it isn't one. */
    public static FluidStack getFilterFluid(ItemStack stack) {
        if (stack.isEmpty()) return FluidStack.EMPTY;
        for (FluidFilterProvider provider : providers()) {
            FluidStack fluid = provider.getFilterFluid(stack);
            if (!fluid.isEmpty()) return fluid;
        }
        return FluidStack.EMPTY;
    }

    private static FluidFilterProvider providerOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (FluidFilterProvider provider : providers())
            if (provider.isFluidFilter(stack)) return provider;
        return null;
    }

    public static int fluidStock(java.util.UUID network, ItemStack filter) {
        FluidFilterProvider provider = providerOf(filter);
        return provider == null ? 0 : provider.stock(network, filter);
    }

    public static int fluidPromised(java.util.UUID network, ItemStack filter, int expiry) {
        FluidFilterProvider provider = providerOf(filter);
        return provider == null ? 0 : provider.promised(network, filter, expiry);
    }

    public static void addFluidPromise(java.util.UUID network, ItemStack filter, int amount) {
        FluidFilterProvider provider = providerOf(filter);
        if (provider != null) provider.addPromise(network, filter, amount);
    }

    // ── Promise limit (dedicated fluid backends) ────────────────────────────────

    /** Promises {@code filter} tagged with the minting gauge/address, so it counts toward the promise limit. */
    public static void addControllerFluidPromise(java.util.UUID network, ItemStack filter, int amount,
                                                 String ownerKey, String address) {
        FluidFilterProvider provider = providerOf(filter);
        if (provider != null) provider.addControllerPromise(network, filter, amount, ownerKey, address);
    }

    /** Active tagged fluid promises minted by {@code ownerKey} on {@code network} this tick (0 if unsupported). */
    public static int fluidOwnedPromises(java.util.UUID network, ItemStack filter, String ownerKey, long gameTime) {
        FluidFilterProvider provider = providerOf(filter);
        return provider == null ? 0 : provider.ownedPromises(network, ownerKey, gameTime);
    }

    /** Active tagged fluid promises targeting {@code address} on {@code network} this tick */
    public static int fluidAddressPromises(java.util.UUID network, String address, long gameTime) {
        int total = 0;
        for (FluidFilterProvider provider : providers())
            total += provider.addressPromises(network, address, gameTime);   // default 0 → only dedicated backends add
        return total;
    }

    /** Folds a just-dispatched tagged fluid promise into this tick's count cache. */
    public static void onFluidPromiseAdded(java.util.UUID network, ItemStack filter, String ownerKey,
                                           String address, long gameTime) {
        FluidFilterProvider provider = providerOf(filter);
        if (provider != null) provider.onPromiseAdded(network, ownerKey, address, gameTime);
    }

    public static void forceClearFluid(java.util.UUID network, ItemStack filter) {
        FluidFilterProvider provider = providerOf(filter);
        if (provider != null) provider.forceClear(network, filter);
    }

    public static boolean dispatchFluid(java.util.UUID network, ItemStack filter, int amount, String address,
                                        int orderId, int linkIndex, boolean finalLink) {
        FluidFilterProvider provider = providerOf(filter);
        return provider != null && provider.dispatch(network, filter, amount, address, orderId, linkIndex, finalLink);
    }

    public static boolean broadcastRepackagedRecipe(java.util.UUID network,
                                                    com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts itemOrder,
                                                    List<com.simibubi.create.content.logistics.BigItemStack> fluidDemand,
                                                    String address) {
        List<FluidStack> fluids = new ArrayList<>();
        for (com.simibubi.create.content.logistics.BigItemStack b : fluidDemand) {
            FluidStack fluid = getFilterFluid(b.stack);
            if (!fluid.isEmpty()) fluids.add(fluid.copyWithAmount(b.count));
        }
        return RepackagedFluidStock.broadcastAll(network, itemOrder, fluids, address);
    }

    /** Builds a filter for a dedicated fluid gauge backend (currently Repackaged); empty when none is installed. */
    public static ItemStack makeFluidGaugeFilter(FluidStack fluid) {
        if (fluid.isEmpty()) return ItemStack.EMPTY;
        for (FluidFilterProvider provider : providers())
            if (!provider.usesCreateItemLogistics()) return provider.makeFluidFilter(fluid);
        return ItemStack.EMPTY;
    }

    /**
     * Display name for a filter stack
     */
    public static Component filterName(ItemStack stack) {
        FluidStack fluid = getFilterFluid(stack);
        return fluid.isEmpty() ? stack.getHoverName() : fluid.getHoverName();
    }

    public static List<Component> fluidTooltip(FluidStack fluid, boolean advanced) {
        List<Component> lines = new ArrayList<>();
        lines.add(fluid.getHoverName());
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        if (advanced) lines.add(Component.literal(id.toString()).withStyle(ChatFormatting.DARK_GRAY));
        String modName = ModList.get().getModContainerById(id.getNamespace())
            .map(c -> c.getModInfo().getDisplayName()).orElse(id.getNamespace());
        lines.add(Component.literal(modName).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
        return lines;
    }

    /** Builds a fluid filter tagging {@code fluid}'s type, using the first installed addon's representation. */
    public static ItemStack makeFluidFilter(FluidStack fluid) {
        if (fluid.isEmpty()) return ItemStack.EMPTY;
        for (FluidFilterProvider provider : providers())
            if (provider.usesCreateItemLogistics()) return provider.makeFluidFilter(fluid);
        return ItemStack.EMPTY;
    }

    /**
     * The fluid held in a container item (bucket, tank, …) via the NeoForge fluid-handler role
     */
    public static FluidStack fluidInContainer(ItemStack container) {
        if (container.isEmpty()) return FluidStack.EMPTY;
        IFluidHandlerItem handler = container.getCapability(Capabilities.FluidHandler.ITEM);
        if (handler == null) return FluidStack.EMPTY;
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack fluid = handler.getFluidInTank(i);
            if (!fluid.isEmpty()) return fluid.copy();
        }
        return FluidStack.EMPTY;
    }
}
