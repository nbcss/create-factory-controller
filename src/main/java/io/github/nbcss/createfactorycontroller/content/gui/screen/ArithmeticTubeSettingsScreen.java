package io.github.nbcss.createfactorycontroller.content.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.ArithmeticTubeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.operator.ArithmeticOperator;
import io.github.nbcss.createfactorycontroller.content.component.operator.BuiltinOperator;
import io.github.nbcss.createfactorycontroller.content.component.operator.OperatorArity;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.gui.widget.TooltipIconButton;
import io.github.nbcss.createfactorycontroller.content.helper.NumberFormatter;
import io.github.nbcss.createfactorycontroller.content.packet.ConfigureArithmeticInputPacket;
import io.github.nbcss.createfactorycontroller.content.packet.ConfigureArithmeticTubePacket;
import io.github.nbcss.createfactorycontroller.content.render.BatchedBlitter;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration overlay for an Arithmetic Tube.
 */
@OnlyIn(Dist.CLIENT)
@IPNIgnore
public class ArithmeticTubeSettingsScreen extends AbstractSimiContainerScreen<FactoryControllerMenu>
        implements PanelSyncListener {

    private static final ResourceLocation FRAME = resource("arithmetic_tube/frame");
    private static final ResourceLocation BOTTOM_BAR = resource("common/bottom_bar");
    private static final ResourceLocation OP_BUTTON = resource("arithmetic_tube/operator_button");
    private static final ResourceLocation OP_BUTTON_HOVER = resource("arithmetic_tube/operator_button_hovered");
    private static final ResourceLocation OP_BUTTON_PRESSED = resource("arithmetic_tube/operator_button_pressed");
    private static final ResourceLocation DROPDOWN_BG = resource("arithmetic_tube/operator_menu_background");
    private static final ResourceLocation RESULT_ICON = resource("arithmetic_tube/result_icon");
    private static final ResourceLocation RESULT_BG = resource("arithmetic_tube/result_entry_background");
    private static final ResourceLocation RESULT_VALUE_BOX = resource("arithmetic_tube/result_value_box");
    private static final ResourceLocation BTN_NORMAL = resource("common/button/normal");
    private static final ResourceLocation BTN_HOVER = resource("common/button/hovered");
    private static final ResourceLocation BTN_TOGGLED = resource("common/button/toggled");
    private static final ResourceLocation BTN_DISABLED = resource("common/button/disabled");
    private static final ResourceLocation OPERAND_BLUE_SLOT = resource("arithmetic_tube/operand_blue_icon_slot");
    private static final ResourceLocation OPERAND_RED_SLOT = resource("arithmetic_tube/operand_red_icon_slot");
    private static final ResourceLocation ENTRY_BG = resource("arithmetic_tube/entry_background");
    private static final ResourceLocation CONN_VALUE_BOX = resource("arithmetic_tube/connection_value_box");
    private static final ResourceLocation CONSTANT_INPUT_FIELD = resource("arithmetic_tube/constant_input_field");
    private static final ResourceLocation OPERATOR_DROPDOWN_ICON = resource("arithmetic_tube/operator_dropdown_icon");
    private static final ResourceLocation CONSTANT_ICON = resource("icons/constant");
    private static final ResourceLocation ELLIPSIS_ICON = resource("icons/ellipsis");
    private static final ResourceLocation ADD_CONSTANT_ICON = resource("icons/add_constant");

    private static final int PANEL_W = 184;
    private static final int HEADER_H = 16;
    private static final int BOTTOM_H = 30;
    private static final int SCROLLBAR_X = PANEL_W - 6;

    private static final int SIDE_PAD = 10;
    private static final int TOP_PAD = 5, ENTRY_GAP = 4, BOTTOM_PAD = 5;
    private static final int OP_H = 21;
    private static final int OP_SLOT_W = 19;
    private static final int OP_ICON = 15;
    private static final int RESULT_H = 20, RESULT_ICON_SIZE = 20, RESULT_GAP = 2;
    private static final int INPUT_H = 20, SLOT = 20, SLOT_GAP = 2, INPUT_ROW_GAP = 4, ROW_BTN = 18;
    private static final int ADD_PAD_L = 4;
    private static final int ICON16 = 16;

    /** TODO self loop? */
    private static final boolean allowLoop = false;

    private static final int DD_INSET = 1;
    private static final int DD_BTN = 17;
    private static final int DD_COLS = 8, DD_ROWS = 2;      // 16 operators atm
    private static final int DD_TOP_PAD = 3, DD_BOTTOM_PAD = 5;

    private static final int BORDER_BLACK = 0xFF000000;
    private static final int VALUE_COLOR = 0xFFFFFFFF;
    private static final int NAME_COLOR = 0xF9DFFA;
    private static final int TITLE_COLOR = 0xF9DFFA;

    private static final BuiltinOperator[] OPERATORS = BuiltinOperator.values();
    private static final String OPERATOR_PATH = "arithmetic_tube/operators/";

    private final FactoryControllerScreen controller;
    private final VirtualComponentPosition tubePos;
    private final LerpedFloat scroll = LerpedFloat.linear().startWithValue(0);

    private int panelX, panelY, panelH;
    private int viewportH;
    private float renderedScroll;
    private boolean dropdownOpen;
    private boolean operatorHeld;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private TooltipIconButton closeButton, relocateButton, swapButton;

    /** The input rows (primary inputs, primary-add, secondary input/add) */
    private List<Row> rows = List.of();
    @Nullable private EditBox constantBox;   // the focused constant editor, nullable
    private boolean editPrimary;
    private int editIndex = -1;
    /** Optimistic post-commit display: show the just-committed value for the slot until the sync catches up (else the
     *  box flashes back to the old value for a tick). {@code commitIndex == NO_COMMIT} disables it. */
    private static final int NO_COMMIT = -2;
    private boolean commitPrimary;
    private int commitIndex = NO_COMMIT;
    private double commitValue;

    private enum RowKind { INPUT, ADD, LOOP }
    private record Row(RowKind kind, boolean primary, int index, @Nullable ArithmeticTubeBehaviour.NumberInput input) {}

    public ArithmeticTubeSettingsScreen(FactoryControllerScreen controller, VirtualComponentPosition tubePos) {
        super(controller.getMenu(), Minecraft.getInstance().player.getInventory(),
                Component.translatable("createfactorycontroller.gui.arithmetic_tube_settings"));
        this.controller = controller;
        this.tubePos = tubePos;
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_OPEN.get(), 1f));
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, path);
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
        addWidget(closeButton);

        relocateButton = new TooltipIconButton(0, 0, AllIcons.I_MOVE_GAUGE);
        relocateButton.withCallback(() -> { controller.beginRelocateMode(tubePos); Minecraft.getInstance().setScreen(controller); });
        relocateButton.setToolTip(Component.translatable("createfactorycontroller.gui.action_relocate"));
        addWidget(relocateButton);

        swapButton = new TooltipIconButton(0, 0, AllIcons.I_FLIP);
        swapButton.withCallback(() -> { sendInput(ConfigureArithmeticInputPacket.SWAP, true, -1, 0); playClickSound(); });
        swapButton.setToolTip(Component.translatable("createfactorycontroller.gui.arithmetic_tube.swap_inputs"));
        addWidget(swapButton);

        relayout();
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
        boolean binary = t != null && t.getOperator().arity() == OperatorArity.BINARY;
        swapButton.setX(binary ? panelX + 27 : -1000);   // right of the move button; off-screen (hidden) for non-binary
        swapButton.setY(panelY + panelH - 24);
    }

    private void relayout() {
        recomputeLayout();
        scroll.setValue(0);
        scroll.chase(0, 0.5, Chaser.EXP);
        renderedScroll = 0;
    }

    private List<Row> buildRows(ArithmeticTubeBehaviour tube) {
        List<Row> list = new ArrayList<>();
        List<ArithmeticTubeBehaviour.NumberInput> prim = tube.getPrimaryInputs();
        for (int i = 0; i < prim.size(); i++) {
            boolean loop = prim.get(i) instanceof ArithmeticTubeBehaviour.LoopInput;
            list.add(new Row(loop ? RowKind.LOOP : RowKind.INPUT, true, i, prim.get(i)));
        }
        if (prim.size() < tube.getOperator().arity().maxPrimary) list.add(new Row(RowKind.ADD, true, -1, null));
        if (tube.getOperator().arity().allowsSecondary) {
            ArithmeticTubeBehaviour.NumberInput sec = tube.getSecondaryInput();
            list.add(sec != null ? new Row(RowKind.INPUT, false, -1, sec) : new Row(RowKind.ADD, false, -1, null));
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

    private void renderScrollbar(GuiGraphics gfx, int mouseX, int mouseY) {
        if (maxScroll() <= 0) return;
        int thumbY = scrollbarThumbY(), thumbH = scrollbarThumbHeight();
        gfx.fill(panelX + SCROLLBAR_X, viewportY(), panelX + SCROLLBAR_X + 3, viewportY() + viewportH, 0x503D3C48);
        int thumbColor = overScrollbar(mouseX, mouseY) ? 0xFFE2E2E2 : 0xFFC6C6C6;
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
        if (constantBox != null && rows.stream().noneMatch(this::isEditing)) removeConstantBox();
        controller.renderBoard(gfx, -1, -1, partialTick, true);

        TiledSpriteRenderer.create(FRAME).render(gfx, panelX, panelY, PANEL_W, panelH - BOTTOM_H + 1);
        TiledSpriteRenderer.create(BOTTOM_BAR).render(gfx, panelX, panelY + panelH - BOTTOM_H, PANEL_W, BOTTOM_H);
        renderedScroll = Mth.clamp(scroll.getValue(partialTick), 0, (float) maxScroll());

        RenderSystem.enableBlend();

        int hoverX = dropdownOpen ? Integer.MIN_VALUE : mouseX, hoverY = dropdownOpen ? Integer.MIN_VALUE : mouseY;
        gfx.enableScissor(panelX, viewportY(), panelX + PANEL_W, viewportY() + viewportH);   // clip the scrolled content
        renderOperatorEntry(gfx, tube, mouseX, mouseY);
        renderInputEntries(gfx, tube, hoverX, hoverY);
        renderResultEntry(gfx, tube);
        if (constantBox != null) constantBox.render(gfx, mouseX, mouseY, partialTick);
        gfx.disableScissor();

        relocateButton.render(gfx, mouseX, mouseY, partialTick);
        if (tube.getOperator().arity() == OperatorArity.BINARY) swapButton.render(gfx, mouseX, mouseY, partialTick);
        closeButton.render(gfx, mouseX, mouseY, partialTick);
        renderScrollbar(gfx, mouseX, mouseY);

        if (dropdownOpen) {
            gfx.flush();
            RenderSystem.clear(256, Minecraft.ON_OSX);
            renderDropdown(gfx, tube, mouseX, mouseY);
        }
    }

    private static void tiled(GuiGraphics gfx, ResourceLocation sprite, int x, int y, int w, int h) {
        TiledSpriteRenderer.create(sprite).render(gfx, x, y, w, h);
    }

    private void renderOperatorEntry(GuiGraphics gfx, ArithmeticTubeBehaviour tube, int mouseX, int mouseY) {
        int x = entryX(), y = opEntryY(), w = entryW();
        boolean over = inOperatorButton(mouseX, mouseY);
        ResourceLocation sprite = (operatorHeld && over) ? OP_BUTTON_PRESSED : (over ? OP_BUTTON_HOVER : OP_BUTTON);
        tiled(gfx, sprite, x, y, w, OP_H);

        ArithmeticOperator op = tube.getOperator();
        int iconX = x + (OP_SLOT_W - OP_ICON) / 2 + 1;
        int iconY = y + (OP_H - OP_ICON) / 2;
        drawOperatorIcon(gfx, op, iconX, iconY, 0xFFFFFFFF, true,
                op.arity() == OperatorArity.BINARY);

        Component name = op.displayName();
        int nameX = (x + OP_SLOT_W + x + w) / 2 - font.width(name) / 2;
        int nameY = y + (OP_H - font.lineHeight) / 2;
        gfx.drawString(font, name, nameX, nameY, NAME_COLOR, false);
        gfx.blitSprite(OPERATOR_DROPDOWN_ICON, x + w - 6 - 7, y + (OP_H - 4) / 2, 7, 4);
    }

    private void renderResultEntry(GuiGraphics gfx, ArithmeticTubeBehaviour tube) {
        int x = entryX(), y = resultEntryY(), w = entryW();

        BatchedBlitter.forSprite(RESULT_ICON).blit(gfx.bufferSource(), gfx.pose(), x, y, RESULT_ICON_SIZE, RESULT_ICON_SIZE);

        int bgX = x + RESULT_ICON_SIZE + RESULT_GAP, bgW = w - RESULT_ICON_SIZE - RESULT_GAP;
        tiled(gfx, RESULT_BG, bgX, y, bgW, RESULT_H);

        int vbX = bgX + 1, vbW = bgW - 2, vbY = y + 1, vbH = 18;
        tiled(gfx, RESULT_VALUE_BOX, vbX, vbY, vbW, vbH);

        int tx = vbX + 6, ty = vbY + (vbH - font.lineHeight) / 2 + 2;
        gfx.drawString(font, tube.getOutputText(), tx, ty, VALUE_COLOR, false);
    }

    // ── Input rows ──────────────────────

    private void renderInputEntries(GuiGraphics gfx, ArithmeticTubeBehaviour tube, int mouseX, int mouseY) {
        for (int k = 0; k < rows.size(); k++) {
            Row row = rows.get(k);
            switch (row.kind()) {
                case INPUT -> renderInputRow(gfx, tube, row, rowY(k), mouseX, mouseY);
                case LOOP -> renderLoopRow(gfx, tube, rowY(k), mouseX, mouseY);
                case ADD -> renderAddRow(gfx, tube, row, rowY(k), mouseX, mouseY);
            }
        }
    }

    /** The synthetic feedback row: read-only, shows the tube's own output; its delete button turns the loop off. */
    private void renderLoopRow(GuiGraphics gfx, ArithmeticTubeBehaviour tube, int y, int mouseX, int mouseY) {
        int x = entryX();
        renderSlot(gfx, true, x, y);                      // primary (blue) slot
        AllIcons.I_REFRESH.render(gfx, x + 2, y + 2);      // loop indicator

        int bgX = x + SLOT + SLOT_GAP, bgW = entryW() - SLOT - SLOT_GAP;
        tiled(gfx, ENTRY_BG, bgX, y, bgW, INPUT_H);

        int delX = bgX + bgW - 1 - ROW_BTN, delY = y + 1;   // delete → disable loop
        tiled(gfx, inRect(mouseX, mouseY, delX, delY, ROW_BTN, ROW_BTN) ? BTN_HOVER : BTN_NORMAL, delX, delY, ROW_BTN, ROW_BTN);
        AllIcons.I_TRASH.render(gfx, delX + (ROW_BTN - ICON16) / 2, delY + (ROW_BTN - ICON16) / 2);

        int boxX = bgX + 1, boxW = delX - 2 - boxX;         // read-only output value
        tiled(gfx, CONN_VALUE_BOX, boxX, y + 1, boxW, 18);
        gfx.drawString(font, tube.getOutputText(), boxX + 6, y + 1 + (18 - font.lineHeight) / 2 + 2, VALUE_COLOR, false);
    }

    private void renderInputRow(GuiGraphics gfx, ArithmeticTubeBehaviour tube, Row row, int y, int mouseX, int mouseY) {
        int x = entryX();
        renderSlot(gfx, row.primary(), x, y);
        // slot content: a constant icon, or the connected component's item
        if (row.input() instanceof ArithmeticTubeBehaviour.ConstantInput)
            BatchedBlitter.forSprite(CONSTANT_ICON).blit(gfx.bufferSource(), gfx.pose(), x + 2, y + 2, ICON16, ICON16);
        else if (row.input() instanceof ArithmeticTubeBehaviour.ConnectionInput w) {
            var comp = menu.componentAt(w.source());
            if (comp != null) gfx.renderItem(new ItemStack(comp.getItem()), x + 2, y + 2);
        }

        int bgX = x + SLOT + SLOT_GAP, bgW = entryW() - SLOT - SLOT_GAP;
        tiled(gfx, ENTRY_BG, bgX, y, bgW, INPUT_H);

        int delX = bgX + bgW - 1 - ROW_BTN, delY = y + 1;   // delete button: right, 1px margin
        tiled(gfx, inRect(mouseX, mouseY, delX, delY, ROW_BTN, ROW_BTN) ? BTN_HOVER : BTN_NORMAL, delX, delY, ROW_BTN, ROW_BTN);
        AllIcons.I_TRASH.render(gfx, delX + (ROW_BTN - ICON16) / 2, delY + (ROW_BTN - ICON16) / 2);

        int boxX = bgX + 1, boxW = delX - 2 - boxX;
        boolean constant = row.input() instanceof ArithmeticTubeBehaviour.ConstantInput;
        tiled(gfx, constant ? CONSTANT_INPUT_FIELD : CONN_VALUE_BOX, boxX, y + 1, boxW, 18);
        int textX = boxX + 6, textY = y + 1 + (18 - font.lineHeight) / 2 + 2;
        if (isEditing(row) && constantBox != null) {
            constantBox.setX(textX);
            constantBox.setY(textY);
            constantBox.setWidth(boxW - 9);
        } else {
            gfx.drawString(font, NumberFormatter.format(rowValue(tube, row)),
                    textX, textY, VALUE_COLOR, false);
        }
    }

    // ── ADD-row buttons: 0 = add connection, 1 = add constant, 2 = add self-loop input ──
    /** The self-loop button is only offered on the PRIMARY group of a multi-input operator, and only while the master
     *  switch is on. Scoping it to the primary group keeps loop independent of the (separate) secondary slot, so were
     *  loop ever allowed for a binary operator its secondary wouldn't be blocked by the primary loop. */
    private boolean showLoopAdd(ArithmeticTubeBehaviour tube, boolean primary) {
        return allowLoop && primary && tube.getOperator().arity() == OperatorArity.NARY;
    }
    private int addButtonX(int bgX, int i) { return bgX + ADD_PAD_L + i * (ROW_BTN + 2); }
    /** Add-entry background width for {@code n} buttons (left pad + buttons with 2px gaps + 1px right margin). */
    private int addEntryW(int n) { return ADD_PAD_L + n * ROW_BTN + (n - 1) * 2 + 1; }

    private void renderAddRow(GuiGraphics gfx, ArithmeticTubeBehaviour tube, Row row, int y, int mouseX, int mouseY) {
        int x = entryX();
        renderSlot(gfx, row.primary(), x, y);
        BatchedBlitter.forSprite(ELLIPSIS_ICON).blit(gfx.bufferSource(), gfx.pose(), x + 2, y + 2, ICON16, ICON16);

        boolean loop = showLoopAdd(tube, row.primary());
        int bgX = x + SLOT + SLOT_GAP, bY = y + 1;
        tiled(gfx, ENTRY_BG, bgX, y, addEntryW(loop ? 3 : 2), INPUT_H);   // background extends for the third button

        int b1X = addButtonX(bgX, 0), b2X = addButtonX(bgX, 1), b3X = addButtonX(bgX, 2);
        tiled(gfx, inRect(mouseX, mouseY, b1X, bY, ROW_BTN, ROW_BTN) ? BTN_HOVER : BTN_NORMAL, b1X, bY, ROW_BTN, ROW_BTN);
        AllIcons.I_ADD.render(gfx, b1X + (ROW_BTN - ICON16) / 2, bY + (ROW_BTN - ICON16) / 2);
        // add constant: one per operand group (this row's group), so it greys out once this group has one
        tiled(gfx, tube.hasConstant(row.primary()) ? BTN_DISABLED
                : inRect(mouseX, mouseY, b2X, bY, ROW_BTN, ROW_BTN) ? BTN_HOVER : BTN_NORMAL, b2X, bY, ROW_BTN, ROW_BTN);
        gfx.blitSprite(ADD_CONSTANT_ICON, b2X + (ROW_BTN - ICON16) / 2, bY + (ROW_BTN - ICON16) / 2, ICON16, ICON16);
        // add self-loop input: only one per tube, so it greys out once the loop is on
        if (loop) {
            tiled(gfx, tube.hasLoopInput() ? BTN_DISABLED
                    : inRect(mouseX, mouseY, b3X, bY, ROW_BTN, ROW_BTN) ? BTN_HOVER : BTN_NORMAL, b3X, bY, ROW_BTN, ROW_BTN);
            AllIcons.I_REFRESH.render(gfx, b3X + (ROW_BTN - ICON16) / 2, bY + (ROW_BTN - ICON16) / 2);
        }
    }

    private void renderSlot(GuiGraphics gfx, boolean primary, int x, int y) {
        BatchedBlitter.forSprite(primary ? OPERAND_BLUE_SLOT : OPERAND_RED_SLOT)
                .blit(gfx.bufferSource(), gfx.pose(), x, y, SLOT, SLOT);
    }

    /** A row's numeric value, honouring the optimistic post-commit value for a just-edited constant. */
    private double rowValue(ArithmeticTubeBehaviour tube, Row row) {
        double v = row.input().getValue(tube);
        if (row.input() instanceof ArithmeticTubeBehaviour.ConstantInput && matchesCommit(row)) {
            if (Double.compare(v, commitValue) == 0) commitIndex = NO_COMMIT;   // the sync caught up
            else return commitValue;
        }
        return v;
    }

    private boolean matchesCommit(Row row) {
        return commitIndex != NO_COMMIT && row.kind() == RowKind.INPUT
                && row.primary() == commitPrimary && row.index() == commitIndex;
    }

    private boolean isEditing(Row row) {
        return constantBox != null && row.kind() == RowKind.INPUT
                && row.primary() == editPrimary && row.index() == editIndex;
    }

    private void renderDropdown(GuiGraphics gfx, ArithmeticTubeBehaviour tube, int mouseX, int mouseY) {
        tiled(gfx, DROPDOWN_BG, ddX(), ddY(), ddW(), ddH());
        ArithmeticOperator current = tube.getOperator();
        for (int i = 0; i < OPERATORS.length; i++) {
            ArithmeticOperator op = OPERATORS[i];
            int bx = ddBtnX(i % DD_COLS), by = ddBtnY(i / DD_COLS);
            boolean enabled = tube.canSwitchTo(op);
            boolean active = op.id().equals(current.id());
            boolean hover = enabled && inRect(mouseX, mouseY, bx, by, DD_BTN, DD_BTN);
            ResourceLocation state = !enabled ? BTN_DISABLED : active ? BTN_TOGGLED : hover ? BTN_HOVER : BTN_NORMAL;
            tiled(gfx, state, bx, by, DD_BTN, DD_BTN);
            drawOperatorIcon(gfx, op, bx + (DD_BTN - OP_ICON) / 2, by + (DD_BTN - OP_ICON) / 2, 0xFFFFFFFF, false, false);
        }
    }

    private void drawOperatorIcon(GuiGraphics gfx, ArithmeticOperator op, int x, int y, int tint,
                                  boolean border, boolean operands) {
        String icon = op.iconName();
        if (border) {
            setColor(gfx, BORDER_BLACK);
            gfx.blitSprite(resource(OPERATOR_PATH + icon + "_border"), x, y, OP_ICON, OP_ICON);
        }
        setColor(gfx, tint);
        gfx.blitSprite(resource(OPERATOR_PATH + icon), x, y, OP_ICON, OP_ICON);
        gfx.setColor(1f, 1f, 1f, 1f);
        if (operands)
            gfx.blitSprite(resource(OPERATOR_PATH + icon + "_operands"), x, y, OP_ICON, OP_ICON);
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
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        if (dropdownOpen) {
            int idx = dropdownButtonAt(mouseX, mouseY);
            if (idx >= 0) gfx.renderComponentTooltip(font, operatorTooltip(OPERATORS[idx]), mouseX, mouseY);
        } else {
            ArithmeticTubeBehaviour t = tube();
            List<Component> tip = t == null ? null : contentTooltip(t, mouseX, mouseY);
            if (tip != null && !tip.isEmpty()) gfx.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
        TooltipIconButton.renderFirstTooltip(gfx, font, mouseX, mouseY, closeButton, relocateButton, swapButton);
    }

    /** Tooltip for the hovered content element (icons and buttons) */
    @Nullable
    private List<Component> contentTooltip(ArithmeticTubeBehaviour tube, double mx, double my) {
        if (my < viewportY() || my >= viewportY() + viewportH) return null;
        if (inOperatorButton(mx, my)) return
                tr("tooltip.operator", ChatFormatting.WHITE);
        for (int k = 0; k < rows.size(); k++) {
            Row row = rows.get(k);
            int y = rowY(k), x = entryX();
            if (inRect(mx, my, x, y, SLOT, SLOT)) return slotTooltip(row);
            int bgX = x + SLOT + SLOT_GAP, bgW = entryW() - SLOT - SLOT_GAP;
            if (row.kind() == RowKind.INPUT || row.kind() == RowKind.LOOP) {   // both carry a delete button at the same spot
                int delX = bgX + bgW - 1 - ROW_BTN;
                if (inRect(mx, my, delX, y + 1, ROW_BTN, ROW_BTN))
                    return tr("tooltip.remove", ChatFormatting.WHITE);
                if (row.kind() == RowKind.INPUT) {
                    int boxX = bgX + 1, boxW = delX - 2 - boxX;
                    if (inRect(mx, my, boxX, y + 1, boxW, 18)) {
                        if (row.input() instanceof ArithmeticTubeBehaviour.ConstantInput)
                            return tr("tooltip.click_to_edit", ChatFormatting.GRAY);
                        if (row.input() instanceof ArithmeticTubeBehaviour.ConnectionInput)
                            return tr("tooltip.connection_value", ChatFormatting.GRAY);
                    }
                }
            } else {
                int b1X = addButtonX(bgX, 0), b2X = addButtonX(bgX, 1), b3X = addButtonX(bgX, 2);
                if (inRect(mx, my, b1X, y + 1, ROW_BTN, ROW_BTN))
                    return List.of(CreateLang.translate("gui.factory_panel.connect_input").component());
                if (inRect(mx, my, b2X, y + 1, ROW_BTN, ROW_BTN))
                    return tr("tooltip.add_constant", ChatFormatting.WHITE);
                if (showLoopAdd(tube, row.primary()) && inRect(mx, my, b3X, y + 1, ROW_BTN, ROW_BTN))
                    return List.of(Component.translatable("createfactorycontroller.gui.arithmetic_tube.loop_input"));
            }
        }
        if (inRect(mx, my, entryX(), resultEntryY(), RESULT_ICON_SIZE, RESULT_ICON_SIZE))
            return tr("tooltip.result", ChatFormatting.YELLOW);
        return null;
    }

    /** Tooltip for an operand icon slot. */
    @Nullable
    private List<Component> slotTooltip(Row row) {
        if (row.kind() == RowKind.ADD)
            return tr("tooltip.new_input", row.primary ? ChatFormatting.BLUE : ChatFormatting.RED);
        if (row.kind() == RowKind.LOOP)
            return List.of(Component.translatable("createfactorycontroller.gui.arithmetic_tube.loop_input"));
        if (row.input() instanceof ArithmeticTubeBehaviour.ConstantInput)
            return tr("tooltip.constant", ChatFormatting.GRAY);
        if (row.input() instanceof ArithmeticTubeBehaviour.ConnectionInput w) {
            var comp = menu.componentAt(w.source());
            if (comp != null) {
                List<Component> tip = new ArrayList<>();
                tip.add(comp.getName().copy().withColor(comp.getColor()));
                tip.addAll(comp.infoTooltip());
                return tip;
            }
        }
        return null;
    }

    private static List<Component> tr(String key, ChatFormatting color, Component... extra) {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.translatable("createfactorycontroller.arithmetic_tube." + key).withStyle(color));
        tooltip.addAll(List.of(extra));
        return tooltip;
    }

    private List<Component> operatorTooltip(ArithmeticOperator op) {
        List<Component> tip = new ArrayList<>();
        tip.add(op.displayName().copy().withStyle(ChatFormatting.WHITE));
        var inputs = Component.translatable(
                "createfactorycontroller.arithmetic_tube.operator_inputs." + op.arity().name().toLowerCase())
                .withStyle(ChatFormatting.GRAY);
        inputs.append(Component.literal(" ■").withStyle(ChatFormatting.BLUE));
        if (op.arity() == OperatorArity.BINARY)
            inputs.append(Component.literal("■").withStyle(ChatFormatting.RED));
        else if (op.arity() == OperatorArity.NARY)
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
        return mx >= x && mx < x + w && my >= y && my < y + h;
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean inViewport = mouseY >= viewportY() && mouseY < viewportY() + viewportH;
        if (button == 0 && inViewport && inOperatorButton(mouseX, mouseY)) operatorHeld = true;
        if (dropdownOpen) {
            int idx = dropdownButtonAt(mouseX, mouseY);
            if (idx >= 0) {
                ArithmeticOperator op = OPERATORS[idx];
                ArithmeticTubeBehaviour tube = tube();
                if (tube != null && tube.canSwitchTo(op)) {
                    PacketDistributor.sendToServer(new ConfigureArithmeticTubePacket(menu.controllerPos, tubePos, op.id()));
                    playClickSound();
                    dropdownOpen = false;
                }
                return true;
            }
            if (inDropdown(mouseX, mouseY)) return true;
            dropdownOpen = false;
            return true;
        }
        if (button == 0 && overScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            int thumbY = scrollbarThumbY(), thumbH = scrollbarThumbHeight();
            boolean onThumb = mouseY >= thumbY && mouseY < thumbY + thumbH;
            scrollbarGrabOffset = onThumb ? mouseY - thumbY : thumbH / 2.0;
            if (!onThumb) dragScrollbarTo(mouseY);
            return true;
        }
        // constant editor
        if (constantBox != null) {
            if (constantBox.isMouseOver(mouseX, mouseY)) {
                if (button == 1) { constantBox.setValue(""); return true; }
                return super.mouseClicked(mouseX, mouseY, button);
            }
            commitConstant();
        }

        if (inViewport) {
            for (int k = 0; k < rows.size(); k++)
                if (handleRowClick(rows.get(k), rowY(k), mouseX, mouseY, button)) return true;
            if (inOperatorButton(mouseX, mouseY)) { dropdownOpen = true; playClickSound(); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleRowClick(Row row, int y, double mx, double my, int button) {
        int x = entryX();
        if (row.kind() == RowKind.LOOP) {   // read-only feedback row: only its delete button acts (turns the loop off)
            int bgX = x + SLOT + SLOT_GAP, bgW = entryW() - SLOT - SLOT_GAP;
            int delX = bgX + bgW - 1 - ROW_BTN, delY = y + 1;
            if (inRect(mx, my, delX, delY, ROW_BTN, ROW_BTN)) {
                sendInput(ConfigureArithmeticInputPacket.LOOP, true, -1, 0);
                playClickSound();
                return true;
            }
            return inRect(mx, my, x, y, entryW(), INPUT_H);
        }
        if (row.kind() == RowKind.INPUT) {
            int bgX = x + SLOT + SLOT_GAP, bgW = entryW() - SLOT - SLOT_GAP;
            int delX = bgX + bgW - 1 - ROW_BTN, delY = y + 1;
            if (inRect(mx, my, delX, delY, ROW_BTN, ROW_BTN)) {
                sendInput(ConfigureArithmeticInputPacket.REMOVE, row.primary(), row.index(), 0);
                playClickSound();
                return true;
            }
            if (row.input() instanceof ArithmeticTubeBehaviour.ConstantInput c) {
                int boxX = bgX + 1, boxW = delX - 2 - boxX;
                if (inRect(mx, my, boxX, y + 1, boxW, 18)) {
                    startEdit(row, button == 1 ? 0 : c.value(), boxW - 9);
                    playClickSound();
                    return true;
                }
            }
            return inRect(mx, my, x, y, entryW(), INPUT_H);
        }
        int bgX = x + SLOT + SLOT_GAP;
        int b1X = addButtonX(bgX, 0), b2X = addButtonX(bgX, 1), b3X = addButtonX(bgX, 2), bY = y + 1;
        if (inRect(mx, my, b1X, bY, ROW_BTN, ROW_BTN)) {
            sendInput(ConfigureArithmeticInputPacket.PREPARE_WIRE, row.primary(), -1, 0);
            controller.beginConnectionMode(tubePos);
            Minecraft.getInstance().setScreen(controller);
            return true;
        }
        ArithmeticTubeBehaviour t = tube();
        if (inRect(mx, my, b2X, bY, ROW_BTN, ROW_BTN)) {
            if (t != null && !t.hasConstant(row.primary())) {
                sendInput(ConfigureArithmeticInputPacket.ADD_CONSTANT, row.primary(), -1, 0);
                playClickSound();
            }
            return true;   // swallow even when disabled
        }
        if (t != null && showLoopAdd(t, row.primary()) && inRect(mx, my, b3X, bY, ROW_BTN, ROW_BTN)) {   // add self-loop input — only one per tube
            if (!t.hasLoopInput()) { sendInput(ConfigureArithmeticInputPacket.LOOP, true, -1, 1); playClickSound(); }
            return true;   // swallow even when disabled
        }
        return false;
    }

    private void sendInput(int op, boolean primary, int index, double value) {
        PacketDistributor.sendToServer(new ConfigureArithmeticInputPacket(menu.controllerPos, tubePos, op, primary, index, value));
    }

    private void startEdit(Row row, double value, int width) {
        commitConstant();   // commit any prior edit
        editPrimary = row.primary();
        editIndex = row.index();
        constantBox = new EditBox(font, 0, 0, Math.max(10, width), font.lineHeight, Component.empty());
        constantBox.setBordered(false);
        constantBox.setTextColor(VALUE_COLOR);
        constantBox.setMaxLength(12);
        constantBox.setFilter(s -> s.matches("-?\\d*\\.?\\d*"));
        constantBox.setValue(value == 0 ? "" : NumberFormatter.format(value));
        addWidget(constantBox);
        setFocused(constantBox);
        constantBox.setFocused(true);
    }

    private void commitConstant() {
        if (constantBox == null) return;
        String s = constantBox.getValue();
        double value;
        try { value = s.isEmpty() || s.equals("-") || s.equals(".") ? 0 : Double.parseDouble(s); }
        catch (NumberFormatException e) { value = 0; }
        sendInput(ConfigureArithmeticInputPacket.SET_CONSTANT, editPrimary, editIndex, value);
        commitPrimary = editPrimary;
        commitIndex = editIndex;
        commitValue = value;
        removeConstantBox();
    }

    private void removeConstantBox() {
        if (constantBox == null) return;
        removeWidget(constantBox);
        constantBox = null;
        setFocused(null);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (constantBox != null) {
            if (keyCode == 257 || keyCode == 335) { commitConstant(); return true; }
            if (keyCode == 256) { removeConstantBox(); return true; }
            // other input fall back to super input box to handle
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.25f));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        operatorHeld = false;
        if (button == 0 && draggingScrollbar) { draggingScrollbar = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && draggingScrollbar) { dragScrollbarTo(mouseY); return true; }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            double target = Mth.clamp(scroll.getChaseTarget() - scrollY * 18, 0, maxScroll());
            scroll.chase(target, 0.5, Chaser.EXP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        commitConstant();
        Minecraft.getInstance().setScreen(controller);
    }

    @Override
    public void removed() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_CLOSE.get(), 1f));
        super.removed();
    }
}
