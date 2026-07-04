package io.github.nbcss.createfactorycontroller.content.gui.widget;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared windowing for the scroll-selector hover tooltips (network selector, background-setting selector).
 */
public final class ScrollListWindow {

    /** A {@code "> ..."} row (hidden options) rather than a concrete option index. */
    public static final int MARKER = -1;
    /** Fixed option-area height (rows), markers included, whenever there are at least this many options. */
    public static final int SIZE = 7;

    private ScrollListWindow() {}

    public static List<Integer> rows(int n, int selected) {
        List<Integer> rows = new ArrayList<>();
        if (n <= 0) return rows;

        int total = Math.min(n, SIZE);
        // Centre a `total`-wide window on the selection, clamped so it stays within [0, n].
        int start = Mth.clamp(selected - SIZE / 2, 0, Math.max(0, n - total));
        int end = start + total;

        boolean top = start > 0;          // options hidden above the window
        boolean bot = end < n;            // options hidden below the window
        if (top) start += 1;              // drop the boundary option; the marker subsumes it (+ everything above)
        if (bot) end -= 1;

        if (top) rows.add(MARKER);
        for (int i = start; i < end; i++) rows.add(i);
        if (bot) rows.add(MARKER);
        return rows;
    }
}
