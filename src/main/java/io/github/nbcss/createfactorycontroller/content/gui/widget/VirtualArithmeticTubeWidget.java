package io.github.nbcss.createfactorycontroller.content.gui.widget;

import com.mojang.math.Axis;
import io.github.nbcss.createfactorycontroller.ClientConfig;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.ArithmeticTubeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.operator.ArithmeticOperator;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.NumberConnection;
import io.github.nbcss.createfactorycontroller.content.gui.screen.ArithmeticTubeSettingsScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.ConnectionPathResolver;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.helper.NumberFormatter;
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
 * An Arithmetic Tube on the canvas.
 */
@OnlyIn(Dist.CLIENT)
public record VirtualArithmeticTubeWidget(ArithmeticTubeBehaviour behaviour) implements VirtualComponentWidget {

    private static final int CELL = 16;
    private static final int PRIMARY_INPUT_COLOR = 0x385BC1;     // blue
    private static final int SECONDARY_INPUT_COLOR = 0xCF2D3A;   // red
    private static final int STRIP_HEIGHT = 8;
    private static final float ICON_DRAW = 15 * 0.5f;   // operator icon: 15×15
    private static final int ICON_BORDER_COLOR = 0x80000000;
    private static final int LABEL_INSET = 1;
    private static final float LABEL_SCALE = 0.5f;
    private static final float LABEL_MIN_SCALE = 0.25f;

    private static final ResourceLocation OPERATOR_ICONS =
            ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "arithmetic_tube/operators/");

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
        if (behaviour.getOperator().arity() == ArithmeticOperator.Arity.BINARY)
            renderStrips(gfx, x0, y0, params.occupiedCells());
        renderOperatorIcon(gfx, x0, y0);
    }

    /** Placement/relocate preview: sprites + operator icon only. */
    @Override
    public void renderGhost(RenderingParameters params) {
        GuiGraphics gfx = params.graphics();
        int x0 = position().x() * CELL, y0 = position().y() * CELL;
        BatchedBlitter.forSprite(sprite("back")).blit(gfx.bufferSource(), gfx.pose(), x0, y0, CELL, CELL);
        BatchedBlitter.forSprite(sprite("front")).blit(gfx.bufferSource(), gfx.pose(), x0, y0, CELL, CELL);
        renderOperatorIcon(gfx, x0, y0);
    }

    /**
     * Draws a coloured strip on each face that has incoming connections
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
            String name = (p && s) ? "strip_both" : p ? "strip_blue" : s ? "strip_red" : null;
            if (name != null) renderStrip(gfx, x0, y0, face, name);
        }
    }

    /** The face a connection enters through. */
    private static Face entryFace(List<Vector2i> path) {
        Vector2i last = path.get(path.size() - 1);
        Vector2i prev = path.get(path.size() - 2);
        int dx = last.x - prev.x, dy = last.y - prev.y;
        if (dx > 0) return Face.LEFT;
        if (dx < 0) return Face.RIGHT;
        if (dy > 0) return Face.UP;
        return Face.DOWN;
    }

    /** Blits the 16×8 UP-oriented strip sprite rotated onto {@code face}. */
    private void renderStrip(GuiGraphics gfx, int x0, int y0, Face face, String spriteName) {
        gfx.pose().pushPose();
        gfx.pose().translate(x0 + CELL / 2f, y0 + CELL / 2f, 0);
        gfx.pose().mulPose(Axis.ZP.rotationDegrees(face.rotation));
        BatchedBlitter.forSprite(sprite(spriteName))
                .blit(gfx.bufferSource(), gfx.pose(), -CELL / 2, -CELL / 2, CELL, STRIP_HEIGHT);
        gfx.pose().popPose();
    }

    private void renderOperatorIcon(GuiGraphics gfx, int x0, int y0) {
        String icon = behaviour.getOperator().iconName();
        gfx.pose().pushPose();
        gfx.pose().translate(x0 + (CELL - ICON_DRAW) / 2f, y0 + (CELL - ICON_DRAW) / 2f, 0);
        gfx.pose().scale(0.5f, 0.5f, 1f);
        BatchedBlitter.forSprite(OPERATOR_ICONS.withSuffix(icon + "_border")).setColorARGB(ICON_BORDER_COLOR)
                .blit(gfx.bufferSource(), gfx.pose(), 0, 0, 15, 15);
        BatchedBlitter.forSprite(OPERATOR_ICONS.withSuffix(icon))
                .blit(gfx.bufferSource(), gfx.pose(), 0, 0, 15, 15);
        gfx.pose().popPose();
    }

    @Override
    public void renderOverlay(RenderingParameters params) {
        if (!params.renderOverlay()) return;
        String label = NumberFormatter.formatCompact(behaviour.getOutput());
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
                Component.literal(NumberFormatter.format(behaviour.getOutput())).withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
        lines.add(selected
                ? Component.translatable("createfactorycontroller.gui.drag_to_relocate").withStyle(ChatFormatting.GRAY)
                : Component.translatable("createfactorycontroller.gui.action_configure").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("createfactorycontroller.gui.action_remove_component").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    public boolean onClick(FactoryControllerScreen screen, ItemStack carried, double mouseX, double mouseY, int button) {
        if (!carried.isEmpty()) return false;
        screen.clearSelection();
        Minecraft.getInstance().setScreen(new ArithmeticTubeSettingsScreen(screen, behaviour.position()));
        return true;
    }

    @Override
    public void remove(FactoryControllerScreen screen) {
        PacketDistributor.sendToServer(new RemoveComponentPacket(screen.getMenu().controllerPos, behaviour.position()));
    }
}
