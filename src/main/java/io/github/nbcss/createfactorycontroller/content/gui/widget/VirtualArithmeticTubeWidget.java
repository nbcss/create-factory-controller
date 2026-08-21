package io.github.nbcss.createfactorycontroller.content.gui.widget;

import com.mojang.math.Axis;
import io.github.nbcss.createfactorycontroller.ClientConfig;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.ArithmeticTubeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.NumberConnection;
import io.github.nbcss.createfactorycontroller.content.gui.screen.ConnectionPathResolver;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.packet.RemoveComponentPacket;
import io.github.nbcss.createfactorycontroller.content.render.BatchedBlitter;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * An Arithmetic Tube on the canvas. Borrows the Logical Tube's {@code back}/{@code front_off} sprites, draws the
 * current operator's glyph centred, and — like a gauge — draws the current output value as a bottom-right label.
 * No configuration GUI yet; empty-hand clicks do nothing, shift-click removes (handled by the screen).
 */
@OnlyIn(Dist.CLIENT)
public record VirtualArithmeticTubeWidget(ArithmeticTubeBehaviour behaviour) implements VirtualComponentWidget {

    private static final int CELL = 16;
    private static final int PRIMARY_INPUT_COLOR = 0xCF2D3A;
    private static final int SECONDARY_INPUT_COLOR = 0x385BC1;
    private static final int STRIP_HEIGHT = 8;   // the strip sprite is 16×8, UP-oriented and rotated per face
    private static final int LABEL_INSET = 1;
    private static final float LABEL_SCALE = 0.5f;
    private static final float LABEL_MIN_SCALE = 0.25f;
    private static final float SYMBOL_MAX_SCALE = 0.7f;

    /** A connection-carrying face of the tube. {@code rotation} rotates the UP-oriented strip sprite (clockwise,
     *  {@code Axis.ZP}) onto this face. */
    private enum Face {
        UP(0), RIGHT(90), DOWN(180), LEFT(270);
        static final Face[] VALUES = values();
        final float rotation;
        Face(float rotation) { this.rotation = rotation; }
    }

    @Override
    public VirtualComponentPosition position() {
        return behaviour.position();
    }

    @Override
    public int connectedTargetColor(ConnectedTargetRole role, VirtualComponentPosition neighbour,
                                    List<Connection> connections) {
        if (role == ConnectedTargetRole.INPUT)
            return behaviour.isSecondarySource(neighbour) ? SECONDARY_INPUT_COLOR : PRIMARY_INPUT_COLOR;
        return VirtualComponentWidget.super.connectedTargetColor(role, neighbour, connections);
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
        BatchedBlitter.forSprite(sprite("front")).blit(gfx.bufferSource(), gfx.pose(), x0, y0, CELL, CELL);
        renderStrips(gfx, x0, y0, params.occupiedCells());
        renderOperatorSymbol(gfx, x0, y0);
    }

    /** Placement/relocate preview: sprites + operator glyph only. A ghost has no live connection graph, so it draws
     *  no connection strips (nothing to show anyway). */
    @Override
    public void renderGhost(RenderingParameters params) {
        GuiGraphics gfx = params.graphics();
        int x0 = position().x() * CELL, y0 = position().y() * CELL;
        BatchedBlitter.forSprite(sprite("back")).blit(gfx.bufferSource(), gfx.pose(), x0, y0, CELL, CELL);
        BatchedBlitter.forSprite(sprite("front")).blit(gfx.bufferSource(), gfx.pose(), x0, y0, CELL, CELL);
        renderOperatorSymbol(gfx, x0, y0);
    }

    /**
     * Draws a coloured strip on each face that has incoming connections: red = all primary inputs, blue = only the
     * secondary input, both = a mix on one face. A face is a wire's entry direction (its resolved path's final
     * segment into this cell); several wires may share a face.
     */
    private void renderStrips(GuiGraphics gfx, int x0, int y0, Set<VirtualComponentPosition> occupied) {
        boolean[] primary = new boolean[Face.VALUES.length];
        boolean[] secondary = new boolean[Face.VALUES.length];
        for (Connection c : behaviour.incomingConnections(NumberConnection.TYPE)) {
            List<Vector2i> path = ConnectionPathResolver.resolvePath(c, occupied);
            if (path == null || path.size() < 2) continue;
            int face = entryFace(path).ordinal();
            if (behaviour.isSecondarySource(c.from)) secondary[face] = true;
            else primary[face] = true;
        }
        for (Face face : Face.VALUES) {
            boolean p = primary[face.ordinal()], s = secondary[face.ordinal()];
            String name = (p && s) ? "strip_both" : p ? "strip_red" : s ? "strip_blue" : null;
            if (name != null) renderStrip(gfx, x0, y0, face, name);
        }
    }

