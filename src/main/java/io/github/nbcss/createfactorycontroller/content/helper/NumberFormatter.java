package io.github.nbcss.createfactorycontroller.content.helper;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class NumberFormatter {
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final MathContext COMPACT_PRECISION = new MathContext(2, RoundingMode.HALF_UP);
    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Q", "Qi"};

    private NumberFormatter() {}

    /** Past this magnitude the exact form would print 16+ digits — and doubles have already lost integer precision
     *  (~9e15), so those digits are false precision. Switch to scientific instead. */
    private static final double PLAIN_LIMIT = 1e15;
    /** One step past the largest suffix ({@code Qi} = 10^18): beyond here the compact form's mantissa itself would
     *  keep 20+ digits, so switch to scientific. */
    private static final double COMPACT_LIMIT = 1e21;

    /** Infinity glyphs — a saturated (overflow / ÷0) value reads as ±∞. */
    private static final String INF = "∞", NEG_INF = "-∞";

    public static String format(double value) {
        if (Double.isNaN(value)) return "0";
        if (value == Double.POSITIVE_INFINITY) return INF;
        if (value == Double.NEGATIVE_INFINITY) return NEG_INF;
        if (Math.abs(value) >= PLAIN_LIMIT) return scientific(value);
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    public static String formatCompact(double value) {
        if (Double.isNaN(value)) return "0";
        if (value == Double.POSITIVE_INFINITY) return INF;
        if (value == Double.NEGATIVE_INFINITY) return NEG_INF;
        if (Math.abs(value) < 1000) return format(value);
        if (Math.abs(value) >= COMPACT_LIMIT) return scientific(value);

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

    /** Compact scientific form, e.g. {@code 1.3e30}, {@code -2.5e40}, {@code 1e21}. */
    private static String scientific(double value) {
        String[] parts = String.format(java.util.Locale.ROOT, "%.1E", value).split("E");
        String mantissa = new BigDecimal(parts[0]).stripTrailingZeros().toPlainString();
        return mantissa + "e" + Integer.parseInt(parts[1]);
    }
}
