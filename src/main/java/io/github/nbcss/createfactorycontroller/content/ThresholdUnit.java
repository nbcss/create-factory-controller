package io.github.nbcss.createfactorycontroller.content;

import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * How a gauge's target threshold is measured. Replaces the old {@code upTo} boolean so the unit
 * box can cycle between states.
 */
public enum ThresholdUnit {
    ITEMS("", Type.ITEM, 100) {
        @Override
        public int toCountMultiplier(ItemStack stack) {
            return 1;
        }
        @Override
        public Component label() {
            return CreateLang.translate("schedule.condition.threshold.items").component();
        }
    },
    STACKS("▤", Type.ITEM, 100) {
        @Override
        public int toCountMultiplier(ItemStack stack) {
            return Math.max(1, stack.getMaxStackSize());
        }
        @Override
        public Component label() {
            return CreateLang.translate("schedule.condition.threshold.stacks").component();
        }
    },
    /** Fluid amount in millibuckets (1 mB = the unit value). Only valid for a fluid filter. */
    FLUID_MB("mB", Type.FLUID, 10_000) {
        @Override
        public int toCountMultiplier(ItemStack stack) {
            return 1;
        }
        @Override
        public Component label() {
            return Component.translatable("createfactorycontroller.gui.unit_fluid_mb");
        }
    },
    /** Fluid amount in buckets (1 B = 1000 mB). Only valid for a fluid filter. */
    FLUID_BUCKET("B", Type.FLUID, 1_000) {
        @Override
        public int toCountMultiplier(ItemStack stack) {
            return 1000;
        }
        @Override
        public Component label() {
            return Component.translatable("createfactorycontroller.gui.unit_fluid_bucket");
        }
    };
    public enum Type { ITEM, FLUID }

    public final String suffix;
    /** Whether this unit measures a fluid (mB/B) rather than items; fluid and item units never mix. */
    public final Type type;
    public final int maxRequestCount;

    ThresholdUnit(String suffix, Type type, int maxRequestCount) {
        this.suffix = suffix;
        this.type = type;
        this.maxRequestCount = maxRequestCount;
    }

    public int getMaxRequestCount() {
        return maxRequestCount;
    }

    public boolean isFluid() {
        return type == Type.FLUID;
    }

    /** Next mode in the cycle, advancing by {@code dir} (±1) but staying within the same item/fluid group. */
    public ThresholdUnit cycle(int dir) {
        int step = Integer.signum(dir == 0 ? 1 : dir);
        ThresholdUnit[] vals = values();
        int i = ordinal();
        do {
            i = Math.floorMod(i + step, vals.length);
        } while (vals[i].type != this.type);
        return vals[i];
    }

    /**
     * Formats a fluid amount (millibuckets) for display: {@code "XmB"} below one bucket, otherwise buckets with up
     * to three decimals and trailing zeros trimmed ({@code 512→"512mB"}, {@code 2000→"2B"}, {@code 12511→"12.511B"}).
     */
    public static String formatFluidAmount(int millibuckets) {
        if (millibuckets < 1000) return millibuckets + "mB";
        return new java.math.BigDecimal(millibuckets).movePointLeft(3).stripTrailingZeros().toPlainString() + "B";
    }

    /**
     * Formats a millibucket amount in THIS fluid unit (respecting the gauge's choice): the mB unit always shows
     * {@code "XmB"}; the bucket unit shows buckets rounded to one decimal with the trailing {@code .0} trimmed
     * ({@code 2000→"2B"}, {@code 2500→"2.5B"}, {@code 512→"0.5B"}), even below one bucket. Non-fluid units fall
     * back to the magnitude-based {@link #formatFluidAmount}.
     */
    public String formatInUnit(int millibuckets) {
        if (this == FLUID_BUCKET)
            return new java.math.BigDecimal(millibuckets).movePointLeft(3)
                .setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "B";
        if (this == FLUID_MB)
            return millibuckets + "mB";
        return formatFluidAmount(millibuckets);
    }

    public abstract int toCountMultiplier(ItemStack stack);

    /** Display label for the unit box / tooltip. */
    public abstract Component label();

    /** One selectable line of the unit-box tooltip: the active mode gets an arrow + white, others a
     *  bullet + gray. */
    public Component tooltipLine(boolean active) {
        return Component.literal(active ? "-> " : "> ").append(label())
            .withStyle(active ? ChatFormatting.WHITE : ChatFormatting.GRAY);
    }

    public static ThresholdUnit fromName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return ITEMS;
        }
    }
}
