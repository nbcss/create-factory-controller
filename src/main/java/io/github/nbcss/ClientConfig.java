package io.github.nbcss;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side, per-installation persisted settings (stored in {@code config/createfactorycontroller-client.toml}).
 *
 * <p>Currently just the "full overlay" toggle for the controller canvas: when on, every gauge draws its
 * count label; when off, only the hovered gauge does. It persists across controllers and game sessions and
 * is local to each player's client. Toggled in-GUI via the rebindable keybind (default Left Alt); also
 * editable from the mod's config screen.</p>
 */
public final class ClientConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue FULL_OVERLAY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        FULL_OVERLAY = builder
                .comment("Show the count label on every gauge in the controller overlay (on), or only the hovered gauge (off).",
                        "Toggle in the controller GUI with the keybind (default Left Alt).")
                .translation("createfactorycontroller.config.full_overlay")
                .define("fullOverlay", true);
        SPEC = builder.build();
    }

    private ClientConfig() {}

    public static boolean fullOverlay() {
        return FULL_OVERLAY.get();
    }

    /** Flips the setting and persists it; returns the new value. Safe to call only once the config is loaded. */
    public static boolean toggleFullOverlay() {
        boolean next = !FULL_OVERLAY.get();
        FULL_OVERLAY.set(next);
        return next;
    }
}
