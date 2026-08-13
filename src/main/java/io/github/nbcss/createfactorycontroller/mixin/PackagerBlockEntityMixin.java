package io.github.nbcss.createfactorycontroller.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.helper.CfcFilterDispatch;
import io.github.nbcss.createfactorycontroller.content.helper.FilterApplication;
import io.github.nbcss.createfactorycontroller.content.helper.PackageFilter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bug Fix: https://github.com/Creators-of-Create/Create/pull/10496
 */
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

    @ModifyExpressionValue(method = "attemptToSend", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/logistics/box/PackageItem;containing(Lnet/neoforged/neoforge/items/ItemStackHandler;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack cfc$stampFilterOnFreshBox(ItemStack box) {
        ItemStack filter = CfcFilterDispatch.get();
        if (!filter.isEmpty())
            box.set(CreateFactoryController.PACKAGE_FILTER.get(), PackageFilter.of(filter));
        java.util.List<java.util.List<com.simibubi.create.content.logistics.BigItemStack>> groups = CfcFilterDispatch.getGroups();
        if (!groups.isEmpty())
            box.set(CreateFactoryController.NONCRAFT_GROUPS.get(),
                new io.github.nbcss.createfactorycontroller.content.helper.NonCraftGroups(groups));
        return box;
    }

    @Inject(method = "unwrapBox", at = @At("RETURN"))
    private void cfc$applyFilterOnUnwrap(ItemStack box, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        if (simulate || !Boolean.TRUE.equals(cir.getReturnValue())) return;
        PackagerBlockEntity self = (PackagerBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide) return;
        FilterApplication.applyFromBox(self, box);
    }
}
