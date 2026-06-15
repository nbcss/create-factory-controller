package io.github.nbcss.mixin;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.StockCheckingBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.LogisticalStockRequestPacket;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import io.github.nbcss.content.factorycontroller.item.ProductionPatternItem;
import io.github.nbcss.content.factorycontroller.item.ProductionTarget;
import io.github.nbcss.content.factorycontroller.production.OrderableGaugeRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

/**
 * Item 4 (display side): when a Stock Keeper answers a client's stock request, append one infinite
 * Promise-Blueprint stack per passive gauge exposed on the keeper's network, so they show in the normal item
 * list and can be ordered alongside real items. A {@link InventorySummary#copy() copy} of the recent summary
 * is augmented and sent, so the cached fulfillment summary is never polluted with the virtual stacks.
 */
@Mixin(LogisticalStockRequestPacket.class)
public abstract class LogisticalStockRequestPacketMixin {

    @Inject(method = "applySettings", at = @At("HEAD"), cancellable = true)
    private void cfc$injectBlueprints(ServerPlayer player, StockCheckingBlockEntity be, CallbackInfo ci) {
        if (!(be instanceof StockTickerBlockEntity) || !(be.getLevel() instanceof ServerLevel level)) return;
        UUID network = be.behaviour == null ? null : be.behaviour.freqId;
        if (network == null) return;

        long now = level.getServer().overworld().getGameTime();
        List<OrderableGaugeRegistry.Entry> patterns = OrderableGaugeRegistry.patternFor(network, now);
        if (patterns.isEmpty()) return;   // nothing to add → let the vanilla response proceed

        InventorySummary summary = be.getRecentSummary().copy();
        for (OrderableGaugeRegistry.Entry e : patterns) {
            ProductionTarget target = new ProductionTarget(e.network(), e.patternId(), e.display());
            summary.add(new BigItemStack(ProductionPatternItem.of(target), BigItemStack.INF));
        }
        summary.divideAndSendTo(player, be.getBlockPos());
        ci.cancel();
    }
}
