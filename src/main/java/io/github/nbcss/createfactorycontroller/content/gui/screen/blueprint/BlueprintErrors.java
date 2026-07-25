package io.github.nbcss.createfactorycontroller.content.gui.screen.blueprint;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Shared formatting for blueprint load/save failures shown to the player. */
public final class BlueprintErrors {
    private BlueprintErrors() {}

    public static Component describe(String key, Throwable throwable) {
        return Component.translatable(key, throwable.toString()).withStyle(ChatFormatting.RED);
    }
}
