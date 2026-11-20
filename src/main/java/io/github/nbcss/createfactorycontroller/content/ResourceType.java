package io.github.nbcss.createfactorycontroller.content;

import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.compat.gas.GasCompat;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What a gauge filter measures. Fluid (mB) and gas (GU) keys are virtual items riding item logistics, but are
 * "bulk": amount-based, one recipe slot per ingredient, their own units, never ignore-data.
 */
public enum ResourceType {
    ITEM(1, 1, 1, ""),
    FLUID(1000, 90_000, 64_000, "b"),
    GAS(1000, 1_000_000, 1_000_000, "k");

    /** Amount a fresh ingredient wire asks for, and what one ordered production pattern stands for (1 B / 1 kGU). */
    public final int baseAmount;
    /** Max amount of one bulk ingredient per craft (unused for items, which cap at their stack size). */
    public final int bulkIngredientCap;
    /** Max produced amount per request for a bulk output (unused for items, see {@code itemOutputCap}). */
    public final int bulkOutputCap;
    /** Compact-font suffix for a count of production patterns (one pattern = {@link #baseAmount}). */
    public final String patternGlyph;

    ResourceType(int baseAmount, int bulkIngredientCap, int bulkOutputCap, String patternGlyph) {
        this.baseAmount = baseAmount;
        this.bulkIngredientCap = bulkIngredientCap;
        this.bulkOutputCap = bulkOutputCap;
        this.patternGlyph = patternGlyph;
    }

    public static ResourceType of(ItemStack stack) {
        return FluidCompat.isFluidFilter(stack) ? FLUID : GasCompat.isGasFilter(stack) ? GAS : ITEM;
    }

    public static boolean isBulk(ItemStack stack) {
        return of(stack).isBulk();
    }

    public boolean isBulk() {
        return this != ITEM;
    }

    /** Max amount of {@code ingredient} (of this kind) in one craft: a bulk cap, or one item stack. */
    public int ingredientCap(ItemStack ingredient) {
        return isBulk() ? bulkIngredientCap : Math.max(1, ingredient.getMaxStackSize());
    }

    /** Unit a gauge switches to when its filter becomes this kind. */
    public ThresholdUnit defaultUnit() {
        return switch (this) {
            case ITEM -> ThresholdUnit.ITEMS;
            case FLUID -> ThresholdUnit.FLUID_BUCKET;
            case GAS -> ThresholdUnit.GAS_KGU;
        };
    }

    /** The {fine, coarse} unit pair the unit box toggles between. */
    public ThresholdUnit fineUnit() {
        return switch (this) {
            case ITEM -> ThresholdUnit.ITEMS;
            case FLUID -> ThresholdUnit.FLUID_MB;
            case GAS -> ThresholdUnit.GAS_GU;
        };
    }

    public ThresholdUnit coarseUnit() {
        return switch (this) {
            case ITEM -> ThresholdUnit.STACKS;
            case FLUID -> ThresholdUnit.FLUID_BUCKET;
            case GAS -> ThresholdUnit.GAS_KGU;
        };
    }

    /** Full-precision amount in base units: {@code 1234}, {@code 1.234B}, {@code 1.234kGU}. */
    public String format(int amount) {
        return switch (this) {
            case ITEM -> String.valueOf(amount);
            case FLUID -> amount < 1000 ? amount + "mB" : thousands(amount, 3) + "B";
            case GAS -> amount < 1000 ? amount + "GU"
                : amount < 1_000_000 ? thousands(amount, 3) + "kGU" : rounded(amount, 6, 2) + "MGU";
        };
    }

    /** Compact amount (one decimal) for tight labels: {@code 1.2B}, {@code 1.2kGU}. */
    public String formatShort(int amount) {
        return switch (this) {
            case ITEM -> String.valueOf(amount);
            case FLUID -> amount < 1000 ? amount + "mB" : rounded(amount, 3, 1) + "B";
            case GAS -> amount < 1000 ? amount + "GU"
                : amount < 1_000_000 ? rounded(amount, 3, 1) + "kGU" : rounded(amount, 6, 1) + "MGU";
        };
    }

    private static String thousands(int amount, int shift) {
        return new BigDecimal(amount).movePointLeft(shift).stripTrailingZeros().toPlainString();
    }

    private static String rounded(int amount, int shift, int scale) {
        return new BigDecimal(amount).movePointLeft(shift).setScale(scale, RoundingMode.HALF_UP)
            .stripTrailingZeros().toPlainString();
    }
}
