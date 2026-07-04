package io.github.nbcss.createfactorycontroller.content;

/**
 * How a virtual gauge sources its target amount and whether it's player-orderable:
 * <ul>
 *   <li>{@link #NORMAL} — a fixed manual threshold (Create's default behaviour).</li>
 *   <li>{@link #PASSIVE} — target auto-computed from live downstream demand (no manual count).</li>
 *   <li>{@link #PASSIVE_AND_ALLOW_ORDER} — passive, and also exposed in network Stock Keepers as an
 *       orderable Promise Blueprint so players can add demand by hand.</li>
 * </ul>
 */
public enum RequestMode {
    NORMAL("createfactorycontroller.gui.request_mode.normal"),
    PASSIVE("createfactorycontroller.gui.request_mode.passive"),
    PASSIVE_AND_ALLOW_ORDER("createfactorycontroller.gui.request_mode.allow_order");

    public final String translationKey;
    RequestMode(String key) {
        translationKey = key;
    }

    /** Whether the target is demand-driven (was {@code passiveMode}). */
    public boolean isPassive() {
        return this == PASSIVE || this == PASSIVE_AND_ALLOW_ORDER;
    }

    /** Whether this gauge is offered as an orderable blueprint in Stock Keepers (was {@code exposeInStockKeeper}). */
    public boolean allowsOrder() {
        return this == PASSIVE_AND_ALLOW_ORDER;
    }

    /** Safe parse for NBT/packets; unknown names fall back to {@link #NORMAL}. */
    public static RequestMode fromName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }

    public static RequestMode byOrdinal(int ordinal) {
        RequestMode[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
