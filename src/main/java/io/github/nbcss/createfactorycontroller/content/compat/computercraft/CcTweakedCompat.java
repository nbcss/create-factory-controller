package io.github.nbcss.createfactorycontroller.content.compat.computercraft;

import net.neoforged.fml.ModList;

/**
 * The seam between this mod and the optional CC: Tweaked mod. Every reference to a {@code dan200.computercraft}
 * class lives in {@link CcTweakedIntegration} / {@link FactoryControllerPeripheral}, reached only through a branch
 * guarded by {@link #isLoaded()}, so the JVM never resolves a CC: Tweaked class when it's absent. This class itself
 * has no such reference, so it's always safe to load and query.
 */
public final class CcTweakedCompat {

    public static final String MODID = "computercraft";

    private CcTweakedCompat() {}

    /** Whether CC: Tweaked is installed; gates every reference to a {@code dan200.computercraft} class. */
    public static boolean isLoaded() {
        ModList list = ModList.get();
        return list != null && list.isLoaded(MODID);
    }
}
