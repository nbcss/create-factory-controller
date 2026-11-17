package io.github.nbcss.createfactorycontroller.content.supply;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public record ComponentItemTier(@Nullable UUID target, Set<UUID> known) {

    public static final int UNUSABLE = -1;
    public static final int EXACT = 0;
    public static final int UNTUNED = 1;
    public static final int KNOWN = 2;
    public static final int FOREIGN = 3;

    /** Rank of {@code stack} as a source of {@code item}; networkless items always rank {@link #EXACT}. */
    public int rank(ItemStack stack, Item item) {
        if (stack.isEmpty() || !stack.is(item)) return UNUSABLE;
        if (!ComponentRegistry.containsNetworkItem(stack)) return EXACT;
        if (!LogisticallyLinkedBlockItem.isTuned(stack)) return UNTUNED;
        UUID network = LogisticallyLinkedBlockItem.networkFromStack(stack);
        if (network == null) return FOREIGN;
        if (network.equals(target)) return EXACT;
        return known.contains(network) ? KNOWN : FOREIGN;
    }

    /** The network a tuned component item carries, or null (not a networked component, untuned, or unreadable). */
    @Nullable
    public static UUID tunedNetwork(ItemStack stack) {
        if (!ComponentRegistry.containsNetworkItem(stack) || !LogisticallyLinkedBlockItem.isTuned(stack)) return null;
        return LogisticallyLinkedBlockItem.networkFromStack(stack);
    }
}
