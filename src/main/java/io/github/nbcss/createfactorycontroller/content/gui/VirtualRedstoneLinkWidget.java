package io.github.nbcss.createfactorycontroller.content.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.VirtualPanelPosition;
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
 * A redstone link on the canvas. Renders the {@code base} sprite with a {@code receiver} overlay in RECEIVE mode and
 * a {@code power} overlay while powered, plus the two channel frequency icons (Red/Blue). Left/right-click with an item
 * sets the Red/Blue frequency; the empty-hand click is a no-op until the link gets its own GUI.
 */
@OnlyIn(Dist.CLIENT)
public record VirtualRedstoneLinkWidget(VirtualRedstoneLinkBehaviour behaviour) implements VirtualComponentWidget {

    private static final int CELL = 16;

    @Override
    public VirtualPanelPosition position() {
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
    public void renderFront(GuiGraphics gfx, double mouseX, double mouseY, float glow, boolean showCount) {
        int x0 = position().x() * CELL, y0 = position().y() * CELL;
        RenderSystem.enableBlend();
        gfx.blitSprite(sprite("front"), x0, y0, CELL, CELL);
        if (behaviour.powered)
            gfx.blitSprite(sprite("front_power"), x0, y0, CELL, CELL);
        String path = (behaviour.receive ? "receiver_" : "transmitter_") +
                (behaviour.powered ? "on" : "off");
        gfx.blitSprite(sprite(path), x0, y0, CELL, CELL);

        // The two channel frequency icons (half-size): Red top-left, Blue bottom-right.
        renderFreqIcon(gfx, behaviour.redFreq, x0 + 6, y0 + 3);
        renderFreqIcon(gfx, behaviour.blueFreq, x0 + 6, y0 + 9);
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
    public List<Component> getTooltip(FactoryControllerMenu menu) {
        List<Component> lines = new ArrayList<>();
        lines.add(CreateLang.itemName(AllBlocks.REDSTONE_LINK.asStack()).color(0xFBDC7D).component());
        String modeKey = behaviour.receive
            ? "createfactorycontroller.gui.redstone_link.mode.receive"
            : "createfactorycontroller.gui.redstone_link.mode.send";
        lines.add(Component.translatable("createfactorycontroller.gui.redstone_link.mode",
                Component.translatable(modeKey).withStyle(ChatFormatting.WHITE))
            .withStyle(ChatFormatting.GRAY));
        lines.add(CreateLang.translate("factory_panel.click_to_configure").style(ChatFormatting.GRAY).component());
        lines.add(Component.translatable("createfactorycontroller.gui.action_remove_component").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    public boolean onClick(FactoryControllerScreen screen, ItemStack carried, double mouseX, double mouseY, int button) {
        if (carried.isEmpty()) {                   // empty hand → open the full-config GUI
            Minecraft.getInstance().setScreen(new ConfigureRedstoneLinkScreen(screen, behaviour.position()));
            return true;
        }
        boolean red = mouseY < position().y() + (double) CELL / 2;
        System.out.println(red);
        ItemStack newRed = red ? carried.copy() : behaviour.redFreq;
        ItemStack newBlue = red ? behaviour.blueFreq : carried.copy();
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
