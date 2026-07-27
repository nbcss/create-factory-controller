package io.github.nbcss.createfactorycontroller.mixin;

import com.simibubi.create.content.logistics.packager.IdentifiedInventory;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import io.github.nbcss.createfactorycontroller.content.production.OrderableStockAugment;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrderManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Add orderable Production Blueprints to logistics network
 */
@Mixin(value = LogisticsManager.class, remap = false)
public abstract class LogisticsManagerMixin {

    @Inject(method = "createSummaryOfNetwork", at = @At("RETURN"))
    private static void cfc$injectBlueprints(UUID network, CallbackInfoReturnable<InventorySummary> cir) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) return;
        InventorySummary summary = cir.getReturnValue();
        if (summary == null) return;
        OrderableStockAugment.augmentInPlace(summary, network, server.overworld().getGameTime());
    }

    @Inject(method = "broadcastPackageRequest", at = @At("HEAD"), cancellable = true)
    private static void cfc$interceptProductionOrder(UUID network, RequestType type, PackageOrderWithCrafts order,
                                                     IdentifiedInventory inv, String address,
                                                     CallbackInfoReturnable<Boolean> cir) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSameThread()) return;
        switch (ProductionOrderManager.interceptProductionOrder(server, network, type, order, address)) {
            case HANDLED -> cir.setReturnValue(true);
            case REJECTED -> cir.setReturnValue(false);
            case NOT_A_PRODUCTION_ORDER -> { }
        }
    }
}
