package io.github.nbcss.createfactorycontroller.content.component;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public interface LogisticsControl {
    int stockOf(VirtualGaugeBehaviour gauge, ItemStack stack);
    int promised(VirtualGaugeBehaviour gauge);
    void forceClearPromise(UUID networkId, ItemStack filter);
    /** Adds a promise; {@code ownerKey}/{@code targetAddress}/{@code ttl} tag it as a controller promise (see
     *  {@code ControllerPromise}) so a gauge's timeout only clears its own and it can be counted for the limit. */
    void addPromise(UUID networkId, ItemStack filter, boolean ignoreData, int amount,
                    String ownerKey, String targetAddress, int ttl);
}
