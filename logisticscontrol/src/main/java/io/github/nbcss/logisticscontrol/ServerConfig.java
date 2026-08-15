package io.github.nbcss.logisticscontrol;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings (stored in {@code serverconfig/createlogisticscontrol-server.toml}, synced to clients). */
public final class ServerConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue FILTER_LINK_RANGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        FILTER_LINK_RANGE = builder
            .comment("Maximum distance (in blocks) a Filter Link may reach to select its filter target blocks.")
            .translation("createlogisticscontrol.config.filter_link_range")
            .defineInRange("filterLinkRange", 16, 1, 256);
        SPEC = builder.build();
    }

    private ServerConfig() {}

    public static int filterLinkRange() {
        return FILTER_LINK_RANGE.get();
    }
}
