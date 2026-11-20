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
    ITEMS("", ResourceType.ITEM, 9_999) {
        @Override
        public int toCountMultiplier(ItemStack stack) {
            return 1;
        }
        @Override
        public Component label() {
            return CreateLang.translate("schedule.condition.threshold.items").component();
        }
    },
    STACKS("▤", ResourceType.ITEM, 9_999) {
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
    FLUID_MB("mB", ResourceType.FLUID, 9_999) {
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
    FLUID_BUCKET("B", ResourceType.FLUID, 9_999) {
        @Override
        public int toCountMultiplier(ItemStack stack) {
            return 1000;
        }
        @Override
        public Component label() {
            return Component.translatable("createfactorycontroller.gui.unit_fluid_bucket");
        }
    },
    /** Gas amount in gas units (Create: Crafted Beginning). Only valid for a gas filter. */
    GAS_GU("GU", ResourceType.GAS, 9_999) {
        @Override
        public int toCountMultiplier(ItemStack stack) {
            return 1;
        }
        @Override
        public Component label() {
            return Component.translatable("createfactorycontroller.gui.unit_gas_gu");
        }
    },
    /** Gas amount in kilo gas units (1 kGU = 1000 GU). Only valid for a gas filter. */
    GAS_KGU("kGU", ResourceType.GAS, 9_999) {
        @Override
        public int toCountMultiplier(ItemStack stack) {
            return 1000;
        }
        @Override
        public Component label() {
            return Component.translatable("createfactorycontroller.gui.unit_gas_kgu");
        }
    };

    public final String suffix;
    /** What this unit measures; units of different kinds never mix. */
    public final ResourceType type;
    /** Maximum value in gauge's target storage amount */
    public final int maxRequestCount;

    ThresholdUnit(String suffix, ResourceType type, int maxRequestCount) {
        this.suffix = suffix;
        this.type = type;
        this.maxRequestCount = maxRequestCount;
    }

    public int getMaxRequestCount() {
        return maxRequestCount;
    }

    /** Next mode in the cycle, advancing by {@code dir} (±1) but staying within the same kind. */
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
     * Formats a bulk base-unit amount (mB / GU) in THIS unit: the coarse unit (B / kGU) rounds to one decimal, the
     * fine unit is exact.
     */
    public String formatInUnit(int amount) {
        int multiplier = toCountMultiplier(ItemStack.EMPTY);
        if (multiplier == 1) return amount + suffix;
        return new java.math.BigDecimal(amount).divide(java.math.BigDecimal.valueOf(multiplier))
            .setScale(1, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + suffix;
    }

    /**
     * Renders a threshold {@code value} expressed in THIS unit for display: a bulk amount uses this unit's own
     * notation, an item/stack amount is the plain count followed by the unit marker. Any class holding a count in
     * this unit (target, demand, minimum, …) can route it through here for one consistent look.
     */
    public String format(int value) {
        return format(value, false);
    }

    /**
     * {@link #format(int)} with {@code thousandsSeparator} toggling the {@code 1,234} grouping of item/stack counts
     * (bulk units ignore it — they carry their own notation).
     */
    public String format(int value, boolean thousandsSeparator) {
        if (type.isBulk())
            return formatInUnit(value * toCountMultiplier(ItemStack.EMPTY));
        String number = thousandsSeparator ? String.format(java.util.Locale.US, "%,d", value) : Integer.toString(value);
        return number + suffix;
    }

    public abstract int toCountMultiplier(ItemStack stack);

    /** Display label for the unit box / tooltip. */
    public abstract Component label();

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
