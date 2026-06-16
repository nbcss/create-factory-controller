package io.github.nbcss.createfactorycontroller.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import io.github.nbcss.createfactorycontroller.content.gui.IngredientCheckClient;
import io.github.nbcss.createfactorycontroller.content.item.ProductionPatternItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Hides the stock-count label on Promise Blueprint entries in the Stock Keeper item list, and adds the Send-button
 * ingredient pre-check tooltip (see {@link IngredientCheckClient}).
 */
@Mixin(StockKeeperRequestScreen.class)
public abstract class StockKeeperRequestScreenMixin {

    @Shadow int windowHeight;
    @Shadow public List<BigItemStack> itemsToOrder;

    @WrapWithCondition(method = "renderItemEntry", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;drawItemCount(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private boolean cfc$hideBlueprintStockCount(StockKeeperRequestScreen self, GuiGraphics graphics, int count,
                                                int customCount,
                                                @Local(argsOnly = true) BigItemStack entry,
                                                @Local(argsOnly = true, ordinal = 1) boolean isRenderingOrders) {
        // Skip only the stock-list count for blueprints; keep it for the order preview.
        return isRenderingOrders || !ProductionPatternItem.isPattern(entry.stack);
    }

    /** When hovering the Send button, show the ingredient-availability tooltip for any staged Production Patterns. */
    @Inject(method = "renderForeground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void cfc$ingredientCheckTooltip(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        StockKeeperRequestScreen self = (StockKeeperRequestScreen) (Object) this;
        int cx = self.getGuiLeft() + 143, cy = self.getGuiTop() + windowHeight - 39;   // Create's confirm hitbox
        if (mouseX < cx || mouseX >= cx + 78 || mouseY < cy || mouseY >= cy + 18) return;
        BlockPos keeperPos = self.getMenu().contentHolder.getBlockPos();
        IngredientCheckClient.onSendHover(keeperPos, itemsToOrder, graphics, mouseX, mouseY);
    }
}
