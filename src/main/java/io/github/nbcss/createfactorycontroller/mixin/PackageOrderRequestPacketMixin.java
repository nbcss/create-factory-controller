package io.github.nbcss.createfactorycontroller.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderRequestPacket;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.helper.CfcFilterDispatch;
import io.github.nbcss.createfactorycontroller.content.helper.JeiFilterCarry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = PackageOrderRequestPacket.class, remap = false)
public abstract class PackageOrderRequestPacketMixin {
    @WrapOperation(method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;Lcom/simibubi/create/content/logistics/stockTicker/StockTickerBlockEntity;)V", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/logistics/stockTicker/StockTickerBlockEntity;broadcastPackageRequest(Lcom/simibubi/create/content/logistics/packagerLink/LogisticallyLinkedBehaviour$RequestType;Lcom/simibubi/create/content/logistics/stockTicker/PackageOrderWithCrafts;Lcom/simibubi/create/content/logistics/packager/IdentifiedInventory;Ljava/lang/String;)Z"))
    private boolean cfc$stampJeiFilter(StockTickerBlockEntity be, RequestType type, PackageOrderWithCrafts order,
                                       IdentifiedInventory ignored, String address, Operation<Boolean> original) {
        PackageOrderWithCrafts cleaned = JeiFilterCarry.prepare(order, be.getLevel());
        if (cleaned == null)
            return original.call(be, type, order, ignored, address);
        try {
            return original.call(be, type, cleaned, ignored, address);
        } finally {
            CfcFilterDispatch.clear();
        }
    }
}
