package io.github.nbcss.createfactorycontroller.content.gui.widget;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared windowing for the scroll-selector hover tooltips (network selector, background-setting selector).
 *
 * <p>Given {@code n} options and a {@code selected} index, it returns the rows to display: each row is either an
 * option index or {@link #MARKER} (a {@code "> ..."} line standing in for hidden options). The option area is always
 * exactly {@link #SIZE} rows when {@code n >= SIZE} (markers included), and the selected option stays centred (3 rows
 * above / 3 below) until it nears an end, where the window slides so the row count never changes.</p>
 *
 * <p>A marker is only ever shown when it hides <b>two or more</b> options — the window slides by whole pairs as the
 * selection moves, so a marker never wastes a line standing in for a single hidden option.</p>
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
