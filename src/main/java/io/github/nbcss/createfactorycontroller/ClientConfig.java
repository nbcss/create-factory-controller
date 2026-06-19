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
    public static final ModConfigSpec.BooleanValue ORDER_FROM_MATERIAL_LIST;

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
        ORDER_FROM_MATERIAL_LIST = builder
                .comment("When right-clicking a Stock Keeper with a material list (clipboard), request a Production",
                        "Order for any listed item that is short in stock but producible by an orderable gauge on the",
                        "network — instead of only requesting the in-stock amount.")
                .translation("createfactorycontroller.config.order_from_material_list")
                .define("orderFromMaterialList", true);
        SPEC = builder.build();
    }

    private ClientConfig() {}

    public static boolean fullOverlay() {
        return FULL_OVERLAY.get();
    }

    public static boolean checkIngredientsOnSend() {
        return CHECK_INGREDIENTS_ON_SEND.get();
    }

    public static boolean orderFromMaterialList() {
        return ORDER_FROM_MATERIAL_LIST.get();
    }

    public static String getControllerBackground() {
        return CONTROLLER_BACKGROUND.get();
    }

    /** The spec's default background name (used by the settings screen's Reset button). */
    public static String defaultControllerBackground() {
        return CONTROLLER_BACKGROUND.getDefault();
    }

    /** Sets and persists the background selection; takes effect immediately (the controller reads it each frame). */
    public static void setControllerBackground(String name) {
        CONTROLLER_BACKGROUND.set(name);
    }

    /** Flips the setting and persists it; returns the new value. Safe to call only once the config is loaded. */
    public static boolean toggleFullOverlay() {
        boolean next = !FULL_OVERLAY.get();
        FULL_OVERLAY.set(next);
        return next;
    }
}
