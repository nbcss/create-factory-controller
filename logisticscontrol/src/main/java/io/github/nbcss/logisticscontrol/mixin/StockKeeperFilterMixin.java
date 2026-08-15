package io.github.nbcss.logisticscontrol.mixin;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.CraftableBigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.StockKeeperRequestScreen;
import io.github.nbcss.logisticscontrol.content.compat.fluids.FluidCompat;
import io.github.nbcss.logisticscontrol.content.helper.FilterOrderCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * Carries each JEI-ordered recipe's output as its package filter. Non-crafting recipes (saw/press/mixing) are dropped by
 * Create's sendIt, so append them as orderedCrafts entries (re-packager splits one box per recipe) and encode their
 * outputs; crafting recipes stay in orderedCrafts and get their filter derived from the pattern in the re-packager.
 */
@Mixin(StockKeeperRequestScreen.class)
public abstract class StockKeeperFilterMixin {

    @Shadow public List<BigItemStack> itemsToOrder;
    @Shadow public List<CraftableBigItemStack> recipesToOrder;
    @Shadow boolean encodeRequester;

    @ModifyVariable(method = "sendIt", at = @At("STORE"), name = "order")
    private PackageOrderWithCrafts clc$encodeFilter(PackageOrderWithCrafts order) {
        if (encodeRequester || recipesToOrder == null || recipesToOrder.isEmpty()) return order;
        Level level = Minecraft.getInstance().level;
        if (level == null) return order;
        List<PackageOrderWithCrafts.CraftingEntry> crafts = new ArrayList<>(order.orderedCrafts());
        List<ItemStack> nonCraftOutputs = new ArrayList<>();
        for (CraftableBigItemStack cbis : recipesToOrder) {
            if (cbis.stack == null || cbis.stack.isEmpty() || cbis.recipe instanceof CraftingRecipe
                || FluidCompat.isFluidFilter(cbis.stack)) continue;   // a fluid output is not a valid filter
            List<BigItemStack> pattern = clc$resolveNonCraftingPattern(cbis);
            if (pattern.stream().allMatch(b -> b.stack.isEmpty())) continue;
            int outputCount = Math.max(1, cbis.getOutputCount(level));
            int count = Math.max(1, cbis.count / outputCount);
            crafts.add(new PackageOrderWithCrafts.CraftingEntry(new PackageOrder(pattern), count));
            nonCraftOutputs.add(cbis.stack.copyWithCount(1));
        }
        if (nonCraftOutputs.isEmpty()) return order;
        return FilterOrderCodec.encodeList(new PackageOrderWithCrafts(order.orderedStacks(), crafts), nonCraftOutputs);
    }

    @Unique
    private List<BigItemStack> clc$resolveNonCraftingPattern(CraftableBigItemStack cbis) {
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
}
