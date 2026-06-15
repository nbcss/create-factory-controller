package io.github.nbcss.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import io.github.nbcss.content.factorycontroller.item.ProductionPatternItem;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hides the stock-count label on Promise Blueprint entries in the Stock Keeper item list. Blueprints are injected
 * with an infinite count (so the player can order any amount), which would otherwise render a meaningless huge
 * "∞"-style number on the slot. The label is still drawn in the order preview ({@code isRenderingOrders}), where
 * the count is the actual ordered quantity.
 */
@Mixin(StockKeeperRequestScreen.class)
public abstract class StockKeeperRequestScreenMixin {

    @WrapWithCondition(method = "renderItemEntry", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;drawItemCount(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private boolean cfc$hideBlueprintStockCount(StockKeeperRequestScreen self, GuiGraphics graphics, int count,
                                                int customCount,
                                                @Local(argsOnly = true) BigItemStack entry,
                                                @Local(argsOnly = true, ordinal = 1) boolean isRenderingOrders) {
        // Skip only the stock-list count for blueprints; keep it for the order preview.
        return isRenderingOrders || !ProductionPatternItem.isPattern(entry.stack);
    }
}
