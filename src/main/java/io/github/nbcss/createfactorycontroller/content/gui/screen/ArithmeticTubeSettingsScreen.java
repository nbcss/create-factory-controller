package io.github.nbcss.createfactorycontroller.content.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.arithmetic.ArithmeticTubeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.arithmetic.ArithmeticTubeBehaviour.Comparison;
import io.github.nbcss.createfactorycontroller.content.component.arithmetic.ArithmeticTubeBehaviour.RedstoneMode;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.arithmetic.ArithmeticOperator;
import io.github.nbcss.createfactorycontroller.content.component.arithmetic.BuiltinOperator;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.gui.widget.InteractiveAreaWidget;
import io.github.nbcss.createfactorycontroller.content.gui.widget.TooltipIconButton;
import io.github.nbcss.createfactorycontroller.content.gui.widget.VerticalScrollView;
import io.github.nbcss.createfactorycontroller.content.helper.NumberFormatter;
import io.github.nbcss.createfactorycontroller.content.helper.Rect2i;
import io.github.nbcss.createfactorycontroller.content.helper.TooltipBuilder;
import io.github.nbcss.createfactorycontroller.content.packet.ConfigureArithmeticInputPacket;
import io.github.nbcss.createfactorycontroller.content.packet.ConfigureArithmeticTubePacket;
import io.github.nbcss.createfactorycontroller.content.render.BatchedBlitter;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.anti_ad.mc.ipn.api.IPNIgnore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.github.nbcss.createfactorycontroller.content.helper.Rect2i.Boundary.HALF_OPEN;

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
        ResourceLocation REDSTONE_INPUT_ICON_ON = resource("arithmetic_tube/redstone_input_icon_on");
        ResourceLocation REDSTONE_INPUT_ICON_OFF = resource("arithmetic_tube/redstone_input_icon_off");
        ResourceLocation REDSTONE_OUTPUT_ICON_ON = resource("arithmetic_tube/redstone_output_icon_on");
        ResourceLocation REDSTONE_OUTPUT_ICON_OFF = resource("arithmetic_tube/redstone_output_icon_off");
        ResourceLocation REDSTONE_SELECTOR_BG = resource("arithmetic_tube/redstone_entry_selector_background");
        ResourceLocation REDSTONE_SELECTOR = resource("arithmetic_tube/redstone_entry_selector");
        ResourceLocation REDSTONE_VALUE_BG = resource("arithmetic_tube/redstone_entry_value_background");
        ResourceLocation REDSTONE_VALUE_DISABLED = resource("arithmetic_tube/redstone_entry_value_disabled");
        ResourceLocation REDSTONE_VALUE_INPUT = resource("arithmetic_tube/redstone_entry_value_input");
        ResourceLocation OPERATOR_DROPDOWN_ICON = resource("arithmetic_tube/operator_dropdown_icon");
        ResourceLocation CONSTANT_ICON = resource("icons/constant");
        ResourceLocation ADD_CONSTANT_ICON = resource("icons/add_constant");
        ResourceLocation OPERATOR_PATH = resource("arithmetic_tube/operators/");

        private static ResourceLocation resource(String path) {
            return ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, path);
        }
    }

    private static final int PANEL_W = 184;
    private static final int HEADER_H = 16;
    private static final int BOTTOM_H = 30;
    private static final int BOTTOM_CLOSE_GROUP_W = 32;
    private static final int POINTER_W = 11, POINTER_H = 18;

    private static final int SIDE_PAD = 10;
    private static final int TOP_PAD = 5, ENTRY_GAP = 4, BOTTOM_PAD = 5;
    private static final int OP_H = 21;
    private static final int OP_SLOT_W = 19;
    private static final int OP_ICON = 15;
    private static final int RESULT_H = 20, RESULT_ICON_SIZE = 20, RESULT_GAP = 2;
    private static final int INPUT_H = 20, SLOT = 20, SLOT_GAP = 2, INPUT_ROW_GAP = 4, ROW_BTN = 18;
    private static final int ICON16 = 16;

    private static final int DD_INSET = 1;
    private static final int DD_BTN = 17;
    private static final int DD_COLS = 8, DD_ROWS = 2;      // 16 operators atm
    private static final int DD_TOP_PAD = 3, DD_BOTTOM_PAD = 5;

    private static final int OP_BORDER_COLOR = 0xFF1A1A1A;
    private static final int OP_ICON_COLOR = 0xFFEBEBEB;
    private static final int INPUT_VALUE_COLOR = 0xFFE2E2E2;
    private static final int CONSTANT_VALUE_COLOR = 0xFFF2F2F2;
    private static final int RESULT_POWERED_COLOR = 0xFFFC8068;
    private static final int NAME_COLOR = 0xF7DFF5;
    private static final int TITLE_COLOR = 0xF9DFFA;

    private static final BuiltinOperator[] OPERATORS = BuiltinOperator.values();

    private final FactoryControllerScreen controller;
    private final VirtualComponentPosition tubePos;

    private int panelX, panelY, panelH;
    private int viewportH;
    private TooltipIconButton closeButton, relocateButton, swapButton, addConnectionButton, addConstantButton;
    private VerticalScrollView scrollView;
    private ArithmeticScrollContent scrollContent;
    private OperatorDropdownWidget operatorDropdown;
    private ConstantDropdownWidget constantDropdown;

    /** The input rows (primary inputs, primary-add, secondary input/add) */
    private List<Row> rows = List.of();
    private final ConstantEditor constantEditor = new ConstantEditor();

    /**
     * A row in the scrollable body. Each row owns its own layout, rendering, and interaction (render / click / scroll /
     * tooltip) so hit-testing and rendering share one geometry source — the screen never recomputes a row's regions.
     * {@code y} is the row's top; {@code mouseX/mouseY} are {@link Integer#MIN_VALUE} when the row isn't hovered.
     */
    private sealed interface Row permits InputRow, MissingInputRow, RedstoneInputRow, ResultRow, RedstoneOutputRow {
        boolean primary();

        void render(GuiGraphics gfx, ArithmeticTubeBehaviour tube, int y, int mouseX, int mouseY);

        default boolean clicked(ArithmeticTubeBehaviour tube, int y, double mx, double my, int button) { return false; }

        default boolean scrolled(ArithmeticTubeBehaviour tube, int y, double mx, double my, double scrollY) { return false; }

        @Nullable
        default List<FormattedCharSequence> tooltip(ArithmeticTubeBehaviour tube, int y, double mx, double my) { return null; }
    }

    /**
     * The single redstone-control row (present iff the tube has an incoming REDSTONE edge). Unlike the item rows it
     * owns its own sub-regions (icon | mode selector | value field) and handles its own render / scroll / click /
     * tooltip, so the geometry lives in exactly one place instead of being recomputed by both the renderer and a
     * {@code contentTargetAt} hit-test.
     */
    private final class RedstoneInputRow implements Row {
        @Override public boolean primary() { return false; }

        @Override
        public void render(GuiGraphics gfx, ArithmeticTubeBehaviour tube, int y, int mouseX, int mouseY) {
            boolean powered = tube.anyRedstonePowered();
            boolean hold = tube.getRedstoneMode() == RedstoneMode.HOLD;
            int boxY = y + 1, boxH = 18;

            // signal icon
            SplitRowBounds bounds = splitRowBounds(y);
            Rect2i icon = bounds.icon();
            BatchedBlitter.forSprite(powered ? SpriteLocations.REDSTONE_INPUT_ICON_ON : SpriteLocations.REDSTONE_INPUT_ICON_OFF)
                    .blit(gfx.bufferSource(), gfx.pose(), icon.x(), icon.y(), icon.w(), icon.h());

            Rect2i mode = bounds.mode();
            TiledSpriteRenderer.create(SpriteLocations.REDSTONE_SELECTOR_BG).render(gfx, mode.x(), mode.y(), mode.w(), mode.h());
            TiledSpriteRenderer.create(SpriteLocations.REDSTONE_SELECTOR).render(gfx, mode.x() + 1, boxY, mode.w() - 2, boxH);
            Component modeLabel = Component.translatable(hold
                    ? "createfactorycontroller.arithmetic_tube.redstone_input.hold"
                    : "createfactorycontroller.arithmetic_tube.redstone_input.override");
            gfx.drawString(font, modeLabel, mode.x() + 8, boxY + (boxH - font.lineHeight) / 2 + 1,
                    0xFFFFFF, false);

            // right half: value field — background + (disabled overlay in HOLD | input box + value in OVERRIDE)
            Rect2i value = bounds.value();
            TiledSpriteRenderer.create(SpriteLocations.REDSTONE_VALUE_BG).render(gfx, value.x(), value.y(), value.w(), value.h());
            if (hold) {
                TiledSpriteRenderer.create(SpriteLocations.REDSTONE_VALUE_DISABLED).render(gfx, value.x() + 1, boxY, value.w() - 2, boxH);
            } else {
                TiledSpriteRenderer.create(SpriteLocations.REDSTONE_VALUE_INPUT).render(gfx, value.x() + 1, boxY, value.w() - 2, boxH);
                int textX = value.x() + 6, textY = boxY + (boxH - font.lineHeight) / 2 + 1;
                if (constantEditor.isEditing(ConstantEditor.RedstoneField.OVERRIDE)) {
                    constantEditor.position(textX, textY, value.w() - 12);
                } else {
                    double v = constantEditor.optimisticRedstone(ConstantEditor.RedstoneField.OVERRIDE, tube.getOverrideValue());
                    gfx.drawString(font, NumberFormatter.format(v), textX, textY, CONSTANT_VALUE_COLOR,
                            value.contains(mouseX, mouseY, HALF_OPEN));
                }
            }
        }

        /** Scroll over the mode selector cycles OVERRIDE ⇄ HOLD (handled before the panel scroll). Two states, so the
         *  scroll direction doesn't matter. */
        @Override
        public boolean scrolled(ArithmeticTubeBehaviour tube, int y, double mx, double my, double scrollY) {
            if (!splitRowBounds(y).mode().contains(mx, my, HALF_OPEN)) return false;
            if (constantEditor.active()) constantEditor.commit();
            RedstoneMode next = tube.getRedstoneMode() == RedstoneMode.OVERRIDE ? RedstoneMode.HOLD : RedstoneMode.OVERRIDE;
            sendInput(ConfigureArithmeticInputPacket.SET_REDSTONE_MODE, false, -1, next.ordinal());
            playClickSound();
            return true;
        }

        /** Handles a click on any of the row's regions. Value field (OVERRIDE) starts the editor; mode is scroll-only;
         *  the icon is display-only. Returns whether the click landed on the row. */
        @Override
        public boolean clicked(ArithmeticTubeBehaviour tube, int y, double mx, double my, int button) {
            SplitRowBounds bounds = splitRowBounds(y);
            if (bounds.value().contains(mx, my, HALF_OPEN)) {
                if (tube.getRedstoneMode() == RedstoneMode.OVERRIDE) {
                    constantEditor.setEditingRedstone(bounds.value().w() - 12, ConstantEditor.RedstoneField.OVERRIDE);
                    constantEditor.clearOnSecondary(button);
                } else if (constantEditor.active()) constantEditor.commit();
                return true;
            }
            if (bounds.mode().contains(mx, my, HALF_OPEN)) {   // left/right click both toggle (two modes)
                if (constantEditor.active()) constantEditor.commit();
                RedstoneMode next = tube.getRedstoneMode() == RedstoneMode.OVERRIDE ? RedstoneMode.HOLD : RedstoneMode.OVERRIDE;
                sendInput(ConfigureArithmeticInputPacket.SET_REDSTONE_MODE, false, -1, next.ordinal());
                playClickSound();
                return true;
            }
            if (bounds.icon().contains(mx, my, HALF_OPEN)) {
                if (constantEditor.active()) constantEditor.commit();
                return true;
            }
            return false;
        }

        @Override
        @Nullable
        public List<FormattedCharSequence> tooltip(ArithmeticTubeBehaviour tube, int y, double mx, double my) {
            SplitRowBounds bounds = splitRowBounds(y);
            if (bounds.icon().contains(mx, my, HALF_OPEN)) return tr("tooltip.redstone_input");
            if (bounds.mode().contains(mx, my, HALF_OPEN)) {
                var mode = tube.getRedstoneMode();
                return TooltipBuilder.of(font)
                        .line(atComponent("redstone_input.mode_header").withColor(ScrollInput.HEADER_RGB.getRGB()))
                        .selector(atComponent("redstone_input.override"), mode == RedstoneMode.OVERRIDE)
                        .selector(atComponent("redstone_input.hold"), mode == RedstoneMode.HOLD)
                        .wrapped(atComponent(mode == RedstoneMode.OVERRIDE
                                ? "redstone_input.override.desc" : "redstone_input.hold.desc")
                                .withStyle(Style.EMPTY.withColor(TooltipBuilder.SELECTOR_DESCRIPTION_COLOR)))
                        .line(CreateLang.translate("gui.scrollInput.scrollToSelect")
                                .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component())
                        .build();
            }
            return null;
        }
    }

    /** An operand row: a wire/constant input, with a value box and a delete button. */
    private final class InputRow implements Row {
        private final boolean primary;
        private final int index;
        private final ArithmeticTubeBehaviour.NumberInput input;

        InputRow(boolean primary, int index, ArithmeticTubeBehaviour.NumberInput input) {
            this.primary = primary;
            this.index = index;
            this.input = input;
        }

        @Override public boolean primary() { return primary; }
        int index() { return index; }
        ArithmeticTubeBehaviour.NumberInput input() { return input; }

        private Rect2i deleteBounds(int y) {
            Rect2i bg = rowBgBounds(y);
            return Rect2i.fromXYWH(bg.maxX() - 1 - ROW_BTN, y + 1, ROW_BTN, ROW_BTN);
        }
        private Rect2i boxBounds(int y) {
            Rect2i bg = rowBgBounds(y), delete = deleteBounds(y);
            return Rect2i.fromXYWH(bg.x() + 1, y + 1, delete.x() - 2 - (bg.x() + 1), 18);
        }

        @Override
        public void render(GuiGraphics gfx, ArithmeticTubeBehaviour tube, int y, int mouseX, int mouseY) {
            Rect2i icon = slotBounds(y);
            renderSlot(gfx, primary, icon.x(), icon.y());
            // slot content: a constant icon, or the connected component's item
            if (input instanceof ArithmeticTubeBehaviour.ConstantInput)
                BatchedBlitter.forSprite(SpriteLocations.CONSTANT_ICON).blit(gfx.bufferSource(), gfx.pose(), icon.x() + 2, icon.y() + 2, ICON16, ICON16);
            else if (input instanceof ArithmeticTubeBehaviour.ConnectionInput w) {
                var comp = menu.componentAt(w.source());
                if (comp != null) gfx.renderItem(new ItemStack(comp.getItem()), icon.x() + 2, icon.y() + 2);
            }

            Rect2i bg = rowBgBounds(y);
            TiledSpriteRenderer.create(SpriteLocations.ENTRY_BG).render(gfx, bg.x(), bg.y(), bg.w(), bg.h());

            Rect2i delete = deleteBounds(y);   // delete button: right, 1px margin
            boolean removeHovered = delete.contains(mouseX, mouseY, HALF_OPEN);
            TiledSpriteRenderer.create(removeHovered ? SpriteLocations.BTN_HOVER : SpriteLocations.BTN_NORMAL)
                    .render(gfx, delete.x(), delete.y(), delete.w(), delete.h());
            AllIcons.I_TRASH.render(gfx, delete.x() + (ROW_BTN - ICON16) / 2, delete.y() + (ROW_BTN - ICON16) / 2);

            Rect2i box = boxBounds(y);
            boolean constant = input instanceof ArithmeticTubeBehaviour.ConstantInput;
            TiledSpriteRenderer.create(constant ? SpriteLocations.CONSTANT_INPUT_FIELD : SpriteLocations.CONN_VALUE_BOX)
                    .render(gfx, box.x(), box.y(), box.w(), box.h());
            int textX = box.x() + 6, textY = box.y() + (box.h() - font.lineHeight) / 2 + 2;
            if (constantEditor.isEditing(this)) {
                constantEditor.position(textX, textY, box.w() - 9);
            } else if (constant) {
                boolean fieldHovered = box.contains(mouseX, mouseY, HALF_OPEN);
                double value = input.getValue(tube);
                value = constantEditor.optimisticValue(this, value).orElse(value);
                gfx.drawString(font, SpecialConstant.displayValue(value), textX, textY, CONSTANT_VALUE_COLOR, fieldHovered);
            } else {
                gfx.drawString(font, NumberFormatter.format(input.getValue(tube)), textX, textY, INPUT_VALUE_COLOR, false);
            }
        }

        @Override
        public boolean clicked(ArithmeticTubeBehaviour tube, int y, double mx, double my, int button) {
            if (deleteBounds(y).contains(mx, my, HALF_OPEN)) {
                sendInput(ConfigureArithmeticInputPacket.REMOVE, primary, index, 0);
                playClickSound();
                return true;
            }
            Rect2i box = boxBounds(y);
            if (input instanceof ArithmeticTubeBehaviour.ConstantInput && box.contains(mx, my, HALF_OPEN)) {
                constantEditor.start(this, box.w() - 9);
                constantEditor.clearOnSecondary(button);
                return true;
            }
            return entryBounds(y).contains(mx, my, HALF_OPEN);   // consume clicks anywhere else on the row
        }

        @Override
        @Nullable
        public List<FormattedCharSequence> tooltip(ArithmeticTubeBehaviour tube, int y, double mx, double my) {
            if (deleteBounds(y).contains(mx, my, HALF_OPEN))
                return tr("tooltip.remove", ChatFormatting.WHITE);
            if (slotBounds(y).contains(mx, my, HALF_OPEN)) return slotTooltip();
            return null;
        }

        @Nullable
        private List<FormattedCharSequence> slotTooltip() {
            if (input instanceof ArithmeticTubeBehaviour.ConstantInput)
                return tr("tooltip.constant", ChatFormatting.WHITE);
            if (input instanceof ArithmeticTubeBehaviour.ConnectionInput(VirtualComponentPosition source)) {
                var comp = menu.componentAt(source);
                if (comp != null)
                    return TooltipBuilder.of(font)
                            .line(comp.getName().copy().withColor(comp.getColor()))
                            .lines(comp.infoTooltip())
                            .build();
            }
            return null;
        }
    }

    /** Placeholder for an operand slot the operator requires but that has no input yet: a blue/red slot plus a
     *  full-width connection value box reading a non-modifiable NaN. Inputs are added from the bottom-bar buttons. */
    private final class MissingInputRow implements Row {
        private final boolean primary;

        MissingInputRow(boolean primary) { this.primary = primary; }

        @Override public boolean primary() { return primary; }

        private Rect2i boxBounds(int y) {
            Rect2i bg = rowBgBounds(y);
            return Rect2i.fromXYWH(bg.x() + 1, y + 1, bg.w() - 2, 18);
        }

        @Override
        public void render(GuiGraphics gfx, ArithmeticTubeBehaviour tube, int y, int mouseX, int mouseY) {
            Rect2i icon = slotBounds(y);
            renderSlot(gfx, primary, icon.x(), icon.y());

            Rect2i bg = rowBgBounds(y), box = boxBounds(y);
            TiledSpriteRenderer.create(SpriteLocations.ENTRY_BG).render(gfx, bg.x(), bg.y(), bg.w(), bg.h());
            TiledSpriteRenderer.create(SpriteLocations.CONN_VALUE_BOX).render(gfx, box.x(), box.y(), box.w(), box.h());
            int textX = box.x() + 6, textY = box.y() + (box.h() - font.lineHeight) / 2 + 2;
            gfx.drawString(font, atComponent("tooltip.no_input"), textX, textY, INPUT_VALUE_COLOR, false);
        }
    }

    /** The numeric-output row: the operator result, display-only. Now a unified {@link Row} (rather than a separate
     *  screen-level entry) so the redstone-output row can sit directly below it through the same render/hit-test loop. */
    private final class ResultRow implements Row {
        @Override public boolean primary() { return false; }

        private Rect2i iconBounds(int y) { return Rect2i.fromXYWH(entryX(), y, RESULT_ICON_SIZE, RESULT_ICON_SIZE); }
        private Rect2i bgBounds(int y) {
            Rect2i icon = iconBounds(y);
            return Rect2i.fromXYWH(icon.maxX() + RESULT_GAP, y, entryW() - RESULT_ICON_SIZE - RESULT_GAP, RESULT_H);
        }
        private Rect2i valueBounds(int y) {
            Rect2i bg = bgBounds(y);
            return Rect2i.fromXYWH(bg.x() + 1, y + 1, bg.w() - 2, 18);
        }

        @Override
        public void render(GuiGraphics gfx, ArithmeticTubeBehaviour tube, int y, int mouseX, int mouseY) {
            Rect2i icon = iconBounds(y);
            BatchedBlitter.forSprite(SpriteLocations.RESULT_ICON).blit(gfx.bufferSource(), gfx.pose(), icon.x(), icon.y(), icon.w(), icon.h());

            Rect2i bg = bgBounds(y);
            TiledSpriteRenderer.create(SpriteLocations.RESULT_BG).render(gfx, bg.x(), bg.y(), bg.w(), bg.h());

            Rect2i value = valueBounds(y);
            TiledSpriteRenderer.create(SpriteLocations.RESULT_VALUE_BOX).render(gfx, value.x(), value.y(), value.w(), value.h());

            int tx = value.x() + 6, ty = value.y() + (value.h() - font.lineHeight) / 2 + 2;
            int color = tube.anyRedstonePowered() ? RESULT_POWERED_COLOR : INPUT_VALUE_COLOR;
            gfx.drawString(font, NumberFormatter.format(tube.getOutput()), tx, ty, color, false);
        }

        @Override
        @Nullable
        public List<FormattedCharSequence> tooltip(ArithmeticTubeBehaviour tube, int y, double mx, double my) {
            if (iconBounds(y).contains(mx, my, HALF_OPEN))
                return tr("tooltip.result");
            return null;
        }
    }

    /**
     * The single redstone-output row (present iff the tube has an outgoing REDSTONE edge). A twin of
     * {@link RedstoneInputRow}: same icon | selector | value layout, but the left half is a comparator selector (scroll
     * cycles the six comparisons, direction-sensitive) and the right half is an always-editable threshold field.
     */
    private final class RedstoneOutputRow implements Row {
        @Override public boolean primary() { return false; }

        @Override
        public void render(GuiGraphics gfx, ArithmeticTubeBehaviour tube, int y, int mouseX, int mouseY) {
            boolean powered = tube.redstoneOutputPowered();
            int boxY = y + 1, boxH = 18;

            // output signal icon (20x20, lit iff the tube currently sources POWERED)
            SplitRowBounds bounds = splitRowBounds(y);
            Rect2i icon = bounds.icon();
            BatchedBlitter.forSprite(powered ? SpriteLocations.REDSTONE_OUTPUT_ICON_ON : SpriteLocations.REDSTONE_OUTPUT_ICON_OFF)
                    .blit(gfx.bufferSource(), gfx.pose(), icon.x(), icon.y(), icon.w(), icon.h());

            // left half: comparator selector — recessed background + selector element + centered comparison glyph
            Rect2i mode = bounds.mode();
            TiledSpriteRenderer.create(SpriteLocations.REDSTONE_SELECTOR_BG).render(gfx, mode.x(), mode.y(), mode.w(), mode.h());
            TiledSpriteRenderer.create(SpriteLocations.REDSTONE_SELECTOR).render(gfx, mode.x() + 1, boxY, mode.w() - 2, boxH);
            String glyph = tube.getOutputComparison().symbol();
            gfx.drawString(font, glyph, mode.x() + 8, boxY + (boxH - font.lineHeight) / 2 + 1, 0xFFFFFF, false);

            // right half: threshold value — background + editable input box (no HOLD-style disabled state)
            Rect2i value = bounds.value();
            TiledSpriteRenderer.create(SpriteLocations.REDSTONE_VALUE_BG).render(gfx, value.x(), value.y(), value.w(), value.h());
            TiledSpriteRenderer.create(SpriteLocations.REDSTONE_VALUE_INPUT).render(gfx, value.x() + 1, boxY, value.w() - 2, boxH);
            int textX = value.x() + 6, textY = boxY + (boxH - font.lineHeight) / 2 + 1;
            if (constantEditor.isEditing(ConstantEditor.RedstoneField.THRESHOLD)) {
                constantEditor.position(textX, textY, value.w() - 12);
            } else {
                double v = constantEditor.optimisticRedstone(ConstantEditor.RedstoneField.THRESHOLD, tube.getOutputThreshold());
                gfx.drawString(font, NumberFormatter.format(v), textX, textY, CONSTANT_VALUE_COLOR,
                        value.contains(mouseX, mouseY, HALF_OPEN));
            }
        }

        /** Scroll over the comparator selector cycles the six comparisons (down → next, matching the left-to-right
         *  tooltip order), before the panel scroll. */
        @Override
        public boolean scrolled(ArithmeticTubeBehaviour tube, int y, double mx, double my, double scrollY) {
            if (!splitRowBounds(y).mode().contains(mx, my, HALF_OPEN)) return false;
            if (constantEditor.active()) constantEditor.commit();
            Comparison[] all = Comparison.values();
            int step = scrollY > 0 ? -1 : 1;
            Comparison next = all[Math.floorMod(tube.getOutputComparison().ordinal() + step, all.length)];
            sendInput(ConfigureArithmeticInputPacket.SET_OUTPUT_COMPARISON, false, -1, next.ordinal());
            playClickSound();
            return true;
        }

        @Override
        public boolean clicked(ArithmeticTubeBehaviour tube, int y, double mx, double my, int button) {
            SplitRowBounds bounds = splitRowBounds(y);
            if (bounds.value().contains(mx, my, HALF_OPEN)) {
                constantEditor.setEditingRedstone(bounds.value().w() - 12, ConstantEditor.RedstoneField.THRESHOLD);
                constantEditor.clearOnSecondary(button);
                return true;
            }
            if (bounds.mode().contains(mx, my, HALF_OPEN)) {   // left click advances, right click reverts
                if (constantEditor.active()) constantEditor.commit();
                Comparison[] all = Comparison.values();
                int step = button == 1 ? -1 : 1;
                Comparison next = all[Math.floorMod(tube.getOutputComparison().ordinal() + step, all.length)];
                sendInput(ConfigureArithmeticInputPacket.SET_OUTPUT_COMPARISON, false, -1, next.ordinal());
                playClickSound();
                return true;
            }
            if (bounds.icon().contains(mx, my, HALF_OPEN)) {
                if (constantEditor.active()) constantEditor.commit();
                return true;
            }
            return false;
        }

        @Override
        @Nullable
        public List<FormattedCharSequence> tooltip(ArithmeticTubeBehaviour tube, int y, double mx, double my) {
            SplitRowBounds bounds = splitRowBounds(y);
            if (bounds.icon().contains(mx, my, HALF_OPEN)) return tr("tooltip.redstone_output");
            if (bounds.mode().contains(mx, my, HALF_OPEN)) return TooltipBuilder.of(font)
                    .line(atComponent("redstone_output.mode_header").withColor(ScrollInput.HEADER_RGB.getRGB()))
                    .line(comparatorLine(tube.getOutputComparison()))
                    .wrapped(atComponent("redstone_output.desc")
                            .withStyle(Style.EMPTY.withColor(TooltipBuilder.SELECTOR_DESCRIPTION_COLOR)))
                    .line(CreateLang.translate("gui.scrollInput.scrollToSelect")
                            .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component())
                    .build();;
            return null;
        }
    }

    /** Non-row content targets (rows handle their own hit-testing). */
    private enum TargetKind { OPERATOR, CONSTANT_MENU }

    private record ContentTarget(TargetKind kind) {}

    private record SplitRowBounds(Rect2i icon, Rect2i bg, Rect2i mode, Rect2i value) {}

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

        addConnectionButton = new TooltipIconButton(0, 0, AllIcons.I_ADD);
        addConnectionButton.withCallback(() -> { controller.beginConnectionMode(tubePos); Minecraft.getInstance().setScreen(controller); });
        addConnectionButton.setToolTip(CreateLang.translate("gui.factory_panel.connect_input").component());

        ScreenElement addConstantIcon = (g, ix, iy) -> g.blitSprite(SpriteLocations.ADD_CONSTANT_ICON, ix, iy, ICON16, ICON16);
        addConstantButton = new TooltipIconButton(0, 0, addConstantIcon);
        addConstantButton.withCallback(() -> { sendInput(ConfigureArithmeticInputPacket.ADD_CONSTANT, true, -1, 0); playClickSound(); });
        addConstantButton.withTooltip(() -> {
            ArithmeticTubeBehaviour t = tube();
            boolean canAdd = t != null && t.canAddConstant();
            TooltipBuilder tip = TooltipBuilder.of(font)
                    .line(Component.translatable("createfactorycontroller.arithmetic_tube.tooltip.add_constant"));
            if (!canAdd)
                tip.line(Component.translatable("createfactorycontroller.arithmetic_tube.tooltip.constant_full")
                        .withStyle(ChatFormatting.RED));
            return tip.build();
        });

        operatorDropdown = new OperatorDropdownWidget();
        constantDropdown = new ConstantDropdownWidget();
        scrollContent = new ArithmeticScrollContent();
        scrollView = new VerticalScrollView(0, 0, 0, 0, scrollContent);
        addWidget(scrollView);
        addWidget(closeButton);
        addWidget(relocateButton);
        addWidget(swapButton);
        addWidget(addConnectionButton);
        addWidget(addConstantButton);

        recomputeLayout();
        scrollView.reset();
    }

    private int rowsHeight(int n) { return n <= 0 ? 0 : n * INPUT_H + (n - 1) * INPUT_ROW_GAP; }

    /** Content height of the stacked body entries (operator + every row, result and redstone rows included); the
     *  dropdown is an overlay. The result is now one of {@code rows}, so it needs no separate term — the equal row
     *  constants (RESULT_H==INPUT_H, ENTRY_GAP==INPUT_ROW_GAP) keep this identical to the pre-unification layout. */
    private int contentHeight() {
        return TOP_PAD + OP_H + ENTRY_GAP + rowsHeight(rows.size()) + BOTTOM_PAD;
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
        swapButton.visible = t != null && t.getOperator().arity() == ArithmeticOperator.Arity.BINARY;
        swapButton.setX(panelX + PANEL_W - BOTTOM_CLOSE_GROUP_W + 1 - 5 - ROW_BTN);
        swapButton.setY(panelY + panelH - 24);
        int addY = panelY + panelH - 24;
        addConnectionButton.setX(panelX + 7 + ROW_BTN + 4);
        addConnectionButton.setY(addY);
        addConstantButton.setX(panelX + 7 + ROW_BTN + 4 + ROW_BTN);
        addConstantButton.setY(addY);
        addConnectionButton.active = true;   // never disabled — a redstone connection is always allowed
        addConstantButton.active = t != null && t.canAddConstant();
        if (scrollView != null) {
            Rect2i viewport = viewportBounds();
            scrollContent.setRectangle(viewport.w(), contentHeight(), viewport.x(), viewport.y());
            scrollView.setRectangle(viewport.w(), viewport.h(), viewport.x(), viewport.y());
        }
        if (operatorDropdown != null)
            operatorDropdown.updateBounds();
        if (constantDropdown != null)
            constantDropdown.updateBounds();
    }

    private List<Row> buildRows(ArithmeticTubeBehaviour tube) {
        List<Row> list = new ArrayList<>();
        List<ArithmeticTubeBehaviour.NumberInput> prim = tube.getPrimaryInputs();
        ArithmeticOperator.Arity arity = tube.getOperator().arity();
        if (arity == ArithmeticOperator.Arity.BINARY) {
            // exactly two slots — each is a filled input or a "missing input" placeholder
            list.add(prim.isEmpty() ? new MissingInputRow(true) : new InputRow(true, 0, prim.get(0)));
            ArithmeticTubeBehaviour.NumberInput sec = tube.getSecondaryInput();
            list.add(sec != null ? new InputRow(false, -1, sec) : new MissingInputRow(false));
        } else {
            for (int i = 0; i < prim.size(); i++) list.add(new InputRow(true, i, prim.get(i)));
            if (prim.isEmpty()) list.add(new MissingInputRow(true));   // one placeholder when nothing is specified
        }
        if (tube.hasRedstoneInput()) list.add(new RedstoneInputRow());   // sits directly above the result row
        list.add(new ResultRow());                                       // the numeric output — always present
        if (tube.hasRedstoneOutput()) list.add(new RedstoneOutputRow()); // sits directly below the result row
        return list;
    }

    // ── Layout geometry ───────────────────────────────────────────────────────────

    private int entryX() { return panelX + SIDE_PAD; }
    private int entryW() { return PANEL_W - 2 * SIDE_PAD; }
    private Vector2ic entryPosition(int y) { return new Vector2i(entryX(), y); }
    private Rect2i entryBounds(int y) { return Rect2i.fromXYWH(entryPosition(y), new Vector2i(entryW(), INPUT_H)); }
    private Rect2i slotBounds(int y) { return Rect2i.fromXYWH(entryX(), y, SLOT, SLOT); }
    private Rect2i rowBgBounds(int y) { return Rect2i.fromXYWH(entryX() + SLOT + SLOT_GAP, y, entryW() - SLOT - SLOT_GAP, INPUT_H); }
    private SplitRowBounds splitRowBounds(int y) {
        Rect2i icon = slotBounds(y);
        Rect2i bg = rowBgBounds(y);
        Rect2i mode = Rect2i.fromXYWH(bg.x(), bg.y(), bg.w() / 2, bg.h());
        Rect2i value = Rect2i.fromXYWH(mode.maxX() - 1, bg.y(), bg.w() - mode.w() + 1, bg.h());   // 1px overlap so the two panels meet cleanly
        return new SplitRowBounds(icon, bg, mode, value);
    }
    private int viewportY() { return panelY + HEADER_H; }
    private Rect2i viewportBounds() { return Rect2i.fromXYWH(panelX, viewportY(), PANEL_W, viewportH); }
    private int opEntryY() { return viewportY() + TOP_PAD; }
    private Rect2i operatorBounds() { return Rect2i.fromXYWH(entryPosition(opEntryY()), new Vector2i(entryW(), OP_H)); }
    private int inputStartY() { return opEntryY() + OP_H + ENTRY_GAP; }
    private int rowY(int k) { return inputStartY() + k * (INPUT_H + INPUT_ROW_GAP); }

    private Rect2i dropdownBounds() {
        return Rect2i.fromXYWH(
                entryX() + DD_INSET,
                opEntryY() + OP_H,
                entryW() - 2 * DD_INSET,
                DD_TOP_PAD + DD_ROWS * DD_BTN + DD_BOTTOM_PAD
        );
    }
    private Rect2i dropdownButtonBounds(int index) {
        var dropdownBounds = dropdownBounds();
        return Rect2i.fromXYWH(
                dropdownBounds.x() + (dropdownBounds.w() - DD_COLS * DD_BTN) / 2 + index % DD_COLS * DD_BTN,
                dropdownBounds.y() + DD_TOP_PAD + index / DD_COLS * DD_BTN,
                DD_BTN, DD_BTN
        );
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
        RenderSystem.enableBlend();

        scrollView.render(gfx, mouseX, mouseY, partialTick);

        relocateButton.render(gfx, mouseX, mouseY, partialTick);
        addConnectionButton.render(gfx, mouseX, mouseY, partialTick);
        addConstantButton.render(gfx, mouseX, mouseY, partialTick);
        if (tube.getOperator().arity() == ArithmeticOperator.Arity.BINARY) swapButton.render(gfx, mouseX, mouseY, partialTick);
        closeButton.render(gfx, mouseX, mouseY, partialTick);
        GuiGameElement.of(tube.getItem()).scale(2).at(0, 0, 100)
                .render(gfx, panelX + PANEL_W + 13, panelY + panelH - 30);
    }

    private void renderOperatorEntry(GuiGraphics gfx, ArithmeticTubeBehaviour tube, boolean hovered, boolean pressed) {
        Rect2i bounds = operatorBounds();
        ResourceLocation sprite = pressed ? SpriteLocations.OP_BUTTON_PRESSED
                : (hovered ? SpriteLocations.OP_BUTTON_HOVER : SpriteLocations.OP_BUTTON);
        TiledSpriteRenderer.create(sprite).render(gfx, bounds.x(), bounds.y(), bounds.w(), bounds.h());

        ArithmeticOperator op = tube.getOperator();
        int iconX = bounds.x() + (OP_SLOT_W - OP_ICON) / 2 + 1;
        int iconY = bounds.y() + (bounds.h() - OP_ICON) / 2;
        drawOperatorIcon(gfx, op, iconX, iconY, OP_ICON_COLOR, true,
                op.arity() == ArithmeticOperator.Arity.BINARY);

        Component name = op.displayName();
        int nameX = bounds.x() + (bounds.w() - font.width(name)) / 2;
        int nameY = bounds.y() + Math.ceilDiv(bounds.h() - font.lineHeight, 2);
        gfx.drawString(font, name, nameX, nameY, NAME_COLOR, false);
        gfx.blitSprite(SpriteLocations.OPERATOR_DROPDOWN_ICON, bounds.maxX() - 6 - 7, bounds.y() + Math.ceilDiv(bounds.h() - 4, 2), 7, 4);
    }

    // ── Input rows ──────────────────────

    private void renderInputEntries(GuiGraphics gfx, ArithmeticTubeBehaviour tube, int mouseX, int mouseY) {
        for (int k = 0; k < rows.size(); k++)
            rows.get(k).render(gfx, tube, rowY(k), mouseX, mouseY);
    }

    private void renderSlot(GuiGraphics gfx, boolean primary, int x, int y) {
        BatchedBlitter.forSprite(primary ? SpriteLocations.OPERAND_BLUE_SLOT : SpriteLocations.OPERAND_RED_SLOT)
                .blit(gfx.bufferSource(), gfx.pose(), x, y, SLOT, SLOT);
    }

    private void renderDropdown(GuiGraphics gfx, ArithmeticTubeBehaviour tube, int mouseX, int mouseY) {
        Rect2i menu = dropdownBounds();
        TiledSpriteRenderer.create(SpriteLocations.DROPDOWN_BG).render(gfx, menu.x(), menu.y(), menu.w(), menu.h());
        ArithmeticOperator current = tube.getOperator();
        for (int i = 0; i < OPERATORS.length; i++) {
            ArithmeticOperator op = OPERATORS[i];
            Rect2i button = dropdownButtonBounds(i);
            boolean enabled = tube.canSwitchTo(op);
            boolean active = op.name().equals(current.name());
            boolean hover = enabled && button.contains(mouseX, mouseY, HALF_OPEN);
            ResourceLocation state = !enabled ? SpriteLocations.BTN_DISABLED : active ? SpriteLocations.BTN_TOGGLED : hover ? SpriteLocations.BTN_HOVER : SpriteLocations.BTN_NORMAL;
            TiledSpriteRenderer.create(state).render(gfx, button.x(), button.y(), button.w(), button.h());
            drawOperatorIcon(gfx, op, button.x() + (button.w() - OP_ICON) / 2, button.y() + (button.h() - OP_ICON) / 2, 0xFFE2E2E2, false, false);
        }
    }

    private void drawOperatorIcon(GuiGraphics gfx, ArithmeticOperator op, int x, int y, int tint,
                                  boolean border, boolean operands) {
        String icon = op.iconName();
        if (border) {
            setColor(gfx, OP_BORDER_COLOR);
            gfx.blitSprite(SpriteLocations.OPERATOR_PATH.withSuffix(icon + "_border"), x, y, OP_ICON, OP_ICON);
        }
        setColor(gfx, tint);
        gfx.blitSprite(SpriteLocations.OPERATOR_PATH.withSuffix(icon), x, y, OP_ICON, OP_ICON);
        gfx.setColor(1f, 1f, 1f, 1f);
        if (operands)
            gfx.blitSprite(SpriteLocations.OPERATOR_PATH.withSuffix(icon + "_operands"), x, y, OP_ICON, OP_ICON);
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
        if (operatorDropdown.isOpen() || constantDropdown.isOpen()) return null;
        if (constantEditor.box != null && constantEditor.showMenuButton()
                && constantEditor.menuButtonBounds().contains(
                        mouseX, mouseY, HALF_OPEN))
            return new ContentTarget(TargetKind.CONSTANT_MENU);
        if (operatorBounds().contains(mouseX, mouseY, HALF_OPEN)) return new ContentTarget(TargetKind.OPERATOR);
        return null;
    }

    @Nullable
    private List<FormattedCharSequence> contentTooltip(@Nullable ContentTarget target) {
        if (target == null) return null;
        return switch (target.kind()) {
            case OPERATOR -> tr("tooltip.operator", ChatFormatting.WHITE);
            default -> null;
        };
    }

    private List<FormattedCharSequence> tr(String key, ChatFormatting... styles) {
        return TooltipBuilder.of(font)
                .line(Component.translatable("createfactorycontroller.arithmetic_tube." + key).withStyle(styles))
                .build();
    }

    private MutableComponent atComponent(String key) {
        return Component.translatable("createfactorycontroller.arithmetic_tube." + key);
    }

    private static Component comparatorLine(Comparison current) {
        MutableComponent line = Component.empty();
        Comparison[] all = Comparison.values();
        for (int i = 0; i < all.length; i++) {
            if (i > 0) line.append(Component.literal(" "));
            Comparison c = all[i];
            boolean selected = c == current;
            line.append(Component.literal(selected ? "[" + c.symbol() + "]" : c.symbol())
                    .withStyle(selected ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }
        return line;
    }

    private List<FormattedCharSequence> operatorTooltip(ArithmeticOperator op) {
        TooltipBuilder tip = TooltipBuilder.of(font)
                .line(op.displayName().copy().withStyle(ChatFormatting.WHITE));
        var inputs = Component.translatable(
                "createfactorycontroller.arithmetic_tube.operator_inputs." + op.arity().name().toLowerCase())
                .withStyle(ChatFormatting.GRAY);
        inputs.append(Component.literal(" ■").withStyle(ChatFormatting.BLUE));
        if (op.arity() == ArithmeticOperator.Arity.BINARY)
            inputs.append(Component.literal("■").withStyle(ChatFormatting.RED));
        else if (op.arity() == ArithmeticOperator.Arity.N_ARY)
            inputs.append(Component.literal("■■").withStyle(ChatFormatting.BLUE));
        tip.line(inputs);
        ArithmeticTubeBehaviour tube = tube();
        if (tube != null && !tube.canSwitchTo(op))
            tip.line(Component.translatable("createfactorycontroller.arithmetic_tube.operator_locked")
                    .withStyle(ChatFormatting.DARK_GRAY));
        return tip.build();
    }

    // ── Interaction ───────────────────────────────────────────────────────────────

    private int dropdownButtonAt(double mx, double my) {
        for (int i = 0; i < OPERATORS.length; i++)
            if (dropdownButtonBounds(i).contains(mx, my, HALF_OPEN)) return i;
        return -1;
    }

    private class ArithmeticScrollContent extends AbstractWidget {
        ArithmeticScrollContent() {
            super(0, 0, 0, 0, Component.empty());
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            ArithmeticTubeBehaviour tube = tube();
            if (tube == null) return;
            ContentTarget hovered = contentTargetAt(mouseX, mouseY);
            boolean contentHovered = hovered != null || scrollView.isHovered()
                    && !operatorDropdown.isOpen() && !constantDropdown.isOpen();
            int hoverX = contentHovered ? mouseX : Integer.MIN_VALUE;
            int hoverY = contentHovered ? mouseY : Integer.MIN_VALUE;
            // Pressed whenever the left mouse button is physically held over the operator button — in any case,
            // whether or not the dropdown is open (read the raw button state so it doesn't depend on a click landing).
            boolean leftDown = GLFW.glfwGetMouseButton(
                    Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            boolean opPressed = leftDown && operatorBounds().contains(mouseX, mouseY, HALF_OPEN);
            renderOperatorEntry(gfx, tube, hovered != null && hovered.kind() == TargetKind.OPERATOR, opPressed);
            renderInputEntries(gfx, tube, hoverX, hoverY);
            RenderSystem.clear(256, Minecraft.ON_OSX); // clear depth buffer for dropdown menu to render atop
            constantEditor.render(gfx, hoverX, hoverY, partialTick,
                    hovered != null && hovered.kind() == TargetKind.CONSTANT_MENU);
            if (scrollView.isHovered()) renderTooltip(mouseX, mouseY);
        }

        @Override
        public void visitWidgets(@NotNull Consumer<AbstractWidget> consumer) {
            consumer.accept(this);
            operatorDropdown.updateBounds();
            consumer.accept(operatorDropdown);
            constantDropdown.updateBounds();
            consumer.accept(constantDropdown);
        }

        private void renderTooltip(int mouseX, int mouseY) {
            if (operatorDropdown.isOpen() || constantDropdown.isOpen()) return;
            ArithmeticTubeBehaviour tube = tube();
            if (tube != null)
                for (int k = 0; k < rows.size(); k++) {
                    List<FormattedCharSequence> rt = rows.get(k).tooltip(tube, rowY(k), mouseX, mouseY);
                    if (rt != null) {
                        setTooltip(rt);
                        return;
                    }
                }
            List<FormattedCharSequence> tooltip = ArithmeticTubeSettingsScreen.this.contentTooltip(contentTargetAt(mouseX, mouseY));
            setTooltip(tooltip == null ? List.of() : tooltip);
        }

        private void setTooltip(List<FormattedCharSequence> lines) {
            Screen screen = Minecraft.getInstance().screen;
            if (screen != null && !lines.isEmpty())
                screen.setTooltipForNextRenderPass(lines, DefaultTooltipPositioner.INSTANCE, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (operatorDropdown.isOpen() || constantDropdown.isOpen()) return false;
            ContentTarget target = contentTargetAt(mouseX, mouseY);
            if (target != null && target.kind() == TargetKind.CONSTANT_MENU && button == 0) {
                constantDropdown.toggle();
                return true;
            }
            if (constantEditor.box != null && constantEditor.box.isMouseOver(mouseX, mouseY)) {
                if (button == 1) {
                    constantEditor.box.setValue("");
                    return true;
                }
                ArithmeticTubeSettingsScreen.this.setFocused(scrollView);
                constantEditor.box.setFocused(true);
                return constantEditor.box.mouseClicked(mouseX, mouseY, button);
            }
            if (constantEditor.active()) constantEditor.commit();
            if (target != null && target.kind() == TargetKind.OPERATOR) {
                operatorDropdown.open();
                return true;
            }
            ArithmeticTubeBehaviour tube = tube();
            if (tube != null)
                for (int k = 0; k < rows.size(); k++)
                    if (rows.get(k).clicked(tube, rowY(k), mouseX, mouseY, button)) return true;
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (operatorDropdown.isOpen() || constantDropdown.isOpen()) return false;
            ArithmeticTubeBehaviour tube = tube();
            if (tube != null)
                for (int k = 0; k < rows.size(); k++)
                    if (rows.get(k).scrolled(tube, rowY(k), mouseX, mouseY, scrollY)) return true;
            return false;
        }

        @Override
        public void setFocused(boolean focused) {
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

        @Override
        protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {}
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

        void updateBounds() {
            Rect2i bounds = dropdownBounds();
            setRectangle(bounds.w(), bounds.h(), bounds.x(), bounds.y());
        }

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
            if (!dropdownBounds().contains(mouseX, mouseY, HALF_OPEN)) close();
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
            if (constantEditor.menuBounds().contains(mouseX, mouseY, HALF_OPEN)) return true;
            if (button == 0 && constantEditor.menuButtonBounds().contains(mouseX, mouseY, HALF_OPEN)) {
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
        int contentMouseY = (int) (mouseY + scrollView.getScroll());
        if (constantEditor.active()
                && !constantEditor.box.isMouseOver(mouseX, contentMouseY)
                && !(constantDropdown.isOpen() && constantEditor.menuBounds().contains(mouseX, contentMouseY, HALF_OPEN))
                && !(button == 0 && constantEditor.showMenuButton()
                        && constantEditor.menuButtonBounds().contains(mouseX, contentMouseY, HALF_OPEN))) {
            constantEditor.commit();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollView != null && scrollView.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
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
        if (scrollView != null) scrollView.tick();
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

        enum RedstoneField { OVERRIDE, THRESHOLD }

        @Nullable private EditBox box;
        private boolean editPrimary;
        private int editIndex = -1;
        /** Which redstone value field the box edits, or {@code null} while it edits a constant slot. */
        @Nullable private RedstoneField redstoneField;

        private static final int NO_COMMIT = -2;

        /** Optimistic post-commit display: show the just-committed value for the slot until the sync catches up (else
         *  the box flashes back to the old value for a tick). {@code commitIndex == NO_COMMIT} disables it. */
        private boolean commitPrimary;
        private int commitIndex = NO_COMMIT;
        private double commitValue;
        /** Optimistic post-commit for a redstone value field ({@code null} = inactive). */
        @Nullable private RedstoneField redstoneCommitField;
        private double redstoneCommitValue;

        public boolean active() { return box != null; }

        public boolean isEditing(InputRow row) {
            return box != null && redstoneField == null && row.primary() == editPrimary && row.index() == editIndex;
        }

        public boolean isEditing(RedstoneField field) {
            return box != null && redstoneField == field;
        }

        public void discardIfRowGone(List<Row> rows) {
            if (box == null) return;
            if (redstoneField == RedstoneField.OVERRIDE) {
                if (rows.stream().noneMatch(row -> row instanceof RedstoneInputRow)) remove();
                return;
            }
            if (redstoneField == RedstoneField.THRESHOLD) {
                if (rows.stream().noneMatch(row -> row instanceof RedstoneOutputRow)) remove();
                return;
            }
            if (rows.stream().noneMatch(row -> row instanceof InputRow input && isEditing(input))) remove();
        }

        /** Right-click on a value field clears it at once, so the first right-click clears (not the second). Call
         *  right after starting/while editing a field. */
        public void clearOnSecondary(int button) {
            if (button == 1 && box != null) box.setValue("");
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
        public OptionalDouble optimisticValue(InputRow row, double currentValue) {
            if (!(row.input() instanceof ArithmeticTubeBehaviour.ConstantInput) || !matchesCommit(row))
                return OptionalDouble.empty();
            if (Double.compare(currentValue, commitValue) == 0) {
                commitIndex = NO_COMMIT;   // the sync caught up
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(commitValue);
        }

        private boolean matchesCommit(InputRow row) {
            return commitIndex != NO_COMMIT &&
                    row.primary() == commitPrimary &&
                    row.index() == commitIndex;
        }

        public void start(InputRow row, int width) {
            commit();   // commit any prior edit
            redstoneField = null;
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

            setFocused(scrollView);
            box.setFocused(true);
            box.setHighlightPos(0);
        }

        /** Start editing a redstone value field (OVERRIDE constant / output THRESHOLD). */
        public void setEditingRedstone(int width, RedstoneField field) {
            commit();
            redstoneField = field;
            editPrimary = false;
            editIndex = -1;

            box = new EditBox(font, 0, 0, Math.max(10, width), font.lineHeight, Component.empty());
            box.setBordered(false);
            box.setTextColor(CONSTANT_VALUE_COLOR);
            box.setMaxLength(MAX_LENGTH);
            box.setFilter(input -> FLOAT_LITERAL_PATTERN.matcher(input).matches());

            ArithmeticTubeBehaviour tube = tube();
            double value = tube == null ? 0
                    : field == RedstoneField.OVERRIDE ? tube.getOverrideValue() : tube.getOutputThreshold();
            box.setValue(NumberFormatter.format(value));

            setFocused(scrollView);
            box.setFocused(true);
            box.setHighlightPos(0);
        }

        public void commit() {
            if (box == null) return;
            if (constantDropdown != null) constantDropdown.close();
            String input = box.getValue();

            if (redstoneField != null) {
                double value;
                try {
                    value = input.isEmpty() ? 0 : Double.parseDouble(input);
                } catch (NumberFormatException e) {
                    value = 0;
                }
                sendInput(redstoneField == RedstoneField.OVERRIDE
                        ? ConfigureArithmeticInputPacket.SET_REDSTONE_VALUE
                        : ConfigureArithmeticInputPacket.SET_OUTPUT_THRESHOLD, false, -1, value);
                redstoneCommitField = redstoneField;
                redstoneCommitValue = value;
                remove();
                return;
            }

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

        /** Optimistic redstone value (per field) until the config sync catches up (avoids a one-tick flash-back). */
        public double optimisticRedstone(RedstoneField field, double currentValue) {
            if (redstoneCommitField != field) return currentValue;
            if (Double.compare(currentValue, redstoneCommitValue) == 0) {
                redstoneCommitField = null;   // the sync caught up
                return currentValue;
            }
            return redstoneCommitValue;
        }

        public void remove() {
            if (box == null) return;
            box = null;
            redstoneField = null;
            if (constantDropdown != null) constantDropdown.close();
            setFocused(null);
        }

        private boolean showMenuButton() {
            return box != null && redstoneField == null && box.getValue().length() <= MAX_LENGTH - 2;
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
                    && menuBounds().contains(mx, my, HALF_OPEN))) return -1;
            Rect2i menu = menuBounds();
            int localX = (int) mx - menu.x() - MENU_PAD;
            int localY = (int) my - menu.y() - MENU_PAD;
            if (localY < 0 || localY >= MENU_ITEM_SIZE.y() || localX < 0) return -1;
            int index = localX / MENU_ITEM_SIZE.x();
            return index < MENU_ITEMS.size() ? index : -1;
        }
    }
}
