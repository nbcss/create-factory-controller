package io.github.nbcss.createfactorycontroller;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side, per-installation persisted settings (stored in {@code config/createfactorycontroller-client.toml}).
 */
public final class ClientConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> CONTROLLER_BACKGROUND;
    public static final ModConfigSpec.BooleanValue FULL_OVERLAY;
    public static final ModConfigSpec.BooleanValue CHECK_INGREDIENTS_ON_SEND;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        CONTROLLER_BACKGROUND = builder
                .comment("Background texture path of Controller Screen. The texture file must locate in 'createfactorycontroller/textures/gui/controller_background/' path, and in 16 pixel resolution.")
                .translation("createfactorycontroller.config.controller_background")
                .define("controllerBackground", "cardboard_block_side");
        FULL_OVERLAY = builder
                .comment("Show the count label on every gauge in the controller overlay (on), or only the hovered gauge (off).")
                .translation("createfactorycontroller.config.full_overlay")
                .define("fullOverlay", true);
        CHECK_INGREDIENTS_ON_SEND = builder
                .comment("Show the ingredient-availability tooltip when hovering the Stock Keeper's Send button over",
                        "an order containing Production Patterns. Must also be enabled in the server config.")
                .translation("createfactorycontroller.config.check_ingredients")
                .define("checkIngredientsOnSend", false);
        SPEC = builder.build();
    }

    private ClientConfig() {}

    public static boolean fullOverlay() {
        return FULL_OVERLAY.get();
    }

    public static boolean checkIngredientsOnSend() {
        return CHECK_INGREDIENTS_ON_SEND.get();
    }

    public static String getControllerBackground() {
        return CONTROLLER_BACKGROUND.get();
    }

    /** Flips the setting and persists it; returns the new value. Safe to call only once the config is loaded. */
    public static boolean toggleFullOverlay() {
        boolean next = !FULL_OVERLAY.get();
        FULL_OVERLAY.set(next);
        return next;
    }
}
