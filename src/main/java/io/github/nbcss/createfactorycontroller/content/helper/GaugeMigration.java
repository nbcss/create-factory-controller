package io.github.nbcss.createfactorycontroller.content.helper;

import io.github.nbcss.createfactorycontroller.content.component.CflFluidGaugeBehaviour;
import net.minecraft.nbt.CompoundTag;

/**
 * Optional-mod-conditional, idempotent migration of stored gauge component tags.
 */
public final class GaugeMigration {

    private GaugeMigration() {}

    /** Migrates {@code component} in place (and returns it) */
    public static CompoundTag migrate(CompoundTag component) {
        migrateFluidGaugeToFactoryGauge(component);
        return component;
    }

    private static void migrateFluidGaugeToFactoryGauge(CompoundTag component) {
        if (!CflFluidGaugeBehaviour.isAvailable()) return;
        if (!"GAUGE".equals(component.getString("Type"))) return;
        if (!CflFluidGaugeBehaviour.FILTER_ITEM_ID.toString().equals(component.getCompound("Filter").getString("id")))
            return;
        component.putString("Type", "FLUID_FACTORY_GAUGE");
        component.putString("Item", CflFluidGaugeBehaviour.ITEM_ID.toString());
    }
}
