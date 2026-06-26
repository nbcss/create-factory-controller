package io.github.nbcss.createfactorycontroller.content.component;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public interface LogisticsControl {
    int stockOf(VirtualGaugeBehaviour gauge, ItemStack stack);
    int promised(VirtualGaugeBehaviour gauge);
    void forceClearPromise(UUID networkId, ItemStack filter);
    void addPromise(UUID networkId, ItemStack filter, boolean ignoreData, int amount);
}
