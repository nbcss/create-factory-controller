package io.github.nbcss.createfactorycontroller.content.gui.widget;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.redstone.ManualRedstoneSourceBehaviour;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.helper.TooltipBuilder;
import io.github.nbcss.createfactorycontroller.content.packet.ComponentPressPacket;
import io.github.nbcss.createfactorycontroller.content.packet.RemoveComponentPacket;
import io.github.nbcss.createfactorycontroller.content.render.BatchedBlitter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * A clickable redstone source on the canvas.
 */
@OnlyIn(Dist.CLIENT)
public abstract class VirtualManualSourceWidget<T extends ManualRedstoneSourceBehaviour> implements VirtualComponentWidget {

    private static final int CELL = 16;

    protected final T behaviour;

    protected VirtualManualSourceWidget(T behaviour) {
        this.behaviour = behaviour;
    }

    @Override public T behaviour() { return behaviour; }

    @Override
    public VirtualComponentPosition position() {
        return behaviour.position();
    }

    /** Whether to draw the on sprite. */
    protected boolean renderPowered() {
        return behaviour.isPowered();
    }

    /** The action hint shown in the tooltip when not selected. */
    protected abstract Component actionHint();

    @Override
    public void renderBack(RenderingParameters params) {}

    @Override
    public void renderFront(RenderingParameters params) {
        GuiGraphics gfx = params.graphics();
        int x0 = position().x() * CELL, y0 = position().y() * CELL;
        BatchedBlitter.forSprite(behaviour.getTexture().withSuffix(renderPowered() ? "/front_on" : "/front_off"))
                .blit(gfx.bufferSource(), gfx.pose(), x0, y0, CELL, CELL);
    }

    @Override
    public List<FormattedCharSequence> getTooltip(FactoryControllerMenu menu, boolean selected) {
        return TooltipBuilder.of(Minecraft.getInstance().font)
                .line(behaviour.getName().copy().withColor(behaviour.getColor()))
                .line(behaviour.stateLine())
                .line(selected
                        ? Component.translatable("createfactorycontroller.gui.drag_to_relocate")
                                .withStyle(ChatFormatting.GRAY)
                        : actionHint().copy().withStyle(ChatFormatting.GRAY))
                .line(Component.translatable("createfactorycontroller.gui.action_remove_component")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .build();
    }

    @Override
    public boolean onClick(FactoryControllerScreen screen, ItemStack carried, double mouseX, double mouseY, int button) {
        if (button != 0 || !carried.isEmpty()) return false;
        screen.clearSelection();
        PacketDistributor.sendToServer(new ComponentPressPacket(screen.getMenu().controllerPos, position(), true));
        onPressed(screen);
        return true;
    }

    @Override
    public void remove(FactoryControllerScreen screen) {
        if (screen.isItemTransferDenied()) return;
        PacketDistributor.sendToServer(new RemoveComponentPacket(screen.getMenu().controllerPos, behaviour.position()));
    }
}
