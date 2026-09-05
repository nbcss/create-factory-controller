package io.github.nbcss.createfactorycontroller.content.compat.logisticscontrol;

import io.github.nbcss.logisticscontrol.api.LogisticsControlApi;
import net.minecraft.world.item.ItemStack;

final class LogisticsControlIntegration {

    private LogisticsControlIntegration() {}

    static void beginDispatch(ItemStack filter) {
        LogisticsControlApi.beginDispatch(filter);
    }

    static void endDispatch() {
        LogisticsControlApi.endDispatch();
    }
}
