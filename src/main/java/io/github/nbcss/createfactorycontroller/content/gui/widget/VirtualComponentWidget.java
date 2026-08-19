package io.github.nbcss.createfactorycontroller.content.gui.widget;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.checkerframework.dataflow.qual.Pure;
import org.joml.Vector2d;

import java.util.List;

/**
 * A widget corresponding to a component drawn on the controller canvas (gauge, redstone link, …).
 * Handles rendering, click and removal interactions.
 *
 * <h2>Rendering</h2>
 *
 * <p>Components are rendered in several layers:</p>
 * <ul>
 * <li>Back: behind connections</li>
 * <li>Front: in front of connections</li>
 * <li>Overlay: in front of target boxes</li>
 * </ul>
 *
 * <p>The {@code render*} methods are called by the screen during rendering. An implementation may perform rendering
 * to the screen using the vanilla {@link GuiGraphics} {@code params.graphics()} or other methods.</p>
 *
 * <p>An implementation may improve performance with batch rendering.
 * It may write into {@code params.graphics().bufferSource()}, and assume that the calling screen
 * calls {@link GuiGraphics#flush()} after rendering all the components for each layer.</p>
 *
 * <p>Additionally, item and fluid icons can be batch-rendered with {@code params.addBatchRendereredItem}.
 * The queued icons are rendered after all components in the layer, with the correct lighting setup.</p>
 */
@OnlyIn(Dist.CLIENT)
public interface VirtualComponentWidget {

    VirtualComponentPosition position();

    VirtualComponentBehaviour behaviour();

    /** Advances widget animation state. */
    default void tick() {}

    default int connectedTargetColor(ConnectedTargetRole role, VirtualComponentPosition neighbour,
                                     List<Connection> connections) {
        return role.defaultColor();
    }

    /** Back layer, drawn before the connection arrows. Implementations may enqueue batched blits, so callers
     *  must flush after rendering the layer. The Logical Tube settings screen reuses this (and {@link #renderFront})
     *  for its slots via a pose translation — see {@code LogicalTubeSettingsScreen#atSlot}. */
    void renderBack(RenderingParameters params);

    /** Front layer (over the arrows). Implementations may enqueue batched blits, so callers must flush after rendering
     *  the layer. */
    void renderFront(RenderingParameters params);

    /** Placement/relocate preview: back + front, so the component's configured content (a gauge's filter, a link's
     *  frequencies, a tube's mode) shows. The ghost behaviour is freshly built with default runtime state, so the
     *  front's runtime-driven bits (a lit bulb, transmitted power) stay off. Drawn translucent by the screen's ghost
     *  render; the caller sets the alpha. Implementations may enqueue batched blits, so callers must flush after
     *  rendering the layer. */
    default void renderGhost(RenderingParameters params) {
        renderBack(params);
        renderFront(params);
    }

    /** Top-most overlay (the gauge's count label), drawn AFTER the hover/selection target marks so those never cover
     *  it. Implementations may
     *  enqueue batched blits, so callers must flush after rendering the layer. */
    default void renderOverlay(RenderingParameters params) {}

    /** Hover tooltip. When {@code selected}, the "Click to configure" hint is replaced with "Drag to relocate". */
    List<Component> getTooltip(FactoryControllerMenu menu, boolean selected);

    /** Handles a left/right click on this component with the given cursor stack (the type-specific interaction:
     *  configure, set filter, open config, …). Returns true if consumed. */
    boolean onClick(FactoryControllerScreen screen, ItemStack carried, double mouseX, double mouseY, int button);

    /** Shift-click: remove this component from the board. */
    void remove(FactoryControllerScreen screen);

    /** Parameters passed to the rendering methods. */
    interface RenderingParameters {
        /** Vanilla {@link GuiGraphics}. */
        @Pure GuiGraphics graphics();

        /** Mouse XY in pixels. NaN if irrelevant or unknown. */
        @Pure Vector2d mousePosition();
        /** Whether the mouse is hovering over this componen. */
        @Pure boolean mouseOver();

        /** Positions of all placed components this frame — canvas context for resolving connection paths
         *  (e.g. the Arithmetic Tube resolves each incoming wire's entry face to draw its connection strips). */
        @Pure java.util.Set<VirtualComponentPosition> occupiedCells();

        /** Queues an item or fluid icon to be rendered in batch. */
        void addBatchRenderedItem(ItemStack stack, int x, int y);
    }
}
