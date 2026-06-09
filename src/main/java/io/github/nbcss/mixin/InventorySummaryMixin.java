package io.github.nbcss.mixin;

import com.simibubi.create.content.logistics.packager.InventorySummary;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = InventorySummary.class, remap = false)
public class InventorySummaryMixin {

    // InventorySummary.add stores the raw ItemStack reference in BigItemStack.stack.
    // When a vanilla chest slot is extracted from, ContainerHelper.removeItem calls
    // slot_stack.split → shrink, mutating the stack in-place (count→0, getItem()→AIR).
    // The stored reference then reports AIR, causing getCountOf to return 0 for up to
    // 20 ticks (SUMMARIES TTL).  Passing a copy breaks the aliasing.
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
