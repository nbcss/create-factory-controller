package io.github.nbcss.createfactorycontroller.content.helper;

import io.github.nbcss.createfactorycontroller.ServerConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** For server config migration. */
public abstract class ConfigDataFixer {
    private static final String FILE_ID = "createfactorycontroller_config_migrations";
    public static final int DATA_VERSION = 1;
    private static final List<ConfigDataFixer> FIXERS = new ArrayList<>();

    static {
        FIXERS.add(new ConfigDataFixer(1) {
            @Override
            public void fix() {
                if (ServerConfig.maxComponents() == 256) {
                    ServerConfig.MAX_COMPONENTS.set(512);
                    ServerConfig.SPEC.save();
                }
            }
        });
    }

    private final int version;

    private ConfigDataFixer(int version) {
        this.version = version;
    }

    public abstract void fix();

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener(ConfigDataFixer::migrate);
    }

    private static void migrate(ServerStartedEvent event) {
        MigrationData data = MigrationData.get(event.getServer());
        int version = data.version;
        if (version >= DATA_VERSION)
            return;
        for (ConfigDataFixer fixer : FIXERS)
            if (fixer.version > version)
                fixer.fix();
        data.version = DATA_VERSION;
        data.setDirty();
    }

    private static class MigrationData extends SavedData {
        private int version;

        private static SavedData.Factory<MigrationData> factory() {
            return new SavedData.Factory<>(MigrationData::new, MigrationData::load, null);
        }

        private static MigrationData get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
        }

        @Override
        public @NotNull CompoundTag save(@NotNull CompoundTag tag,
                                         HolderLookup.@NotNull Provider registries) {
            tag.putInt("Version", version);
            return tag;
        }

        private static MigrationData load(CompoundTag tag, HolderLookup.Provider registries) {
            MigrationData data = new MigrationData();
            data.version = tag.getInt("Version");
            return data;
        }
    }
}
