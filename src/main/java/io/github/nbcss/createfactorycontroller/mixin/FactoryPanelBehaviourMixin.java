package io.github.nbcss.createfactorycontroller.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import io.github.nbcss.createfactorycontroller.content.helper.CfcFilterDispatch;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Create's vanilla Factory Panel requests ingredients for its configured output but never tags the resulting packages.
 * Wrap its request tick so those packages carry the panel's output as their filter — same as the mod's own gauge — letting
 * a Filter Link set the target machine's filter from a factory-gauge-driven request too.
 */
@Mixin(value = FactoryPanelBehaviour.class, remap = false)
public abstract class FactoryPanelBehaviourMixin {

    @WrapMethod(method = "tickRequests")
    private void cfc$stampPanelFilter(Operation<Void> original) {
        // getFilter() is inherited from FilteringBehaviour, so call it via the (runtime) target rather than @Shadow.
        CfcFilterDispatch.set(((FactoryPanelBehaviour) (Object) this).getFilter());
        try {
            original.call();
        } finally {
            CfcFilterDispatch.clear();
        }
    }
}
