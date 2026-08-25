package io.github.nbcss.logisticscontrol;

import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.jetbrains.annotations.NotNull;

/** Recipe load condition: true while the Filter Link feature is enabled in the server config. */
public record FilterLinkEnabledCondition() implements ICondition {
    public static final FilterLinkEnabledCondition INSTANCE = new FilterLinkEnabledCondition();
    public static final MapCodec<FilterLinkEnabledCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NotNull IContext context) {
        return ServerConfig.filterLinkEnabled();
    }

    @Override
    public @NotNull MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
