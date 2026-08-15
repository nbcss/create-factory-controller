package io.github.nbcss.logisticscontrol.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBehaviour;
import io.github.nbcss.logisticscontrol.content.helper.FilterDispatch;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = FactoryPanelBehaviour.class, remap = false)
public abstract class FactoryPanelBehaviourMixin {

    @WrapMethod(method = "tickRequests")
    private void clc$stampPanelFilter(Operation<Void> original) {
        // getFilter() is inherited from FilteringBehaviour, so call it via the (runtime) target rather than @Shadow.
        FilterDispatch.set(((FactoryPanelBehaviour) (Object) this).getFilter());
        try {
            original.call();
        } finally {
            FilterDispatch.clear();
        }
    }
}
