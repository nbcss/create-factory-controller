package io.github.nbcss.createfactorycontroller.content.gui;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * A component drawn on the controller canvas (gauge, redstone link, …). The screen keeps one per occupied cell and
 * dispatches rendering, hover tooltips, and clicks through this interface so it stays component-type agnostic.
 */
@OnlyIn(Dist.CLIENT)
public interface VirtualComponentWidget {

    VirtualComponentPosition position();

    VirtualComponentBehaviour behaviour();

    /** Back layer, drawn before the connection arrows. */
    void renderBack(GuiGraphics gfx);

    /** Front layer (over the arrows). {@code glow} is the indicator chase value. */
    void renderFront(GuiGraphics gfx, double mouseX, double mouseY, float glow);

    /** Top-most overlay (the gauge's count label), drawn AFTER the hover/selection target marks so those never cover
     *  it. {@code showCount} gates the label (full-overlay mode, or this is the hovered cell). Default: nothing. */
    default void renderOverlay(GuiGraphics gfx, boolean showCount) {}

    /** Hover tooltip. When {@code selected}, the "Click to configure" hint is replaced with "Drag to relocate". */
    List<Component> getTooltip(FactoryControllerMenu menu, boolean selected);

    /** Handles a left/right click on this component with the given cursor stack (the type-specific interaction:
     *  configure, set filter, open config, …). Returns true if consumed. */
    boolean onClick(FactoryControllerScreen screen, ItemStack carried, double mouseX, double mouseY, int button);

    /** Shift-click: remove this component from the board. */
    void remove(FactoryControllerScreen screen);
}
