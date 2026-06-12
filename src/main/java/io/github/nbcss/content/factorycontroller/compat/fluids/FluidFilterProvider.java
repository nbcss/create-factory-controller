package io.github.nbcss.content.factorycontroller.compat.fluids;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A mod-specific way to recognise and build a "fluid filter" — the virtual item, carrying a fluid, that a fluid
 * logistics addon (CreateFluidLogistic, CreateFluid, …) puts in a factory-panel filter slot. Each implementation
 * references exactly one addon's classes and is only instantiated when that addon is loaded, so the dispatcher
 * ({@link FluidCompat}) can support whichever addon(s) are present without hard-linking any of them.
 *
 * <p>All addons share the same integration architecture (a virtual fluid item riding Create's item logistics in
 * millibuckets), so only this thin recognise/build seam differs between them; everything else in the mod is
 * addon-agnostic.</p>
 */
public interface FluidFilterProvider {

    /** Whether {@code stack} is THIS addon's fluid filter (its virtual item, carrying a non-empty fluid). */
    boolean isFluidFilter(ItemStack stack);

    /** The fluid in {@code stack} if it is THIS addon's filter, else {@link FluidStack#EMPTY}. */
    FluidStack getFilterFluid(ItemStack stack);

    /** Builds a fluid filter for {@code fluid} using THIS addon's representation. */
    ItemStack makeFluidFilter(FluidStack fluid);
}
