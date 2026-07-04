package io.github.nbcss.createfactorycontroller.content.compat;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * The seam between this mod and the optional "Create: Repackaged" addon (modid {@code repackaged}), which adds a
 * dedicated Fluid Gauge and Energy Gauge built on Deployer's generic stock-panel system. Repackaged is a soft
 * dependency: its gauge items are only registered as placeable board components when it's installed, and every
 * reference is by item id (no Repackaged class is touched here), so this mod loads and runs fine without it.
 *
 * <p>The Fluid Gauge is fully supported — placement, fluid stock/promises, recipe dispatch, and production orders, all
 * over Deployer's {@code repackaged:fluid} stock system (see {@code RepackagedFluidStock}). The Energy Gauge is not yet
 * supported. Repackaged itself hard-depends on Deployer, so {@link #isLoaded()} implies Deployer is present.</p>
 */
public final class RepackagedCompat {

    public static final String MODID = "repackaged";

    /** Repackaged's Fluid Gauge item — a network-tuned panel item (reuses Create's frequency component). */
    public static final ResourceLocation FLUID_GAUGE = ResourceLocation.fromNamespaceAndPath(MODID, "fluid_gauge");

    private RepackagedCompat() {}

    /** Whether Create: Repackaged is installed. */
    public static boolean isLoaded() {
        ModList list = ModList.get();
        return list != null && list.isLoaded(MODID);
    }
}
