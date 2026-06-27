package io.github.nbcss.createfactorycontroller.content.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.LogicalTubeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.packet.CycleOperationModePacket;
import io.github.nbcss.createfactorycontroller.content.packet.RemoveComponentPacket;
import net.minecraft.ChatFormatting;
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
 * A logic tube on the canvas. Reuses the gauge {@code back}/{@code front} sprites (interim) and renders the current
 * {@link LogicalTubeBehaviour.Mode} as centered text on the front (nothing for {@code NONE}); no indicator bulb. Empty-hand
 * click cycles the mode; shift-click removes (handled by the screen).
 */
@OnlyIn(Dist.CLIENT)
public record VirtualLogicalTubeWidget(LogicalTubeBehaviour behaviour) implements VirtualComponentWidget {

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
    }

    /** Mode-icon tint by current output value (committed {@code value}, not the pending {@code nextValue}). */
    private static final int ICON_POWERED = 0x913660, ICON_UNPOWERED = 0x741A41;

    @Override
    public void renderFront(GuiGraphics gfx, double mouseX, double mouseY, float glow) {
        int x0 = position().x() * CELL, y0 = position().y() * CELL;
        boolean powered = behaviour.isPowered();   // current value state
        RenderSystem.enableBlend();
        gfx.blitSprite(sprite(powered ? "front_on" : "front_off"), x0, y0, CELL, CELL);

        LogicalTubeBehaviour.Mode mode = behaviour.getMode();
        if (mode == LogicalTubeBehaviour.Mode.NONE) return;   // NONE → no mode icon
        // Mode symbol: 16×16 sprite drawn at half size (8×8), centred, tinted by the value state.
        int rgb = powered ? ICON_POWERED : ICON_UNPOWERED;
        gfx.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        gfx.blitSprite(sprite(mode.name().toLowerCase()), x0 + CELL / 4, y0 + CELL / 4, CELL / 2, CELL / 2);
        gfx.setColor(1f, 1f, 1f, 1f);
    }

    @Override
    public List<Component> getTooltip(FactoryControllerMenu menu, boolean selected) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("createfactorycontroller.component.logical_tube").withColor(0xFC688D));
        lines.add(Component.translatable("createfactorycontroller.gui.mode_prefix",
                Component.translatable("createfactorycontroller.component.logical_tube.mode." + behaviour.getMode().name().toLowerCase())
                        .withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        lines.add(selected
                ? Component.translatable("createfactorycontroller.gui.drag_to_relocate").withStyle(ChatFormatting.GRAY)
                : Component.translatable("createfactorycontroller.gui.action_configure").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("createfactorycontroller.gui.action_remove_component").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    public boolean onClick(FactoryControllerScreen screen, ItemStack carried, double mouseX, double mouseY, int button) {
        if (!carried.isEmpty()) return false;   // no item interaction for now
        PacketDistributor.sendToServer(new CycleOperationModePacket(screen.getMenu().controllerPos, behaviour.position()));
        return true;
    }

    @Override
    public void remove(FactoryControllerScreen screen) {
        PacketDistributor.sendToServer(new RemoveComponentPacket(screen.getMenu().controllerPos, behaviour.position()));
    }
}
