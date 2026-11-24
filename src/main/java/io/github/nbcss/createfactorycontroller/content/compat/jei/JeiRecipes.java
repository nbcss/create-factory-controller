package io.github.nbcss.createfactorycontroller.content.compat.jei;

import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Screen-side access to JEI's recipe pages
 */
public final class JeiRecipes {

    interface Handler {
        boolean hasRecipes(Object ingredient);
        void showRecipes(Object ingredient);
    }

    @Nullable private static Handler handler;
    private static ItemStack lastFilter = ItemStack.EMPTY;
    private static boolean lastHasRecipes;

    private JeiRecipes() {}

    static void setHandler(@Nullable Handler h) {
        handler = h;
        lastFilter = ItemStack.EMPTY;
    }

    /** Whether JEI has a recipe page producing the gauge filter's item or fluid. */
    public static boolean hasRecipes(ItemStack filter) {
        if (handler == null || filter.isEmpty()) return false;
        if (!ItemStack.isSameItemSameComponents(filter, lastFilter)) {
            lastFilter = filter.copy();
            lastHasRecipes = handler.hasRecipes(ingredientOf(filter));
        }
        return lastHasRecipes;
    }

    /** Opens JEI's recipe pages producing the gauge filter; false when there are none. */
    public static boolean showRecipes(ItemStack filter) {
        if (!hasRecipes(filter) || handler == null) return false;
        handler.showRecipes(ingredientOf(filter));
        return true;
    }

    private static Object ingredientOf(ItemStack filter) {
        return FluidCompat.isFluidFilter(filter) ? FluidCompat.getFilterFluid(filter) : filter;
    }
}
