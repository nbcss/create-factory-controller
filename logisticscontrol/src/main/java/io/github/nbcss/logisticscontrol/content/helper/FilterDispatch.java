package io.github.nbcss.logisticscontrol.content.helper;

import com.simibubi.create.content.logistics.BigItemStack;
import io.github.nbcss.logisticscontrol.content.compat.fluids.FluidCompat;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class FilterDispatch {
    private static final ThreadLocal<ItemStack> CURRENT = ThreadLocal.withInitial(() -> ItemStack.EMPTY);
    private static final ThreadLocal<List<List<BigItemStack>>> CURRENT_GROUPS = ThreadLocal.withInitial(List::of);

    private FilterDispatch() {}

    /** A fluid (carried as a virtual fluid-filter item) is not a valid output filter, so it never enters the system. */
    public static void set(ItemStack filter) {
        CURRENT.set(filter == null || FluidCompat.isFluidFilter(filter) ? ItemStack.EMPTY : filter);
    }

    public static ItemStack get() {
        return CURRENT.get();
    }

    public static void setGroups(List<List<BigItemStack>> groups) {
        CURRENT_GROUPS.set(groups == null ? List.of() : groups);
    }

    public static List<List<BigItemStack>> getGroups() {
        return CURRENT_GROUPS.get();
    }

    public static void clear() {
        CURRENT.set(ItemStack.EMPTY);
        CURRENT_GROUPS.set(List.of());
    }
}
