package io.github.nbcss.mixin;

import com.simibubi.create.content.logistics.packager.InventorySummary;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = InventorySummary.class, remap = false)
public class InventorySummaryMixin {
    @ModifyArg(
        method = "add(Lnet/minecraft/world/item/ItemStack;I)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/BigItemStack;<init>(Lnet/minecraft/world/item/ItemStack;I)V"
        ),
        index = 0
    )
    private ItemStack copyStackBeforeStore(ItemStack stack) {
        return stack.copyWithCount(1);
    }
}
