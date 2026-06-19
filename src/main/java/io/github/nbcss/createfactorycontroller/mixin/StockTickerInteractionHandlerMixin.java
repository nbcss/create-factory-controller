package io.github.nbcss.createfactorycontroller.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler;
import io.github.nbcss.createfactorycontroller.content.production.OrderableStockAugment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Makes orderable Production Blueprints present in the Stock Keeper's <i>initial</i> stock snapshot (the one sent
 * straight from the interaction handler on GUI open, which bypasses {@code LogisticalStockRequestPacket.applySettings}).
 * Without this the patterns only appear on the first periodic refresh a tick or two later — enough to make the
 * material-list auto-order miss orderable items on the opening frame. See {@link OrderableStockAugment}.
 */
@Mixin(StockTickerInteractionHandler.class)
public abstract class StockTickerInteractionHandlerMixin {

    @WrapOperation(method = "interactWithLogisticsManagerAt", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/logistics/packager/InventorySummary;divideAndSendTo(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;)V"))
    private static void cfc$augmentInitialSummary(InventorySummary summary, ServerPlayer player, BlockPos pos,
                                                  Operation<Void> original) {
        InventorySummary augmented = summary;
        if (player.level() instanceof ServerLevel level
                && level.getBlockEntity(pos) instanceof StockTickerBlockEntity be
                && be.behaviour != null && be.behaviour.freqId != null) {
            long now = level.getServer().overworld().getGameTime();
            augmented = OrderableStockAugment.augment(summary, be.behaviour.freqId, now);
        }
        original.call(augmented, player, pos);
    }
}
