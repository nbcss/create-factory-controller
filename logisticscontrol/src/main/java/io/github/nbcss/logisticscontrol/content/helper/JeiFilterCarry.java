package io.github.nbcss.logisticscontrol.content.helper;

import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Server-side setup at order dispatch: reads the JEI order's carried filter data into {@link FilterDispatch} and
 *  returns the order to actually dispatch (with the transient recipe scaffolding stripped). */
public final class JeiFilterCarry {
    private JeiFilterCarry() {}

    @Nullable
    public static PackageOrderWithCrafts prepare(PackageOrderWithCrafts order, Level level) {
        List<ItemStack> outputs = FilterOrderCodec.decodeList(order);
        List<ItemStack> craftOutputs = FilterOrderCodec.decodeCraftOutputs(order);
        int nonCrafting = outputs.size();
        boolean carried = nonCrafting > 0 || !craftOutputs.isEmpty();
        PackageOrderWithCrafts cleaned = FilterOrderCodec.stripNonCrafting(order, nonCrafting);
        int craftEntries = cleaned.orderedCrafts().size();

        ItemStack single = ItemStack.EMPTY;
        if (craftEntries + nonCrafting == 1) {
            single = nonCrafting == 1 ? outputs.getFirst()
                : !craftOutputs.isEmpty() ? craftOutputs.getFirst()
                : RecipeFilters.craftingResult(cleaned.orderedCrafts().getFirst().pattern().stacks(), level);
        } else if (nonCrafting == 0 && craftEntries >= 1 && allSame(craftOutputs)) {
            single = craftOutputs.getFirst();
        }

        if (!carried && single.isEmpty()) return null;
        if (nonCrafting > 0) FilterDispatch.setGroups(FilterOrderCodec.extractGroups(order, outputs));
        if (!craftOutputs.isEmpty()) FilterDispatch.setCraftOutputs(craftOutputs);
        if (!single.isEmpty()) FilterDispatch.set(single);
        return cleaned;
    }

    private static boolean allSame(List<ItemStack> stacks) {
        if (stacks.isEmpty() || stacks.getFirst().isEmpty()) return false;
        for (ItemStack s : stacks)
            if (s.isEmpty() || !ItemStack.isSameItem(stacks.getFirst(), s)) return false;
        return true;
    }
}
