package io.github.nbcss.mixin;

import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = PackagerBlockEntity.class, remap = false)
public abstract class PackagerBlockEntityMixin {

    @Shadow private InventorySummary availableItems;
    @Shadow public InvManipulationBehaviour targetInventory;

    @Inject(method = "getAvailableItems", at = @At("HEAD"), cancellable = true)
    private void cfc$preserveBaselineOnTransientCapLoss(CallbackInfoReturnable<InventorySummary> cir) {
        if (availableItems == null || targetInventory == null) return;
        if (!targetInventory.hasInventory())
            cir.setReturnValue(availableItems);
    }

    @Inject(method = "submitNewArrivals", at = @At("HEAD"), cancellable = true)
    private void cfc$creditPromisesServerSideOnly(InventorySummary before, InventorySummary after, CallbackInfo ci) {
        Level level = ((PackagerBlockEntity) (Object) this).getLevel();
        if (level == null || level.isClientSide)
            ci.cancel();
    }
}
