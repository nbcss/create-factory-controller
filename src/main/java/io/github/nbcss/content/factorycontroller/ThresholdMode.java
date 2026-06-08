package io.github.nbcss.content.factorycontroller;

import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * How a gauge's target threshold is measured / managed. Replaces the old {@code upTo} boolean so the
 * unit box can cycle through three states.
 *
 * <ul>
 *   <li>{@link #ITEMS} — target is a raw item count (Create's {@code upTo == true}).</li>
 *   <li>{@link #STACKS} — target is measured in stacks (Create's {@code upTo == false}).</li>
 *   <li>{@link #AUTO} — Auto Request Mode: the target is managed automatically to match the demand of
 *       the recipes this gauge feeds; counted in items, not player-editable.</li>
 * </ul>
 */
public enum ThresholdMode {
    ITEMS,
    STACKS,
    AUTO;

    private static final ThresholdMode[] VALUES = values();

    /** Next mode in the cycle ITEMS → STACKS → AUTO → ITEMS, advancing by {@code dir} (±1). */
    public ThresholdMode cycle(int dir) {
        int next = Math.floorMod(ordinal() + Integer.signum(dir == 0 ? 1 : dir), VALUES.length);
        return VALUES[next];
    }

    public boolean isAuto() {
        return this == AUTO;
    }

    /** Whether the threshold is counted per single item (ITEMS and AUTO) rather than per stack. */
    public boolean isPerItem() {
        return this != STACKS;
    }

    public static ThresholdMode fromName(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return ITEMS;
        }
    }

    /** Display label for the unit box / tooltip. */
    public Component label() {
        return switch (this) {
            case ITEMS -> CreateLang.translate("schedule.condition.threshold.items").component();
            case STACKS -> CreateLang.translate("schedule.condition.threshold.stacks").component();
            case AUTO -> Component.translatable("createfactorycontroller.gui.threshold.auto");
        };
    }

    /** One selectable line of the unit-box tooltip: the active mode gets an arrow + white, others a
     *  bullet + gray. */
    public Component tooltipLine(boolean active) {
        return Component.literal(active ? "-> " : "> ").append(label())
            .withStyle(active ? ChatFormatting.WHITE : ChatFormatting.GRAY);
    }
}
