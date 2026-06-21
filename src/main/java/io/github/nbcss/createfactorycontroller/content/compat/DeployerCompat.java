package io.github.nbcss.createfactorycontroller.content.compat;

import net.neoforged.fml.ModList;

/**
 * The seam between this mod and the optional Deployer mod. Deployer adds the Stock Keeper tab system
 * ({@code KeeperTabScreen}/{@code TabsWidget}, registered via {@code ClientRegisterHelpers}) and the generic order
 * dispatch path ({@code LogisticsGenericManager}) that this mod's Production Orders feature hooks into.
 *
 * <p>Deployer is a soft dependency: every reference to its classes is reached only through a branch guarded by
 * {@link #isLoaded()} (or, for the order-dispatch mixin, skipped by {@code CfcMixinPlugin}), so the JVM never resolves
 * a Deployer class when it's absent. When Deployer is present the Production Orders page is a keeper tab; when it's
 * absent the page is reached via an in-GUI button on the Stock Keeper and order registration falls back to Create's
 * vanilla {@code broadcastPackageRequest} (see {@code StockTickerBlockEntityMixin}).</p>
 */
public final class DeployerCompat {

    public static final String MODID = "deployer";

    private DeployerCompat() {}

    /** Whether Deployer is installed; gates every reference to a {@code net.liukrast.deployer} class. */
    public static boolean isLoaded() {
        ModList list = ModList.get();
        return list != null && list.isLoaded(MODID);
    }
}
