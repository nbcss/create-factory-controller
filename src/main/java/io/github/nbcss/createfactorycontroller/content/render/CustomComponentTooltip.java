package io.github.nbcss.createfactorycontroller.content.render;

import io.github.nbcss.createfactorycontroller.ClientConfig;
import io.github.nbcss.createfactorycontroller.content.ComponentTooltipStyle;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import org.joml.Vector2ic;

import java.util.List;

/**
 * helper to render custom (enhanced) component tooltip :3
 */
@OnlyIn(Dist.CLIENT)
public final class CustomComponentTooltip {
    private static final int BACKGROUND_RGB = 0x100010;
    private static final float FRAME_END_SHADE = 0.5f;
    private static final int Z = 400;

    private CustomComponentTooltip() {}

    public static void render(GuiGraphics graphics, Font font, List<FormattedCharSequence> lines,
                              int rgb, int mouseX, int mouseY) {
        if (ClientConfig.componentTooltipStyle() == ComponentTooltipStyle.CLASSIC) {
            graphics.renderTooltip(font, lines, mouseX, mouseY);
            return;
        }
        int background = ClientConfig.enhancedTooltipBackgroundOpacity() << 24 | BACKGROUND_RGB;
        int frameStart = 0xFF000000 | rgb;
        int frameEnd = 0xFF000000 | Color.mixColors(rgb & 0xFFFFFF, 0x000000, FRAME_END_SHADE);
        List<ClientTooltipComponent> components = lines.stream().map(ClientTooltipComponent::create).toList();
        renderTooltip(graphics, font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE,
                background, frameStart, frameEnd);
    }

    private static void renderTooltip(GuiGraphics graphics, Font font, List<ClientTooltipComponent> components,
                                      int mouseX, int mouseY, ClientTooltipPositioner positioner,
                                      int background, int frameStart, int frameEnd) {
        if (components.isEmpty()) return;
        RenderTooltipEvent.Pre pre = ClientHooks.onRenderTooltipPre(ItemStack.EMPTY, graphics, mouseX, mouseY,
                graphics.guiWidth(), graphics.guiHeight(), components, font, positioner);
        if (pre.isCanceled()) return;
        Font tooltipFont = pre.getFont();

        int width = 0;
        int height = components.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent component : components) {
            width = Math.max(width, component.getWidth(tooltipFont));
            height += component.getHeight();
        }
        int w = width, h = height;
        Vector2ic pos = positioner.positionTooltip(graphics.guiWidth(), graphics.guiHeight(),
                pre.getX(), pre.getY(), w, h);
        int x = pos.x(), y = pos.y();

        graphics.pose().pushPose();
        graphics.drawManaged(() -> TooltipRenderUtil.renderTooltipBackground(graphics, x, y, w, h, Z,
                background, background, frameStart, frameEnd));
        graphics.pose().translate(0.0F, 0.0F, Z);
        int lineY = y;
        for (int i = 0; i < components.size(); i++) {
            ClientTooltipComponent component = components.get(i);
            component.renderText(tooltipFont, x, lineY, graphics.pose().last().pose(), graphics.bufferSource());
            lineY += component.getHeight() + (i == 0 ? 2 : 0);
        }
        lineY = y;
        for (int i = 0; i < components.size(); i++) {
            ClientTooltipComponent component = components.get(i);
            component.renderImage(tooltipFont, x, lineY, graphics);
            lineY += component.getHeight() + (i == 0 ? 2 : 0);
        }
        graphics.pose().popPose();
    }
}
