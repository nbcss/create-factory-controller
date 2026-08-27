package io.github.nbcss.logisticscontrol.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import io.github.nbcss.logisticscontrol.ServerConfig;
import io.github.nbcss.logisticscontrol.content.helper.CrafterRecipeFilter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * When a crafter resolves its recipe, honour the recipe filter the Filter Link pinned on it: pick the recipe whose
 * output matches the filter instead of Create's first-match. Falls back to the original resolution when no filter is
 * set or no matching recipe fits the grid.
 */
@Mixin(value = MechanicalCrafterBlockEntity.class, remap = false)
public abstract class MechanicalCrafterFilterMixin {

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/kinetics/crafter/RecipeGridHandler;tryToApplyRecipe(Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/kinetics/crafter/RecipeGridHandler$GroupedItems;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack clc$applyRecipeFilter(Level level, RecipeGridHandler.GroupedItems items, Operation<ItemStack> original) {
        if (ServerConfig.filterLinkEnabled()) {
            FilteringBehaviour filter = ((SmartBlockEntity) (Object) this).getBehaviour(FilteringBehaviour.TYPE);
            ItemStack want = filter == null ? ItemStack.EMPTY : filter.getFilter();
            if (!want.isEmpty()) {
                ItemStack result = CrafterRecipeFilter.resolveFiltered(level, items, want);
                if (result != null && !result.isEmpty()) return result;
            }
        }
        return original.call(level, items);
    }
}
