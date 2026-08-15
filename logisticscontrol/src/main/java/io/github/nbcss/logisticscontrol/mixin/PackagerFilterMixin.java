package io.github.nbcss.logisticscontrol.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import io.github.nbcss.logisticscontrol.CreateLogisticsControl;
import io.github.nbcss.logisticscontrol.content.helper.FilterApplication;
import io.github.nbcss.logisticscontrol.content.helper.FilterDispatch;
import io.github.nbcss.logisticscontrol.content.helper.NonCraftGroups;
import io.github.nbcss.logisticscontrol.content.helper.PackageFilter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Filter Link: stamp the dispatch-time filter onto fresh packages and re-apply it when a box is unwrapped.
 */
@Mixin(value = PackagerBlockEntity.class, remap = false)
public abstract class PackagerFilterMixin {

    @ModifyExpressionValue(method = "attemptToSend", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/logistics/box/PackageItem;containing(Lnet/neoforged/neoforge/items/ItemStackHandler;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack clc$stampFilterOnFreshBox(ItemStack box) {
        ItemStack filter = FilterDispatch.get();
        if (!filter.isEmpty())
            box.set(CreateLogisticsControl.PACKAGE_FILTER.get(), PackageFilter.of(filter));
        List<List<BigItemStack>> groups = FilterDispatch.getGroups();
        if (!groups.isEmpty())
            box.set(CreateLogisticsControl.NONCRAFT_GROUPS.get(), new NonCraftGroups(groups));
        return box;
    }

    @Inject(method = "unwrapBox", at = @At("RETURN"))
    private void clc$applyFilterOnUnwrap(ItemStack box, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        if (simulate || !Boolean.TRUE.equals(cir.getReturnValue())) return;
        PackagerBlockEntity self = (PackagerBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide) return;
        FilterApplication.applyFromBox(self, box);
    }
}
