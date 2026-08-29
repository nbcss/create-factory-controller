package io.github.nbcss.logisticscontrol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.repackager.PackageRepackageHelper;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import io.github.nbcss.logisticscontrol.CreateLogisticsControl;
import io.github.nbcss.logisticscontrol.content.helper.CraftOutputs;
import io.github.nbcss.logisticscontrol.content.helper.NonCraftGroups;
import io.github.nbcss.logisticscontrol.content.helper.PackageFilter;
import io.github.nbcss.logisticscontrol.content.helper.RecipeFilters;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(value = PackageRepackageHelper.class, remap = false)
public abstract class FilterPackageRepackageMixin {
    @Shadow protected Map<Integer, List<ItemStack>> collectedPackages;

    @WrapOperation(method = "repack", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/logistics/packager/repackager/PackageRepackageHelper;repackBasedOnRecipes(Lcom/simibubi/create/content/logistics/packager/InventorySummary;Lcom/simibubi/create/content/logistics/stockTicker/PackageOrderWithCrafts;Ljava/lang/String;Lnet/minecraft/util/RandomSource;)Ljava/util/List;"))
    private List<BigItemStack> clc$splitRecipes(PackageRepackageHelper self, InventorySummary summary,
            PackageOrderWithCrafts orderContext, String address, RandomSource r, Operation<List<BigItemStack>> original,
            @Local(argsOnly = true) int orderId) {
        List<List<BigItemStack>> groups = null;
        PackageFilter single = null;
        List<ItemStack> craftOutputs = List.of();
        List<ItemStack> inputs = collectedPackages.get(orderId);
        if (inputs != null)
            for (ItemStack box : inputs) {
                NonCraftGroups ncg = box.get(CreateLogisticsControl.NONCRAFT_GROUPS.get());
                if (ncg != null && !ncg.groups().isEmpty()) groups = ncg.groups();
                if (single == null) {
                    PackageFilter f = box.get(CreateLogisticsControl.PACKAGE_FILTER.get());
                    if (f != null) single = f;
                }
                if (craftOutputs.isEmpty()) {
                    CraftOutputs co = box.get(CreateLogisticsControl.CRAFT_OUTPUTS.get());
                    if (co != null && !co.outputs().isEmpty()) craftOutputs = co.outputs();
                }
            }

        List<BigItemStack> result = original.call(self, summary, orderContext, address, r);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ServerLevel level = server == null ? null : server.overworld();
        int craftingCount = orderContext.orderedCrafts().size();
        for (int i = 0; i < result.size() && i < craftingCount; i++) {
            BigItemStack bis = result.get(i);
            if (bis == null || bis.stack.isEmpty()) continue;

            ItemStack prefer = i < craftOutputs.size() && !craftOutputs.get(i).isEmpty() ? craftOutputs.get(i)
                : single == null ? ItemStack.EMPTY : single.stack();
            ItemStack f = !prefer.isEmpty() ? prefer
                : level == null ? ItemStack.EMPTY
                : RecipeFilters.craftingResult(orderContext.orderedCrafts().get(i).pattern().stacks(), level);
            if (!f.isEmpty()) bis.stack.set(CreateLogisticsControl.PACKAGE_FILTER.get(), PackageFilter.of(f));
        }

        List<BigItemStack> combined = new ArrayList<>(result);
        if (groups != null)
            for (List<BigItemStack> group : groups)
                combined.addAll(clc$bundleGroup(summary, group));
        return combined;
    }

    @Inject(method = "repack", at = @At("RETURN"))
    private void clc$propagateSingle(int orderId, RandomSource r, CallbackInfoReturnable<List<BigItemStack>> cir) {
        List<BigItemStack> out = cir.getReturnValue();
        if (out == null || out.isEmpty()) return;
        List<ItemStack> inputs = collectedPackages.get(orderId);
        if (inputs == null) return;
        boolean hadRecipes = false;
        PackageFilter single = null;
        for (ItemStack box : inputs) {
            PackageItem.PackageOrderData data = box.get(AllDataComponents.PACKAGE_ORDER_DATA);
            if (data != null && data.orderContext() != null && !data.orderContext().orderedCrafts().isEmpty())
                hadRecipes = true;
            if (box.has(CreateLogisticsControl.NONCRAFT_GROUPS.get())) hadRecipes = true;
            if (single == null) {
                PackageFilter f = box.get(CreateLogisticsControl.PACKAGE_FILTER.get());
                if (f != null) single = f;
            }
        }
        if (hadRecipes || single == null) return;   // recipe boxes were filtered in the wrap; leftovers stay unfiltered
        for (BigItemStack bis : out)
            if (bis != null && !bis.stack.isEmpty())
                bis.stack.set(CreateLogisticsControl.PACKAGE_FILTER.get(), single);
    }

    /** A group is [output, ...consolidated ingredient totals]; consume the totals out of the summary and pack them into
     *  as few filtered boxes as fit. */
    private static List<BigItemStack> clc$bundleGroup(InventorySummary summary, List<BigItemStack> group) {
        if (group.size() < 2) return List.of();
        ItemStack output = group.getFirst().stack;
        List<ItemStack> flat = new ArrayList<>();
        for (int i = 1; i < group.size(); i++) {
            BigItemStack ing = group.get(i);
            if (ing.stack.isEmpty() || ing.count <= 0) continue;
            int take = Math.min(ing.count, summary.getCountOf(ing.stack));
            if (take <= 0) continue;
            summary.add(ing.stack, -take);
            for (int rem = take; rem > 0; ) {
                int c = Math.min(rem, ing.stack.getMaxStackSize());
                flat.add(ing.stack.copyWithCount(c));
                rem -= c;
            }
        }
        List<BigItemStack> boxes = new ArrayList<>();
        if (flat.isEmpty()) return boxes;
        ItemStackHandler target = new ItemStackHandler(9);
        int slot = 0;
        for (ItemStack s : flat) {
            target.setStackInSlot(slot++, s);
            if (slot >= 9) {
                boxes.add(clc$makeFilteredBox(target, output));
                target = new ItemStackHandler(9);
                slot = 0;
            }
        }
        if (slot > 0) boxes.add(clc$makeFilteredBox(target, output));
        return boxes;
    }

    private static BigItemStack clc$makeFilteredBox(ItemStackHandler target, ItemStack output) {
        ItemStack box = PackageItem.containing(target);
        box.set(CreateLogisticsControl.PACKAGE_FILTER.get(), PackageFilter.of(output));
        return new BigItemStack(box, 1);
    }
}
