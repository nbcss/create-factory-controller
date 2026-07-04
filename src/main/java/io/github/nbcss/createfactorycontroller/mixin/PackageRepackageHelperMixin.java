package io.github.nbcss.createfactorycontroller.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.repackager.PackageRepackageHelper;
import net.minecraft.core.NonNullList;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Lets Create's Re-Packager pack a crafting grid with <b>stacked</b> slots instead of one item per slot, so large
 * and batched mechanical-crafting recipes fit in a package and their ingredients aren't truncated and silently lost.
 */
@Mixin(PackageRepackageHelper.class)
public class PackageRepackageHelperMixin {

    /**
     * Consolidates the recipe pattern (one item per cell) into stacked slots
     */
    @ModifyExpressionValue(method = "repackBasedOnRecipes",
        at = @At(value = "INVOKE",
            target = "Lcom/simibubi/create/content/logistics/stockTicker/PackageOrder;stacks()Ljava/util/List;",
            ordinal = 1))
    private List<BigItemStack> cfc$stackPackedSlots(List<BigItemStack> original) {
        List<ItemStack> cells = original.stream().map(b -> b.stack.copy()).toList();
        NonNullList<ItemStack> slots = NonNullList.withSize(cells.size(), ItemStack.EMPTY);
        for (ItemStack stack : cells) {
            for (int i = 0; i < slots.size(); i++) {
                ItemStack slot = slots.get(i);
                if (slot.isEmpty()) { slots.set(i, stack); break; }
                if (ItemStack.isSameItemSameComponents(stack, slot)) {
                    int canPut = slot.getMaxStackSize() - slot.getCount();
                    slot.setCount(slot.getCount() + Mth.clamp(stack.getCount(), 0, canPut));
                    stack.setCount(Math.max(stack.getCount() - canPut, 0));
                    if (stack.getCount() == 0) break;
                }
            }
        }
        return slots.stream().map(BigItemStack::new).toList();
    }

    /** Keeps the (now stacked) per-slot count when filling the package, instead of forcing it back to 1. */
    @WrapOperation(method = "repackBasedOnRecipes",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;copyWithCount(I)Lnet/minecraft/world/item/ItemStack;",
            ordinal = 0))
    private ItemStack cfc$keepStackedCount(ItemStack instance, int count, Operation<ItemStack> original) {
        return instance.copy();
    }
}
