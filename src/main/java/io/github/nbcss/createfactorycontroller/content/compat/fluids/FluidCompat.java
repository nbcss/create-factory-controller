package io.github.nbcss.createfactorycontroller.content.compat.fluids;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import java.util.ArrayList;
import java.util.List;

/**
 * The seam between this mod and the optional fluid-logistics addons. A "fluid filter" is a virtual item carrying a
 * fluid type that stands in a factory-panel filter slot; this class recognises and builds them uniformly so the rest
 * of the mod (units, rendering, container reads) stays addon-agnostic. There are two families:
 *
 * <ul>
 * <li><b>Addon wrapper filters</b> — CreateFluidLogistic (modid {@code fluidlogistics}) and CreateFluid (modid
 *     {@code fluid}) each have their own item that rides Create's <i>item</i> logistics in millibuckets. The thin
 *     recognise/build seam per addon is a {@link FluidFilterProvider}; one is added (reflectively, so a missing class
 *     in a build without that addon's jar is simply skipped) only when its mod is loaded. The two are mutually
 *     incompatible, so at most one is ever active.</li>
 * <li><b>Non-item-logistics filters</b> — e.g. Create: Repackaged's Fluid Gauge uses this mod's fluid-filter token,
 *     which rides Deployer's separate fluid logistics rather than Create's item logistics.</li>
 * </ul>
 *
 * <p>Every addon reference is {@code compileOnly} and soft-detected at runtime, so the JVM never resolves an absent
 * addon's classes and the mod loads and runs fine with any combination present or none.</p>
 */
public final class FluidCompat {

    private static final String CFL_MODID = "fluidlogistics";
    private static final String CREATEFLUID_MODID = "fluid";
    private static final String REPACKAGED_MODID = "repackaged";

    private static final String CFL_PROVIDER = "io.github.nbcss.createfactorycontroller.content.compat.fluids.CflFluidProvider";
    private static final String CREATEFLUID_PROVIDER = "io.github.nbcss.createfactorycontroller.content.compat.fluids.CreateFluidProvider";
    private static final String REPACKAGED_PROVIDER = "io.github.nbcss.createfactorycontroller.content.compat.fluids.RepackagedFluidProvider";

    /** The active providers (one per installed addon), tried in order. Built lazily because FluidCompat can be touched
     * before ModList is ready during mod construction/component registration. */
    private static final List<FluidFilterProvider> PROVIDERS = new ArrayList<>();
    private static boolean providersInitialized = false;

    private FluidCompat() {}

    private static List<FluidFilterProvider> providers() {
        if (providersInitialized) return PROVIDERS;
        ModList list = ModList.get();
        if (list == null) return PROVIDERS;
        providersInitialized = true;
        if (list.isLoaded(CFL_MODID)) addProvider(PROVIDERS, CFL_PROVIDER);
        if (list.isLoaded(CREATEFLUID_MODID)) addProvider(PROVIDERS, CREATEFLUID_PROVIDER);
        if (list.isLoaded(REPACKAGED_MODID)) addProvider(PROVIDERS, REPACKAGED_PROVIDER);
        return PROVIDERS;
    }

    /**
     * Instantiates an addon provider by name and adds it. The provider's source is excluded from the build when its
     * addon jar was absent at compile time, so the class may not exist here even though the addon is loaded (a build
     * mismatch); in that case it's skipped. If the addon is loaded the class normally is present, so this resolves it.
     */
    private static void addProvider(List<FluidFilterProvider> providers, String className) {
        try {
            Class<?> cls = Class.forName(className);
            providers.add((FluidFilterProvider) cls.getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException | LinkageError e) {
            // Provider class not in this build (its addon jar wasn't on the compile classpath); skip it.
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

    /** Active tagged fluid promises targeting {@code address} on {@code network} this tick, summed over every
     *  dedicated fluid backend — no filter needed, so an <b>item</b> gauge can fold the fluid side into its
     *  address-scope quota (shared cross-kind limit). 0 with no dedicated backend installed (item-only installs never
     *  touch Deployer here). */
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

    /** Dispatches a whole recipe (Repackaged backend) as ONE order so the Package Shelf groups its fragments: the
     *  item ingredients ({@code itemOrder}, may be empty) plus the Repackaged fluid ingredients ({@code fluidDemand} —
     *  generic fluid filters whose {@code count} is the mB to fetch), to packagers at {@code address}. Returns whether
     *  a packager accepted it. */
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
     * Display name for a filter stack: the contained fluid's name when it's a fluid filter, otherwise the item's
     * normal hover name. The addons' filter items render/name themselves as a "Fluid Manifest (water)"-style wrapper,
     * so we always source the name (and icon, elsewhere) from the fluid itself for a clean, addon-agnostic label.
     */
    public static Component filterName(ItemStack stack) {
        FluidStack fluid = getFilterFluid(stack);
        return fluid.isEmpty() ? stack.getHoverName() : fluid.getHoverName();
    }

    /**
     * A tooltip for a fluid, mirroring how an item's tooltip reads: the fluid's name, the registry id when advanced
     * tooltips are on, and the blue-italic source-mod line ("which mod provides this fluid"). Fluids carry no native
     * tooltip API, so we build the same lines from the fluid registry + {@link ModList}.
     */
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
     * The fluid held in a container item (bucket, tank, …) via the NeoForge fluid-handler role — used to turn a
     * right-clicked container into a fluid filter. This reads the role only, so it needs no addon classes and is
     * safe to call regardless of {@link #isLoaded()}; an empty result means "not a (filled) fluid container".
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
