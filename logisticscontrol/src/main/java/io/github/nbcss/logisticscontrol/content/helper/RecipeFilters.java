package io.github.nbcss.logisticscontrol.content.helper;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.logistics.BigItemStack;
import io.github.nbcss.logisticscontrol.content.compat.fluids.FluidCompat;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class RecipeFilters {
    private RecipeFilters() {}

    public static ItemStack craftingResult(List<BigItemStack> pattern, Level level) {
        List<ItemStack> items = new ArrayList<>(9);
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            ItemStack s = i < pattern.size() ? pattern.get(i).stack : ItemStack.EMPTY;
            if (s.isEmpty()) {
                items.add(ItemStack.EMPTY);
            } else {
                items.add(s.copyWithCount(1));
                any = true;
            }
        }
        if (!any) return ItemStack.EMPTY;
        CraftingInput input = CraftingInput.of(3, 3, items);
        RegistryAccess ra = level.registryAccess();
        ItemStack result = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
            .map(h -> h.value().assemble(input, ra)).orElse(ItemStack.EMPTY);
        if (result.isEmpty())
            result = AllRecipeTypes.MECHANICAL_CRAFTING.find(input, level).map(h -> h.value().assemble(input, ra)).orElse(ItemStack.EMPTY);
        return FluidCompat.isFluidFilter(result) ? ItemStack.EMPTY : result;   // a fluid is not a valid output filter
    }
}
