package io.github.nbcss.logisticscontrol.content.helper;

import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.logistics.BigItemStack;
import io.github.nbcss.logisticscontrol.content.compat.fluids.FluidCompat;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class RecipeFilters {
    private RecipeFilters() {}

    /**
     * Create's first-match output for a crafting {@code pattern} (regular before mechanical), matching
     * {@link com.simibubi.create.content.kinetics.crafter.RecipeGridHandler#tryToApplyRecipe}. Only a best-effort guess
     * for when an order carried no filter — a carried filter is kept verbatim upstream, never re-derived here.
     */
    public static ItemStack craftingResult(List<BigItemStack> pattern, Level level) {
        CraftingInput input = buildInput(pattern);
        if (input == null) return ItemStack.EMPTY;
        RegistryAccess registries = level.registryAccess();
        ItemStack result = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
            .map(h -> h.value().assemble(input, registries)).orElse(ItemStack.EMPTY);
        if (result.isEmpty())
            result = AllRecipeTypes.MECHANICAL_CRAFTING.find(input, level).map(h -> h.value().assemble(input, registries)).orElse(ItemStack.EMPTY);
        return FluidCompat.isFluidFilter(result) || !PackageFilter.canLabel(result) ? ItemStack.EMPTY : result;
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
