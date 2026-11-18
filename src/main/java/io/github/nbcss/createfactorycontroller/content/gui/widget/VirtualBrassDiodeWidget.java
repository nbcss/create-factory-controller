package io.github.nbcss.createfactorycontroller.content.gui.widget;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.redstone.BrassDiodeBehaviour;
import io.github.nbcss.createfactorycontroller.content.gui.screen.BrassDiodeSettingsScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.helper.TooltipBuilder;
import io.github.nbcss.createfactorycontroller.content.render.BatchedBlitter;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * A brass diode on the canvas: body sprites, the progress-tinted indicator, the output torch overlay and the interval label.
 * Click opens {@link BrassDiodeSettingsScreen}.
 */
@OnlyIn(Dist.CLIENT)
public record VirtualBrassDiodeWidget(BrassDiodeBehaviour behaviour, String outputOverlay) implements VirtualComponentWidget {

    private static final int CELL = 16;
    /** Create's {@code BrassDiodeRenderer} indicator colours at progress 0 and 1. */
    public static final int INDICATOR_IDLE = 0x2C0300, INDICATOR_FULL = 0xCD0000;
    private static final float LABEL_SCALE = 0.4f;
    /** Width of the body's middle window the label must fit in. */
    private static final float LABEL_WIDTH = 5f;

    @Override
    public VirtualComponentPosition position() {
        return behaviour.position();
    }

    private static ResourceLocation overlay(String name) {
        return BrassDiodeBehaviour.SPRITE_ROOT.withSuffix("/" + name);
    }

    private void blit(GuiGraphics gfx, BatchedBlitter blitter) {
        blitter.blit(gfx.bufferSource(), gfx.pose(), position().x() * CELL, position().y() * CELL, CELL, CELL);
    }

    @Override
    public void tick() {
        behaviour.clientTick();
    }

    @Override
    public void renderBack(RenderingParameters params) {
        blit(params.graphics(), BatchedBlitter.forSprite(behaviour.getTexture().withSuffix("/back")));
    }

    @Override
    public void renderFront(RenderingParameters params) {
        GuiGraphics gfx = params.graphics();
        blit(gfx, BatchedBlitter.forSprite(behaviour.getTexture().withSuffix("/front")));
        blit(gfx, BatchedBlitter.forSprite(overlay("indicator"))
                .setColorRGB(Color.mixColors(INDICATOR_IDLE, INDICATOR_FULL, behaviour.progress())));
        if (behaviour.outputPowered())
            blit(gfx, BatchedBlitter.forSprite(overlay(outputOverlay)));
    }

    /** The interval, centred on the body like Create's value box. */
    @Override
    public void renderOverlay(RenderingParameters params) {
        if (!params.renderOverlay()) return;
        GuiGraphics gfx = params.graphics();
        Font font = Minecraft.getInstance().font;
        String label = BrassDiodeBehaviour.formatInterval(behaviour.getRedstoneInterval());
        int w = font.width(label);
        float scale = Math.min(LABEL_SCALE, LABEL_WIDTH / w);

        gfx.pose().pushPose();
        gfx.pose().translate(position().x() * CELL + CELL / 2f, position().y() * CELL + CELL / 2f, 200);
        gfx.pose().scale(scale, scale, 1);
        font.drawInBatch8xOutline(Component.literal(label).getVisualOrderText(), -w / 2f, -(font.lineHeight - 1) / 2f,
                0xFFFFFFFF, 0x000000, gfx.pose().last().pose(), gfx.bufferSource(), LightTexture.FULL_BRIGHT);
        gfx.pose().popPose();
    }

    @Override
    public List<FormattedCharSequence> getTooltip(FactoryControllerMenu menu, boolean selected) {
        TooltipBuilder tooltip = TooltipBuilder.of(Minecraft.getInstance().font)
                .line(behaviour.getName().copy().withColor(behaviour.getColor()));
        for (Component line : behaviour.infoTooltip()) tooltip.line(line);
        tooltip.line(selected
                ? Component.translatable("createfactorycontroller.gui.drag_to_relocate").withStyle(ChatFormatting.GRAY)
                : Component.translatable("createfactorycontroller.gui.action_configure").withStyle(ChatFormatting.GRAY));
        return tooltip.line(Component.translatable("createfactorycontroller.gui.action_remove_component")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .build();
    }

    @Override
    public boolean onClick(FactoryControllerScreen screen, ItemStack carried, double mouseX, double mouseY, int button) {
        if (!carried.isEmpty()) return false;
        screen.clearSelection();
        Minecraft.getInstance().setScreen(new BrassDiodeSettingsScreen(screen, behaviour.position()));
        return true;
    }

    @Override
    public void remove(FactoryControllerScreen screen) {
        screen.sendRemoveComponent(behaviour.position());
    }
}
