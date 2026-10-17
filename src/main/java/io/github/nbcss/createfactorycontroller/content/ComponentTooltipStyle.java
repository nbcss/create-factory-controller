package io.github.nbcss.createfactorycontroller.content;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.TranslatableEnum;

import java.util.Locale;

/** Style of component and connection hover tooltips in the Controller GUI */
public enum ComponentTooltipStyle implements TranslatableEnum {
    /** Custom one */
    ENHANCED,
    /** The vanilla tooltip */
    CLASSIC;

    @Override
    public Component getTranslatedName() {
        return Component.translatable("createfactorycontroller.config.component_tooltip_style."
                + name().toLowerCase(Locale.ROOT));
    }
}
