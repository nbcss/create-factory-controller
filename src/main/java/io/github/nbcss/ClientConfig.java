package io.github.nbcss;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side, per-installation persisted settings (stored in {@code config/createfactorycontroller-client.toml}).
 */
public final class ClientConfig {

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue FULL_OVERLAY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        FULL_OVERLAY = builder
                .comment("Show the count label on every gauge in the controller overlay (on), or only the hovered gauge (off).")
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
