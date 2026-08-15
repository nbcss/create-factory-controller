package io.github.nbcss.logisticscontrol.mixin;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import io.github.nbcss.logisticscontrol.content.helper.FilterApplication;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CreateFluidLogistics' Unpackager ({@code FluidRepackagerBlockEntity}) reimplements {@code unwrapBox} without calling
 * {@code super}, so the Filter Link's application never fires there. Re-apply it.
 */
@Pseudo
@Mixin(targets = "com.yision.fluidlogistics.content.logistics.fluidPackager.repackager.FluidRepackagerBlockEntity", remap = false)
public abstract class FluidRepackagerFilterMixin {

    @Inject(method = "unwrapBox", at = @At("RETURN"))
    private void clc$applyFilterOnFluidUnwrap(ItemStack box, boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        if (simulate || !Boolean.TRUE.equals(cir.getReturnValue())) return;
        PackagerBlockEntity self = (PackagerBlockEntity) (Object) this;   // FluidRepackagerBlockEntity extends it
        Level level = self.getLevel();
        if (level == null || level.isClientSide) return;
        FilterApplication.applyFromBox(self, box);
    }
}
