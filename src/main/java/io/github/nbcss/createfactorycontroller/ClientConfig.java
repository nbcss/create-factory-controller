package io.github.nbcss.createfactorycontroller;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side, per-installation persisted settings (stored in {@code config/createfactorycontroller-client.toml}).
 */
public final class ClientConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> CONTROLLER_BACKGROUND;
    public static final ModConfigSpec.BooleanValue ALWAYS_SHOW_LABEL;
    public static final ModConfigSpec.BooleanValue DYNAMIC_LABEL_SCALING;
    public static final ModConfigSpec.BooleanValue CHECK_INGREDIENTS_ON_SEND;
    public static final ModConfigSpec.BooleanValue ORDER_FROM_MATERIAL_LIST;
    public static final ModConfigSpec.BooleanValue COMPACT_RECIPE_COUNT_FONT;
    public static final ModConfigSpec.BooleanValue HIDE_MISSING_LINK_WARNING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        CONTROLLER_BACKGROUND = builder
                .comment("Background texture path of Controller Screen. The texture file must locate in 'createfactorycontroller/textures/gui/controller_background/' path, and in 16 pixel resolution.")
                .translation("createfactorycontroller.config.controller_background")
                .define("controllerBackground", "plain_cardboard");
        ALWAYS_SHOW_LABEL = builder
                .comment("Show the count label on every gauge in the controller interface, instead of only the hovered gauge.")
                .translation("createfactorycontroller.config.always_show_label")
                .define("alwaysShowLabel", true);
        DYNAMIC_LABEL_SCALING = builder
                .comment("Shrink a gauge's count label when it is too wide for the gauge.")
                .translation("createfactorycontroller.config.dynamic_label_scaling")
                .define("dynamicLabelScaling", true);
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
                .define("compactRecipeCountFont", true);
        HIDE_MISSING_LINK_WARNING = builder
                .comment("Hide the missing logistics-link warning indicators in the Controller Screen.")
                .translation("createfactorycontroller.config.hide_missing_link_warning")
                .define("hideMissingLinkWarning", false);
        SPEC = builder.build();
    }

    private ClientConfig() {}

    public static boolean alwaysShowLabel() {
        return ALWAYS_SHOW_LABEL.get();
    }

    public static boolean dynamicLabelScaling() {
        return DYNAMIC_LABEL_SCALING.get();
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

    public static boolean hideMissingLinkWarning() {
        return HIDE_MISSING_LINK_WARNING.get();
    }

    public static String getControllerBackground() {
        return CONTROLLER_BACKGROUND.get();
    }

    /** The spec's default background name (used by the settings screen's Reset button). */
    public static String defaultControllerBackground() {
        return CONTROLLER_BACKGROUND.getDefault();
    }

    public static void setControllerBackground(String name) {
        CONTROLLER_BACKGROUND.set(name);
    }

    public static void save() {
        if (SPEC.isLoaded()) SPEC.save();
    }

    /** Flips the setting and persists it; returns the new value. Safe to call only once the config is loaded. */
    public static boolean toggleAlwaysShowLabel() {
        boolean next = !ALWAYS_SHOW_LABEL.get();
        ALWAYS_SHOW_LABEL.set(next);
        return next;
    }
}
