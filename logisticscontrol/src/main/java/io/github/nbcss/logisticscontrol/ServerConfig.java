package io.github.nbcss.logisticscontrol;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings (stored in {@code serverconfig/createlogisticscontrol-server.toml}, synced to clients). */
public final class ServerConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_FILTER_LINK;
    public static final ModConfigSpec.IntValue FILTER_LINK_RANGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ENABLE_FILTER_LINK = builder
            .comment("Enable Filter Link (require reload game).")
            .translation("createlogisticscontrol.config.enable_filter_link")
            .define("enableFilterLink", true);
        FILTER_LINK_RANGE = builder
            .comment("Maximum distance (in blocks) a Filter Link may reach to select its filter target blocks.")
            .translation("createlogisticscontrol.config.filter_link_range")
            .defineInRange("filterLinkRange", 16, 1, 256);
        SPEC = builder.build();
    }

    private ServerConfig() {}

    /** Whether the Filter Link feature is enabled. Assumes enabled if the config has not loaded yet. */
    public static boolean filterLinkEnabled() {
        return !SPEC.isLoaded() || ENABLE_FILTER_LINK.get();
    }

    public static int filterLinkRange() {
        return FILTER_LINK_RANGE.get();
    }
}
