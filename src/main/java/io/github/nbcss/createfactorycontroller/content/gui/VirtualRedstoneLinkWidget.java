package io.github.nbcss.createfactorycontroller.content.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualRedstoneLinkBehaviour;
import io.github.nbcss.createfactorycontroller.content.packet.ConfigureRedstoneLinkPacket;
import io.github.nbcss.createfactorycontroller.content.packet.RemoveComponentPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * A redstone link on the canvas. Renders the {@code base}/{@code front} sprites with mode/power overlays, plus the two
 * type frequency icons (Red/Blue). Clicking the <b>top</b> half with an item sets the Red frequency, the
 * <b>bottom</b> half sets Blue (either mouse button, item not consumed); an empty-hand click opens the full-config GUI.
 * While holding an item, the hovered half is highlighted to show which type a click will set.
 */
@OnlyIn(Dist.CLIENT)
public record VirtualRedstoneLinkWidget(VirtualRedstoneLinkBehaviour behaviour) implements VirtualComponentWidget {

    private static final int CELL = 16;

    @Override
    public VirtualComponentPosition position() {
        return behaviour.position();
    }

    private ResourceLocation sprite(String name) {
        return behaviour.getTexture().withSuffix("/" + name);
    }

    @Override
    public void renderBack(GuiGraphics gfx) {
        int x0 = position().x() * CELL, y0 = position().y() * CELL;
        RenderSystem.enableBlend();
        gfx.blitSprite(sprite("back"), x0, y0, CELL, CELL);
        if (behaviour.powered)
            gfx.blitSprite(sprite("back_power"), x0, y0, CELL, CELL);
    }

    @Override
    public void renderFront(GuiGraphics gfx, double mouseX, double mouseY, float glow) {
        int x0 = position().x() * CELL, y0 = position().y() * CELL;
        RenderSystem.enableBlend();
        gfx.blitSprite(sprite("front"), x0, y0, CELL, CELL);
        if (behaviour.powered)
            gfx.blitSprite(sprite("front_power"), x0, y0, CELL, CELL);
        String path = (behaviour.receive ? "receiver_" : "transmitter_") +
                (behaviour.powered ? "on" : "off");
        gfx.blitSprite(sprite(path), x0, y0, CELL, CELL);

        // The two type frequency icons (half-size): Red top-left, Blue bottom-right.
        renderFreqIcon(gfx, behaviour.redFreq, x0 + 6, y0 + 3);
        renderFreqIcon(gfx, behaviour.blueFreq, x0 + 6, y0 + 9);

        // While holding an item, cover the hovered half (top = Red, bottom = Blue) to show which type a click sets.
        ItemStack cursor = Minecraft.getInstance().player.containerMenu.getCarried();
        if (!cursor.isEmpty() && mouseX >= x0 && mouseX < x0 + CELL && mouseY >= y0 && mouseY < y0 + CELL) {
            boolean top = mouseY < y0 + CELL / 2.0;
            int hy = top ? y0 : y0 + CELL / 2;
            gfx.fill(x0, hy, x0 + CELL, hy + CELL / 2, top ? 0x80FF5555 : 0x805599FF);
        }
    }

    private static void renderFreqIcon(GuiGraphics gfx, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        gfx.pose().pushPose();
        gfx.pose().translate(x, y, 0);
        gfx.pose().scale(0.25f, 0.25f, 0.25f);
        gfx.renderItem(stack, 0, 0);
        gfx.pose().popPose();
    }

    @Override
    public List<Component> getTooltip(FactoryControllerMenu menu, boolean selected) {
        List<Component> lines = new ArrayList<>();
        lines.add(CreateLang.itemName(AllBlocks.REDSTONE_LINK.asStack()).color(0xFC8068).component());
        String modeKey = behaviour.receive
            ? "createfactorycontroller.gui.redstone_link.mode.receive"
            : "createfactorycontroller.gui.redstone_link.mode.send";
        lines.add(Component.translatable("createfactorycontroller.gui.redstone_link.mode_prefix",
                Component.translatable(modeKey).withStyle(ChatFormatting.WHITE))
            .withStyle(ChatFormatting.GRAY));
        lines.add(selected
            ? Component.translatable("createfactorycontroller.gui.drag_to_relocate").withStyle(ChatFormatting.GRAY)
            : CreateLang.translate("factory_panel.click_to_configure").style(ChatFormatting.GRAY).component());
        lines.add(Component.translatable("createfactorycontroller.gui.action_remove_component").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    public boolean onClick(FactoryControllerScreen screen, ItemStack carried, double mouseX, double mouseY, int button) {
        if (carried.isEmpty()) {                   // empty hand → open the full-config GUI
            screen.clearSelection();   // entering an overlay clears the selection
            Minecraft.getInstance().setScreen(new ConfigureRedstoneLinkScreen(screen, behaviour.position()));
            return true;
        }
        // Top half sets Red, bottom half sets Blue (either mouse button); the cursor item isn't consumed.
        boolean top = mouseY < position().y() * CELL + CELL / 2.0;
        ItemStack newRed = top ? carried.copy() : behaviour.redFreq;
        ItemStack newBlue = top ? behaviour.blueFreq : carried.copy();
        PacketDistributor.sendToServer(new ConfigureRedstoneLinkPacket(
            screen.getMenu().controllerPos, behaviour.position(), behaviour.receive, newRed, newBlue));
        return true;
    }

    @Override
    public void remove(FactoryControllerScreen screen) {
        PacketDistributor.sendToServer(
            new RemoveComponentPacket(screen.getMenu().controllerPos, behaviour.position()));
    }
}
