package io.github.nbcss.content.factorycontroller;

import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * How a gauge's target threshold is measured. Replaces the old {@code upTo} boolean so the unit
 * box can cycle between states.
 */
public enum ThresholdUnit {
    ITEMS("") {
        @Override
        public int toItemCount(ItemStack stack) {
            return 1;
        }
        @Override
        public Component label() {
            return CreateLang.translate("schedule.condition.threshold.items").component();
        }
    },
    STACKS("▤") {
        @Override
        public int toItemCount(ItemStack stack) {
            return Math.max(1, stack.getMaxStackSize());
        }
        @Override
        public Component label() {
            return CreateLang.translate("schedule.condition.threshold.stacks").component();
        }
    };

    public final String suffix;
    ThresholdUnit(String suffix) {
        this.suffix = suffix;
    }

    /** Next mode in the cycle ITEMS → STACKS, advancing by {@code dir} (±1). */
    public ThresholdUnit cycle(int dir) {
        int next = Math.floorMod(ordinal() + Integer.signum(dir == 0 ? 1 : dir), values().length);
        return values()[next];
    }

    public abstract int toItemCount(ItemStack stack);

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
