package io.github.nbcss.mixin;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import io.github.nbcss.content.factorycontroller.item.ProductionPatternItem;
import io.github.nbcss.content.factorycontroller.production.ProductionOrderManager;
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

/**
 * Item 5 (real path): the Deployer keeper UI sends orders through {@code GenericOrderRequestPacket} →
 * {@code LogisticsGenericManager.broadcastAllPackageRequest}, which dispatches the default item order directly via
 * {@code LogisticsManager} (never the BE's {@code broadcastPackageRequest}). So we intercept it here: split off
 * Promise Blueprints from the default order, register the Promise Order + ship the real items, then let the
 * original handle any non-item (typed) orders ordered alongside.
 */
@Mixin(value = LogisticsGenericManager.class, remap = false)
public abstract class LogisticsGenericManagerMixin {

    @Inject(method = "broadcastAllPackageRequest", at = @At("HEAD"), cancellable = true)
    private static void cfc$splitProductionOrder(PackageOrderWithCrafts defaultOrder, UUID freqId, RequestType type,
                                              Map<StockInventoryType<?, ?, ?>, GenericOrderContained<?>> requests,
                                              String address, CallbackInfoReturnable<Boolean> cir) {
        boolean hasPattern = false;
        for (BigItemStack b : defaultOrder.orderedStacks().stacks())
            if (ProductionPatternItem.isPattern(b.stack)) { hasPattern = true; break; }
        if (!hasPattern) return;   // no blueprints → normal Deployer dispatch

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Returns false when the blueprints couldn't be resolved to live gauges → let the original dispatch run
        // (real items still ship; the dead blueprints simply produce nothing).
        if (!ProductionOrderManager.interceptProductionOrder(server, freqId, type, defaultOrder, address)) return;

        // Dispatch any typed (e.g. fluid) orders ordered alongside, with an empty default to avoid re-entering
        // the blueprint branch (the recursive call sees no blueprints and runs the vanilla typed dispatch).
        if (!requests.isEmpty())
            LogisticsGenericManager.broadcastAllPackageRequest(PackageOrderWithCrafts.empty(), freqId, type, requests, address);
        cir.setReturnValue(true);
    }
}
