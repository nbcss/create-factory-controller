package io.github.nbcss.createfactorycontroller.content.gui.screen.blueprint;

import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
        if (material.isUnknown()) {
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("createfactorycontroller.gui.blueprint.unknown_item")
                            .withStyle(ChatFormatting.RED)), mouseX, mouseY);
            return;
        }
        graphics.renderTooltip(font, icon(material), mouseX, mouseY);
    }
}