    /** The face a wire enters through — the direction of its resolved path's final segment into this (sink) cell. */
    private static Face entryFace(List<Vector2i> path) {
        Vector2i last = path.get(path.size() - 1);   // the sink (this tube) cell
        Vector2i prev = path.get(path.size() - 2);
        int dx = last.x - prev.x, dy = last.y - prev.y;
        if (dx > 0) return Face.LEFT;
        if (dx < 0) return Face.RIGHT;
        if (dy > 0) return Face.UP;
        return Face.DOWN;
    }

    /** Blits the 16×8 UP-oriented strip sprite rotated onto {@code face} (about the cell centre). */
    private void renderStrip(GuiGraphics gfx, int x0, int y0, Face face, String spriteName) {
        gfx.pose().pushPose();
        gfx.pose().translate(x0 + CELL / 2f, y0 + CELL / 2f, 0);
        gfx.pose().mulPose(Axis.ZP.rotationDegrees(face.rotation));
        BatchedBlitter.forSprite(sprite(spriteName))
                .blit(gfx.bufferSource(), gfx.pose(), -CELL / 2, -CELL / 2, CELL, STRIP_HEIGHT);
        gfx.pose().popPose();
    }

    /** The operator glyph, centred in the cell and scaled to fit (placeholder for a future icon). */
    private void renderOperatorSymbol(GuiGraphics gfx, int x0, int y0) {
        String symbol = behaviour.getOperator().symbol();
        if (symbol.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        int w = font.width(symbol);
        if (w <= 0) return;
        float scale = Mth.clamp((CELL - 3) / (float) w, LABEL_MIN_SCALE, SYMBOL_MAX_SCALE);

        gfx.pose().pushPose();
        gfx.pose().translate(x0 + CELL / 2f, y0 + CELL / 2f, 200);
        gfx.pose().scale(scale, scale, 1);
        Matrix4f matrix = gfx.pose().last().pose();
        font.drawInBatch8xOutline(Component.literal(symbol).getVisualOrderText(),
                -w / 2f, -font.lineHeight / 2f, 0xFFFFFFFF, 0x000000,
                matrix, gfx.bufferSource(), LightTexture.FULL_BRIGHT);
        gfx.pose().popPose();
    }

    @Override
    public void renderOverlay(RenderingParameters params) {
        if (!params.renderOverlay()) return;
        String label = behaviour.getOutputLabel();
        if (label.isEmpty()) return;

        GuiGraphics gfx = params.graphics();
        int x0 = position().x() * CELL, y0 = position().y() * CELL;
        Font font = Minecraft.getInstance().font;
        int w = font.width(label);
        float scale = ClientConfig.dynamicLabelScaling()
                ? Mth.clamp((CELL - LABEL_INSET) / (float) (w + 1), LABEL_MIN_SCALE, LABEL_SCALE)
                : LABEL_SCALE;

        gfx.pose().pushPose();
        gfx.pose().translate(x0 + CELL - LABEL_INSET, y0 + CELL - LABEL_INSET, 200);
        gfx.pose().scale(scale, scale, 1);
        Matrix4f matrix = gfx.pose().last().pose();
        font.drawInBatch8xOutline(Component.literal(label).getVisualOrderText(), -w, -font.lineHeight,
                0xFFFFFFFF, 0x000000, matrix, gfx.bufferSource(), LightTexture.FULL_BRIGHT);
        gfx.pose().popPose();
    }

    @Override
    public List<Component> getTooltip(FactoryControllerMenu menu, boolean selected) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("createfactorycontroller.component.arithmetic_tube").withColor(behaviour.getColor()));
        lines.add(Component.translatable("createfactorycontroller.arithmetic_tube.operator_prefix",
                behaviour.getOperator().displayName().copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("createfactorycontroller.arithmetic_tube.output",
                Component.literal(behaviour.getOutputText()).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        if (selected)
            lines.add(Component.translatable("createfactorycontroller.gui.drag_to_relocate").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("createfactorycontroller.gui.action_remove_component").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    public boolean onClick(FactoryControllerScreen screen, ItemStack carried, double mouseX, double mouseY, int button) {
        return false;   // no configuration GUI yet
    }

    @Override
    public void remove(FactoryControllerScreen screen) {
        PacketDistributor.sendToServer(new RemoveComponentPacket(screen.getMenu().controllerPos, behaviour.position()));
    }
}
