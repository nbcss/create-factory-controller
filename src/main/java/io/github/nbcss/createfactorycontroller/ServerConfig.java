package io.github.nbcss.createfactorycontroller;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side gameplay settings (stored in {@code serverconfig/createfactorycontroller-server.toml}, and
 * synced to connected clients so their in-GUI capacity display and pre-send cap check match the server).
 */
public final class ServerConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue MAX_COMPONENTS;
    public static final ModConfigSpec.IntValue MAX_CRAFT_GRID_SIZE;
    public static final ModConfigSpec.BooleanValue CHECK_INGREDIENTS_ON_SEND;
    public static final ModConfigSpec.BooleanValue PRESERVE_CONTROLLER_DATA;
    public static final ModConfigSpec.BooleanValue PASSIVE_TOTAL_DEMAND;

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
        CHECK_INGREDIENTS_ON_SEND = builder
                .comment("When hovering the Stock Keeper's Send button over an order containing Production Patterns,",
                        "compute and show whether the network has enough ingredients to produce the order.",
                        "Must also be enabled in the client config to show. Disable to skip the server-side computation.")
                .translation("createfactorycontroller.config.check_ingredients")
                .define("checkIngredientsOnSend", false);
        PRESERVE_CONTROLLER_DATA = builder
                .comment("Preserve a Factory Controller's board setup ",
                        "on the dropped controller item when the block is broken, restoring it when re-placed.",
                        "When off, the controller drops empty and its gauges drop as separate items instead.")
                .translation("createfactorycontroller.config.preserve_controller_data")
                .define("preserveControllerData", true);
        PASSIVE_TOTAL_DEMAND = builder
                .comment("Alternative passive-request strategy. When disabled, a passive gauge's target tracks one",
                        "recipe set per demanding consumer, so demand ripples one hop per tick. When enabled, the controller",
                        "sizes each passive gauge's target storage to the TOTAL remaining downstream demand (scaled by",
                        "recipe ratios, minus stock and open promises at every stage) in one consistent pass — so the",
                        "whole chain stays active and produces continuously until the total is met, ramping far faster",
                        "for deep production chains.")
                .translation("createfactorycontroller.config.passive_total_demand")
                .define("passiveTotalDemand", false);
        SPEC = builder.build();
    }

    private ServerConfig() {}

    public static int maxComponents() {
        return MAX_COMPONENTS.get();
    }

    public static int maxCraftGridSize() {
        return MAX_CRAFT_GRID_SIZE.get();
    }

    public static boolean checkIngredientsOnSend() {
        return CHECK_INGREDIENTS_ON_SEND.get();
    }

    public static boolean preserveControllerData() {
        return PRESERVE_CONTROLLER_DATA.get();
    }

    public static boolean passiveTotalDemand() {
        return PASSIVE_TOTAL_DEMAND.get();
    }
}
