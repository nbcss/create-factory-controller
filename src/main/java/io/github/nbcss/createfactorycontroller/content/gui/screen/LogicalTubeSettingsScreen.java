package io.github.nbcss.createfactorycontroller.content.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.item.TooltipHelper;
import io.github.nbcss.createfactorycontroller.content.render.ComponentRenderingHelper;
import org.anti_ad.mc.ipn.api.IPNIgnore;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.gauge.LogicalTubeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.RedstoneConnection;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.gui.widget.HelpButton;
import io.github.nbcss.createfactorycontroller.content.gui.widget.InteractiveAreaWidget;
import io.github.nbcss.createfactorycontroller.content.gui.widget.VirtualComponentWidget;
import io.github.nbcss.createfactorycontroller.content.gui.widget.TooltipIconButton;
import io.github.nbcss.createfactorycontroller.content.packet.ConfigureLogicalTubePacket;
import io.github.nbcss.createfactorycontroller.content.packet.RemoveConnectionPacket;
import io.github.nbcss.createfactorycontroller.content.packet.ReverseConnectionPacket;
import io.github.nbcss.createfactorycontroller.content.render.VirtualConnectionRenderer;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2d;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Full-config overlay for a Logical Tube.
 */
@OnlyIn(Dist.CLIENT)
@IPNIgnore
public class LogicalTubeSettingsScreen extends AbstractSimiContainerScreen<FactoryControllerMenu>
        implements PanelSyncListener {

    private static final ResourceLocation PANEL_TEX =   // TEMP: reuse the redstone-link panel texture
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "textures/gui/logical_tube.png");
    private static final ResourceLocation MODE_BUTTON_ICON_PREFIX =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "icons/logic_gates/");
    private static final ResourceLocation LOGIC_GATE_ICON_PREFIX =
            ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "factory_controller/logical_tube/");
    private static final int PANEL_W = 200, PANEL_H = 103;

    private static final int CELL = 16;
    private static final int COLS_PER_SIDE = 5;
    private static final int GRID_COLS = COLS_PER_SIDE * 2 + 1;
    private static final int TUBE_COL = COLS_PER_SIDE, MID_ROW = 1;
    private static final int MAX_PER_SIDE = COLS_PER_SIDE * 2;    // 5 cols × 2 rows

    private final FactoryControllerScreen controller;
    private final VirtualComponentPosition tubePos;

    private final ComponentRenderingHelper componentRenderingHelper = new ComponentRenderingHelper();

    private int panelX, panelY;
    private TooltipIconButton relocateButton, addConnectionButton, confirmButton;
    private HelpButton helpButton;
    /** One toggle button per {@link LogicalTubeBehaviour.Mode}, ordered as the enum; the live mode glows green. */
    private final java.util.EnumMap<LogicalTubeBehaviour.Mode, TooltipIconButton> modeButtons =
            new java.util.EnumMap<>(LogicalTubeBehaviour.Mode.class);

    public LogicalTubeSettingsScreen(FactoryControllerScreen controller, VirtualComponentPosition tubePos) {
        super(controller.getMenu(), Minecraft.getInstance().player.getInventory(),
              Component.translatable("createfactorycontroller.gui.logical_tube_settings"));
        this.controller = controller;
        this.tubePos = tubePos;
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_OPEN.get(), 1f));
    }

    @Override
    protected void init() {
        setWindowSize(controller.guiWidth(), controller.guiHeight());
        setWindowOffset(0, 0);
        super.init();

        panelX = leftPos + (imageWidth - PANEL_W) / 2;
        panelY = topPos + (imageHeight - PANEL_H) / 2;

        menu.repositionSlots(-2000, -2000, false);

        relocateButton = new TooltipIconButton(panelX + 8, panelY + 79, AllIcons.I_MOVE_GAUGE);
        relocateButton.withCallback(() -> { controller.beginRelocateMode(tubePos); Minecraft.getInstance().setScreen(controller); });
        relocateButton.setToolTip(Component.translatable("createfactorycontroller.gui.action_relocate"));
        addWidget(relocateButton);

        addConnectionButton = new TooltipIconButton(panelX + 30, panelY + 79, AllIcons.I_ADD);
        addConnectionButton.withCallback(() -> { controller.beginConnectionMode(tubePos); Minecraft.getInstance().setScreen(controller); });
        addConnectionButton.setToolTip(CreateLang.translate("gui.factory_panel.connect_input").component());
        addWidget(addConnectionButton);

        LogicalTubeBehaviour.Mode[] modes = LogicalTubeBehaviour.Mode.values();
        int groupX = panelX + 160 - modes.length * 18 - 4;
        for (int i = 0; i < modes.length; i++) {
            LogicalTubeBehaviour.Mode m = modes[i];
            ScreenElement element = modeButtonIcon(m);
            TooltipIconButton button = new TooltipIconButton(groupX + i * 18, panelY + 79, element);
            button.withCallback(() -> {
                PacketDistributor.sendToServer(new ConfigureLogicalTubePacket(menu.controllerPos, tubePos, m.name()));
                playClickSound();
            });
            button.withTooltip(() -> modeButtonTooltip(m));
            modeButtons.put(m, button);
            addWidget(button);
        }

        confirmButton = new TooltipIconButton(panelX + 167, panelY + 79, AllIcons.I_CONFIRM);
        confirmButton.withCallback(() -> Minecraft.getInstance().setScreen(controller));
        confirmButton.setToolTip(CreateLang.translate("gui.factory_panel.save_and_close").component());
        addWidget(confirmButton);

        helpButton = new HelpButton(panelX + PANEL_W - HelpButton.WIDTH - 13, panelY + 3,
                HelpButton.ColorPalette.ROSE, "electron-tube.html");
        addWidget(helpButton);

        addRenderableWidget(new InteractiveAreaWidget(
                gridX(), gridY(), GRID_COLS * CELL, 3 * CELL,
                (mouseX, mouseY) -> {
                    ConnectionSlot hovered = connectionSlotAt(mouseX, mouseY);
                    if (hovered != null) {
                        VirtualComponentBehaviour partner = menu.componentAt(
                                hovered.output() ? hovered.connection().to : hovered.connection().from);
                        List<Component> tip = new ArrayList<>();
                        if (partner != null) {
                            tip.add(partner.getName().copy().withColor(partner.getColor()));
                            tip.addAll(partner.infoTooltip());
                        }
                        if (hovered.connection().canReverse(menu))
                            tip.add(Component.translatable("createfactorycontroller.gui.logical_tube.reverse")
                                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                        tip.add(Component.translatable("createfactorycontroller.gui.action_disconnect")
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                        return tip;
                    }
                    if (!tubeCellHovered(mouseX, mouseY)) return List.of();
                    int nIn = inputs().size();
                    int nOut = outputs().size();
                    return List.of(
                            Component.translatable("createfactorycontroller.gui.mode_prefix",
                                    Component.translatable("createfactorycontroller.component.logical_tube.mode."
                                                    + currentMode().name().toLowerCase())
                                            .withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY),
                            Component.translatable("createfactorycontroller.gui.logical_tube.input_connections",
                                    Component.literal(String.valueOf(nIn)).withStyle(
                                            nIn > 0 ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY))
                                    .withStyle(ChatFormatting.GRAY),
                            Component.translatable("createfactorycontroller.gui.logical_tube.output_connections",
                                    Component.literal(String.valueOf(nOut)).withStyle(
                                            nOut > 0 ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY))
                                    .withStyle(ChatFormatting.GRAY));
                }).onClick((mouseX, mouseY, button) -> {
                    ConnectionSlot hovered = connectionSlotAt(mouseX, mouseY);
                    if (hovered == null) return false;
                    Connection connection = hovered.connection();
                    if (hasShiftDown())
                        PacketDistributor.sendToServer(new RemoveConnectionPacket(
                                menu.controllerPos, connection.from, connection.to, connection.type.name()));
                    else
                        PacketDistributor.sendToServer(new ReverseConnectionPacket(
                                menu.controllerPos, connection.from, connection.to, connection.type.name()));
                    playClickSound();
                    return true;
                }));
    }

    private static ScreenElement modeButtonIcon(LogicalTubeBehaviour.Mode mode) {
        ResourceLocation icon = MODE_BUTTON_ICON_PREFIX.withSuffix(mode.name().toLowerCase());
        return (gfx, x, y) -> gfx.blitSprite(icon, x, y, 16, 16);
    }

    /** The tube's live mode (fallback OR if it's gone) — committed immediately, so this reflects the server state. */
    private LogicalTubeBehaviour.Mode currentMode() {
        LogicalTubeBehaviour t = tube();
        return t != null ? t.getMode() : LogicalTubeBehaviour.Mode.OR;
    }

    /** Green-highlights the live mode's button (refreshed each frame, since the mode syncs back asynchronously). */
    private void refreshModeButtons() {
        LogicalTubeBehaviour.Mode current = currentMode();
        modeButtons.forEach((m, b) -> b.green = m == current);
    }

    private List<Component> modeButtonTooltip(LogicalTubeBehaviour.Mode m) {
        boolean shift = hasShiftDown();
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("createfactorycontroller.component.logical_tube.mode." + m.name().toLowerCase())
                .withStyle(ChatFormatting.WHITE));
        tip.add(TooltipHelper.holdShift(FontHelper.Palette.YELLOW, shift));
        if (shift) tip.addAll(TooltipHelper.cutTextComponent(
                Component.translatable("createfactorycontroller.component.logical_tube.mode." + m.name().toLowerCase() + ".desc"),
                FontHelper.Palette.ALL_GRAY));
        return tip;
    }

    // ── Layout ────────────────────────

    private int gridX() { return panelX - 4 + (PANEL_W - GRID_COLS * CELL) / 2; }
    private int gridY() { return panelY + 20; }
    private int cellScreenX(int col) { return gridX() + col * CELL; }
    private int cellScreenY(int row) { return gridY() + row * CELL; }

    /** Input slot {@code i} cell: fill right→left, top→bottom (closest column to the tube fills first). */
    private static int inputCol(int i) { return (COLS_PER_SIDE - 1) - (i % COLS_PER_SIDE); }
    /** Output slot {@code i} cell: fill left→right, top→bottom. */
    private static int outputCol(int i) { return (TUBE_COL + 1) + (i % COLS_PER_SIDE); }
    private static int rowOf(int i) { return i < COLS_PER_SIDE ? 0 : 2; }

    // ── Tube state ────────────────────────────────────────────

    private LogicalTubeBehaviour tube() {
        return menu.componentAt(tubePos) instanceof LogicalTubeBehaviour t ? t : null;
    }

    private List<Connection> inputs() {
        LogicalTubeBehaviour t = tube();
        if (t == null) return List.of();
        List<Connection> l = new ArrayList<>(t.incomingConnections());
        l.sort(Comparator.comparingInt((Connection c) -> c.from.x()).thenComparingInt(c -> c.from.y()));
        return l;
    }

    private List<Connection> outputs() {
        LogicalTubeBehaviour t = tube();
        if (t == null) return List.of();
        List<Connection> l = new ArrayList<>(t.outgoingConnections());
        l.sort(Comparator.comparingInt((Connection c) -> c.to.x()).thenComparingInt(c -> c.to.y()));
        return l;
    }

    // ── Render ────────────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        if (tube() == null) { Minecraft.getInstance().setScreen(controller); return; }   // tube removed externally
        controller.renderBoard(gfx, -1, -1, partialTick, true);

        RenderSystem.enableBlend();
        gfx.blit(PANEL_TEX, panelX, panelY, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);

        List<Connection> inputs = inputs(), outputs = outputs();
        ComponentRenderingHelper.RenderingParameters renderingParameters =
                componentRenderingHelper.params(gfx, new Vector2d(Double.NaN, Double.NaN), false);
        renderIconBacks(gfx, renderingParameters, inputs, outputs);      // backs first
        renderConnections(gfx, inputs, outputs);    // then wires (above backs, below fronts)
        renderIconFronts(gfx, renderingParameters, inputs, outputs,
                connectionSlotAt(mouseX, mouseY, inputs, outputs));   // fronts cover the arrow ends

        RenderSystem.enableBlend();
        gfx.blitSprite(
                LOGIC_GATE_ICON_PREFIX.withSuffix(currentMode().name().toLowerCase()),
                cellScreenX(TUBE_COL), cellScreenY(MID_ROW), 16 ,16);

        gfx.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);

        // Decorative electron tube item on the right (off-panel), like the link model.
        GuiGameElement.of(AllItems.ELECTRON_TUBE.asStack()).scale(2.0).at(0, 0, 100).render(gfx, panelX + 206, panelY + 67);

        relocateButton.render(gfx, mouseX, mouseY, partialTick);
        addConnectionButton.render(gfx, mouseX, mouseY, partialTick);
        refreshModeButtons();   // track the live mode (synced back asynchronously)
        modeButtons.values().forEach(b -> b.render(gfx, mouseX, mouseY, partialTick));
        confirmButton.render(gfx, mouseX, mouseY, partialTick);
        helpButton.render(gfx, mouseX, mouseY, partialTick);
    }

    /** Draws every wire in the middle band, ordered INACTIVE → UNPOWERED → POWERED so powered wires sit on top. */
    private void renderConnections(GuiGraphics gfx, List<Connection> inputs, List<Connection> outputs) {
        record Wire(List<Vector2i> path, int color, int prio) {}
        List<Wire> wires = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_PER_SIDE, inputs.size()); i++) {
            int c = inputCol(i), r = rowOf(i);
            wires.add(new Wire(List.of(new Vector2i(c, r), new Vector2i(c, MID_ROW), new Vector2i(TUBE_COL, MID_ROW)),
                    inputs.get(i).getConnectionColor(menu), compareConnection(inputs.get(i))));
        }
        for (int i = 0; i < Math.min(MAX_PER_SIDE, outputs.size()); i++) {
            int c = outputCol(i), r = rowOf(i);
            wires.add(new Wire(List.of(new Vector2i(TUBE_COL, MID_ROW), new Vector2i(c, MID_ROW), new Vector2i(c, r)),
                    outputs.get(i).getConnectionColor(menu), compareConnection(outputs.get(i))));
        }
        wires.sort(Comparator.comparingInt(Wire::prio));

        gfx.pose().pushPose();
        gfx.pose().translate(gridX(), gridY(), 0);
        for (Wire w : wires) VirtualConnectionRenderer.create(w.path(), w.color(), false).drawPath(gfx.bufferSource(), gfx.pose());
        gfx.pose().popPose();
        gfx.flush();
    }

    /** Render order */
    private static int compareConnection(Connection c) {
        if (c instanceof RedstoneConnection rc)
            return switch (rc.state()) { case INACTIVE -> 0; case UNPOWERED -> 1; case POWERED -> 2; };
        return 0;
    }

    private void renderIconBacks(GuiGraphics gfx, VirtualComponentWidget.RenderingParameters params,
                                 List<Connection> inputs, List<Connection> outputs) {
        for (int i = 0; i < Math.min(MAX_PER_SIDE, inputs.size()); i++)
            backAt(gfx, params, inputs.get(i).from, cellScreenX(inputCol(i)), cellScreenY(rowOf(i)));
        for (int i = 0; i < Math.min(MAX_PER_SIDE, outputs.size()); i++)
            backAt(gfx, params, outputs.get(i).to, cellScreenX(outputCol(i)), cellScreenY(rowOf(i)));
        componentRenderingHelper.flushBuffers(gfx);
    }

    private void renderIconFronts(GuiGraphics gfx, VirtualComponentWidget.RenderingParameters params,
                                  List<Connection> inputs, List<Connection> outputs,
                                  ConnectionSlot hovered) {
        for (int i = 0; i < Math.min(MAX_PER_SIDE, inputs.size()); i++)
            frontAt(gfx, params, inputs.get(i).from, cellScreenX(inputCol(i)), cellScreenY(rowOf(i)));
        for (int i = 0; i < Math.min(MAX_PER_SIDE, outputs.size()); i++)
            frontAt(gfx, params, outputs.get(i).to, cellScreenX(outputCol(i)), cellScreenY(rowOf(i)));
        if (hovered != null) highlight(gfx, hovered.x(), hovered.y());
        componentRenderingHelper.flushBuffers(gfx);
    }

    private void backAt(GuiGraphics gfx, VirtualComponentWidget.RenderingParameters params,
                        VirtualComponentPosition pos, int x, int y) {
        VirtualComponentWidget w = controller.componentWidgetAt(pos);
        if (w != null) atSlot(gfx, w, x, y, () -> w.renderBack(params));
    }

    private void frontAt(GuiGraphics gfx, VirtualComponentWidget.RenderingParameters params,
                         VirtualComponentPosition pos, int x, int y) {
        VirtualComponentWidget w = controller.componentWidgetAt(pos);
        if (w != null) atSlot(gfx, w, x, y, () -> w.renderFront(params));
    }

    /** Reuses a component's canvas render at slot {@code (x,y)}: translate the pose so its board cell lands on the slot,
     *  then run the canvas draw. The back/connection/front layering here matches the canvas exactly. */
    private void atSlot(GuiGraphics gfx, VirtualComponentWidget w, int x, int y, Runnable draw) {
        VirtualComponentPosition p = w.position();
        gfx.pose().pushPose();
        gfx.pose().translate(x - p.x() * CELL, y - p.y() * CELL, 0);
        draw.run();
        gfx.pose().popPose();
    }

    private static void highlight(GuiGraphics gfx, int x, int y) {
        // Keep this below the deferred gauge item/fluid icon (rendered at z=150/100 during the batch flush).
        gfx.fill(x, y, x + CELL, y + CELL, 0x80FFFFFF);
    }

    @Override
    protected void renderForeground(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        Component title = getTitle();
        gfx.drawString(font, title, panelX + PANEL_W / 2 - font.width(title) / 2, panelY + 4, 0x741A41, false);
        super.renderForeground(gfx, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics gfx, int mouseX, int mouseY) {}

    @Override
    public List<net.minecraft.client.renderer.Rect2i> getExtraAreas() {
        return List.of(new net.minecraft.client.renderer.Rect2i(panelX + 196, panelY + 60, 40, 40));   // the decorative electron tube on the right
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        helpButton.renderTooltip(gfx, font, mouseX, mouseY);
    }

    // ── Interaction ───────────────────────────────────────────────────────────────

    private record ConnectionSlot(Connection connection, boolean output, int x, int y) {}

    private ConnectionSlot connectionSlotAt(double mouseX, double mouseY) {
        return connectionSlotAt(mouseX, mouseY, inputs(), outputs());
    }

    private ConnectionSlot connectionSlotAt(double mouseX, double mouseY,
                                            List<Connection> inputs, List<Connection> outputs) {
        int col = Math.floorDiv((int) Math.floor(mouseX) - gridX(), CELL);
        int row = Math.floorDiv((int) Math.floor(mouseY) - gridY(), CELL);
        if (row != 0 && row != 2) return null;
        if (col >= 0 && col < COLS_PER_SIDE) {
            int index = (row == 0 ? 0 : COLS_PER_SIDE) + COLS_PER_SIDE - 1 - col;
            return index < inputs.size()
                    ? new ConnectionSlot(inputs.get(index), false, cellScreenX(col), cellScreenY(row)) : null;
        }
        if (col > TUBE_COL && col < GRID_COLS) {
            int index = (row == 0 ? 0 : COLS_PER_SIDE) + col - TUBE_COL - 1;
            return index < outputs.size()
                    ? new ConnectionSlot(outputs.get(index), true, cellScreenX(col), cellScreenY(row)) : null;
        }
        return null;
    }

    private boolean tubeCellHovered(double mouseX, double mouseY) {
        int x = (int) Math.floor(mouseX) - cellScreenX(TUBE_COL);
        int y = (int) Math.floor(mouseY) - cellScreenY(MID_ROW);
        return x >= 0 && x < CELL && y >= 0 && y < CELL;
    }

    /** Create's soft GUI button blip for slot clicks. */
    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f));
    }

    // ── Overlay plumbing ─────────────────────

    @Override public void onPanelSync() { controller.onPanelSync(); }

    @Override
    protected void containerTick() {
        super.containerTick();
        controller.tickComponentWidgets();
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        controller.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(controller);
    }

    @Override
    public void removed() {
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_CLOSE.get(), 1f));
        super.removed();
    }
}
