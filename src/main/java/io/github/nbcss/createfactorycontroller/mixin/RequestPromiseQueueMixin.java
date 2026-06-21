package io.github.nbcss.createfactorycontroller.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets an ignore-data gauge's request promise be settled by ANY data-variant of its item type.
 *
 * <p>An ignore-data gauge promises its produced output as a "pure" item (no NBT/components), but the real
 * output that arrives may carry data, so Create's exact-component match in {@link RequestPromiseQueue} would
 * never clear the promise — it would leak and wedge the gauge into a permanently "promised" state. We mark
 * such promises with {@link CreateFactoryController#FUZZY_PROMISE} and relax the match for them to item-only,
 * leaving all other promises (and Create's leftover/ordering logic) untouched.</p>
 */
@Mixin(value = RequestPromiseQueue.class, remap = false)
public abstract class RequestPromiseQueueMixin {

    @WrapOperation(
        method = {"itemEnteredSystem", "forceClear"},
        at = @At(value = "INVOKE",
                 target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean cfc$fuzzyMatchMarkedPromises(ItemStack promiseStack, ItemStack incoming, Operation<Boolean> original) {
        if (promiseStack.has(CreateFactoryController.FUZZY_PROMISE.get()))
            return promiseStack.is(incoming.getItem());
        return original.call(promiseStack, incoming);
    }
}
