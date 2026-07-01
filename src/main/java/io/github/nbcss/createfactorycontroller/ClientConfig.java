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
    public static final ModConfigSpec.BooleanValue COMPACT_RECIPE_COUNT_FONT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        CONTROLLER_BACKGROUND = builder
                .comment("Background texture path of Controller Screen. The texture file must locate in 'createfactorycontroller/textures/gui/controller_background/' path, and in 16 pixel resolution.")
                .translation("createfactorycontroller.config.controller_background")
                .define("controllerBackground", "plain_cardboard");
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
        COMPACT_RECIPE_COUNT_FONT = builder
                .comment("Draw input/output item counts in the recipe config screen with Create's compact number",
                        "sprite (the same glyphs as the stock icon) instead of the vanilla item-count font.")
                .translation("createfactorycontroller.config.compact_recipe_count_font")
                .define("compactRecipeCountFont", false);
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

    public static boolean compactRecipeCountFont() {
        return COMPACT_RECIPE_COUNT_FONT.get();
    }

    public static String getControllerBackground() {
        return CONTROLLER_BACKGROUND.get();
    }

    /** The spec's default background name (used by the settings screen's Reset button). */
    public static String defaultControllerBackground() {
        return CONTROLLER_BACKGROUND.getDefault();
    }

    /** Sets the background selection; takes effect immediately (the controller reads it each frame), but only updates
     *  the in-memory config — call {@link #save()} to persist it to disk. */
    public static void setControllerBackground(String name) {
        CONTROLLER_BACKGROUND.set(name);
    }

    /**
     * Flushes the in-memory client config to disk. NeoForge's {@code ConfigValue.set} only mutates the loaded
     * in-memory config and never writes the file, so changes made through the settings screens are lost on restart
     * unless this is called (e.g. when closing the background settings screen). No-op until the config is loaded.
     */
    public static void save() {
        if (SPEC.isLoaded()) SPEC.save();
    }

    /** Flips the setting and persists it; returns the new value. Safe to call only once the config is loaded. */
    public static boolean toggleFullOverlay() {
        boolean next = !FULL_OVERLAY.get();
        FULL_OVERLAY.set(next);
        return next;
    }
}
