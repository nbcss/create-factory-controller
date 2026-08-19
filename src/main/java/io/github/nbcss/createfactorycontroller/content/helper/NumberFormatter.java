package io.github.nbcss.createfactorycontroller.content.helper;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class NumberFormatter {
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final MathContext COMPACT_PRECISION = new MathContext(2, RoundingMode.HALF_UP);
    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Q", "Qi"};

    private NumberFormatter() {}

    public static String format(double value) {
        if (!Double.isFinite(value)) return "0";
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    public static String formatCompact(double value) {
        if (!Double.isFinite(value)) return "0";
        if (Math.abs(value) < 1000) return format(value);

        BigDecimal scaled = BigDecimal.valueOf(value);
        int suffix = 0;
        while (scaled.abs().compareTo(THOUSAND) >= 0 && suffix < SUFFIXES.length - 1) {
            scaled = scaled.movePointLeft(3);
            suffix++;
        }

        scaled = scaled.round(COMPACT_PRECISION);
        if (scaled.abs().compareTo(THOUSAND) >= 0 && suffix < SUFFIXES.length - 1) {
            scaled = scaled.movePointLeft(3).round(COMPACT_PRECISION);
            suffix++;
        }
        return scaled.stripTrailingZeros().toPlainString() + SUFFIXES[suffix];
    }
}
