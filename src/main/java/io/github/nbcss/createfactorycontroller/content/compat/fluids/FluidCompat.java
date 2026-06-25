package io.github.nbcss.createfactorycontroller.content.compat.fluids;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
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
 * <li><b>The generic token</b> — for Create: Repackaged's Fluid Gauge we use our own {@link
 *     CreateFactoryController#FLUID_FILTER} item ({@link #isGenericFluidFilter}), which rides Deployer's separate fluid
 *     logistics rather than Create's item logistics; its stock/promises/dispatch go through {@link RepackagedFluidStock}
 *     (the {@code repackaged*} methods here).</li>
 * </ul>
 *
 * <p>Every addon reference is {@code compileOnly} and soft-detected at runtime, so the JVM never resolves an absent
 * addon's classes and the mod loads and runs fine with any combination present or none.</p>
 */
public final class FluidCompat {

    private static final String CFL_MODID = "fluidlogistics";
    private static final String CREATEFLUID_MODID = "fluid";

    private static final String CFL_PROVIDER = "io.github.nbcss.createfactorycontroller.content.compat.fluids.CflFluidProvider";
    private static final String CREATEFLUID_PROVIDER = "io.github.nbcss.createfactorycontroller.content.compat.fluids.CreateFluidProvider";

    /** The active providers (one per installed addon), tried in order. {@code makeFluidFilter} uses the first. */
    private static final List<FluidFilterProvider> PROVIDERS = createProviders();

    private FluidCompat() {}

    private static List<FluidFilterProvider> createProviders() {
        List<FluidFilterProvider> providers = new ArrayList<>();
        ModList list = ModList.get();
        if (list == null) return providers;
        if (list.isLoaded(CFL_MODID)) addProvider(providers, CFL_PROVIDER);
        if (list.isLoaded(CREATEFLUID_MODID)) addProvider(providers, CREATEFLUID_PROVIDER);
        return providers;
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
        return !PROVIDERS.isEmpty();
    }

    /** Whether {@code stack} is our addon-agnostic generic fluid-filter token (the FLUID-gauge filter, riding
     *  Repackaged's Deployer fluid logistics — NOT the CFL/CreateFluid wrapper items, which ride Create's item
     *  logistics). The token + its component are only registered when Create: Repackaged is installed, so the
     *  holders may be null. */
    public static boolean isGenericFluidFilter(ItemStack stack) {
        return CreateFactoryController.FLUID_FILTER != null && stack.is(CreateFactoryController.FLUID_FILTER.get());
    }

    /** True when {@code stack} is a fluid filter — our addon-agnostic {@link #makeGenericFluidFilter generic token}
     *  (used by fluid gauges) or any installed addon's wrapper item (a virtual item holding a fluid type). */
    public static boolean isFluidFilter(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (isGenericFluidFilter(stack)) return true;
        for (FluidFilterProvider provider : PROVIDERS)
            if (provider.isFluidFilter(stack)) return true;
        return false;
    }

    /** The fluid carried by a fluid-filter stack, or {@link FluidStack#EMPTY} if it isn't one. */
    public static FluidStack getFilterFluid(ItemStack stack) {
        if (stack.isEmpty()) return FluidStack.EMPTY;
        if (isGenericFluidFilter(stack))
            return stack.getOrDefault(CreateFactoryController.FLUID_CONTENT.get(),
                    net.neoforged.neoforge.fluids.SimpleFluidContent.EMPTY).copy();
        for (FluidFilterProvider provider : PROVIDERS) {
            FluidStack fluid = provider.getFilterFluid(stack);
            if (!fluid.isEmpty()) return fluid;
        }
        return FluidStack.EMPTY;
    }

    /** Network stock (mB) of a fluid gauge's {@code filter} fluid, read from Repackaged's Deployer-backed fluid stock
     *  system. Only meaningful for a FLUID gauge (its generic filter rides no Create item logistics). Delegates to
     *  {@link RepackagedFluidStock} so the Deployer references stay lazily loaded (Repackaged-only). */
    public static int repackagedFluidStock(java.util.UUID network, ItemStack filter) {
        return RepackagedFluidStock.stock(network, getFilterFluid(filter));
    }

    /** Open promised amount (mB) of a fluid gauge's {@code filter} fluid on the network (Repackaged backend). */
    public static int repackagedFluidPromised(java.util.UUID network, ItemStack filter, int expiry) {
        return RepackagedFluidStock.promised(network, getFilterFluid(filter), expiry);
    }

    /** Promises {@code amount} mB of a fluid gauge's produced output (Repackaged backend). */
    public static void repackagedAddFluidPromise(java.util.UUID network, ItemStack filter, int amount) {
        RepackagedFluidStock.addPromise(network, getFilterFluid(filter), amount);
    }

    /** Force-clears a fluid gauge's open output promises (Repackaged backend). */
    public static void repackagedForceClearFluid(java.util.UUID network, ItemStack filter) {
        RepackagedFluidStock.forceClear(network, getFilterFluid(filter));
    }

    /** Ships {@code amount} mB of a generic fluid filter's fluid to {@code address} as link
     *  ({@code linkIndex}/{@code finalLink}) of the shared production order {@code orderId} (Repackaged backend). */
    public static boolean dispatchRepackagedFluid(java.util.UUID network, ItemStack filter, int amount, String address,
                                                  int orderId, int linkIndex, boolean finalLink) {
        return RepackagedFluidStock.dispatchWithOrderId(network, getFilterFluid(filter).copyWithAmount(amount),
            address, orderId, linkIndex, finalLink);
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

    /** Builds the addon-agnostic generic fluid-filter token for a fluid gauge (the {@link CreateFactoryController#FLUID_FILTER}
     *  item tagged with the chosen fluid type). Used when the gauge's stock type is FLUID; empty when the token isn't
     *  registered (Repackaged absent). */
    public static ItemStack makeGenericFluidFilter(FluidStack fluid) {
        if (fluid.isEmpty() || CreateFactoryController.FLUID_FILTER == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(CreateFactoryController.FLUID_FILTER.get());
        stack.set(CreateFactoryController.FLUID_CONTENT.get(),
                net.neoforged.neoforge.fluids.SimpleFluidContent.copyOf(fluid.copyWithAmount(1)));
        return stack;
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
        if (fluid.isEmpty() || PROVIDERS.isEmpty()) return ItemStack.EMPTY;
        return PROVIDERS.getFirst().makeFluidFilter(fluid);
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
