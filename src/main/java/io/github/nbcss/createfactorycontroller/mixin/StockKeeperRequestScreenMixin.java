package io.github.nbcss.createfactorycontroller.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.content.equipment.clipboard.ClipboardEntry;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.AddressEditBox;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.helper.FilterOrderCodec;
import com.simibubi.create.foundation.gui.widget.IconButton;
import io.github.nbcss.createfactorycontroller.ClientConfig;
import io.github.nbcss.createfactorycontroller.content.compat.DeployerCompat;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.gui.screen.IngredientCheckClient;
import io.github.nbcss.createfactorycontroller.content.gui.screen.ProductionOrdersScreen;
import io.github.nbcss.createfactorycontroller.content.item.ProductionPatternItem;
import io.github.nbcss.createfactorycontroller.content.packet.RegisterOrderNotificationPacket;
import io.github.nbcss.createfactorycontroller.content.render.SpriteNumbersRender;
import net.neoforged.neoforge.network.PacketDistributor;
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
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Hides the stock-count label on Promise Blueprint entries in the Stock Keeper item list
 */
@Mixin(StockKeeperRequestScreen.class)
public abstract class StockKeeperRequestScreenMixin {

    @Shadow int windowHeight;
    @Shadow public List<BigItemStack> itemsToOrder;
    @Shadow public List<CraftableBigItemStack> recipesToOrder;
    @Shadow boolean encodeRequester;
    @Shadow @Nullable List<List<ClipboardEntry>> clipboardItem;
    @Shadow StockTickerBlockEntity blockEntity;
    @Shadow public AddressEditBox addressBox;

    /** Non-crafting recipes ([+]'d saw/press/mixing) are dropped by Create's sendIt (crafting-only orderedCrafts), so
     *  each would merge into one package with the first order's filter. Append them as real orderedCrafts entries so the
     *  re-packager splits one box per recipe, and carry their outputs so each box gets its own filter. Crafting recipes
     *  are already in orderedCrafts and get their filter derived from the pattern in the re-packager. */
    @ModifyVariable(method = "sendIt", at = @At("STORE"), name = "order")
    private PackageOrderWithCrafts cfc$encodeFilter(PackageOrderWithCrafts order) {
        if (encodeRequester || recipesToOrder == null || recipesToOrder.isEmpty()) return order;
        Level level = Minecraft.getInstance().level;
        if (level == null) return order;
        List<PackageOrderWithCrafts.CraftingEntry> crafts = new ArrayList<>(order.orderedCrafts());
        List<ItemStack> nonCraftOutputs = new ArrayList<>();
        for (CraftableBigItemStack cbis : recipesToOrder) {
            if (cbis.stack == null || cbis.stack.isEmpty() || cbis.recipe instanceof CraftingRecipe
                || FluidCompat.isFluidFilter(cbis.stack)) continue;   // a fluid output is not a valid filter
            List<BigItemStack> pattern = cfc$resolveNonCraftingPattern(cbis);
            if (pattern.stream().allMatch(b -> b.stack.isEmpty())) continue;
            int outputCount = Math.max(1, cbis.getOutputCount(level));
            int count = Math.max(1, cbis.count / outputCount);
            crafts.add(new PackageOrderWithCrafts.CraftingEntry(new PackageOrder(pattern), count));
            nonCraftOutputs.add(cbis.stack.copyWithCount(1));
        }
        if (nonCraftOutputs.isEmpty()) return order;
        return FilterOrderCodec.encodeList(new PackageOrderWithCrafts(order.orderedStacks(), crafts), nonCraftOutputs);
    }

    /** Resolve a non-crafting recipe's ingredients to the concrete in-stock stacks that were ordered, laid out one per
     *  pattern slot, so the re-packager can consume them from the summary. */
    @Unique
    private List<BigItemStack> cfc$resolveNonCraftingPattern(CraftableBigItemStack cbis) {
        List<BigItemStack> pattern = new ArrayList<>();
        for (Ingredient ing : cbis.recipe.getIngredients()) {
            ItemStack chosen = ItemStack.EMPTY;
            if (!ing.isEmpty()) {
                for (BigItemStack b : itemsToOrder)
                    if (!b.stack.isEmpty() && ing.test(b.stack)) { chosen = b.stack.copyWithCount(1); break; }
                if (chosen.isEmpty()) {
                    ItemStack[] items = ing.getItems();
                    if (items.length > 0 && !items[0].isEmpty()) chosen = items[0].copyWithCount(1);
                }
            }
            pattern.add(new BigItemStack(chosen));
        }
        return pattern;
    }

    /** Before the order is sent, register the player for status toasts on this (network, address) — but only when the
     *  order actually contains a Production Blueprint (otherwise no ProductionOrder is created). The subscribe packet
     *  is sent first, so the server has the pending subscription ready when it creates the order. */
    @Inject(method = "sendIt", at = @At("HEAD"))
    private void cfc$subscribeForOrderNotification(CallbackInfo ci) {
        if (blockEntity == null || blockEntity.behaviour == null || blockEntity.behaviour.freqId == null) return;
        if (itemsToOrder.stream().noneMatch(b -> ProductionPatternItem.isPattern(b.stack))) return;
        PacketDistributor.sendToServer(
            new RegisterOrderNotificationPacket(blockEntity.behaviour.freqId, addressBox.getValue()));
    }

    @WrapOperation(method = "renderItemEntry", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/logistics/stockTicker/StockKeeperRequestScreen;drawItemCount(Lnet/minecraft/client/gui/GuiGraphics;II)V"))
    private void cfc$blueprintCount(StockKeeperRequestScreen self, GuiGraphics graphics, int count, int customCount,
                                    Operation<Void> original,
                                    @Local(argsOnly = true) BigItemStack entry,
                                    @Local(argsOnly = true, ordinal = 1) boolean isRenderingOrders) {
        if (ProductionPatternItem.isPattern(entry.stack)) {
            if (!isRenderingOrders) return;   // stock-list blueprint: infinite supply, no count
            if (FluidCompat.isFluidFilter(ProductionPatternItem.displayOf(entry.stack))) {
                SpriteNumbersRender.drawCount(graphics, SpriteNumbersRender.abbreviate(customCount) + "b", 0, 0);
                return;
            }
        }
        original.call(self, graphics, count, customCount);
    }

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
