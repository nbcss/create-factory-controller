package io.github.nbcss.createfactorycontroller.content.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.arithmetic.ArithmeticTubeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.arithmetic.ArithmeticOperator;
import io.github.nbcss.createfactorycontroller.content.component.arithmetic.BuiltinOperator;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.gui.widget.InteractiveAreaWidget;
import io.github.nbcss.createfactorycontroller.content.gui.widget.TooltipIconButton;
import io.github.nbcss.createfactorycontroller.content.helper.NumberFormatter;
import io.github.nbcss.createfactorycontroller.content.helper.Rect2i;
import io.github.nbcss.createfactorycontroller.content.packet.ConfigureArithmeticInputPacket;
import io.github.nbcss.createfactorycontroller.content.packet.ConfigureArithmeticTubePacket;
import io.github.nbcss.createfactorycontroller.content.render.BatchedBlitter;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.world.item.ItemStack;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.anti_ad.mc.ipn.api.IPNIgnore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Configuration overlay for an Arithmetic Tube.
 */
@OnlyIn(Dist.CLIENT)
@IPNIgnore
public class ArithmeticTubeSettingsScreen extends AbstractSimiContainerScreen<FactoryControllerMenu>
        implements PanelSyncListener {

    private interface SpriteLocations {
        ResourceLocation FRAME = resource("arithmetic_tube/frame");
        ResourceLocation BOTTOM_BAR = resource("common/bottom_bar");
        ResourceLocation BOTTOM_BAR_VDIV = resource("common/bottom_bar_vdiv");
        ResourceLocation BOTTOM_BAR_POINTER_RIGHT = resource("common/bottom_bar_pointer_right");
        ResourceLocation OP_BUTTON = resource("arithmetic_tube/operator_button");
        ResourceLocation OP_BUTTON_HOVER = resource("arithmetic_tube/operator_button_hovered");
        ResourceLocation OP_BUTTON_PRESSED = resource("arithmetic_tube/operator_button_pressed");
        ResourceLocation DROPDOWN_BG = resource("arithmetic_tube/operator_menu_background");
        ResourceLocation RESULT_ICON = resource("arithmetic_tube/result_icon");
        ResourceLocation RESULT_BG = resource("arithmetic_tube/result_entry_background");
        ResourceLocation RESULT_VALUE_BOX = resource("arithmetic_tube/result_value_box");
        ResourceLocation BTN_NORMAL = resource("common/button/normal");
        ResourceLocation BTN_HOVER = resource("common/button/hovered");
        ResourceLocation BTN_TOGGLED = resource("common/button/toggled");
        ResourceLocation BTN_DISABLED = resource("common/button/disabled");
        ResourceLocation OPERAND_BLUE_SLOT = resource("arithmetic_tube/operand_blue_icon_slot");
        ResourceLocation OPERAND_RED_SLOT = resource("arithmetic_tube/operand_red_icon_slot");
        ResourceLocation ENTRY_BG = resource("arithmetic_tube/entry_background");
        ResourceLocation CONN_VALUE_BOX = resource("arithmetic_tube/connection_value_box");
        ResourceLocation CONSTANT_INPUT_FIELD = resource("arithmetic_tube/constant_input_field");
        ResourceLocation OPERATOR_DROPDOWN_ICON = resource("arithmetic_tube/operator_dropdown_icon");
        ResourceLocation CONSTANT_ICON = resource("icons/constant");
        ResourceLocation ELLIPSIS_ICON = resource("icons/ellipsis");
        ResourceLocation ADD_CONSTANT_ICON = resource("icons/add_constant");

        private static ResourceLocation resource(String path) {
            return ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, path);
        }
    }

    private static final int PANEL_W = 184;
    private static final int HEADER_H = 16;
    private static final int BOTTOM_H = 30;
    private static final int BOTTOM_CLOSE_GROUP_W = 32;
    private static final int SCROLLBAR_X = PANEL_W - 6;
    private static final int POINTER_W = 11, POINTER_H = 18;

    private static final int SIDE_PAD = 10;
    private static final int TOP_PAD = 5, ENTRY_GAP = 4, BOTTOM_PAD = 5;
    private static final int OP_H = 21;
    private static final int OP_SLOT_W = 19;
    private static final int OP_ICON = 15;
    private static final int RESULT_H = 20, RESULT_ICON_SIZE = 20, RESULT_GAP = 2;
    private static final int INPUT_H = 20, SLOT = 20, SLOT_GAP = 2, INPUT_ROW_GAP = 4, ROW_BTN = 18;
    private static final int ADD_PAD_L = 4;
    private static final int ICON16 = 16;

    private static final int DD_INSET = 1;
    private static final int DD_BTN = 17;
    private static final int DD_COLS = 8, DD_ROWS = 2;      // 16 operators atm
    private static final int DD_TOP_PAD = 3, DD_BOTTOM_PAD = 5;

    private static final int OP_BORDER_COLOR = 0xFF1A1A1A;
    private static final int OP_ICON_COLOR = 0xFFEBEBEB;
    private static final int INPUT_VALUE_COLOR = 0xFFE2E2E2;
    private static final int CONSTANT_VALUE_COLOR = 0xFFF2F2F2;
    private static final int NAME_COLOR = 0xF7DFF5;
    private static final int TITLE_COLOR = 0xF9DFFA;

    private static final BuiltinOperator[] OPERATORS = BuiltinOperator.values();
    private static final String OPERATOR_PATH = "arithmetic_tube/operators/";

    private final FactoryControllerScreen controller;
    private final VirtualComponentPosition tubePos;
    private final LerpedFloat scroll = LerpedFloat.linear().startWithValue(0);

    private int panelX, panelY, panelH;
    private int viewportH;
    private float renderedScroll;
    private boolean operatorHeld;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private TooltipIconButton closeButton, relocateButton, swapButton;
    private ArithmeticViewportWidget viewportWidget;
    private OperatorDropdownWidget operatorDropdown;
    private ConstantDropdownWidget constantDropdown;

    /** The input rows (primary inputs, primary-add, secondary input/add) */
    private List<Row> rows = List.of();
    private final ConstantEditor constantEditor = new ConstantEditor();

    private sealed interface Row permits Row.Add, Row.Input {
        boolean primary();

        record Add(boolean primary) implements Row {}
        record Input(boolean primary, int index, ArithmeticTubeBehaviour.NumberInput input) implements Row {}
    }

    private enum TargetKind {
        OPERATOR, SLOT, REMOVE, CONSTANT_FIELD, CONSTANT_MENU, ADD_CONNECTION, ADD_CONSTANT,
        RESULT, SCROLLBAR, ROW
    }

    private record ContentTarget(TargetKind kind, @Nullable Row row) {}

    public ArithmeticTubeSettingsScreen(FactoryControllerScreen controller, VirtualComponentPosition tubePos) {
        super(controller.getMenu(), Minecraft.getInstance().player.getInventory(),
                Component.translatable("createfactorycontroller.gui.arithmetic_tube_settings"));
        this.controller = controller;
        this.tubePos = tubePos;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_OPEN.get(), 1f));
    }

    @Override
    protected void init() {
        setWindowSize(controller.guiWidth(), controller.guiHeight());
        setWindowOffset(0, 0);
        super.init();
        menu.repositionSlots(-2000, -2000, false);

        closeButton = new TooltipIconButton(0, 0, AllIcons.I_CONFIRM);
        closeButton.withCallback(() -> Minecraft.getInstance().setScreen(controller));   // save & close
        closeButton.setToolTip(CreateLang.translate("gui.factory_panel.save_and_close").component());

        relocateButton = new TooltipIconButton(0, 0, AllIcons.I_MOVE_GAUGE);
        relocateButton.withCallback(() -> { controller.beginRelocateMode(tubePos); Minecraft.getInstance().setScreen(controller); });
        relocateButton.setToolTip(Component.translatable("createfactorycontroller.gui.action_relocate"));

        swapButton = new TooltipIconButton(0, 0, AllIcons.I_FLIP);
        swapButton.withCallback(() -> { sendInput(ConfigureArithmeticInputPacket.SWAP, true, -1, 0); playClickSound(); });
        swapButton.setToolTip(Component.translatable("createfactorycontroller.gui.arithmetic_tube.swap_inputs"));

        operatorDropdown = new OperatorDropdownWidget();
        addWidget(operatorDropdown);
        constantDropdown = new ConstantDropdownWidget();
        addWidget(constantDropdown);
        viewportWidget = new ArithmeticViewportWidget();
        addWidget(viewportWidget);
        addWidget(closeButton);
        addWidget(relocateButton);
        addWidget(swapButton);

        recomputeLayout();
        scroll.setValue(0);
        scroll.chase(0, 0.5, Chaser.EXP);
        renderedScroll = 0;
    }

    private int rowsHeight(int n) { return n <= 0 ? 0 : n * INPUT_H + (n - 1) * INPUT_ROW_GAP; }

    /** Content height of the stacked body entries (operator, the input rows, result); the dropdown is an overlay. */
    private int contentHeight() {
        return TOP_PAD + OP_H + ENTRY_GAP + rowsHeight(rows.size()) + ENTRY_GAP + RESULT_H + BOTTOM_PAD;
    }

    /** Recompute panel geometry from the current input rows (the body grows with the number of inputs). */
    private void recomputeLayout() {
        ArithmeticTubeBehaviour t = tube();
        rows = t == null ? List.of() : buildRows(t);
        int wanted = HEADER_H + contentHeight() + BOTTOM_H + 1;
        panelH = Math.min(height - 48, wanted);
        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;
        viewportH = panelH - HEADER_H - BOTTOM_H - 1;
        closeButton.setX(panelX + PANEL_W - 25);
        closeButton.setY(panelY + panelH - 24);
        relocateButton.setX(panelX + 7);
        relocateButton.setY(panelY + panelH - 24);
        boolean binary = t != null && t.getOperator().arity() == ArithmeticOperator.Arity.BINARY;
        swapButton.setX(binary ? panelX + PANEL_W - BOTTOM_CLOSE_GROUP_W + 1 - 5 - ROW_BTN : -1000);
        swapButton.setY(panelY + panelH - 24);
        if (viewportWidget != null)
            viewportWidget.setRectangle(PANEL_W, viewportH, panelX, viewportY());
        if (operatorDropdown != null)
            operatorDropdown.updateBounds();
        if (constantDropdown != null)
            constantDropdown.updateBounds();
    }

    private List<Row> buildRows(ArithmeticTubeBehaviour tube) {
        List<Row> list = new ArrayList<>();
        List<ArithmeticTubeBehaviour.NumberInput> prim = tube.getPrimaryInputs();
        for (int i = 0; i < prim.size(); i++)
            list.add(new Row.Input(true, i, prim.get(i)));
        if (prim.size() < tube.getOperator().arity().maxPrimary) list.add(new Row.Add(true));
        if (tube.getOperator().arity().allowsSecondary) {
            ArithmeticTubeBehaviour.NumberInput sec = tube.getSecondaryInput();
            list.add(sec != null ? new Row.Input(false, -1, sec) : new Row.Add(false));
        }
        return list;
    }

    // ── Layout geometry ───────────────────────────────────────────────────────────

    private int entryX() { return panelX + SIDE_PAD; }
    private int entryW() { return PANEL_W - 2 * SIDE_PAD; }
    private int viewportY() { return panelY + HEADER_H; }
    private int opEntryY() { return viewportY() + TOP_PAD - (int) renderedScroll; }   // scrolls with the content
    private int inputStartY() { return opEntryY() + OP_H + ENTRY_GAP; }
    private int rowY(int k) { return inputStartY() + k * (INPUT_H + INPUT_ROW_GAP); }
    private int resultEntryY() { return inputStartY() + rowsHeight(rows.size()) + ENTRY_GAP; }

    private int ddX() { return entryX() + DD_INSET; }
    private int ddW() { return entryW() - 2 * DD_INSET; }
    private int ddY() { return opEntryY() + OP_H; }
    private int ddPadX() { return (ddW() - DD_COLS * DD_BTN) / 2; }
    private int ddH() { return DD_TOP_PAD + DD_ROWS * DD_BTN + DD_BOTTOM_PAD; }
    private int ddBtnX(int col) { return ddX() + ddPadX() + col * DD_BTN; }
    private int ddBtnY(int row) { return ddY() + DD_TOP_PAD + row * DD_BTN; }

    private double maxScroll() { return Math.max(0, contentHeight() - viewportH); }

    // ── Scrollbar ────────────────────

    private void renderScrollbar(GuiGraphics gfx, boolean hovered) {
        if (maxScroll() <= 0) return;
        int thumbY = scrollbarThumbY(), thumbH = scrollbarThumbHeight();
        gfx.fill(panelX + SCROLLBAR_X, viewportY(), panelX + SCROLLBAR_X + 3, viewportY() + viewportH, 0x503D3C48);
        int thumbColor = hovered ? 0xFFE2E2E2 : 0xFFC6C6C6;
        gfx.fill(panelX + SCROLLBAR_X, thumbY, panelX + SCROLLBAR_X + 3, thumbY + thumbH, thumbColor);
    }

    private boolean overScrollbar(double mx, double my) {
        return maxScroll() > 0 && mx >= panelX + SCROLLBAR_X && mx < panelX + SCROLLBAR_X + 3
                && my >= viewportY() && my < viewportY() + viewportH;
    }

    private int scrollbarThumbHeight() { return Math.max(12, (int) (viewportH * (viewportH / (double) contentHeight()))); }
    private int scrollbarTravel() { return Math.max(0, viewportH - scrollbarThumbHeight()); }

    private int scrollbarThumbY() {
        double max = maxScroll();
        return max <= 0 ? viewportY() : viewportY() + (int) Math.round(scrollbarTravel() * (renderedScroll / max));
    }

    private void dragScrollbarTo(double mouseY) {
        double max = maxScroll();
        int travel = scrollbarTravel();
        if (max <= 0 || travel <= 0) return;
        double thumbTop = Mth.clamp(mouseY - scrollbarGrabOffset, viewportY(), viewportY() + travel);
        float value = (float) ((thumbTop - viewportY()) / travel * max);
        scroll.setValue(value);
        scroll.chase(value, 0.5, Chaser.EXP);
        renderedScroll = value;
    }

    private ArithmeticTubeBehaviour tube() {
        return menu.componentAt(tubePos) instanceof ArithmeticTubeBehaviour t ? t : null;
    }

    // ── Render ──────────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        ArithmeticTubeBehaviour tube = tube();
        if (tube == null) { Minecraft.getInstance().setScreen(controller); return; }
        recomputeLayout();
        constantEditor.discardIfRowGone(rows);
        controller.renderBoard(gfx, -1, -1, partialTick, true);

        TiledSpriteRenderer.create(SpriteLocations.FRAME).render(gfx, panelX, panelY, PANEL_W, panelH - BOTTOM_H + 1);
        TiledSpriteRenderer.create(SpriteLocations.BOTTOM_BAR).render(gfx, panelX, panelY + panelH - BOTTOM_H, PANEL_W, BOTTOM_H);
        TiledSpriteRenderer.create(SpriteLocations.BOTTOM_BAR_VDIV)
                .render(gfx, panelX + PANEL_W - BOTTOM_CLOSE_GROUP_W + 1, panelY + panelH - BOTTOM_H, 2, BOTTOM_H);
        gfx.blitSprite(SpriteLocations.BOTTOM_BAR_POINTER_RIGHT,
                panelX + PANEL_W - 3, panelY + panelH - BOTTOM_H + (BOTTOM_H - POINTER_H) / 2, POINTER_W, POINTER_H);
        renderedScroll = Mth.clamp(scroll.getValue(partialTick), 0, (float) maxScroll());

        RenderSystem.enableBlend();

        viewportWidget.render(gfx, mouseX, mouseY, partialTick);

        relocateButton.render(gfx, mouseX, mouseY, partialTick);
        if (tube.getOperator().arity() == ArithmeticOperator.Arity.BINARY) swapButton.render(gfx, mouseX, mouseY, partialTick);
        closeButton.render(gfx, mouseX, mouseY, partialTick);
        GuiGameElement.of(tube.getItem()).scale(2).at(0, 0, 100)
                .render(gfx, panelX + PANEL_W + 13, panelY + panelH - 30);
        if (operatorDropdown.isOpen()) {
            gfx.flush();
            RenderSystem.clear(256, Minecraft.ON_OSX);
        }
        operatorDropdown.updateBounds();
        operatorDropdown.render(gfx, mouseX, mouseY, partialTick);
        constantDropdown.updateBounds();
        constantDropdown.render(gfx, mouseX, mouseY, partialTick);
    }

    private void renderOperatorEntry(GuiGraphics gfx, ArithmeticTubeBehaviour tube, boolean hovered) {
        int x = entryX(), y = opEntryY(), w = entryW();
        ResourceLocation sprite = (operatorHeld && hovered) ? SpriteLocations.OP_BUTTON_PRESSED
                : (hovered ? SpriteLocations.OP_BUTTON_HOVER : SpriteLocations.OP_BUTTON);
        TiledSpriteRenderer.create(sprite).render(gfx, x, y, w, OP_H);

        ArithmeticOperator op = tube.getOperator();
        int iconX = x + (OP_SLOT_W - OP_ICON) / 2 + 1;
        int iconY = y + (OP_H - OP_ICON) / 2;
        drawOperatorIcon(gfx, op, iconX, iconY, OP_ICON_COLOR, true,
                op.arity() == ArithmeticOperator.Arity.BINARY);

        Component name = op.displayName();
        int nameX = x + (w - font.width(name)) / 2;
        int nameY = y + Math.ceilDiv(OP_H - font.lineHeight, 2);
        gfx.drawString(font, name, nameX, nameY, NAME_COLOR, false);
        gfx.blitSprite(SpriteLocations.OPERATOR_DROPDOWN_ICON, x + w - 6 - 7, y + Math.ceilDiv(OP_H - 4, 2), 7, 4);
    }

    private void renderResultEntry(GuiGraphics gfx, ArithmeticTubeBehaviour tube) {
        int x = entryX(), y = resultEntryY(), w = entryW();

        BatchedBlitter.forSprite(SpriteLocations.RESULT_ICON).blit(gfx.bufferSource(), gfx.pose(), x, y, RESULT_ICON_SIZE, RESULT_ICON_SIZE);

        int bgX = x + RESULT_ICON_SIZE + RESULT_GAP, bgW = w - RESULT_ICON_SIZE - RESULT_GAP;
        TiledSpriteRenderer.create(SpriteLocations.RESULT_BG).render(gfx, bgX, y, bgW, RESULT_H);

        int vbX = bgX + 1, vbW = bgW - 2, vbY = y + 1, vbH = 18;
        TiledSpriteRenderer.create(SpriteLocations.RESULT_VALUE_BOX).render(gfx, vbX, vbY, vbW, vbH);

        int tx = vbX + 6, ty = vbY + (vbH - font.lineHeight) / 2 + 2;
        gfx.drawString(font, NumberFormatter.format(tube.getOutput()), tx, ty, INPUT_VALUE_COLOR, false);
    }

    // ── Input rows ──────────────────────

    private void renderInputEntries(GuiGraphics gfx, ArithmeticTubeBehaviour tube, @Nullable ContentTarget hovered) {
        for (int k = 0; k < rows.size(); k++) {
            Row row = rows.get(k);
            if (row instanceof Row.Input input)
                renderInputRow(gfx, tube, input, rowY(k), hovered);
            else if (row instanceof Row.Add add)
                renderAddRow(gfx, tube, add, rowY(k), hovered);
        }
    }

    private void renderInputRow(GuiGraphics gfx, ArithmeticTubeBehaviour tube, Row.Input row, int y,
                                @Nullable ContentTarget hovered) {
        int x = entryX();
        renderSlot(gfx, row.primary(), x, y);
        // slot content: a constant icon, or the connected component's item
        if (row.input() instanceof ArithmeticTubeBehaviour.ConstantInput)
            BatchedBlitter.forSprite(SpriteLocations.CONSTANT_ICON).blit(gfx.bufferSource(), gfx.pose(), x + 2, y + 2, ICON16, ICON16);
        else if (row.input() instanceof ArithmeticTubeBehaviour.ConnectionInput w) {
            var comp = menu.componentAt(w.source());
            if (comp != null) gfx.renderItem(new ItemStack(comp.getItem()), x + 2, y + 2);
        }

        int bgX = x + SLOT + SLOT_GAP, bgW = entryW() - SLOT - SLOT_GAP;
        TiledSpriteRenderer.create(SpriteLocations.ENTRY_BG).render(gfx, bgX, y, bgW, INPUT_H);

        int delX = bgX + bgW - 1 - ROW_BTN, delY = y + 1;   // delete button: right, 1px margin
        boolean removeHovered = hovered != null && hovered.kind() == TargetKind.REMOVE && hovered.row() == row;
        ResourceLocation sprite1 = removeHovered ? SpriteLocations.BTN_HOVER : SpriteLocations.BTN_NORMAL;
        TiledSpriteRenderer.create(sprite1).render(gfx, delX, delY, ROW_BTN, ROW_BTN);
        AllIcons.I_TRASH.render(gfx, delX + (ROW_BTN - ICON16) / 2, delY + (ROW_BTN - ICON16) / 2);

        int boxX = bgX + 1, boxW = delX - 2 - boxX;
        boolean constant = row.input() instanceof ArithmeticTubeBehaviour.ConstantInput;
        ResourceLocation sprite = constant ? SpriteLocations.CONSTANT_INPUT_FIELD : SpriteLocations.CONN_VALUE_BOX;
        TiledSpriteRenderer.create(sprite).render(gfx, boxX, y + 1, boxW, 18);
        int textX = boxX + 6, textY = y + 1 + (18 - font.lineHeight) / 2 + 2;
        if (constantEditor.isEditing(row)) {
            constantEditor.position(textX, textY, boxW - 9);
        } else if (row.input() instanceof ArithmeticTubeBehaviour.ConstantInput) {
            boolean fieldHovered = hovered != null && hovered.kind() == TargetKind.CONSTANT_FIELD && hovered.row() == row;
            double value = row.input().getValue(tube);
            value = constantEditor.optimisticValue(row, value).orElse(value);
            gfx.drawString(font, SpecialConstant.displayValue(value),
                    textX, textY, CONSTANT_VALUE_COLOR, fieldHovered);
        } else {
            gfx.drawString(font, NumberFormatter.format(row.input().getValue(tube)),
                    textX, textY, INPUT_VALUE_COLOR, false);
        }
    }

    // ── ADD-row buttons: 0 = add connection, 1 = add constant ──
    private int addButtonX(int bgX, int i) { return bgX + ADD_PAD_L + i * (ROW_BTN + 2); }
    /** Add-entry background width (left pad + two buttons with a 2px gap + 1px right margin). */
    private int addEntryW() { return ADD_PAD_L + 2 * ROW_BTN + 2 + 1; }

    private void renderAddRow(GuiGraphics gfx, ArithmeticTubeBehaviour tube, Row.Add row, int y,
                              @Nullable ContentTarget hovered) {
        int x = entryX();
        renderSlot(gfx, row.primary(), x, y);
        BatchedBlitter.forSprite(SpriteLocations.ELLIPSIS_ICON).blit(gfx.bufferSource(), gfx.pose(), x + 2, y + 2, ICON16, ICON16);

        int bgX = x + SLOT + SLOT_GAP, bY = y + 1;
        int w = addEntryW();
        TiledSpriteRenderer.create(SpriteLocations.ENTRY_BG).render(gfx, bgX, y, w, INPUT_H);

        int b1X = addButtonX(bgX, 0), b2X = addButtonX(bgX, 1);
        boolean addConnectionHovered = hovered != null && hovered.kind() == TargetKind.ADD_CONNECTION
                && hovered.row() == row;
        ResourceLocation sprite2 = addConnectionHovered ? SpriteLocations.BTN_HOVER : SpriteLocations.BTN_NORMAL;
        TiledSpriteRenderer.create(sprite2).render(gfx, b1X, bY, ROW_BTN, ROW_BTN);
        AllIcons.I_ADD.render(gfx, b1X + (ROW_BTN - ICON16) / 2, bY + (ROW_BTN - ICON16) / 2);
        // add constant: one per operand group (this row's group), so it greys out once this group has one
        ResourceLocation sprite1 = tube.hasConstant(row.primary()) ? SpriteLocations.BTN_DISABLED
                : hovered != null && hovered.kind() == TargetKind.ADD_CONSTANT && hovered.row() == row
                        ? SpriteLocations.BTN_HOVER : SpriteLocations.BTN_NORMAL;
        TiledSpriteRenderer.create(sprite1).render(gfx, b2X, bY, ROW_BTN, ROW_BTN);
        gfx.blitSprite(SpriteLocations.ADD_CONSTANT_ICON, b2X + (ROW_BTN - ICON16) / 2, bY + (ROW_BTN - ICON16) / 2, ICON16, ICON16);
    }

    private void renderSlot(GuiGraphics gfx, boolean primary, int x, int y) {
        BatchedBlitter.forSprite(primary ? SpriteLocations.OPERAND_BLUE_SLOT : SpriteLocations.OPERAND_RED_SLOT)
                .blit(gfx.bufferSource(), gfx.pose(), x, y, SLOT, SLOT);
    }

    private void renderDropdown(GuiGraphics gfx, ArithmeticTubeBehaviour tube, int mouseX, int mouseY) {
        TiledSpriteRenderer.create(SpriteLocations.DROPDOWN_BG).render(gfx, ddX(), ddY(), ddW(), ddH());
        ArithmeticOperator current = tube.getOperator();
        for (int i = 0; i < OPERATORS.length; i++) {
            ArithmeticOperator op = OPERATORS[i];
            int bx = ddBtnX(i % DD_COLS), by = ddBtnY(i / DD_COLS);
            boolean enabled = tube.canSwitchTo(op);
            boolean active = op.name().equals(current.name());
            boolean hover = enabled && inRect(mouseX, mouseY, bx, by, DD_BTN, DD_BTN);
            ResourceLocation state = !enabled ? SpriteLocations.BTN_DISABLED : active ? SpriteLocations.BTN_TOGGLED : hover ? SpriteLocations.BTN_HOVER : SpriteLocations.BTN_NORMAL;
            TiledSpriteRenderer.create(state).render(gfx, bx, by, DD_BTN, DD_BTN);
            drawOperatorIcon(gfx, op, bx + (DD_BTN - OP_ICON) / 2, by + (DD_BTN - OP_ICON) / 2, 0xFFE2E2E2, false, false);
        }
    }

    private void drawOperatorIcon(GuiGraphics gfx, ArithmeticOperator op, int x, int y, int tint,
                                  boolean border, boolean operands) {
        String icon = op.iconName();
        if (border) {
            setColor(gfx, OP_BORDER_COLOR);
            gfx.blitSprite(SpriteLocations.resource(OPERATOR_PATH + icon + "_border"), x, y, OP_ICON, OP_ICON);
        }
        setColor(gfx, tint);
        gfx.blitSprite(SpriteLocations.resource(OPERATOR_PATH + icon), x, y, OP_ICON, OP_ICON);
        gfx.setColor(1f, 1f, 1f, 1f);
        if (operands)
            gfx.blitSprite(SpriteLocations.resource(OPERATOR_PATH + icon + "_operands"), x, y, OP_ICON, OP_ICON);
    }

    private static void setColor(GuiGraphics gfx, int argb) {
        gfx.setColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
    }

    @Override
    protected void renderForeground(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        Component title = getTitle();
        gfx.drawString(font, title, panelX + PANEL_W / 2 - font.width(title) / 2, panelY + 4, TITLE_COLOR, false);
        super.renderForeground(gfx, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics gfx, int mouseX, int mouseY) {}

    @Override
    public List<net.minecraft.client.renderer.Rect2i> getExtraAreas() {
        return List.of(new net.minecraft.client.renderer.Rect2i(panelX + PANEL_W, panelY + panelH - 35, 45, 35));
    }

    @Nullable
    private ContentTarget contentTargetAt(double mouseX, double mouseY) {
        if (viewportWidget == null || !viewportWidget.isMouseOver(mouseX, mouseY)
                || operatorDropdown.isOpen() || constantDropdown.isOpen()) return null;
        if (overScrollbar(mouseX, mouseY)) return new ContentTarget(TargetKind.SCROLLBAR, null);
        if (constantEditor.box != null && constantEditor.showMenuButton()
                && constantEditor.menuButtonBounds().contains(
                        (int) mouseX, (int) mouseY, Rect2i.Boundary.HALF_OPEN))
            return new ContentTarget(TargetKind.CONSTANT_MENU, null);
        if (inOperatorButton(mouseX, mouseY)) return new ContentTarget(TargetKind.OPERATOR, null);
        for (int k = 0; k < rows.size(); k++) {
            Row row = rows.get(k);
            int y = rowY(k), x = entryX();
            if (inRect(mouseX, mouseY, x, y, SLOT, SLOT))
                return new ContentTarget(TargetKind.SLOT, row);
            int bgX = x + SLOT + SLOT_GAP, bgW = entryW() - SLOT - SLOT_GAP;
            if (row instanceof Row.Input input) {
                int deleteX = bgX + bgW - 1 - ROW_BTN;
                if (inRect(mouseX, mouseY, deleteX, y + 1, ROW_BTN, ROW_BTN))
                    return new ContentTarget(TargetKind.REMOVE, input);
                int boxX = bgX + 1, boxW = deleteX - 2 - boxX;
                if (input.input() instanceof ArithmeticTubeBehaviour.ConstantInput
                        && inRect(mouseX, mouseY, boxX, y + 1, boxW, 18))
                    return new ContentTarget(TargetKind.CONSTANT_FIELD, input);
                if (inRect(mouseX, mouseY, x, y, entryW(), INPUT_H))
                    return new ContentTarget(TargetKind.ROW, input);
            } else {
                int addConnectionX = addButtonX(bgX, 0), addConstantX = addButtonX(bgX, 1);
                if (inRect(mouseX, mouseY, addConnectionX, y + 1, ROW_BTN, ROW_BTN))
                    return new ContentTarget(TargetKind.ADD_CONNECTION, row);
                if (inRect(mouseX, mouseY, addConstantX, y + 1, ROW_BTN, ROW_BTN))
                    return new ContentTarget(TargetKind.ADD_CONSTANT, row);
            }
        }
        if (inRect(mouseX, mouseY, entryX(), resultEntryY(), RESULT_ICON_SIZE, RESULT_ICON_SIZE))
            return new ContentTarget(TargetKind.RESULT, null);
        return null;
    }

    @Nullable
    private List<Component> contentTooltip(@Nullable ContentTarget target) {
        if (target == null) return null;
        return switch (target.kind()) {
            case OPERATOR -> tr("tooltip.operator", ChatFormatting.WHITE);
            case SLOT -> slotTooltip(target.row());
            case REMOVE -> tr("tooltip.remove", ChatFormatting.WHITE);
            case CONSTANT_FIELD -> target.row() instanceof Row.Input input && !constantEditor.isEditing(input)
                    ? tr("tooltip.click_to_edit", ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC) : null;
            case ADD_CONNECTION -> List.of(CreateLang.translate("gui.factory_panel.connect_input").component());
            case ADD_CONSTANT -> tr("tooltip.add_constant", ChatFormatting.WHITE);
            case RESULT -> tr("tooltip.result", ChatFormatting.YELLOW);
            default -> null;
        };
    }

    /** Tooltip for an operand icon slot. */
    @Nullable
    private List<Component> slotTooltip(Row row) {
        if (row instanceof Row.Add)
            return tr("tooltip.new_input", row.primary() ? ChatFormatting.BLUE : ChatFormatting.RED);
        if (!(row instanceof Row.Input input))
            return null;
        if (input.input() instanceof ArithmeticTubeBehaviour.ConstantInput)
            return tr("tooltip.constant", ChatFormatting.WHITE);
        if (input.input() instanceof ArithmeticTubeBehaviour.ConnectionInput(VirtualComponentPosition source)) {
            var comp = menu.componentAt(source);
            if (comp != null) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("createfactorycontroller.arithmetic_tube.tooltip.connection")
                        .withStyle(ChatFormatting.WHITE));
                tip.add(comp.getName().copy().withColor(comp.getColor()));
                tip.addAll(comp.infoTooltip());
                return tip;
            }
        }
        return null;
    }

    private static List<Component> tr(String key, ChatFormatting... styles) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("createfactorycontroller.arithmetic_tube." + key).withStyle(styles));
        return tooltip;
    }

    private List<Component> operatorTooltip(ArithmeticOperator op) {
        List<Component> tip = new ArrayList<>();
        tip.add(op.displayName().copy().withStyle(ChatFormatting.WHITE));
        var inputs = Component.translatable(
                "createfactorycontroller.arithmetic_tube.operator_inputs." + op.arity().name().toLowerCase())
                .withStyle(ChatFormatting.GRAY);
        inputs.append(Component.literal(" ■").withStyle(ChatFormatting.BLUE));
        if (op.arity() == ArithmeticOperator.Arity.BINARY)
            inputs.append(Component.literal("■").withStyle(ChatFormatting.RED));
        else if (op.arity() == ArithmeticOperator.Arity.N_ARY)
            inputs.append(Component.literal("■■").withStyle(ChatFormatting.BLUE));
        tip.add(inputs);
        ArithmeticTubeBehaviour tube = tube();
        if (tube != null && !tube.canSwitchTo(op))
            tip.add(Component.translatable("createfactorycontroller.arithmetic_tube.operator_locked")
                    .withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }

    // ── Interaction ───────────────────────────────────────────────────────────────

    private boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return Rect2i.fromXYWH(x, y, w, h).contains((int) mx, (int) my, Rect2i.Boundary.HALF_OPEN);
    }

    private boolean inOperatorButton(double mx, double my) {
        return inRect(mx, my, entryX(), opEntryY(), entryW(), OP_H);
    }

    private boolean inDropdown(double mx, double my) {
        return inRect(mx, my, ddX(), ddY(), ddW(), ddH());
    }

    private int dropdownButtonAt(double mx, double my) {
        for (int i = 0; i < OPERATORS.length; i++)
            if (inRect(mx, my, ddBtnX(i % DD_COLS), ddBtnY(i / DD_COLS), DD_BTN, DD_BTN)) return i;
        return -1;
    }

    private class ArithmeticViewportWidget extends InteractiveAreaWidget {
        ArithmeticViewportWidget() {
            super(0, 0, 0, 0, (mouseX, mouseY) -> {
                if (operatorDropdown.isOpen() || constantDropdown.isOpen()) return List.of();
                List<Component> tooltip = contentTooltip(contentTargetAt(mouseX, mouseY));
                return tooltip == null ? List.of() : tooltip;
            });
            onClick(this::contentClicked);
            onScroll((mouseX, mouseY, scrollX, scrollY) -> {
                if (operatorDropdown.isOpen() || constantDropdown.isOpen() || maxScroll() <= 0) return false;
                double target = Mth.clamp(scroll.getChaseTarget() - scrollY * 18, 0, maxScroll());
                scroll.chase(target, 0.5, Chaser.EXP);
                return true;
            });
            onRelease((mouseX, mouseY, button) -> {
                operatorHeld = false;
                if (button == 0 && draggingScrollbar) {
                    draggingScrollbar = false;
                    return true;
                }
                return false;
            });
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            ArithmeticTubeBehaviour tube = tube();
            if (tube == null) return;
            ContentTarget hovered = contentTargetAt(mouseX, mouseY);
            boolean contentHovered = hovered != null || isHovered()
                    && !operatorDropdown.isOpen() && !constantDropdown.isOpen();
            int hoverX = contentHovered ? mouseX : Integer.MIN_VALUE;
            int hoverY = contentHovered ? mouseY : Integer.MIN_VALUE;
            gfx.enableScissor(getX(), getY(), getRight(), getBottom());
            renderOperatorEntry(gfx, tube, hovered != null && hovered.kind() == TargetKind.OPERATOR);
            renderInputEntries(gfx, tube, hovered);
            renderResultEntry(gfx, tube);
            constantEditor.render(gfx, hoverX, hoverY, partialTick,
                    hovered != null && hovered.kind() == TargetKind.CONSTANT_MENU);
            gfx.disableScissor();
            renderScrollbar(gfx, hovered != null && hovered.kind() == TargetKind.SCROLLBAR);
            super.renderWidget(gfx, mouseX, mouseY, partialTick);
        }

        private boolean contentClicked(double mouseX, double mouseY, int button) {
            if (operatorDropdown.isOpen() || constantDropdown.isOpen()) return false;
            ContentTarget target = contentTargetAt(mouseX, mouseY);
            if (target == null) return false;
            if (button == 0 && target.kind() == TargetKind.SCROLLBAR) {
                draggingScrollbar = true;
                int thumbY = scrollbarThumbY(), thumbH = scrollbarThumbHeight();
                boolean onThumb = mouseY >= thumbY && mouseY < thumbY + thumbH;
                scrollbarGrabOffset = onThumb ? mouseY - thumbY : thumbH / 2.0;
                if (!onThumb) dragScrollbarTo(mouseY);
                return true;
            }
            if (target.kind() == TargetKind.CONSTANT_MENU && button == 0) {
                constantDropdown.toggle();
                return true;
            }
            if (constantEditor.box != null && constantEditor.box.isMouseOver(mouseX, mouseY)) {
                if (button == 1) {
                    constantEditor.box.setValue("");
                    return true;
                }
                ArithmeticTubeSettingsScreen.this.setFocused(viewportWidget);
                constantEditor.box.setFocused(true);
                return constantEditor.box.mouseClicked(mouseX, mouseY, button);
            }
            if (constantEditor.active()) constantEditor.commit();
            if (target.kind() == TargetKind.OPERATOR) {
                operatorHeld = button == 0;
                operatorDropdown.open();
                return true;
            }
            if (target.kind() == TargetKind.REMOVE && target.row() instanceof Row.Input input) {
                sendInput(ConfigureArithmeticInputPacket.REMOVE, input.primary(), input.index(), 0);
                playClickSound();
                return true;
            }
            if (target.kind() == TargetKind.CONSTANT_FIELD && target.row() instanceof Row.Input input) {
                int bgX = entryX() + SLOT + SLOT_GAP;
                int deleteX = bgX + entryW() - SLOT - SLOT_GAP - 1 - ROW_BTN;
                int fieldWidth = deleteX - 2 - (bgX + 1);
                constantEditor.start(input, fieldWidth - 9);
                return true;
            }
            if (target.kind() == TargetKind.ADD_CONNECTION && target.row() instanceof Row.Add add) {
                sendInput(ConfigureArithmeticInputPacket.PREPARE_WIRE, add.primary(), -1, 0);
                controller.beginConnectionMode(tubePos);
                Minecraft.getInstance().setScreen(controller);
                return true;
            }
            if (target.kind() == TargetKind.ADD_CONSTANT && target.row() instanceof Row.Add add) {
                ArithmeticTubeBehaviour tube = tube();
                if (tube != null && !tube.hasConstant(add.primary())) {
                    sendInput(ConfigureArithmeticInputPacket.ADD_CONSTANT, add.primary(), -1, 0);
                    playClickSound();
                }
                return true;
            }
            return target.kind() == TargetKind.SLOT && target.row() instanceof Row.Input
                    || target.kind() == TargetKind.ROW;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (button == 0 && draggingScrollbar) {
                dragScrollbarTo(mouseY);
                return true;
            }
            return false;
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (constantEditor.box != null) constantEditor.box.setFocused(focused);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return constantEditor.box != null && constantEditor.box.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
            return constantEditor.box != null && constantEditor.box.keyReleased(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            return constantEditor.box != null && constantEditor.box.charTyped(codePoint, modifiers);
        }
    }

    private class OperatorDropdownWidget extends InteractiveAreaWidget {
        OperatorDropdownWidget() {
            super(0, 0, 0, 0, (mouseX, mouseY) -> {
                int index = dropdownButtonAt(mouseX, mouseY);
                return index >= 0 ? operatorTooltip(OPERATORS[index]) : List.of();
            });
            visible = false;
        }

        boolean isOpen() { return visible; }

        void open() {
            constantDropdown.close();
            visible = true;
            updateBounds();
        }

        void close() { visible = false; }

        void updateBounds() { setRectangle(ddW(), ddH(), ddX(), ddY()); }

        @Override
        protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            ArithmeticTubeBehaviour tube = tube();
            if (tube != null) renderDropdown(gfx, tube, mouseX, mouseY);
            super.renderWidget(gfx, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isOpen()) return false;
            int index = dropdownButtonAt(mouseX, mouseY);
            if (button == 0 && index >= 0) {
                ArithmeticOperator operator = OPERATORS[index];
                ArithmeticTubeBehaviour tube = tube();
                if (tube != null && tube.canSwitchTo(operator)) {
                    PacketDistributor.sendToServer(new ConfigureArithmeticTubePacket(
                            menu.controllerPos, tubePos, operator.name()));
                    playClickSound();
                    close();
                }
                return true;
            }
            if (!inDropdown(mouseX, mouseY)) close();
            return true;
        }
    }

    private class ConstantDropdownWidget extends InteractiveAreaWidget {
        ConstantDropdownWidget() {
            super(0, 0, 0, 0, () -> List.of());
            visible = false;
        }

        boolean isOpen() { return visible; }

        void toggle() {
            if (isOpen()) close();
            else {
                operatorDropdown.close();
                visible = true;
                updateBounds();
            }
        }

        void close() { visible = false; }

        void updateBounds() {
            Rect2i bounds = constantEditor.menuBounds();
            setRectangle(bounds.w(), bounds.h(), bounds.x(), bounds.y());
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            if (!constantEditor.showMenuButton()) {
                close();
                return;
            }
            RenderSystem.enableBlend();
            Rect2i menu = constantEditor.menuBounds();
            gfx.fill(menu.minX(), menu.minY(), menu.maxX(), menu.maxY(), 0xA0000000);
            int hovered = constantEditor.menuItemAt(mouseX, mouseY);
            for (int i = 0; i < ConstantEditor.MENU_ITEMS.size(); i++) {
                Rect2i item = constantEditor.menuItemBounds(menu, i);
                String label = ConstantEditor.MENU_ITEMS.get(i);
                gfx.drawString(font, label,
                        item.x() + (item.w() - font.width(label)) / 2,
                        item.y() + (item.h() - font.lineHeight) / 2 + 1,
                        i == hovered ? ConstantEditor.MENU_ITEM_HOVER_COLOR : ConstantEditor.MENU_ITEM_COLOR, true);
            }
            super.renderWidget(gfx, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isOpen()) return false;
            int index = constantEditor.menuItemAt(mouseX, mouseY);
            if (button == 0 && index >= 0) {
                constantEditor.box.setValue(ConstantEditor.MENU_ITEMS.get(index));
                constantEditor.commit();
                return true;
            }
            if (constantEditor.menuBounds().contains(
                    (int) mouseX, (int) mouseY, Rect2i.Boundary.HALF_OPEN)) return true;
            if (button == 0 && constantEditor.menuButtonBounds().contains(
                    (int) mouseX, (int) mouseY, Rect2i.Boundary.HALF_OPEN)) {
                close();
                return true;
            }
            close();
            return false;
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (constantEditor.box != null) constantEditor.box.setFocused(focused);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return constantEditor.box != null && constantEditor.box.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
            return constantEditor.box != null && constantEditor.box.keyReleased(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            return constantEditor.box != null && constantEditor.box.charTyped(codePoint, modifiers);
        }
    }

    private void sendInput(int op, boolean primary, int index, double value) {
        PacketDistributor.sendToServer(new ConfigureArithmeticInputPacket(menu.controllerPos, tubePos, op, primary, index, value));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (constantEditor.active()
                && !constantEditor.box.isMouseOver(mouseX, mouseY)
                && !(constantDropdown.isOpen() && constantEditor.menuBounds().contains(
                        (int) mouseX, (int) mouseY, Rect2i.Boundary.HALF_OPEN))
                && !(button == 0 && constantEditor.showMenuButton()
                        && constantEditor.menuButtonBounds().contains(
                                (int) mouseX, (int) mouseY, Rect2i.Boundary.HALF_OPEN))) {
            constantEditor.commit();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (viewportWidget != null && viewportWidget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (constantEditor.active()) {
            return switch (keyCode) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    constantEditor.commit();
                    yield true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    constantEditor.remove();
                    yield true;
                }
                default -> constantEditor.box.keyPressed(keyCode, scanCode, modifiers);
            };
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f));
    }

    // ── Overlay plumbing ─────────────────────

    @Override public void onPanelSync() { controller.onPanelSync(); }

    @Override
    protected void containerTick() {
        super.containerTick();
        controller.tickComponentWidgets();
        scroll.tickChaser();
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        controller.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }

    @Override
    public void onClose() {
        constantEditor.commit();
        Minecraft.getInstance().setScreen(controller);
    }

    @Override
    public void removed() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_CLOSE.get(), 1f));
        super.removed();
    }

    /** Special constants that can be entered by name (case-insensitive) */
    private record SpecialConstant(double value, List<String> names, boolean visibleInMenu) {
        /** Infinity and NaN are already parsed by {@link Double#parseDouble}, but we handle them anyway
         * since the rest of the infrastructure is here */
        public static final List<SpecialConstant> LIST = List.of(
                new SpecialConstant(Math.PI, List.of("π", "pi"), true),
                new SpecialConstant(Math.TAU, List.of("τ", "tau"), true),
                new SpecialConstant(Math.E, List.of("e"), true),
                new SpecialConstant(Double.POSITIVE_INFINITY, List.of("∞", "Inf", "Infinity"), true),
                new SpecialConstant(Double.NEGATIVE_INFINITY, List.of("-∞", "-Inf", "-Infinity"), true),
                new SpecialConstant(Double.NaN, List.of("NaN"), false)
        );

        /** Name -> Value lookup table of special constants */
        public static final Map<String, Double> LOOKUP = LIST.stream()
                .flatMap(s ->
                        s.names.stream().map(name -> Map.entry(name.toLowerCase(Locale.ROOT), s.value))
                ).collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));

        public static String displayValue(double value) {
            return LIST.stream()
                    .filter(s -> Double.valueOf(s.value).equals(value))
                    .findFirst()
                    .map(s -> s.names.getFirst())
                    .orElseGet(() -> NumberFormatter.format(value));
        }
    }

    private class ConstantEditor {
        // Notation of a finite floating point number
        private static final Pattern FLOAT_LITERAL_PATTERN = Pattern.compile("[+-]?\\d*\\.?\\d*(?:e[+-]?\\d*)?", Pattern.CASE_INSENSITIVE);

        private static final List<String> MENU_ITEMS = SpecialConstant.LIST.stream()
                .filter(SpecialConstant::visibleInMenu)
                .map(s -> s.names.getFirst())
                .toList();

        private static final Vector2ic MENU_BUTTON_SIZE = new Vector2i(10, 10);
        private static final Vector2ic MENU_ITEM_SIZE = new Vector2i(20, 10);
        private static final int MENU_PAD = 2;
        private static final int MENU_ITEM_COLOR = 0xFFCCCCCC;
        private static final int MENU_ITEM_HOVER_COLOR = 0xFFFFFF00;

        private static final int MAX_LENGTH = 18;

        @Nullable private EditBox box;
        private boolean editPrimary;
        private int editIndex = -1;

        private static final int NO_COMMIT = -2;

        /** Optimistic post-commit display: show the just-committed value for the slot until the sync catches up (else
         *  the box flashes back to the old value for a tick). {@code commitIndex == NO_COMMIT} disables it. */
        private boolean commitPrimary;
        private int commitIndex = NO_COMMIT;
        private double commitValue;

        public boolean active() { return box != null; }

        public boolean isEditing(Row.Input row) {
            return box != null && row.primary() == editPrimary && row.index() == editIndex;
        }

        public void discardIfRowGone(List<Row> rows) {
            if (box != null &&
                    rows.stream().noneMatch(row -> row instanceof Row.Input input && isEditing(input))
            ) remove();
        }

        public void position(int x, int y, int width) {
            if (box == null) return;
            box.setX(x);
            box.setY(y);
            box.setWidth(width);
        }

        public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick,
                           boolean menuButtonHovered) {
            if (box == null) return;
            box.render(gfx, mouseX, mouseY, partialTick);
            if (showMenuButton()) {
                gfx.drawString(font, "⏷", menuButtonBounds().x() + 2, menuButtonBounds().y() + 1,
                        menuButtonHovered ? MENU_ITEM_HOVER_COLOR : CONSTANT_VALUE_COLOR, menuButtonHovered);
            }
        }

        /** The optimistic post-commit value for a just-edited constant, if the server sync has not caught up yet. */
        public OptionalDouble optimisticValue(Row.Input row, double currentValue) {
            if (!(row.input() instanceof ArithmeticTubeBehaviour.ConstantInput) || !matchesCommit(row))
                return OptionalDouble.empty();
            if (Double.compare(currentValue, commitValue) == 0) {
                commitIndex = NO_COMMIT;   // the sync caught up
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(commitValue);
        }

        private boolean matchesCommit(Row.Input row) {
            return commitIndex != NO_COMMIT &&
                    row.primary() == commitPrimary &&
                    row.index() == commitIndex;
        }

        public void start(Row.Input row, int width) {
            commit();   // commit any prior edit
            editPrimary = row.primary();
            editIndex = row.index();

            box = new EditBox(font, 0, 0, Math.max(10, width), font.lineHeight, Component.empty());
            box.setBordered(false);
            box.setTextColor(CONSTANT_VALUE_COLOR);
            box.setMaxLength(MAX_LENGTH);
            box.setFilter(input ->
                    FLOAT_LITERAL_PATTERN.matcher(input).matches() ||
                    SpecialConstant.LOOKUP.keySet().stream().anyMatch(name -> name.toLowerCase(Locale.ROOT).startsWith(input))
            );

            double value = ((ArithmeticTubeBehaviour.ConstantInput) row.input()).value();
            box.setValue(SpecialConstant.displayValue(value));

            setFocused(viewportWidget);
            box.setFocused(true);
            box.setHighlightPos(0);
        }

        public void commit() {
            if (box == null) return;
            if (constantDropdown != null) constantDropdown.close();
            String input = box.getValue();

            double value;
            var specialValue = SpecialConstant.LOOKUP.get(input.toLowerCase(Locale.ROOT));
            if (specialValue != null) {
               value = specialValue;
            } else if (input.isEmpty()) {
                value = 0;
            } else {
                try {
                    value = Double.parseDouble(input);
                } catch (NumberFormatException e) {
                    value = 0;
                }
            }

            sendInput(ConfigureArithmeticInputPacket.SET_CONSTANT, editPrimary, editIndex, value);
            commitPrimary = editPrimary;
            commitIndex = editIndex;
            commitValue = value;
            remove();
        }

        public void remove() {
            if (box == null) return;
            box = null;
            if (constantDropdown != null) constantDropdown.close();
            setFocused(null);
        }

        private boolean showMenuButton() {
            return box != null && box.getValue().length() <= MAX_LENGTH - 2;
        }

        private Rect2i menuButtonBounds() {
            if (box == null) return Rect2i.fromXYWH(0, 0, 0, 0);
            int hCenter = box.getY() + font.lineHeight / 2;
            return Rect2i.fromXYWH(
                    box.getX() + box.getWidth() - MENU_BUTTON_SIZE.x() + 2, hCenter - MENU_BUTTON_SIZE.y() / 2,
                    MENU_BUTTON_SIZE.x(), MENU_BUTTON_SIZE.y());
        }

        private Rect2i menuBounds() {
            if (box == null) return Rect2i.fromXYWH(0, 0, 0, 0);
            int menuW = MENU_PAD * 2 + MENU_ITEMS.size() * MENU_ITEM_SIZE.x();
            return Rect2i.fromXYWH(
                    box.getX() + box.getWidth() + 2 - menuW, box.getY() + box.getHeight() + 2,
                    menuW, MENU_PAD * 2 + MENU_ITEM_SIZE.y());
        }

        private Rect2i menuItemBounds(Rect2i menu, int index) {
            return Rect2i.fromXYWH(
                    menu.x() + MENU_PAD + index * MENU_ITEM_SIZE.x(),
                    menu.y() + MENU_PAD,
                    MENU_ITEM_SIZE.x(), MENU_ITEM_SIZE.y());
        }

        private int menuItemAt(double mx, double my) {
            if (!(constantDropdown.isOpen() && box != null
                    && menuBounds().contains((int) mx, (int) my, Rect2i.Boundary.HALF_OPEN))) return -1;
            Rect2i menu = menuBounds();
            int localX = (int) mx - menu.x() - MENU_PAD;
            int localY = (int) my - menu.y() - MENU_PAD;
            if (localY < 0 || localY >= MENU_ITEM_SIZE.y() || localX < 0) return -1;
            int index = localX / MENU_ITEM_SIZE.x();
            return index < MENU_ITEMS.size() ? index : -1;
        }
    }
}
