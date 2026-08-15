package io.github.nbcss.logisticscontrol.content.helper;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts.CraftingEntry;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class FilterOrderCodec {
    private FilterOrderCodec() {}

    public static PackageOrderWithCrafts encode(PackageOrderWithCrafts order, ItemStack filter) {
        if (filter == null || filter.isEmpty()) return order;
        List<CraftingEntry> crafts = new ArrayList<>(order.orderedCrafts());
        crafts.add(new CraftingEntry(new PackageOrder(List.of(new BigItemStack(filter.copyWithCount(1)))), 0));
        return new PackageOrderWithCrafts(order.orderedStacks(), crafts);
    }

    public static ItemStack decode(PackageOrderWithCrafts order) {
        for (CraftingEntry e : order.orderedCrafts())
            if (e.count() == 0 && !e.pattern().stacks().isEmpty()) {
                ItemStack s = e.pattern().stacks().get(0).stack;
                if (!s.isEmpty()) return s;
            }
        return ItemStack.EMPTY;
    }

    /** Carries the per-non-crafting-recipe output list as a single count-0 sentinel whose pattern holds the outputs. */
    public static PackageOrderWithCrafts encodeList(PackageOrderWithCrafts order, List<ItemStack> outputs) {
        if (outputs == null || outputs.isEmpty()) return order;
        List<BigItemStack> payload = new ArrayList<>();
        for (ItemStack s : outputs)
            if (s != null && !s.isEmpty()) payload.add(new BigItemStack(s.copyWithCount(1)));
        if (payload.isEmpty()) return order;
        List<CraftingEntry> crafts = new ArrayList<>(order.orderedCrafts());
        crafts.add(new CraftingEntry(new PackageOrder(payload), 0));
        return new PackageOrderWithCrafts(order.orderedStacks(), crafts);
    }

    public static List<ItemStack> decodeList(PackageOrderWithCrafts order) {
        for (CraftingEntry e : order.orderedCrafts())
            if (e.count() == 0) {
                List<ItemStack> out = new ArrayList<>();
                for (BigItemStack b : e.pattern().stacks())
                    if (!b.stack.isEmpty()) out.add(b.stack);
                if (!out.isEmpty()) return out;
            }
        return List.of();
    }

    /** Extracts the appended non-crafting entries (the last {@code outputs.size()} real entries) into consolidated
     *  [output, ...ingredient totals] groups, summing each ingredient over the recipe's cells × its craft count. */
    public static List<List<BigItemStack>> extractGroups(PackageOrderWithCrafts order, List<ItemStack> outputs) {
        List<CraftingEntry> real = new ArrayList<>();
        for (CraftingEntry e : order.orderedCrafts()) if (e.count() != 0) real.add(e);
        int craftingCount = real.size() - outputs.size();
        List<List<BigItemStack>> groups = new ArrayList<>();
        if (craftingCount < 0) return groups;
        for (int j = 0; j < outputs.size(); j++) {
            CraftingEntry e = real.get(craftingCount + j);
            List<BigItemStack> group = new ArrayList<>();
            group.add(new BigItemStack(outputs.get(j).copyWithCount(1), 1));
            for (BigItemStack cell : e.pattern().stacks()) {
                if (cell.stack.isEmpty()) continue;
                BigItemStack existing = null;
                for (int i = 1; i < group.size(); i++)
                    if (ItemStack.isSameItemSameComponents(group.get(i).stack, cell.stack)) { existing = group.get(i); break; }
                if (existing != null) existing.count += e.count();
                else group.add(new BigItemStack(cell.stack.copyWithCount(1), e.count()));
            }
            groups.add(group);
        }
        return groups;
    }

    /** Drops the outputs sentinel and the appended non-crafting entries, leaving only real crafting entries. */
    public static PackageOrderWithCrafts stripNonCrafting(PackageOrderWithCrafts order, int nonCraftingCount) {
        List<CraftingEntry> real = new ArrayList<>();
        for (CraftingEntry e : order.orderedCrafts()) if (e.count() != 0) real.add(e);
        int craftingCount = real.size() - nonCraftingCount;
        List<CraftingEntry> kept = new ArrayList<>();
        for (int i = 0; i < craftingCount && i < real.size(); i++) kept.add(real.get(i));
        return new PackageOrderWithCrafts(order.orderedStacks(), kept);
    }

    public static PackageOrderWithCrafts strip(PackageOrderWithCrafts order) {
        List<CraftingEntry> kept = new ArrayList<>();
        for (CraftingEntry e : order.orderedCrafts())
            if (e.count() != 0) kept.add(e);
        if (kept.size() == order.orderedCrafts().size()) return order;
        return new PackageOrderWithCrafts(order.orderedStacks(), kept);
    }
}
