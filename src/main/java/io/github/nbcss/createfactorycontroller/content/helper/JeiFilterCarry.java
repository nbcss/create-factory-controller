package io.github.nbcss.createfactorycontroller.content.helper;

import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Server-side setup at order dispatch: reads the JEI order's carried filter data into {@link CfcFilterDispatch} and
 *  returns the order to actually dispatch (with the transient recipe scaffolding stripped). */
public final class JeiFilterCarry {
    private JeiFilterCarry() {}

    /** Decodes the carried non-crafting groups + a single-recipe whole-order filter into CfcFilterDispatch and returns
     *  the crafting-only order to dispatch, or null if there's nothing to carry (dispatch the original untouched).
     *
     *  <p>When the whole order is exactly one recipe (crafting or non-crafting), its output is also set as the single
     *  filter, so the packager stamps PACKAGE_FILTER directly — the package then works without a re-packager, same as a
     *  gauge order. Multi-recipe orders set only the groups and still need the re-packager to split per recipe. */
    @Nullable
    public static PackageOrderWithCrafts prepare(PackageOrderWithCrafts order, Level level) {
        List<ItemStack> outputs = FilterOrderCodec.decodeList(order);
        int nonCrafting = outputs.size();
        PackageOrderWithCrafts cleaned = FilterOrderCodec.stripNonCrafting(order, nonCrafting);
        int total = cleaned.orderedCrafts().size() + nonCrafting;

        ItemStack single = ItemStack.EMPTY;
        if (total == 1)
            single = nonCrafting == 1 ? outputs.get(0)
                : RecipeFilters.craftingResult(cleaned.orderedCrafts().get(0).pattern().stacks(), level);

        if (nonCrafting == 0 && single.isEmpty()) return null;
        if (nonCrafting > 0) CfcFilterDispatch.setGroups(FilterOrderCodec.extractGroups(order, outputs));
        if (!single.isEmpty()) CfcFilterDispatch.set(single);
        return cleaned;
    }
}
