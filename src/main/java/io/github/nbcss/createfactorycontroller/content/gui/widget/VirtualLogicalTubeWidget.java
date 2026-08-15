package io.github.nbcss.createfactorycontroller.content.gui.widget;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.LogicalTubeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.gui.screen.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.LogicalTubeSettingsScreen;
import io.github.nbcss.createfactorycontroller.content.packet.RemoveComponentPacket;
import io.github.nbcss.createfactorycontroller.content.render.BatchedBlitter;
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
 * A logical tube on the canvas. Draws its {@code back}/{@code front_on|off} sprites and the current
 * {@link LogicalTubeBehaviour.Mode} icon (½-size, value-tinted; nothing for {@code NONE}); no indicator bulb. Empty-hand
 * click opens the {@link LogicalTubeSettingsScreen}; shift-click removes (handled by the screen).
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
    public void renderBack(RenderingParameters params) {
        GuiGraphics gfx = params.graphics();
        int x0 = position().x() * CELL, y0 = position().y() * CELL;
        BatchedBlitter.forSprite(sprite("back")).blit(gfx.bufferSource(), gfx.pose(), x0, y0, CELL, CELL);
    }

    @Override
    public void renderFront(RenderingParameters params) {
        GuiGraphics gfx = params.graphics();
        int x0 = position().x() * CELL, y0 = position().y() * CELL;
        boolean powered = behaviour.isPowered();   // current value state
        BatchedBlitter.forSprite(sprite(powered ? "front_on" : "front_off"))
                .blit(gfx.bufferSource(), gfx.pose(), x0, y0, CELL, CELL);

        LogicalTubeBehaviour.Mode mode = behaviour.getMode();
        // Mode symbol: 16×16 sprite drawn at half size (8×8), centred, tinted by the value state.
        BatchedBlitter.forSprite(sprite(mode.name().toLowerCase()))
                .blit(gfx.bufferSource(), gfx.pose(), x0 + CELL / 4, y0 + CELL / 4, CELL / 2, CELL / 2);
    }

    @Override
    public List<Component> getTooltip(FactoryControllerMenu menu, boolean selected) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("createfactorycontroller.component.logical_tube").withColor(behaviour.getColor()));
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
        if (!carried.isEmpty()) return false;   // no item interaction
        screen.clearSelection();
        net.minecraft.client.Minecraft.getInstance().setScreen(new LogicalTubeSettingsScreen(screen, behaviour.position()));
        return true;
    }

    @Override
    public void remove(FactoryControllerScreen screen) {
        PacketDistributor.sendToServer(new RemoveComponentPacket(screen.getMenu().controllerPos, behaviour.position()));
    }
}
