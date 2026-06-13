package io.github.nbcss;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side gameplay settings (stored in {@code serverconfig/createfactorycontroller-server.toml}, and
 * synced to connected clients so their in-GUI capacity display and pre-send cap check match the server).
 */
public final class ServerConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_COMPONENTS;
    public static final ModConfigSpec.IntValue MAX_CRAFT_GRID_SIZE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MAX_COMPONENTS = builder
                .comment("Maximum number of components a single Factory Controller may hold.",
                        "Higher values increase the controller's saved data and its GUI sync packet size.")
                .translation("createfactorycontroller.config.max_components")
                .defineInRange("maxComponents", 256, 1, 1024);
        MAX_CRAFT_GRID_SIZE = builder
                .comment("Largest square mechanical-crafter grid a recipe may be laid out into in the recipe screen")
                .translation("createfactorycontroller.config.max_craft_grid")
                .defineInRange("maxCraftGridSize", 10, 3, 32);
        SPEC = builder.build();
    }

    private ServerConfig() {}

    public static int maxComponents() {
        return MAX_COMPONENTS.get();
    }

    public static int maxCraftGridSize() {
        return MAX_CRAFT_GRID_SIZE.get();
    }
}
