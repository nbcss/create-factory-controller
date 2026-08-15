package io.github.nbcss.logisticscontrol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import io.github.nbcss.logisticscontrol.content.helper.FilterDispatch;
import io.github.nbcss.logisticscontrol.content.helper.JeiFilterCarry;
import net.liukrast.deployer.lib.logistics.stockTicker.GenericOrderRequestPacket;
import net.liukrast.deployer.lib.mixinExtensions.STBEExtension;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;

@Mixin(value = GenericOrderRequestPacket.class, remap = false)
public abstract class GenericOrderRequestPacketMixin {
    @WrapOperation(method = "applySettings(Lnet/minecraft/server/level/ServerPlayer;Lcom/simibubi/create/content/logistics/stockTicker/StockTickerBlockEntity;)V", at = @At(value = "INVOKE",
        target = "Lnet/liukrast/deployer/lib/mixinExtensions/STBEExtension;deployer$broadcastAllPackageRequest(Lcom/simibubi/create/content/logistics/stockTicker/PackageOrderWithCrafts;Lcom/simibubi/create/content/logistics/packagerLink/LogisticallyLinkedBehaviour$RequestType;Ljava/util/Map;Ljava/lang/String;)Z"))
    private boolean clc$stampDeployerJeiFilter(STBEExtension be, PackageOrderWithCrafts order, RequestType type,
                                               Map<?, ?> types, String address, Operation<Boolean> original) {
        PackageOrderWithCrafts cleaned = JeiFilterCarry.prepare(order, ((BlockEntity) be).getLevel());
        if (cleaned == null)
            return original.call(be, order, type, types, address);
        try {
            return original.call(be, cleaned, type, types, address);
        } finally {
            FilterDispatch.clear();
        }
    }
}
