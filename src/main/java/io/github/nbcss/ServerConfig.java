package io.github.nbcss;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side gameplay settings (stored in {@code serverconfig/createfactorycontroller-server.toml}, and
 * synced to connected clients so their in-GUI capacity display and pre-send cap check match the server).
 *
 * <p>Currently just the per-controller component cap. Raising it increases the controller's saved NBT and
 * the size of the sync packet sent to players with the GUI open, hence the hard ceiling.</p>
 */
public final class ServerConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_COMPONENTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MAX_COMPONENTS = builder
                .comment("Maximum number of components a single Factory Controller may hold.",
                        "Higher values increase the controller's saved data and its GUI sync packet size.")
                .translation("createfactorycontroller.config.max_components")
                .defineInRange("maxComponents", 256, 1, 1024);
        SPEC = builder.build();
    }

    private ServerConfig() {}

    public static int maxComponents() {
        return MAX_COMPONENTS.get();
    }
}
