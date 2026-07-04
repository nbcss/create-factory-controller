package io.github.nbcss.createfactorycontroller.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.serialization.Codec;
import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.promise.ControllerPromise;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets an ignore-data gauge's request promise be settled by ANY data-variant of its item type.
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

    // ── Durable ControllerPromise round-trip ────────────────────────────────────
    // Both write() and read() serialize the promise list via Codec.list(RequestPromise.CODEC). We swap in our
    // CFC_CODEC (transparent for base promises; carries our owner/address/ttl/age and rebuilds the subclass) so a
    // ControllerPromise survives save/load.

    private static final String CODEC_FIELD =
        "Lcom/simibubi/create/content/logistics/packagerLink/RequestPromise;CODEC:Lcom/mojang/serialization/Codec;";

    @Redirect(method = "write", at = @At(value = "FIELD", target = CODEC_FIELD, opcode = Opcodes.GETSTATIC))
    private Codec<RequestPromise> cfc$writePromiseCodec() {
        return ControllerPromise.CFC_CODEC;
    }

    @Redirect(method = "read", at = @At(value = "FIELD", target = CODEC_FIELD, opcode = Opcodes.GETSTATIC))
    private static Codec<RequestPromise> cfc$readPromiseCodec() {
        return ControllerPromise.CFC_CODEC;
    }
}
