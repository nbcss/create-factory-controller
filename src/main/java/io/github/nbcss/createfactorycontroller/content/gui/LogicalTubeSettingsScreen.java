package io.github.nbcss.createfactorycontroller.content.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.LogicalTubeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionResolver;
import io.github.nbcss.createfactorycontroller.content.component.connection.RedstoneConnection;
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
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Full-config overlay for a Logical Tube. Two 5×3 grids — inputs (left) and outputs (right) — flank the central,
 * scrollable mode slot; the components sit in the top and bottom rows and the <b>middle row is a connection band</b>
 * that draws the wires (V→H, redstone-state coloured) between each component and the tube. Shares the controller
 * {@link FactoryControllerMenu} and draws the live board behind, like {@link ConfigureRedstoneLinkScreen}. No inventory
 * is shown; all edits are immediate packets (click disconnects, shift-click reverses, scroll/click-NONE sets the mode).
 *
 * <p>Background texture is not ready yet — reuses the Redstone Link panel.</p>
 */
@OnlyIn(Dist.CLIENT)
public class LogicalTubeSettingsScreen extends AbstractSimiContainerScreen<FactoryControllerMenu>
        implements PanelSyncListener {

    private static final ResourceLocation PANEL_TEX =   // TEMP: reuse the redstone-link panel texture
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "textures/gui/logical_tube.png");
    private static final ResourceLocation LOGIC_GATE_ICONS =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "icons/logic_gates");
    private static final int PANEL_W = 200, PANEL_H = 103;

    private static final int CELL = 16;
    private static final int COLS_PER_SIDE = 5;
    private static final int GRID_COLS = COLS_PER_SIDE * 2 + 1;   // [5 inputs][tube][5 outputs]
    private static final int TUBE_COL = COLS_PER_SIDE, MID_ROW = 1;
    private static final int MAX_PER_SIDE = COLS_PER_SIDE * 2;    // 5 cols × 2 rows

    private final FactoryControllerScreen controller;
    private final VirtualComponentPosition tubePos;

    private int panelX, panelY;
    private IconButton relocateButton, addConnectionButton, confirmButton;
    /** One toggle button per {@link LogicalTubeBehaviour.Mode}, ordered as the enum; the live mode glows green. */
    private final java.util.EnumMap<LogicalTubeBehaviour.Mode, IconButton> modeButtons =
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

        menu.repositionSlots(-2000, -2000, false);   // no inventory on this screen — keep the shared slots off-screen

        relocateButton = new IconButton(panelX + 8, panelY + 79, AllIcons.I_MOVE_GAUGE);
        relocateButton.withCallback(() -> { controller.beginRelocateMode(tubePos); Minecraft.getInstance().setScreen(controller); });
        addWidget(relocateButton);

        addConnectionButton = new IconButton(panelX + 30, panelY + 79, AllIcons.I_ADD);
        addConnectionButton.withCallback(() -> { controller.beginConnectionMode(tubePos); Minecraft.getInstance().setScreen(controller); });
        addWidget(addConnectionButton);

        // Mode button group (AND/OR/NOR/NAND), to the LEFT of the confirm button. Each blits its mode sprite; the
        // live mode glows green. Selecting commits immediately (the output then follows one tick later via preTick).
        LogicalTubeBehaviour.Mode[] modes = LogicalTubeBehaviour.Mode.values();
        int groupX = panelX + 161 - modes.length * 18 - 4;
        for (int i = 0; i < modes.length; i++) {
            LogicalTubeBehaviour.Mode m = modes[i];
            ScreenElement element = modeButtonIcon(m);
            IconButton button = new IconButton(groupX + i * 18, panelY + 79, element);
            button.withCallback(() -> {
                PacketDistributor.sendToServer(new ConfigureLogicalTubePacket(menu.controllerPos, tubePos, m.name()));
                playClickSound();
            });
            modeButtons.put(m, button);
            addWidget(button);
        }

        confirmButton = new IconButton(panelX + 167, panelY + 79, AllIcons.I_CONFIRM);
        confirmButton.withCallback(() -> Minecraft.getInstance().setScreen(controller));
        addWidget(confirmButton);
    }

    private static ScreenElement modeButtonIcon(LogicalTubeBehaviour.Mode mode) {
        ResourceLocation icon = LOGIC_GATE_ICONS.withSuffix("/" + mode.name().toLowerCase());
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

    /** Mode-button tooltip: name + hold-shift hint, with the description appended while Shift is held (matches
     *  SetItemScreen's data-toggle tooltips). */
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

    // ── Layout (local 11×3 cell grid centred in the panel) ────────────────────────

    private int gridX() { return panelX - 4 + (PANEL_W - GRID_COLS * CELL) / 2; }
    private int gridY() { return panelY + 20; }
    private int cellScreenX(int col) { return gridX() + col * CELL; }
    private int cellScreenY(int row) { return gridY() + row * CELL; }

    /** Input slot {@code i} cell: fill right→left, top→bottom (closest column to the tube fills first). */
    private static int inputCol(int i) { return (COLS_PER_SIDE - 1) - (i % COLS_PER_SIDE); }
    /** Output slot {@code i} cell: fill left→right, top→bottom. */
    private static int outputCol(int i) { return (TUBE_COL + 1) + (i % COLS_PER_SIDE); }
    private static int rowOf(int i) { return i < COLS_PER_SIDE ? 0 : 2; }

    // ── Tube state (live off the menu) ────────────────────────────────────────────

    private LogicalTubeBehaviour tube() {
        return menu.componentAt(tubePos) instanceof LogicalTubeBehaviour t ? t : null;
    }

    private List<Connection> inputs() {
        LogicalTubeBehaviour t = tube();
        if (t == null) return List.of();
        List<Connection> l = new ArrayList<>(t.targetedBy().values());
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
        renderIconBacks(gfx, inputs, outputs);      // backs first
        renderConnections(gfx, inputs, outputs);    // then wires (above backs, below fronts)
        renderIconFronts(gfx, inputs, outputs, mouseX, mouseY);   // fronts cover the arrow ends

        gfx.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);

        // Decorative electron tube item on the right (off-panel), like the link model.
        GuiGameElement.of(AllItems.ELECTRON_TUBE.asStack()).scale(2.5).at(0, 0, 100).render(gfx, panelX + 206, panelY + 50);

        relocateButton.render(gfx, mouseX, mouseY, partialTick);
        addConnectionButton.render(gfx, mouseX, mouseY, partialTick);
        refreshModeButtons();   // track the live mode (synced back asynchronously)
        modeButtons.values().forEach(b -> b.render(gfx, mouseX, mouseY, partialTick));
        confirmButton.render(gfx, mouseX, mouseY, partialTick);
    }

    /** Draws every wire in the middle band, ordered INACTIVE → UNPOWERED → POWERED so powered wires sit on top. */
    private void renderConnections(GuiGraphics gfx, List<Connection> inputs, List<Connection> outputs) {
        record Wire(List<Vector2i> path, int color, int prio) {}
        List<Wire> wires = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_PER_SIDE, inputs.size()); i++) {
            int c = inputCol(i), r = rowOf(i);
            // input flows component → tube: V (component col into the band) then H (band to tube); arrow at the tube.
            wires.add(new Wire(List.of(new Vector2i(c, r), new Vector2i(c, MID_ROW), new Vector2i(TUBE_COL, MID_ROW)),
                    inputs.get(i).getConnectionColor(menu), compareConnection(inputs.get(i))));
        }
        for (int i = 0; i < Math.min(MAX_PER_SIDE, outputs.size()); i++) {
            int c = outputCol(i), r = rowOf(i);
            // output flows tube → component: H (tube along the band) then V (up/down to the component); arrow at it.
            wires.add(new Wire(List.of(new Vector2i(TUBE_COL, MID_ROW), new Vector2i(c, MID_ROW), new Vector2i(c, r)),
                    outputs.get(i).getConnectionColor(menu), compareConnection(outputs.get(i))));
        }
        wires.sort(Comparator.comparingInt(Wire::prio));

        gfx.pose().pushPose();
        gfx.pose().translate(gridX(), gridY(), 0);
        for (Wire w : wires) VirtualConnectionRenderer.drawGuiPath(gfx, w.path(), w.color());
        gfx.pose().popPose();
    }

    /** Render order INACTIVE(0) → UNPOWERED(1) → POWERED(2). */
    private static int compareConnection(Connection c) {
        if (c instanceof RedstoneConnection rc)
            return switch (rc.state()) { case INACTIVE -> 0; case UNPOWERED -> 1; case POWERED -> 2; };
        return 0;
    }

    private void renderIconBacks(GuiGraphics gfx, List<Connection> inputs, List<Connection> outputs) {
        for (int i = 0; i < Math.min(MAX_PER_SIDE, inputs.size()); i++)
            backAt(gfx, inputs.get(i).from, cellScreenX(inputCol(i)), cellScreenY(rowOf(i)));
        for (int i = 0; i < Math.min(MAX_PER_SIDE, outputs.size()); i++)
            backAt(gfx, outputs.get(i).to, cellScreenX(outputCol(i)), cellScreenY(rowOf(i)));
        backAt(gfx, tubePos, cellScreenX(TUBE_COL), cellScreenY(MID_ROW));
    }

    private void renderIconFronts(GuiGraphics gfx, List<Connection> inputs, List<Connection> outputs, int mouseX, int mouseY) {
        for (int i = 0; i < Math.min(MAX_PER_SIDE, inputs.size()); i++)
            frontAt(gfx, inputs.get(i).from, cellScreenX(inputCol(i)), cellScreenY(rowOf(i)), mouseX, mouseY);
        for (int i = 0; i < Math.min(MAX_PER_SIDE, outputs.size()); i++)
            frontAt(gfx, outputs.get(i).to, cellScreenX(outputCol(i)), cellScreenY(rowOf(i)), mouseX, mouseY);
        // Centre slot: the tube's own canvas front (live mode + powered visual), reused like every other slot.
        VirtualComponentWidget tubeWidget = controller.componentWidgetAt(tubePos);
        if (tubeWidget != null) atSlot(gfx, tubeWidget, cellScreenX(TUBE_COL), cellScreenY(MID_ROW),
                () -> tubeWidget.renderFront(gfx, -10000, -10000, 1f));
    }

    private void backAt(GuiGraphics gfx, VirtualComponentPosition pos, int x, int y) {
        VirtualComponentWidget w = controller.componentWidgetAt(pos);
        if (w != null) atSlot(gfx, w, x, y, () -> w.renderBack(gfx));
    }

    private void frontAt(GuiGraphics gfx, VirtualComponentPosition pos, int x, int y, int mouseX, int mouseY) {
        VirtualComponentWidget w = controller.componentWidgetAt(pos);
        if (w != null) atSlot(gfx, w, x, y, () -> w.renderFront(gfx, -10000, -10000, 1f));   // off-screen mouse, static glow
        if (in(mouseX, mouseY, x, y)) highlight(gfx, x, y);
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
        gfx.fill(x, y, x + CELL, y + CELL, 0x80FFFFFF);
    }

    @Override
    protected void renderForeground(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        Component title = getTitle();
        gfx.drawString(font, title, panelX + PANEL_W / 2 - font.width(title) / 2, panelY + 4, 0x3D3C48, false);
        super.renderForeground(gfx, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics gfx, int mouseX, int mouseY) {}

    @Override
    public List<Rect2i> getExtraAreas() {
        return List.of(new Rect2i(panelX + 196, panelY + 38, 48, 48));   // the decorative electron tube on the right
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        Connection hovered = slotConnectionAt(mouseX, mouseY);
        if (hovered != null) {
            VirtualComponentBehaviour partner = menu.componentAt(isOutputSlot(mouseX, mouseY) ? hovered.to : hovered.from);
            List<Component> tip = new ArrayList<>();
            if (partner != null)
                tip.add(partner.getName().copy().withColor(partner.getColor()));
            if (canReverse(hovered))   // click = reverse (only when legal — a link wire can't)
                tip.add(Component.translatable("createfactorycontroller.gui.logical_tube.reverse").withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("createfactorycontroller.gui.logical_tube.disconnect")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            gfx.renderComponentTooltip(font, tip, mouseX, mouseY);
            return;
        }
        for (var e : modeButtons.entrySet())
            if (e.getValue().isMouseOver(mouseX, mouseY)) {
                gfx.renderComponentTooltip(font, modeButtonTooltip(e.getKey()), mouseX, mouseY);
                return;
            }
        if (relocateButton.isMouseOver(mouseX, mouseY))
            gfx.renderTooltip(font, CreateLang.translate("gui.factory_panel.relocate").component(), mouseX, mouseY);
        else if (addConnectionButton.isMouseOver(mouseX, mouseY))
            gfx.renderTooltip(font, CreateLang.translate("gui.factory_panel.connect_input").component(), mouseX, mouseY);
        else if (confirmButton.isMouseOver(mouseX, mouseY))
            gfx.renderTooltip(font, CreateLang.translate("gui.factory_panel.save_and_close").component(), mouseX, mouseY);
    }

    // ── Interaction ───────────────────────────────────────────────────────────────

    private static boolean in(double mx, double my, int x, int y) {
        return mx >= x && mx < x + CELL && my >= y && my < y + CELL;
    }

    /** The connection under the cursor (input or output slot), or null. */
    private Connection slotConnectionAt(double mx, double my) {
        List<Connection> inputs = inputs();
        for (int i = 0; i < Math.min(MAX_PER_SIDE, inputs.size()); i++)
            if (in(mx, my, cellScreenX(inputCol(i)), cellScreenY(rowOf(i)))) return inputs.get(i);
        List<Connection> outputs = outputs();
        for (int i = 0; i < Math.min(MAX_PER_SIDE, outputs.size()); i++)
            if (in(mx, my, cellScreenX(outputCol(i)), cellScreenY(rowOf(i)))) return outputs.get(i);
        return null;
    }

    private boolean isOutputSlot(double mx, double my) {
        List<Connection> outputs = outputs();
        for (int i = 0; i < Math.min(MAX_PER_SIDE, outputs.size()); i++)
            if (in(mx, my, cellScreenX(outputCol(i)), cellScreenY(rowOf(i)))) return true;
        return false;
    }

    /** Whether the reversed orientation of {@code c} would be legal (mirrors the server check) — false for a link wire.
     *  The no-collision rule is enforced authoritatively server-side; this only gates the tooltip hint. */
    private boolean canReverse(Connection c) {
        VirtualComponentBehaviour newSource = menu.componentAt(c.to), newSink = menu.componentAt(c.from);
        return newSource != null && newSink != null
                && ConnectionResolver.validate(c.type, newSource, newSink).isSuccess();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Connection c = slotConnectionAt(mouseX, mouseY);
        if (c != null) {
            if (hasShiftDown())
                PacketDistributor.sendToServer(new RemoveConnectionPacket(menu.controllerPos, c.from, c.to));   // shift = disconnect
            else
                PacketDistributor.sendToServer(new ReverseConnectionPacket(menu.controllerPos, c.from, c.to));  // click = reverse (server validates)
            playClickSound();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Create's soft GUI button blip for slot clicks. */
    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f));
    }

    // ── Overlay plumbing (mirror ConfigureRedstoneLinkScreen) ─────────────────────

    @Override public void onPanelSync() { controller.onPanelSync(); }

    @Override
    protected void containerTick() {
        super.containerTick();
        controller.tickBulbs();
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
