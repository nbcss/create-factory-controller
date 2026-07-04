package io.github.nbcss.createfactorycontroller.content.display;

/**
 * A category of data the Factory Controller exposes to a Create Display Link. The selected mode is stored per-link in
 * the display source config ({@code "Mode"} int = ordinal).
 */
public enum DisplayMode {
    /** Every gauge that is active and understocked (stock &lt; demand) — the open production requests. */
    ACTIVE_REQUESTS;

    public static DisplayMode byIndex(int index) {
        DisplayMode[] values = values();
        return index >= 0 && index < values.length ? values[index] : ACTIVE_REQUESTS;
    }
}
