package io.github.nbcss.logisticscontrol.content.helper;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.logistics.BigItemStack;
import io.github.nbcss.logisticscontrol.content.compat.fluids.FluidCompat;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class RecipeFilters {
    private RecipeFilters() {}

    public static ItemStack craftingResult(List<BigItemStack> pattern, Level level) {
        return craftingResult(pattern, ItemStack.EMPTY, level);
    }

    /**
     * The output for a crafting {@code pattern}. When the pattern is ambiguous (several recipes share it) and
     * {@code prefer} is one of their outputs, returns {@code prefer} — so the recipe the player/gauge actually chose is
     * pinned instead of being re-derived by first-match. Otherwise falls back to Create's first-match (regular before
     * mechanical), matching {@link com.simibubi.create.content.kinetics.crafter.RecipeGridHandler#tryToApplyRecipe}.
     */
    public static ItemStack craftingResult(List<BigItemStack> pattern, ItemStack prefer, Level level) {
        CraftingInput input = buildInput(pattern);
        if (input == null) return ItemStack.EMPTY;
        RegistryAccess registries = level.registryAccess();
        RecipeManager recipes = level.getRecipeManager();

        if (prefer != null && !prefer.isEmpty()) {
            for (RecipeHolder<CraftingRecipe> holder : recipes.getRecipesFor(RecipeType.CRAFTING, input, level)) {
                ItemStack out = holder.value().assemble(input, registries);
                if (!out.isEmpty() && ItemStack.isSameItem(out, prefer)) return out;
            }
            RecipeType<MechanicalCraftingRecipe> mechanicalType = AllRecipeTypes.MECHANICAL_CRAFTING.getType();
            for (RecipeHolder<MechanicalCraftingRecipe> holder : recipes.getRecipesFor(mechanicalType, input, level)) {
                ItemStack out = holder.value().assemble(input, registries);
                if (!out.isEmpty() && ItemStack.isSameItem(out, prefer)) return out;
            }
        }

        ItemStack result = recipes.getRecipeFor(RecipeType.CRAFTING, input, level)
            .map(h -> h.value().assemble(input, registries)).orElse(ItemStack.EMPTY);
        if (result.isEmpty())
            result = AllRecipeTypes.MECHANICAL_CRAFTING.find(input, level).map(h -> h.value().assemble(input, registries)).orElse(ItemStack.EMPTY);
        return FluidCompat.isFluidFilter(result) ? ItemStack.EMPTY : result;   // a fluid is not a valid output filter
    }

    private static CraftingInput buildInput(List<BigItemStack> pattern) {
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
        return any ? CraftingInput.of(3, 3, items) : null;
    }
}
