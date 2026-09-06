package io.github.nbcss.createfactorycontroller.content.gui.screen.blueprint;

import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import io.github.nbcss.createfactorycontroller.content.helper.TooltipBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Display helpers that keep unavailable blueprint materials visible without losing their stored item IDs. */
public final class BlueprintMaterialDisplay {
    private BlueprintMaterialDisplay() {}

    public static ItemStack icon(BlueprintStorage.Material material) {
        return new ItemStack(material.isUnknown()
                ? Items.BARRIER
                : BuiltInRegistries.ITEM.get(material.item()));
    }

    public static void renderTooltip(GuiGraphics graphics, Font font, BlueprintStorage.Material material,
                              int mouseX, int mouseY) {
        graphics.renderTooltip(font, tooltip(font, material), mouseX, mouseY);
    }

    public static List<FormattedCharSequence> tooltip(Font font, BlueprintStorage.Material material) {
        return TooltipBuilder.of(font)
                .lines(material.isUnknown()
                        ? List.of(Component.translatable("createfactorycontroller.gui.blueprint.unknown_item")
                                .withStyle(ChatFormatting.RED))
                        : Screen.getTooltipFromItem(Minecraft.getInstance(), icon(material)))
                .build();
    }
}
