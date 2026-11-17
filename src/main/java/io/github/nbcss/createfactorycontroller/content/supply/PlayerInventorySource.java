package io.github.nbcss.createfactorycontroller.content.supply;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The player's 36 main + hotbar slots (offhand and armor excluded), in vanilla order.
 */
public final class PlayerInventorySource {

    private PlayerInventorySource() {}

    public static int count(Inventory inventory, Item item) {
        int total = 0;
        for (ItemStack stack : inventory.items)
            if (stack.is(item)) total += stack.getCount();
        return total;
    }

    /** The first slot holding the best-ranked {@code item}, or -1. */
    public static int bestSlot(Inventory inventory, Item item, ComponentItemTier tier) {
        int best = -1;
        int bestRank = Integer.MAX_VALUE;
        for (int i = 0; i < inventory.items.size(); i++) {
            int rank = tier.rank(inventory.items.get(i), item);
            if (rank == ComponentItemTier.UNUSABLE || rank >= bestRank) continue;
            best = i;
            bestRank = rank;
            if (rank == ComponentItemTier.EXACT) break;
        }
        return best;
    }

    /** Networks carried on tuned component items; each lets the player bind that network to a controller. */
    public static Set<UUID> networks(Inventory inventory) {
        Set<UUID> networks = new LinkedHashSet<>();
        for (ItemStack stack : inventory.items) {
            UUID network = ComponentItemTier.tunedNetwork(stack);
            if (network != null) networks.add(network);
        }
        return networks;
    }
}
