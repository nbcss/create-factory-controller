package io.github.nbcss.createfactorycontroller.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Lets this mod's mixin config apply with Deployer and Extra Gauges as soft dependencies:
 * <ul>
 *   <li>{@link LogisticsGenericManagerMixin} targets a Deployer class
 *       ({@code net.liukrast.deployer...LogisticsGenericManager}); applying it without Deployer present would error,
 *       so it is skipped in that case.</li>
 *   <li>{@link PackageRepackageHelperMixin} re-implements the Re-Packager fix that Extra Gauges already ships, so it
 *       is skipped when Extra Gauges ({@code extra_gauges}) is installed to avoid double-patching the same calls.</li>
 * </ul>
 * Every other mixin targets Create/vanilla and always applies.
 *
 * <p>Detection uses {@link LoadingModList} rather than {@code ModList}: mixin configs are processed during early
 * loading, before {@code ModList} is built, but after mod files have been discovered.</p>
 */
public class CfcMixinPlugin implements IMixinConfigPlugin {

    private static final String DEPLOYER_MIXIN =
        "io.github.nbcss.createfactorycontroller.mixin.LogisticsGenericManagerMixin";
    /** Redirects Deployer's generic-promise codec; targets methods Deployer mixes into RequestPromiseQueue, so it only
     *  applies when Deployer is present. */
    private static final String GENERIC_PROMISE_CODEC_MIXIN =
        "io.github.nbcss.createfactorycontroller.mixin.GenericPromiseCodecMixin";
    private static final String REPACKAGE_MIXIN =
        "io.github.nbcss.createfactorycontroller.mixin.PackageRepackageHelperMixin";

    private static final boolean DEPLOYER_PRESENT = isModPresent("deployer");
    private static final boolean EXTRA_GAUGES_PRESENT = isModPresent("extra_gauges");

    private static boolean isModPresent(String modId) {
        try {
            return LoadingModList.get().getModFileById(modId) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (DEPLOYER_MIXIN.equals(mixinClassName)) return DEPLOYER_PRESENT;
        if (GENERIC_PROMISE_CODEC_MIXIN.equals(mixinClassName)) return DEPLOYER_PRESENT;
        if (REPACKAGE_MIXIN.equals(mixinClassName)) return !EXTRA_GAUGES_PRESENT;
        return true;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
