package io.github.nbcss.createfactorycontroller.mixin;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import io.github.nbcss.createfactorycontroller.content.item.ProductionPatternItem;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrderManager;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrderManager.InterceptResult;
import net.liukrast.deployer.lib.logistics.packager.StockInventoryType;
import net.liukrast.deployer.lib.logistics.packagerLink.LogisticsGenericManager;
import net.liukrast.deployer.lib.logistics.stockTicker.GenericOrderContained;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;

@Mixin(value = LogisticsGenericManager.class, remap = false)
public abstract class LogisticsGenericManagerMixin {

    @Inject(method = "broadcastAllPackageRequest", at = @At("HEAD"), cancellable = true)
    private static void cfc$splitProductionOrder(PackageOrderWithCrafts defaultOrder, UUID freqId, RequestType type,
                                              Map<StockInventoryType<?, ?, ?>, GenericOrderContained<?>> requests,
                                              String address, CallbackInfoReturnable<Boolean> cir) {
        boolean hasPattern = false;
        for (BigItemStack b : defaultOrder.orderedStacks().stacks())
            if (ProductionPatternItem.isPattern(b.stack)) { hasPattern = true; break; }
        if (!hasPattern) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) return;

        InterceptResult result = ProductionOrderManager.interceptProductionOrder(server, freqId, type, defaultOrder, address);
        if (result == InterceptResult.NOT_A_PRODUCTION_ORDER) return;

        if (result == InterceptResult.HANDLED && !requests.isEmpty())
            LogisticsGenericManager.broadcastAllPackageRequest(PackageOrderWithCrafts.empty(), freqId, type, requests, address);
        cir.setReturnValue(result == InterceptResult.HANDLED);
    }
}
