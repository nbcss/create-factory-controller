package io.github.nbcss.createfactorycontroller.content.compat.logisticscontrol;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class LogisticsControlCompat {

    public static final String MODID = "createlogisticscontrol";

    private LogisticsControlCompat() {}

    /** Whether Create: Logistics Control is installed; gates the virtual gauge bridge (the Filter Link). */
    public static boolean isLoaded() {
        ModList list = ModList.get();
        return list != null && list.isLoaded(MODID);
    }

    public static void beginDispatch(ItemStack filter) {
        if (isLoaded()) LogisticsControlIntegration.beginDispatch(filter);
    }

    public static void endDispatch() {
        if (isLoaded()) LogisticsControlIntegration.endDispatch();
    }
}
