package io.github.nbcss.createfactorycontroller.content.supply;

import net.minecraft.world.item.ItemStack;

import java.util.function.IntFunction;

/**
 * One stack blueprint placement may spend from: its contents (for ranking; never mutated), how many may be taken, and
 * how to take them. {@code take} returns what was really removed, which a handler may cut short of the request.
 */
public record SpendableSlot(ItemStack view, int available, IntFunction<ItemStack> take) {}
