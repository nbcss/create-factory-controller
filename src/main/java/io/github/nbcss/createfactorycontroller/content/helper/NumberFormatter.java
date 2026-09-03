package io.github.nbcss.createfactorycontroller.content.helper;

import java.text.CompactNumberFormat;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public final class NumberFormatter {
    // Maximum character length of the formatted string
    private static final int FORMAT_LENGTH_LIMIT = 18;

    private static final DecimalFormatSymbols FULL_SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ROOT);
    private static final DecimalFormat FULL_FORMAT = (DecimalFormat) NumberFormat.getNumberInstance(Locale.ROOT);
    private static final DecimalFormat FULL_FRAC_FORMAT = (DecimalFormat) NumberFormat.getNumberInstance(Locale.ROOT);
    private static final DecimalFormat FULL_SCI_FORMAT = (DecimalFormat) NumberFormat.getNumberInstance(Locale.ROOT);
    static {
        FULL_SYMBOLS.setInfinity("∞");
        FULL_SYMBOLS.setNaN("NaN");
        FULL_FORMAT.setDecimalFormatSymbols(FULL_SYMBOLS);
        FULL_FORMAT.applyPattern("0.");
        FULL_FORMAT.setDecimalSeparatorAlwaysShown(false);
        FULL_FORMAT.setMaximumIntegerDigits(309);
        FULL_FORMAT.setMaximumFractionDigits(309);
        FULL_FRAC_FORMAT.setDecimalFormatSymbols(FULL_SYMBOLS);
        FULL_FRAC_FORMAT.applyPattern("0.");
        FULL_FRAC_FORMAT.setMaximumFractionDigits(FORMAT_LENGTH_LIMIT - "0.".length());
        FULL_SCI_FORMAT.setDecimalFormatSymbols(FULL_SYMBOLS);
        FULL_SCI_FORMAT.applyPattern("0.E0");
        FULL_SCI_FORMAT.setDecimalSeparatorAlwaysShown(false);
        FULL_SCI_FORMAT.setMaximumFractionDigits(FORMAT_LENGTH_LIMIT - "0.".length() - "E-308".length());
    }

    public static String format(double value) {
        var full = FULL_FORMAT.format(value);
        var abs = Math.abs(value);
        if (full.length() <= FORMAT_LENGTH_LIMIT)
            return full;
        else if (abs >= 0.0001 && abs < 1)
            return FULL_FRAC_FORMAT.format(value);
        else
            return FULL_SCI_FORMAT.format(value);
    }

    // One compact pattern per power of ten (10^0, 10^1, 10^2, …). The empty patterns (10^0..10^2) leave the value unscaled
    private static final String[] COMPACT_PATTERNS = {
            "", "", "",
            "0K", "00K", "000K",
            "0M", "00M", "000M",
            "0B", "00B", "000B",
            "0T", "00T", "000T",
            "0Q", "00Q", "000Q",
    };

    /** One step past the largest suffix ({@code Q} = 10^15). */
    private static final double COMPACT_LIMIT = 1e18;

    private static final DecimalFormatSymbols COMPACT_SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ROOT);
    private static final CompactNumberFormat COMPACT_FORMAT;
    private static final DecimalFormat COMPACT_FRAC_FORMAT = (DecimalFormat) NumberFormat.getNumberInstance(Locale.ROOT);
    private static final DecimalFormat COMPACT_SCI_FORMAT = (DecimalFormat) NumberFormat.getNumberInstance(Locale.ROOT);
    static {
        COMPACT_SYMBOLS.setInfinity("∞");
        COMPACT_SYMBOLS.setNaN("");
        COMPACT_FORMAT = new CompactNumberFormat(FULL_FORMAT.toPattern(), COMPACT_SYMBOLS, COMPACT_PATTERNS);
        COMPACT_FORMAT.setMaximumFractionDigits(1);
        COMPACT_FRAC_FORMAT.setDecimalFormatSymbols(COMPACT_SYMBOLS);
        COMPACT_FRAC_FORMAT.applyPattern("0.###");
        COMPACT_FRAC_FORMAT.setDecimalSeparatorAlwaysShown(false);
        COMPACT_SCI_FORMAT.setDecimalFormatSymbols(COMPACT_SYMBOLS);
        COMPACT_SCI_FORMAT.applyPattern("0.0E0");
    }

    public static String formatCompact(double value) {
        double abs = Math.abs(value);
        if (Double.isNaN(value) || Double.isInfinite(value) || value == 0 || abs >= 1 && abs < COMPACT_LIMIT)
            return COMPACT_FORMAT.format(value);
        else if (abs >= 0.001 && abs < 1)
            return COMPACT_FRAC_FORMAT.format(value);
        else
            return COMPACT_SCI_FORMAT.format(value);
    }

}
