package io.github.nbcss.createfactorycontroller.content;

/**
 * How a recipe gauge lays out its ingredients — one of three mutually exclusive work modes:
 *
 * <ul>
 * <li>{@link #REGULAR} — each ingredient connection is a single total; the 3×3 grid is derived
 *     (full stacks first, then a partial), contiguous.</li>
 * <li>{@link #CRAFTING} — the inputs form a crafting recipe; the request carries a crafting pattern
 *     (mechanical-crafter unpacked). Available only while a recipe matches the inputs + output.</li>
 * <li>{@link #CUSTOM} — the player lays ingredients into an explicit 9-slot arrangement (arbitrary
 *     placement, per-slot counts, repeated ingredients, empty gaps).</li>
 * </ul>
 */
public enum GaugeWorkMode {
    REGULAR,
    CRAFTING,
    CUSTOM;

    /** Resolves a stored name back to a mode, defaulting to {@link #REGULAR} for unknown/blank values. */
    public static GaugeWorkMode fromName(String name) {
        for (GaugeWorkMode mode : values())
            if (mode.name().equals(name)) return mode;
        return REGULAR;
    }
}
