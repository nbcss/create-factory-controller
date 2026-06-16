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
 * The seam between this mod and the optional fluid-logistics addons. A fluid filter is a virtual item (carrying a
 * fluid) that an addon recognises in a factory-panel filter slot; all addons share the same architecture (the item
 * rides Create's item logistics in millibuckets), so only the thin recognise/build step differs — handled by a
 * {@link FluidFilterProvider} per addon.
 *
 * <p>Supported addons (each {@code compileOnly}, soft-detected at runtime): CreateFluidLogistic (modid
 * {@code fluidlogistics}) and CreateFluid (modid {@code fluid}). A provider is added only when its mod is loaded, so
 * the JVM never resolves an addon's classes when it's absent, and this mod loads and runs fine with either or
 * neither present (the two addons are mutually incompatible, so at most one is ever active). Each provider is
 * instantiated reflectively because its source is excluded from the build when the matching addon jar was absent at
 * compile time (e.g. CI, where the local {@code lib/} jars don't exist) — a missing provider class is simply skipped.
 * The rest of the fluid
 * handling (stock reads via Create's logistics, the mB/B units, the cube + stock-keeper rendering, reading a held
 * container) is addon-agnostic and lives elsewhere.</p>
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

    /** True when {@code stack} is any installed addon's fluid filter (a virtual item holding a fluid type). */
    public static boolean isFluidFilter(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (FluidFilterProvider provider : PROVIDERS)
            if (provider.isFluidFilter(stack)) return true;
        return false;
    }

    /** The fluid carried by a fluid-filter stack, or {@link FluidStack#EMPTY} if it isn't one. */
    public static FluidStack getFilterFluid(ItemStack stack) {
        if (stack.isEmpty()) return FluidStack.EMPTY;
        for (FluidFilterProvider provider : PROVIDERS) {
            FluidStack fluid = provider.getFilterFluid(stack);
            if (!fluid.isEmpty()) return fluid;
        }
        return FluidStack.EMPTY;
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
     * The fluid held in a container item (bucket, tank, …) via the NeoForge fluid-handler capability — used to turn a
     * right-clicked container into a fluid filter. This reads the capability only, so it needs no addon classes and is
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
