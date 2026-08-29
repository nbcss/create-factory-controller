package io.github.nbcss.logisticscontrol.content.helper;

import com.simibubi.create.content.logistics.BigItemStack;
import io.github.nbcss.logisticscontrol.content.compat.fluids.FluidCompat;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class FilterDispatch {
    private static final ThreadLocal<ItemStack> CURRENT = ThreadLocal.withInitial(() -> ItemStack.EMPTY);
    private static final ThreadLocal<List<List<BigItemStack>>> CURRENT_GROUPS = ThreadLocal.withInitial(List::of);
    private static final ThreadLocal<List<ItemStack>> CURRENT_CRAFT_OUTPUTS = ThreadLocal.withInitial(List::of);

    private FilterDispatch() {}

    /** Unsupported virtual fluids and physical Create filter items never enter the package-label flow. */
    public static void set(ItemStack filter) {
        CURRENT.set(filter == null || FluidCompat.isFluidFilter(filter) || !PackageFilter.canLabel(filter)
            ? ItemStack.EMPTY : filter);
    }

    public static ItemStack get() {
        return CURRENT.get();
    }

    public static void setGroups(List<List<BigItemStack>> groups) {
        CURRENT_GROUPS.set(new NonCraftGroups(groups).groups());
    }

    public static List<List<BigItemStack>> getGroups() {
        return CURRENT_GROUPS.get();
    }

    /** The picked crafting outputs, aligned with the order's crafting entries (for multi-recipe disambiguation). */
    public static void setCraftOutputs(List<ItemStack> outputs) {
        CURRENT_CRAFT_OUTPUTS.set(new CraftOutputs(outputs).outputs());
    }

    public static List<ItemStack> getCraftOutputs() {
        return CURRENT_CRAFT_OUTPUTS.get();
    }

    public static void clear() {
        CURRENT.set(ItemStack.EMPTY);
        CURRENT_GROUPS.set(List.of());
        CURRENT_CRAFT_OUTPUTS.set(List.of());
    }
}
