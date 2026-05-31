package io.github.nbcss.content.factorycontroller.gui;

import com.simibubi.create.AllPartialModels;
import io.github.nbcss.content.factorycontroller.VirtualGaugeBehaviour;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VirtualGaugeWidget extends AbstractWidget {

    private static final int CELL = 16;

    // ── 3D model rendering (tune these in-game) ─────────────────────────────
    /** Render the real 3D factory-panel model instead of the flat item icon. */
    private static final boolean USE_3D_MODEL = true;
    /** Create's factory-panel partial model — the gauge body. */
    private static final dev.engine_room.flywheel.lib.model.baked.PartialModel GAUGE_MODEL =
            AllPartialModels.FACTORY_PANEL;
    /** Block-space rotation (degrees) about each axis. Adjust to face the panel at the camera. */
    private static final float MODEL_ROT_X = 70f;
    private static final float MODEL_ROT_Y = 0f;
    private static final float MODEL_ROT_Z = 0f;
    /** Scale as a fraction of the (zoomed) cell size; ~1.0 fills the cell. */
    private static final float MODEL_SCALE = 2.0f;
    /**
     * Anchor of the model relative to the cell's top-left, expressed in multiples of the rendered
     * model size ({@code scale}) so it stays correct at every zoom. {@code GuiGameElement} draws
     * the block's [0,1]³ from the pose origin (Y-flipped, no auto-centering), so the centering
     * offset scales with the model — not with a fixed pixel count.
     */
    private static final float MODEL_ANCHOR_X = 0.0f;
    private static final float MODEL_ANCHOR_Y = 0.84f;
    /** Depth so the model sits above the slot background but below overlays. */
    private static final int MODEL_Z = 100;

    private final VirtualGaugeBehaviour behaviour;
    private boolean gaugeHovered;
    private boolean gaugeSelected;

    public VirtualGaugeWidget(VirtualGaugeBehaviour behaviour) {
        super(0, 0, CELL, CELL, Component.empty());
        this.behaviour = behaviour;
    }

    public VirtualGaugeBehaviour getBehaviour() {
        return behaviour;
    }

    /**
     * Updates this widget's screen bounds from the current canvas transform.
     * Must be called before {@link #isMouseOver} or {@link #renderOnCanvas}.
     */
    public void applyTransform(int centerX, int centerY, double viewX, double viewY, double zoom) {
        setX((int)(centerX + (behaviour.position().x() * CELL - viewX) * zoom));
        setY((int)(centerY + (behaviour.position().y() * CELL - viewY) * zoom));
        int sw = (int) Math.ceil(CELL * zoom);
        width  = sw;
        height = sw;
    }

    /**
     * Applies the canvas transform and renders the gauge.
     * Delegates to {@link AbstractWidget#render} which calls {@link #renderWidget}.
     */
    public void renderOnCanvas(GuiGraphics gfx,
                               int centerX, int centerY,
                               double viewX, double viewY, double zoom,
                               boolean hovered, boolean selected,
                               int mouseX, int mouseY, float partialTick) {
        applyTransform(centerX, centerY, viewX, viewY, zoom);
        this.gaugeHovered  = hovered;
        this.gaugeSelected = selected;
        render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderWidget(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int sx = getX(), sy = getY(), sw = width;

        // Slot background
        //gfx.fill(sx, sy, sx + sw, sy + sw, 0xFF111111);

        // Selection / hover outline
        if (gaugeSelected) {
            gfx.renderOutline(sx - 1, sy - 1, sw + 2, sw + 2, 0xFFFFAA00);
        } else if (gaugeHovered) {
            gfx.renderOutline(sx - 1, sy - 1, sw + 2, sw + 2, 0xFFFFFFFF);
        }

        // Gauge body
        if (USE_3D_MODEL) renderModel(gfx, sx, sy, sw);
        else              renderFlatIcon(gfx, sx, sy, sw);

        // Status dot (3×3, bottom-right corner) — overlay, always on top.
        int dotColor = behaviour.waitingForNetwork ? 0xFFFFAA00
                     : behaviour.satisfied         ? 0xFF55FF55
                     : behaviour.promisedSatisfied ? 0xFFAAAAFF
                     : 0xFFFF5555;
        gfx.fill(sx + sw - 4, sy + sw - 4, sx + sw - 1, sy + sw - 1, dotColor);
    }

    /** Renders the real 3D factory-panel model, facing the camera (rotation via constants). */
    private void renderModel(GuiGraphics gfx, int sx, int sy, int sw) {
        double scale = sw * MODEL_SCALE;
        gfx.pose().pushPose();
        // Anchor relative to the cell top-left, offset by a fraction of the model size so the
        // centering tracks the model at every zoom (lifted on Z to sit above the slot background).
        gfx.pose().translate(sx + MODEL_ANCHOR_X * scale, sy + MODEL_ANCHOR_Y * scale, MODEL_Z);
        GuiGameElement.of(GAUGE_MODEL)
                .rotateBlock(MODEL_ROT_X, MODEL_ROT_Y, MODEL_ROT_Z)
                .scale(scale)
                .render(gfx);
        gfx.pose().popPose();
    }

    /** Fallback: the flat item icon (base size 16, pose-scaled to fill the cell). */
    private void renderFlatIcon(GuiGraphics gfx, int sx, int sy, int sw) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(behaviour.getItemId()));
        if (stack.isEmpty()) return;
        float scale = sw / 16.0f;
        gfx.pose().pushPose();
        gfx.pose().translate(sx, sy, 0);
        gfx.pose().scale(scale, scale, 1);
        gfx.renderItem(stack, 0, 0);
        gfx.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
