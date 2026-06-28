package io.github.nbcss.createfactorycontroller.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import com.simibubi.create.foundation.gui.widget.IconButton;
import io.github.nbcss.createfactorycontroller.ClientConfig;
import io.github.nbcss.createfactorycontroller.content.compat.DeployerCompat;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.gui.screen.IngredientCheckClient;
import io.github.nbcss.createfactorycontroller.content.gui.screen.ProductionOrdersScreen;
import io.github.nbcss.createfactorycontroller.content.item.ProductionPatternItem;
import io.github.nbcss.createfactorycontroller.content.render.SpriteNumbersRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Hides the stock-count label on Promise Blueprint entries in the Stock Keeper item list, adds the Send-button
 * ingredient pre-check tooltip (see {@link IngredientCheckClient}), and — when ordering from a material list
 * (clipboard) — substitutes a Production Order for any listed item that is short in stock but producible by an
 * orderable gauge on the network ({@link #cfc$orderMissingFromGauges}).
 */
@Mixin(StockKeeperRequestScreen.class)
public abstract class StockKeeperRequestScreenMixin {

    @Shadow int windowHeight;
    @Shadow public List<BigItemStack> itemsToOrder;
    @Shadow @Nullable List<List<ClipboardEntry>> clipboardItem;
    @Shadow StockTickerBlockEntity blockEntity;

    @WrapOperation(method = "renderItemEntry", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;drawItemCount(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void cfc$blueprintCount(StockKeeperRequestScreen self, GuiGraphics graphics, int count, int customCount,
                                    Operation<Void> original,
                                    @Local(argsOnly = true) BigItemStack entry,
                                    @Local(argsOnly = true, ordinal = 1) boolean isRenderingOrders) {
        if (ProductionPatternItem.isPattern(entry.stack)) {
            if (!isRenderingOrders) return;   // stock-list blueprint: infinite supply, no count
            // A fluid order is counted in buckets — draw the count with a "b" suffix at drawItemCount's own
            // (pose-relative) bottom-right placement, which SpriteNumbersRender mirrors.
            if (FluidCompat.isFluidFilter(ProductionPatternItem.displayOf(entry.stack))) {
                SpriteNumbersRender.drawCount(graphics, SpriteNumbersRender.abbreviate(customCount) + "b", 0, 0);
                return;
            }
        }
        original.call(self, graphics, count, customCount);
    }

    /**
     * After Create stages the in-stock amounts from the clipboard, replace each short item that an orderable gauge can
     * produce with a Production Order for the full listed amount. The production task nets against current network
     * stock (it ships the full amount once stock catches up, producing only the shortfall), so a single order both
     * pulls the existing stock and makes up the difference — see ProductionOrderManager.
     */
    @Inject(method = "requestSchematicList", at = @At("TAIL"))
    private void cfc$orderMissingFromGauges(CallbackInfo ci) {
        if (!ClientConfig.orderFromMaterialList() || clipboardItem == null) return;
        InventorySummary snapshot = blockEntity.getLastClientsideStockSnapshotAsSummary();
        for (List<ClipboardEntry> list : clipboardItem) {
            for (ClipboardEntry entry : list) {
                ItemStack item = entry.icon;
                int required = entry.itemAmount;
                if (item.isEmpty() || required <= 0) continue;
                if (snapshot.getCountOf(item) >= required) continue;   // already satisfiable from stock
                ItemStack pattern = cfc$orderablePatternFor(snapshot, item);
                if (pattern == null) continue;                          // not producible → keep Create's request
                // Replace Create's in-stock real-item entry with one Production Order for the full demand.
                itemsToOrder.removeIf(b -> !ProductionPatternItem.isPattern(b.stack)
                    && ItemStack.isSameItemSameComponents(b.stack, item));
                itemsToOrder.add(new BigItemStack(pattern.copyWithCount(1), required));
            }
        }
    }

    /** The orderable Production Blueprint in the keeper's stock snapshot that produces {@code item}, or null. */
    @Nullable
    private static ItemStack cfc$orderablePatternFor(InventorySummary snapshot, ItemStack item) {
        for (BigItemStack b : snapshot.getStacks())
            if (ProductionPatternItem.isPattern(b.stack)
                && ItemStack.isSameItemSameComponents(ProductionPatternItem.displayOf(b.stack), item))
                return b.stack;
        return null;
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

    /** The Production Orders gutter button, present only when Deployer is absent. White (vs. green on the orders page). */
    @Unique private IconButton cfc$ordersButton;

    /**
     * When Deployer is absent there's no keeper tab strip, so place a Create IconButton in the gutter (the spot
     * Deployer's tabs would occupy) that opens the standalone {@link ProductionOrdersScreen}. It carries the Production
     * Pattern icon and stays white here (green marks the orders page). With Deployer installed its tab provides this
     * entry instead, so the button is suppressed to avoid a duplicate.
     */
    @Inject(method = "renderForeground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
    private void cfc$renderProductionOrdersButton(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (DeployerCompat.isLoaded()) return;
        StockKeeperRequestScreen self = (StockKeeperRequestScreen) (Object) this;
        if (cfc$ordersButton == null) {
            cfc$ordersButton = new IconButton(0, 0, ProductionOrdersScreen.PRODUCTION_ORDER_ICON);
            cfc$ordersButton.setToolTip(Component.translatable("createfactorycontroller.gui.production_orders"));
            cfc$ordersButton.withCallback(() ->
                Minecraft.getInstance().setScreen(new ProductionOrdersScreen(self, self.getMenu())));
        }
        cfc$ordersButton.setX(ProductionOrdersScreen.gutterButtonX(self.getGuiLeft()));   // reposition after resize
        cfc$ordersButton.setY(ProductionOrdersScreen.gutterButtonY(self.getGuiTop()));
        cfc$ordersButton.render(graphics, mouseX, mouseY, partialTicks);   // draws the button + its own hover tooltip
    }

    /** Opens the Production Orders page when the gutter button (Deployer-absent entry point) is clicked. */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void cfc$openProductionOrders(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (DeployerCompat.isLoaded()) return;
        if (cfc$ordersButton != null && cfc$ordersButton.mouseClicked(mouseX, mouseY, button))
            cir.setReturnValue(true);
    }
}
