package io.github.nbcss.createfactorycontroller.content.supply;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record AdjacentContainerSource(List<IItemHandler> handlers) {

    public static final AdjacentContainerSource NONE = new AdjacentContainerSource(List.of());

    public boolean isEmpty() {
        return handlers.isEmpty();
    }

    /**
     * How many of {@code stack} (the slot's contents) can really be taken; 0 for locked, input-only or otherwise
     * guarded slots. Like a single {@code extractItem} call, this may stop at one stack for an oversized slot.
     */
    private static int extractable(IItemHandler handler, int slot, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        ItemStack simulated = handler.extractItem(slot, stack.getCount(), true);
        return ItemStack.isSameItemSameComponents(simulated, stack) ? simulated.getCount() : 0;
    }

    /**
     * Takes one {@code item} from the first extractable slot holding its best rank, or returns {@link ItemStack#EMPTY}.
     */
    public ItemStack extractOne(Item item, ComponentItemTier tier) {
        IItemHandler bestHandler = null;
        int bestSlot = -1;
        int bestRank = Integer.MAX_VALUE;
        search:
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                int rank = tier.rank(stack, item);
                if (rank == ComponentItemTier.UNUSABLE || rank >= bestRank || extractable(handler, slot, stack) <= 0)
                    continue;
                bestHandler = handler;
                bestSlot = slot;
                bestRank = rank;
                if (rank == ComponentItemTier.EXACT) break search;
            }
        }
        return bestHandler == null ? ItemStack.EMPTY : bestHandler.extractItem(bestSlot, 1, false);
    }

    /** Inserts {@code stack} across the containers in order; returns what did not fit. */
    public ItemStack insert(ItemStack stack, boolean simulate) {
        ItemStack remaining = stack.copy();
        for (IItemHandler handler : handlers) {
            if (remaining.isEmpty()) break;
            remaining = ItemHandlerHelper.insertItemStacked(handler, remaining, simulate);
        }
        return remaining;
    }

    /** Every extractable component-item slot, in container then slot order. */
    public List<SpendableSlot> spendableSlots() {
        List<SpendableSlot> slots = new ArrayList<>();
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || !ComponentSupplyCatalog.contains(stack.getItem())) continue;
                int available = extractable(handler, slot, stack);
                if (available <= 0) continue;
                int index = slot;
                slots.add(new SpendableSlot(stack.copy(), available,
                        count -> handler.extractItem(index, count, false)));
            }
        }
        return slots;
    }

    public ComponentSupplyStock snapshot() {
        if (handlers.isEmpty()) return ComponentSupplyStock.EMPTY;
        Map<Item, Integer> counts = new HashMap<>();
        Set<UUID> networks = new LinkedHashSet<>();
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty() || !ComponentSupplyCatalog.contains(stack.getItem())) continue;
                int available = extractable(handler, slot, stack);
                if (available <= 0) continue;
                counts.merge(stack.getItem(), available, Integer::sum);
                UUID network = ComponentItemTier.tunedNetwork(stack);
                if (network != null) networks.add(network);
            }
        }
        return new ComponentSupplyStock(counts, networks, true);
    }
}
