package io.github.nbcss.createfactorycontroller.content.helper;

import io.github.nbcss.createfactorycontroller.ServerConfig;
import net.neoforged.fml.event.config.ModConfigEvent;

import java.util.ArrayList;
import java.util.List;

/** For server config migration. */
public abstract class ConfigDataFixer {
    public static final int DATA_VERSION = 1;
    private static final List<ConfigDataFixer> FIXERS = new ArrayList<>();

    static {
        FIXERS.add(new ConfigDataFixer(1) {
            @Override
            public void fix() {
                if (ServerConfig.maxComponents() == 256)
                    ServerConfig.MAX_COMPONENTS.set(512);
            }
        });
    }

    private final int version;

    private ConfigDataFixer(int version) {
        this.version = version;
    }

    public abstract void fix();

    public static void migrate(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != ServerConfig.SPEC)
            return;
        int version = ServerConfig.configVersion();
        if (version >= DATA_VERSION)
            return;
        for (ConfigDataFixer fixer : FIXERS)
            if (fixer.version > version)
                fixer.fix();
        ServerConfig.setConfigVersion(DATA_VERSION);
        ServerConfig.SPEC.save();
    }
}
