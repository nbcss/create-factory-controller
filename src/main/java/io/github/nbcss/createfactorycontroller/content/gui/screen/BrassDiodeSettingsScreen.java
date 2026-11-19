package io.github.nbcss.createfactorycontroller.content.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.redstone.diodes.BrassDiodeBlock;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.redstone.BrassDiodeBehaviour;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.gui.widget.InteractiveAreaWidget;
import io.github.nbcss.createfactorycontroller.content.gui.widget.TooltipIconButton;
import io.github.nbcss.createfactorycontroller.content.gui.widget.VirtualBrassDiodeWidget;
import io.github.nbcss.createfactorycontroller.content.helper.TooltipBuilder;
import io.github.nbcss.createfactorycontroller.content.packet.ConfigureBrassDiodePacket;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.anti_ad.mc.ipn.api.IPNIgnore;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Settings overlay for a brass diode (Pulse Repeater / Extender / Timer): redstone interval as value + unit
 * (Create's ticks / seconds / minutes board) and output inversion. Staged; committed once on close.
 */
@OnlyIn(Dist.CLIENT)
@IPNIgnore
public class BrassDiodeSettingsScreen extends AbstractSimiContainerScreen<FactoryControllerMenu>
        implements PanelSyncListener {

    private static final ResourceLocation PANEL_TEX =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "textures/gui/pulse_diode.png");
    private static final int PANEL_W = 200, PANEL_H = 79;
    private static final int VALUE_X = 40, UNIT_X = 92, BOX_Y = 24, VALUE_W = 46, UNIT_W = 61, BOX_H = 16;
    private static final int BUTTON_Y = 55;
    private static final int TITLE_COLOR = 0x5E3201;

    /** Ticks per unit step, indexed like Create's board rows (ticks, seconds, minutes). */
    private static final int[] UNIT_TICKS = {1, 20, 1200};
    private static final int MAX_VALUE = 60;

    private final FactoryControllerScreen controller;
    private final VirtualComponentPosition diodePos;
    private final int openedInterval;
    private final boolean openedInverted;
    /** {@link #stagedInterval()} right after decomposition; unchanged staged value keeps the stored interval exactly. */
    private final int openedStagedInterval;

    private int stagedValue, stagedUnit;
    private boolean stagedInverted;
    private boolean editing;
    private String editBuffer = "";
    private boolean committed;

    private int panelX, panelY;
    private TooltipIconButton relocateButton, addConnectionButton, invertButton, confirmButton;

    public BrassDiodeSettingsScreen(FactoryControllerScreen controller, VirtualComponentPosition diodePos) {
        super(controller.getMenu(), Minecraft.getInstance().player.getInventory(), Component.empty());
        this.controller = controller;
        this.diodePos = diodePos;
        BrassDiodeBehaviour diode = diode();
        openedInterval = diode != null ? diode.getRedstoneInterval() : BrassDiodeBehaviour.MIN_INTERVAL;
        openedInverted = diode != null && diode.isInverted();
        // Create's BrassDiodeScrollValueBehaviour#getValueSettings
        if (openedInterval > 1200) { stagedUnit = 2; stagedValue = openedInterval / 1200; }
        else if (openedInterval > 60) { stagedUnit = 1; stagedValue = openedInterval / 20; }
        else { stagedUnit = 0; stagedValue = openedInterval; }
        stagedValue = clampValue(stagedValue);
        stagedInverted = openedInverted;
        openedStagedInterval = stagedInterval();
        ScreenSounds.panelOpen();
    }

    private BrassDiodeBehaviour diode() {
        return menu.componentAt(diodePos) instanceof BrassDiodeBehaviour d ? d : null;
    }

    /** A 1-tick interval can't be honoured (the 2-tick input latch is the floor), so Ticks starts at 2. */
    private int minValue() {
        return stagedUnit == 0 ? BrassDiodeBehaviour.MIN_INTERVAL : 1;
    }

    private int clampValue(int v) {
        return Mth.clamp(v, minValue(), MAX_VALUE);
    }

    private int stagedInterval() {
        return Mth.clamp(stagedValue * UNIT_TICKS[stagedUnit],
                BrassDiodeBehaviour.MIN_INTERVAL, BrassDiodeBehaviour.MAX_INTERVAL);
    }

    @Override
    protected void init() {
        setWindowSize(controller.guiWidth(), controller.guiHeight());
        setWindowOffset(0, 0);
        super.init();

        panelX = leftPos + (imageWidth - PANEL_W) / 2;
        panelY = topPos + (imageHeight - PANEL_H) / 2;
        menu.repositionSlots(-2000, -2000, false);

        addRenderableWidget(new InteractiveAreaWidget(panelX + VALUE_X, panelY + BOX_Y, VALUE_W, BOX_H, this::valueTooltip)
            .onClick(button -> {
                if (button != 0 && button != 1) return false;
                editing = true;
                editBuffer = button == 1 ? "" : String.valueOf(stagedValue);
                ScreenSounds.click();
                return true;
            })
            .onScroll((scrollX, scrollY) -> {
                if (editing) commitEdit();
                int step = hasShiftDown() ? 10 : 1;
                stagedValue = clampValue(stagedValue + (int) Math.signum(scrollY) * step);
                ScreenSounds.scroll();
                return true;
            }));

        SelectionScrollInput unitInput = new SelectionScrollInput(panelX + UNIT_X, panelY + BOX_Y, UNIT_W, BOX_H);
        unitInput.forOptions(CreateLang.translatedOptions("generic.unit", "ticks", "seconds", "minutes"));
        unitInput.titled(Component.translatable("createfactorycontroller.gui.diode.unit"));
        unitInput.setState(stagedUnit);
        unitInput.calling(u -> {
            if (editing) commitEdit();
            stagedUnit = u;
            stagedValue = clampValue(stagedValue);
        });
        addRenderableWidget(unitInput);

        relocateButton = new TooltipIconButton(panelX + 8, panelY + BUTTON_Y, AllIcons.I_MOVE_GAUGE);
        relocateButton.withCallback(() -> { controller.beginRelocateMode(diodePos); Minecraft.getInstance().setScreen(controller); });
        relocateButton.setToolTip(Component.translatable("createfactorycontroller.gui.action_relocate"));
        addWidget(relocateButton);

        addConnectionButton = new TooltipIconButton(panelX + 30, panelY + BUTTON_Y, AllIcons.I_ADD);
        addConnectionButton.withCallback(() -> { controller.beginConnectionMode(diodePos); Minecraft.getInstance().setScreen(controller); });
        addConnectionButton.setToolTip(CreateLang.translate("gui.factory_panel.connect_input").component());
        addWidget(addConnectionButton);

        invertButton = new TooltipIconButton(panelX + 138, panelY + BUTTON_Y, AllIcons.I_FLIP);
        invertButton.withCallback(() -> stagedInverted = !stagedInverted);
        invertButton.setToolTip(Component.translatable("createfactorycontroller.gui.diode.invert_output")
                .withStyle(ChatFormatting.WHITE));
        addWidget(invertButton);

        confirmButton = new TooltipIconButton(panelX + 167, panelY + BUTTON_Y, AllIcons.I_CONFIRM);
        confirmButton.withCallback(() -> Minecraft.getInstance().setScreen(controller));
        confirmButton.setToolTip(CreateLang.translate("gui.factory_panel.save_and_close").component());
        addWidget(confirmButton);
    }

    private List<FormattedCharSequence> valueTooltip() {
        return TooltipBuilder.of(font)
                .line(CreateLang.translate("logistics.redstone_interval").color(ScrollInput.HEADER_RGB).component())
                .line(CreateLang.translate("gui.scrollInput.scrollToModify")
                        .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component())
                .line(CreateLang.translate("gui.scrollInput.shiftScrollsFaster")
                        .style(ChatFormatting.DARK_GRAY).style(ChatFormatting.ITALIC).component())
                .build();
    }

    // ── Value typing ─────────────────────────────────────────────────────────

    /** Typing is unclamped (so "1"→"15" works in Ticks); the range applies here. Empty → minimum. */
    private void commitEdit() {
        if (!editing) return;
        int typed = editBuffer.isEmpty() ? 0 : Integer.parseInt(editBuffer);   // ≤ 2 digits, never overflows
        stagedValue = clampValue(typed);
        editing = false;
        editBuffer = "";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editing && !isOverValueBox(mouseX, mouseY)) commitEdit();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isOverValueBox(double mouseX, double mouseY) {
        double x = mouseX - panelX - VALUE_X, y = mouseY - panelY - BOX_Y;
        return x >= 0 && x < VALUE_W && y >= 0 && y < BOX_H;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (editing) {
            if (codePoint >= '0' && codePoint <= '9' && editBuffer.length() < 2) editBuffer += codePoint;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editing) {
            switch (keyCode) {
                case 257, 335, 256 -> commitEdit();   // Enter / numpad Enter / Escape
                case 259 -> { if (!editBuffer.isEmpty()) editBuffer = editBuffer.substring(0, editBuffer.length() - 1); }
                default -> { }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Render ───────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        BrassDiodeBehaviour diode = diode();
        if (diode == null) { Minecraft.getInstance().setScreen(controller); return; }   // removed externally
        controller.renderBoard(gfx, -1, -1, partialTick, true);

        RenderSystem.enableBlend();
        gfx.blit(PANEL_TEX, panelX, panelY, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);

        String valueText = editing
                ? editBuffer + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : "")
                : String.valueOf(stagedValue);
        int textY = panelY + BOX_Y + 4;
        gfx.drawString(font, valueText, panelX + VALUE_X + 4, textY, 0xFFFFFFFF, true);
        String unitText = CreateLang.translate("generic.unit." + UNIT_KEYS[stagedUnit]).string();
        gfx.drawString(font, unitText, panelX + UNIT_X + 4, textY, 0xFFFFFFFF, true);

        renderModel(gfx, diode);

        gfx.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);

        invertButton.green = stagedInverted;
        relocateButton.render(gfx, mouseX, mouseY, partialTick);
        addConnectionButton.render(gfx, mouseX, mouseY, partialTick);
        invertButton.render(gfx, mouseX, mouseY, partialTick);
        confirmButton.render(gfx, mouseX, mouseY, partialTick);
    }

    private static final String[] UNIT_KEYS = {"ticks", "seconds", "minutes"};

    private void renderModel(GuiGraphics gfx, BrassDiodeBehaviour diode) {
        if (!(diode.getItem() instanceof BlockItem item)) return;
        BlockState state = item.getBlock().defaultBlockState();
        if (state.hasProperty(BrassDiodeBlock.INVERTED))
            state = state.setValue(BrassDiodeBlock.INVERTED, stagedInverted);
        if (state.hasProperty(HorizontalDirectionalBlock.FACING))
            state = state.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH);   // = unrotated item model
        GuiGameElement.of(state).rotateBlock(30, 45, 0).scale(30).at(0, 0, 100)
                .render(gfx, panelX + 212, panelY + 63);
        // The block texture's indicator ring is white; in-world a BE renderer covers it with a tinted overlay.
        GuiGameElement.of(AllPartialModels.FLEXPEATER_INDICATOR).color(VirtualBrassDiodeWidget.INDICATOR_IDLE)
                .rotateBlock(30, 45, 0).scale(30).at(0, 0, 100)
                .render(gfx, panelX + 212, panelY + 63);
    }

    @Override
    protected void renderForeground(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        BrassDiodeBehaviour diode = diode();
        if (diode != null) {
            Component title = Component.translatable("createfactorycontroller.gui.component_settings", diode.getName());
            gfx.drawString(font, title, panelX + PANEL_W / 2 - font.width(title) / 2, panelY + 4, TITLE_COLOR, false);
        }
        super.renderForeground(gfx, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics gfx, int mouseX, int mouseY) {}

    @Override
    public List<Rect2i> getExtraAreas() {
        return List.of(new Rect2i(panelX + 200, panelY + 34, 47, 40));
    }

    // ── Overlay plumbing ─────────────────────────────────────────────────────

    @Override public void onPanelSync() { controller.onPanelSync(); }
    @Override public FactoryControllerScreen boardScreen() { return controller; }

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
        Minecraft.getInstance().setScreen(controller);   // commit fires in removed()
    }

    @Override
    public void removed() {
        commitEdit();
        commit();
        ScreenSounds.panelClose();
        super.removed();
    }

    /** Sends once, and only on a real change; an untouched off-grid interval (e.g. 70t) is kept as stored. */
    private void commit() {
        if (committed) return;
        committed = true;
        int interval = stagedInterval() == openedStagedInterval ? openedInterval : stagedInterval();
        if (interval != openedInterval || stagedInverted != openedInverted)
            PacketDistributor.sendToServer(new ConfigureBrassDiodePacket(
                menu.controllerPos, diodePos, interval, stagedInverted));
    }
}
