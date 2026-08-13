package io.github.nbcss.createfactorycontroller.mixin;

import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;


public class CfcMixinPlugin implements IMixinConfigPlugin {

    private static final String DEPLOYER_MIXIN =
        "io.github.nbcss.createfactorycontroller.mixin.LogisticsGenericManagerMixin";
    private static final String GENERIC_PROMISE_CODEC_MIXIN =
        "io.github.nbcss.createfactorycontroller.mixin.GenericPromiseCodecMixin";
    private static final String DEPLOYER_ORDER_PACKET_MIXIN =
        "io.github.nbcss.createfactorycontroller.mixin.GenericOrderRequestPacketMixin";
    private static final String REPACKAGE_MIXIN =
        "io.github.nbcss.createfactorycontroller.mixin.PackageRepackageHelperMixin";
    private static final String CFA_MIXIN =
        "io.github.nbcss.createfactorycontroller.mixin.GenericLogisticsManagerMixin";
    private static final String PHANTOM_NOTIFY_MIXIN =
        "io.github.nbcss.createfactorycontroller.mixin.TunablePortableTickerSendOrderMixin";
    private static final String CMP_NOTIFY_MIXIN =
        "io.github.nbcss.createfactorycontroller.mixin.SendPackageMixin";

    private static final boolean DEPLOYER_PRESENT = isModPresent("deployer");
    private static final boolean EXTRA_GAUGES_PRESENT = isModPresent("extra_gauges");
    private static final boolean CFA_PRESENT = isModPresent("create_factory_abstractions");
    private static final boolean CREATEPHANTOM_PRESENT = isModPresent("createphantom");
    private static final boolean CMP_PRESENT = isModPresent("create_mobile_packages");

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
        if (DEPLOYER_ORDER_PACKET_MIXIN.equals(mixinClassName)) return DEPLOYER_PRESENT;
        if (REPACKAGE_MIXIN.equals(mixinClassName)) return !EXTRA_GAUGES_PRESENT;
        if (CFA_MIXIN.equals(mixinClassName)) return CFA_PRESENT;
        if (PHANTOM_NOTIFY_MIXIN.equals(mixinClassName)) return CREATEPHANTOM_PRESENT;
        if (CMP_NOTIFY_MIXIN.equals(mixinClassName)) return CMP_PRESENT;
        return true;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
