package io.github.nbcss.content.factorycontroller.compat;

import com.yision.fluidlogistics.item.CompressedTankItem;
import com.yision.fluidlogistics.registry.AllItems;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * The single seam between this mod and <a href="https://github.com/yision1/CreateFluidLogistic">CreateFluidLogistic</a>
 * (modid {@code fluidlogistics}). Every reference to CFL's types lives here, and every public method short-circuits
 * via {@link #isLoaded()} before touching them — so the JVM never resolves a CFL class when the mod is absent, and
 * this mod loads and runs fine without it (CFL is a {@code compileOnly} dependency).
 *
 * <p>A fluid filter, in CFL's model, is an {@code ItemStack} of their {@code COMPRESSED_STORAGE_TANK} item carrying a
 * <em>virtual</em> {@code FluidTankContent} data component (the fluid is a type tag; its amount is meaningless — the
 * requested quantity in millibuckets is tracked separately by the gauge). We reuse that exact representation so CFL
 * recognises our gauges' fluid filters.</p>
 */
public final class FluidCompat {

    public static final String MODID = "fluidlogistics";

    private static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded(MODID);

    private FluidCompat() {}

    /** Whether CreateFluidLogistic is installed; gates all fluid-filter behaviour. */
    public static boolean isLoaded() {
        return LOADED;
    }

    /** True when {@code stack} is a CFL fluid filter (a compressed tank holding a fluid type). */
    public static boolean isFluidFilter(ItemStack stack) {
        if (!LOADED || stack.isEmpty()) return false;
        return stack.is(AllItems.COMPRESSED_STORAGE_TANK.get()) && !CompressedTankItem.getFluid(stack).isEmpty();
    }

    /** The fluid carried by a fluid-filter stack, or {@link FluidStack#EMPTY} if it isn't one. */
    public static FluidStack getFilterFluid(ItemStack stack) {
        if (!LOADED || stack.isEmpty()) return FluidStack.EMPTY;
        return CompressedTankItem.getFluid(stack);
    }

    /** Builds a virtual fluid-filter stack tagging {@code fluid}'s type (amount normalised to 1). */
    public static ItemStack makeFluidFilter(FluidStack fluid) {
        if (!LOADED || fluid.isEmpty()) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(AllItems.COMPRESSED_STORAGE_TANK.get());
        CompressedTankItem.setFluidVirtual(stack, fluid.copyWithAmount(1));
        return stack;
    }

    /**
     * The fluid held in a container item (bucket, tank, …) via the NeoForge fluid-handler capability — used to turn a
     * right-clicked container into a fluid filter. This reads the capability only, so it needs no CFL classes and is
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
