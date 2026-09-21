package io.github.nbcss.createfactorycontroller.content.gui.screen.blueprint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import io.github.nbcss.createfactorycontroller.content.gui.widget.HelpButton;
import org.anti_ad.mc.ipn.api.IPNIgnore;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintStorage;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.PanelSyncListener;
import io.github.nbcss.createfactorycontroller.content.gui.widget.TooltipIconButton;
import io.github.nbcss.createfactorycontroller.content.gui.widget.VerticalScrollView;
import io.github.nbcss.createfactorycontroller.content.helper.TooltipBuilder;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettings;
import io.github.nbcss.createfactorycontroller.content.render.SpriteNumbersRender;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@IPNIgnore
public abstract class BlueprintFormScreen extends AbstractSimiContainerScreen<FactoryControllerMenu>
        implements PanelSyncListener {
    private static final ResourceLocation FRAME = resource("blueprint/frame");
    private static final ResourceLocation BOTTOM_BAR = resource("common/bottom_bar");
    private static final ResourceLocation BOTTOM_VDIV = resource("common/bottom_bar_vdiv");
    private static final ResourceLocation ELEMENT_BORDER = resource("blueprint/element_border");
    private static final ResourceLocation INPUT_FIELD = resource("common/input_field");
    private static final ResourceLocation TEXTBOX = resource("common/textbox");
    private static final ResourceLocation INPUT_FIELD_LOCKED = resource("blueprint/input_field_disabled");
    private static final ResourceLocation TEXTBOX_LOCKED = resource("blueprint/textbox_disabled");
    private static final ResourceLocation MATERIAL_SLOT = resource("common/display_slot_blue");
    private static final ResourceLocation NETWORK_HEADER = resource("blueprint/network_slot_header");
    private static final ResourceLocation WARNING_ICON = resource("icons/warning");
    private static final ResourceLocation INFO_ICON = resource("icons/info");
    private static final ResourceLocation NETWORK_SLOT = resource("common/network_slot");
    private static final ResourceLocation DEFAULT_NETWORK_ICON = resource("common/network");
    private static final ResourceLocation NO_NETWORK_ICON = resource("common/no_network");

    private static final int PANEL_W = 204;
    private static final int HEADER_H = 16;
    private static final int BOTTOM_H = 30;
    private static final int SLOT = 18;
    private static final int SLOTS_PER_ROW = 9;
    private static final int ELEMENT_W = SLOT * SLOTS_PER_ROW;
    private static final int ELEMENT_X = (PANEL_W - ELEMENT_W) / 2;
    private static final int LABEL_X = ELEMENT_X;
    private static final int NAME_H = 18;
    private static final int NOTE_LINE_H = 12;
    private static final int NOTE_PADDING = 4;
    private static final int ELEMENT_TO_LABEL_GAP = 6;
    private static final int LABEL_TO_ELEMENT_GAP = 3;
    private static final int CONTENT_BOTTOM_GAP = 6;
    private static final int NETWORK_HEADER_H = 11;
    private static final int NETWORK_SEPARATOR_H = 1;
    private static final int NETWORK_SLOT_Y = NETWORK_HEADER_H + NETWORK_SEPARATOR_H;
    private static final int NETWORK_CELL_H = NETWORK_SLOT_Y + SLOT;
    private static final int NETWORK_ROW_SEPARATOR_H = 1;
    private static final int NETWORK_ROW_STEP = NETWORK_CELL_H + NETWORK_ROW_SEPARATOR_H;
    private static final int NO_NETWORK_COLOR = 0xFFCCCCCC;
    private static final int COMPACT_FONT_COLOR = 0xFFDDE5FF;

    protected final FactoryControllerScreen controller;

    private HelpButton helpButton;
    private CenteredEditBox nameBox;
    private SpacedMultiLineEditBox noteBox;
    private TooltipIconButton discardButton;
    private TooltipIconButton confirmButton;
    private VerticalScrollView scrollView;

    private int panelX;
    private int panelY;
    private int panelH;
    private int viewportY;
    private int viewportH;
    private int contentHeight;
    private int noteHeight;
    private int nameLabelY;
    private int nameY;
    private int noteLabelY;
    private int noteY;
    private int materialLabelY;
    private int materialBoxY;
    private int materialBoxH;
    private int networkLabelY;
    private int networkBoxY;
    private int networkBoxH;
    private int draggedNetwork = -1;
    private int hoveredNetworkDrop = -1;
    private boolean validName;
    private boolean nameWasFocused;
    private boolean overwriteExisting;
    private Component errorMessage;

    protected BlueprintFormScreen(FactoryControllerScreen controller, Component title) {
        super(controller.getMenu(), Minecraft.getInstance().player.getInventory(), title);
        this.controller = controller;
    }

    protected static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, path);
    }

    /** Component items previewed below the note editor. */
    protected abstract List<BlueprintStorage.Material> materials();

    /** Number of network placeholder slots the blueprint occupies. */
    protected abstract int networkCount();

    /** Writes the edited name and note; only called once the name is valid. */
    protected abstract void confirm();

    protected abstract String initialName();

    protected abstract String initialNote();

    protected void renderNetworkSlotIcon(GuiGraphics gfx, int slot, int x, int y) {
        int rgb = NO_NETWORK_COLOR;
        renderNetworkSlotBackground(gfx, x, y, NetworkSettings.DARK_FILL);
        gfx.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        gfx.blitSprite(NO_NETWORK_ICON, x + 1, y + 1, 16, 16);
        gfx.setColor(1f, 1f, 1f, 1f);
    }

    protected void renderNetworkSlotBackground(GuiGraphics gfx, int x, int y, int background) {
        gfx.fill(x, y, x + SLOT, y + SLOT, background);
        RenderSystem.enableBlend();
        gfx.blitSprite(NETWORK_SLOT, x, y, SLOT, SLOT);
    }

    protected void renderNetworkIcon(GuiGraphics gfx, int x, int y, int background, int rgb) {
        renderNetworkSlotBackground(gfx, x, y, background);
        gfx.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f, 1f);
        gfx.blitSprite(DEFAULT_NETWORK_ICON, x + 1, y + 1, 16, 16);
        gfx.setColor(1f, 1f, 1f, 1f);
    }

    protected void renderNetworkSlotDecoration(GuiGraphics gfx, int slot, int x, int y) {}

    protected List<FormattedCharSequence> networkTooltip(int slot) {
        return List.of();
    }

    protected boolean scrollNetworkSlot(int slot, double direction) {
        return false;
    }

    protected boolean editable() {
        return true;
    }

    protected boolean canConfirm() {
        return validName;
    }

    protected ScreenElement confirmIcon() {
        return AllIcons.I_CONFIG_SAVE;
    }

    protected Component confirmTooltip() {
        return Component.translatable("createfactorycontroller.gui.blueprint.save");
    }

    protected int materialCountColor(BlueprintStorage.Material material) {
        return COMPACT_FONT_COLOR;
    }

    protected ScreenElement discardIcon() {
        return AllIcons.I_TRASH;
    }

    protected Component discardTooltip() {
        return Component.translatable("createfactorycontroller.gui.blueprint.discard");
    }

    @Nullable
    protected Component confirmBlockedReason() {
        return validName ? null
                : Component.translatable("createfactorycontroller.gui.blueprint.invalid_name");
    }

    protected boolean networksDraggable() {
        return false;
    }

    protected void moveNetwork(int from, int to) {}

    protected boolean isOwnName(String name) {
        return false;
    }

    protected Screen previousScreen() {
        return controller;
    }

    protected void onDiscard() {}

    protected String blueprintName() {
        return nameBox.getValue();
    }

    protected String blueprintNote() {
        return noteBox.getValue();
    }

    protected void showError(Component error) {
        errorMessage = error;
    }

    @Override
    protected void init() {
        setWindowSize(controller.guiWidth(), controller.guiHeight());
        setWindowOffset(0, 0);
        super.init();
        menu.repositionSlots(-2000, -2000, false);

        String oldName = nameBox == null ? initialName() : nameBox.getValue();
        String oldNote = noteBox == null ? initialNote() : noteBox.getValue();

        nameBox = new CenteredEditBox(font, 0, 0, ELEMENT_W, NAME_H, Component.empty());
        nameBox.setBordered(false);
        nameBox.setTextColor(0xFFFFFF);
        nameBox.setMaxLength(BlueprintStorage.MAX_NAME_LENGTH);
        nameBox.setValue(oldName);

        noteBox = new SpacedMultiLineEditBox(font, 0, 0, ELEMENT_W, 32, Component.empty(),
                Component.translatable("createfactorycontroller.gui.blueprint.note_hint"));
        noteBox.setCharacterLimit();
        noteBox.setValue(oldNote);
        noteBox.setValueListener(value -> relayout());

        scrollView = new VerticalScrollView(0, 0, 0, 0, new BlueprintScrollContent());
        addWidget(scrollView);

        discardButton = new TooltipIconButton(0, 0, discardIcon());
        discardButton.withCallback(this::discard);
        discardButton.setToolTip(discardTooltip());
        addWidget(discardButton);

        confirmButton = new TooltipIconButton(0, 0, confirmIcon());
        confirmButton.withCallback(this::trySave);
        confirmButton.withTooltip(() -> {
            Component blocked = confirmBlockedReason();
            return TooltipBuilder.of(font)
                    .line(confirmTooltip())
                    .line(blocked == null ? null : blocked.copy().withStyle(ChatFormatting.RED))
                    .build();
        });
        addWidget(confirmButton);

        nameBox.setResponder(value -> {
            overwriteExisting = false;
            errorMessage = null;
            updateNameValidity();
        });
        updateNameValidity();
        refreshOverwriteState();
        nameWasFocused = nameBox.isFocused();

        relayout();

        helpButton = new HelpButton(panelX + PANEL_W - HelpButton.WIDTH - 5, panelY + 3,
                HelpButton.ColorPalette.GENERAL, "blueprint.html");
        addWidget(helpButton);
    }

    private void updateNameValidity() {
        validName = nameBox != null && BlueprintStorage.isValidBlueprintName(nameBox.getValue());
        if (nameBox != null) nameBox.setTextColor(!editable() || validName ? 0xFFFFFF : 0xFF5555);
        if (confirmButton != null) confirmButton.active = canConfirm();
        if (!validName) overwriteExisting = false;
    }

    private void refreshOverwriteState() {
        updateNameValidity();
        overwriteExisting = editable() && validName && !isOwnName(nameBox.getValue())
                && BlueprintStorage.blueprintExists(nameBox.getValue());
    }

    private void relayout() {
        int wrappedLines = noteBox == null ? 1 : noteBox.getLineCount();
        noteHeight = NOTE_PADDING * 2 + Math.max(2, wrappedLines) * NOTE_LINE_H;

        nameLabelY = 6;
        nameY = boxYAfter(nameLabelY);
        noteLabelY = labelYAfter(nameY, NAME_H);
        noteY = boxYAfter(noteLabelY);
        materialLabelY = labelYAfter(noteY, noteHeight);
        materialBoxY = boxYAfter(materialLabelY);
        int materialRows = Math.max(1, Mth.ceil(materials().size() / (double) SLOTS_PER_ROW));
        materialBoxH = materialRows * SLOT;

        int next = labelYAfter(materialBoxY, materialBoxH);
        int networks = networkCount();
        if (networks == 0) {
            networkLabelY = networkBoxY = networkBoxH = -1;
            contentHeight = materialBoxY + materialBoxH + 1 + CONTENT_BOTTOM_GAP;
        } else {
            networkLabelY = next;
            networkBoxY = boxYAfter(networkLabelY);
            int rows = Mth.ceil(networks / (double) SLOTS_PER_ROW);
            networkBoxH = rows * NETWORK_CELL_H + (rows - 1) * NETWORK_ROW_SEPARATOR_H;
            contentHeight = networkBoxY + networkBoxH + 1 + CONTENT_BOTTOM_GAP;
        }

        int wanted = HEADER_H + contentHeight + BOTTOM_H + 1;
        panelH = Math.clamp(height - 48, 96, wanted);
        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;
        viewportY = panelY + HEADER_H;
        viewportH = panelH - HEADER_H - BOTTOM_H - 1;
        if (scrollView != null) {
            scrollView.setRectangle(PANEL_W, viewportH, panelX, viewportY);
        }
        positionWidgets();
    }

    private int boxYAfter(int labelY) {
        return labelY + font.lineHeight + LABEL_TO_ELEMENT_GAP + 1;
    }

    private int labelYAfter(int boxY, int boxHeight) {
        // +1 is the exclusive bottom of the one-pixel outline.
        return boxY + boxHeight + 1 + ELEMENT_TO_LABEL_GAP;
    }

    private void positionWidgets() {
        if (nameBox == null) return;
        int contentTop = viewportY;
        nameBox.setX(panelX + ELEMENT_X);
        nameBox.setY(contentTop + nameY);
        noteBox.setX(panelX + ELEMENT_X);
        noteBox.setY(contentTop + noteY);
        noteBox.setHeight(noteHeight);
        discardButton.setX(panelX + PANEL_W - 47);
        discardButton.setY(panelY + panelH - 24);
        confirmButton.setX(panelX + PANEL_W - 25);
        confirmButton.setY(panelY + panelH - 24);
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        controller.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        controller.tickComponentWidgets();
        if (scrollView != null) scrollView.tick();
        positionWidgets();
        boolean nameFocused = nameBox.isFocused();
        if (nameFocused && !nameWasFocused) overwriteExisting = false;
        if (!nameFocused && nameWasFocused) refreshOverwriteState();
        nameWasFocused = nameFocused;
        confirmButton.active = canConfirm();
    }

    @Override
    public void onPanelSync() {
        controller.onPanelSync();
    }

    private void trySave() {
        updateNameValidity();
        if (canConfirm()) confirm();
    }

    private void discard() {
        onDiscard();
        Minecraft.getInstance().setScreen(previousScreen());
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        if (draggedNetwork >= 0) {
            renderHeldNetwork(gfx, mouseX, mouseY);
        }
        if (draggedNetwork < 0) {
            helpButton.renderTooltip(gfx, font, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        controller.renderBoard(gfx, -1, -1, partialTick, true);
        TiledSpriteRenderer.create(FRAME).render(gfx, panelX, panelY, PANEL_W, panelH - BOTTOM_H + 1);
        TiledSpriteRenderer.create(BOTTOM_BAR).render(gfx, panelX, panelY + panelH - BOTTOM_H, PANEL_W, BOTTOM_H);
        TiledSpriteRenderer.create(BOTTOM_VDIV).render(gfx, panelX + PANEL_W - 53,
                panelY + panelH - BOTTOM_H, 2, BOTTOM_H);

        positionWidgets();
        scrollView.render(gfx, mouseX, mouseY, partialTick);
        discardButton.render(gfx, mouseX, mouseY, partialTick);
        confirmButton.render(gfx, mouseX, mouseY, partialTick);
        helpButton.render(gfx, mouseX, mouseY, partialTick);
    }

    private void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int x = panelX + ELEMENT_X;
        int labelX = panelX + LABEL_X;
        int top = viewportY;
        int textColor = 0xFFFFFF;

        Component nameTitle = Component.translatable("createfactorycontroller.gui.blueprint.name");
        gfx.drawString(font, nameTitle, labelX, top + nameLabelY, textColor, false);
        if (overwriteExisting) {
            RenderSystem.enableBlend();
            gfx.blitSprite(WARNING_ICON, labelX + font.width(nameTitle) + 2,
                    top + nameLabelY + (font.lineHeight - 8) / 2, 8, 8);
        }
        borderedBox(gfx, editable() ? INPUT_FIELD : INPUT_FIELD_LOCKED, x, top + nameY, ELEMENT_W, NAME_H);

        gfx.drawString(font, Component.translatable("createfactorycontroller.gui.blueprint.note"),
                labelX, top + noteLabelY, textColor, false);
        borderedBox(gfx, editable() ? TEXTBOX : TEXTBOX_LOCKED, x, top + noteY, ELEMENT_W, noteHeight);

        gfx.drawString(font, Component.translatable("createfactorycontroller.gui.blueprint.materials"),
                labelX, top + materialLabelY, textColor, false);
        renderMaterialBox(gfx, x, top + materialBoxY);

        if (networkCount() > 0) {
            Component networkTitle = Component.translatable("createfactorycontroller.gui.blueprint.networks");
            gfx.drawString(font, networkTitle, labelX, top + networkLabelY, textColor, false);
            RenderSystem.enableBlend();
            gfx.blitSprite(INFO_ICON, labelX + font.width(networkTitle) + 2,
                    top + networkLabelY + (font.lineHeight - 8) / 2, 8, 8);
            renderNetworkBox(gfx, x, top + networkBoxY, mouseX, mouseY);
        }

        nameBox.render(gfx, mouseX, mouseY, partialTick);
        noteBox.render(gfx, mouseX, mouseY, partialTick);
    }

    private void borderedBox(GuiGraphics gfx, ResourceLocation fill, int x, int y, int width, int height) {
        TiledSpriteRenderer.create(ELEMENT_BORDER).render(gfx, x - 1, y - 1, width + 2, height + 2);
        TiledSpriteRenderer.create(fill).render(gfx, x, y, width, height);
    }

    private void renderMaterialBox(GuiGraphics gfx, int x, int y) {
        TiledSpriteRenderer.create(ELEMENT_BORDER).render(gfx, x - 1, y - 1, ELEMENT_W + 2, materialBoxH + 2);
        int cells = materialBoxH / SLOT * SLOTS_PER_ROW;
        for (int i = 0; i < cells; i++) {
            int sx = x + i % SLOTS_PER_ROW * SLOT;
            int sy = y + i / SLOTS_PER_ROW * SLOT;
            gfx.blitSprite(MATERIAL_SLOT, sx, sy, SLOT, SLOT);
        }
        List<BlueprintStorage.Material> materials = materials();
        for (int i = 0; i < materials.size(); i++) {
            int sx = x + i % SLOTS_PER_ROW * SLOT;
            int sy = y + i / SLOTS_PER_ROW * SLOT;
            BlueprintStorage.Material material = materials.get(i);
            gfx.renderItem(BlueprintMaterialDisplay.icon(material), sx + 1, sy + 1);
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 200);
            SpriteNumbersRender.drawCountRightAligned(gfx, Integer.toString(material.count()),
                    sx + 17, sy + 10, materialCountColor(material));
            gfx.pose().popPose();
        }
    }

    private void renderNetworkBox(GuiGraphics gfx, int x, int y, int mouseX, int mouseY) {
        TiledSpriteRenderer.create(ELEMENT_BORDER).render(gfx, x - 1, y - 1, ELEMENT_W + 2, networkBoxH + 2);
        int rows = Mth.ceil(networkCount() / (double) SLOTS_PER_ROW);
        int cells = rows * SLOTS_PER_ROW;
        RenderSystem.enableBlend();
        for (int i = 0; i < cells; i++) {
            int sx = x + i % SLOTS_PER_ROW * SLOT;
            int sy = y + i / SLOTS_PER_ROW * NETWORK_ROW_STEP;
            gfx.blitSprite(NETWORK_HEADER, sx, sy, SLOT, NETWORK_HEADER_H);
            gfx.blitSprite(ELEMENT_BORDER, sx, sy + NETWORK_HEADER_H, SLOT, NETWORK_SEPARATOR_H);
            gfx.blitSprite(NETWORK_SLOT, sx, sy + NETWORK_SLOT_Y, SLOT, SLOT);
        }
        for (int row = 0; row < rows - 1; row++)
            gfx.blitSprite(ELEMENT_BORDER, x, y + row * NETWORK_ROW_STEP + NETWORK_CELL_H,
                    ELEMENT_W, NETWORK_ROW_SEPARATOR_H);
        int[] displayedSlots = displayedNetworkSlots();
        for (int i = 0; i < displayedSlots.length; i++) {
            int sx = x + i % SLOTS_PER_ROW * SLOT;
            int sy = y + i / SLOTS_PER_ROW * NETWORK_ROW_STEP;
            String index = Integer.toString(i + 1);
            SpriteNumbersRender.drawCountRightAligned(gfx, index,
                    sx + (SLOT + SpriteNumbersRender.width(index)) / 2, sy + 1, COMPACT_FONT_COLOR);
            if (displayedSlots[i] >= 0) {
                renderNetworkSlotIcon(gfx, displayedSlots[i], sx, sy + NETWORK_SLOT_Y);
                renderNetworkSlotDecoration(gfx, displayedSlots[i], sx, sy + NETWORK_SLOT_Y);
            }
        }

        int hoveredSlot = networksDraggable() ? networkAt(mouseX, mouseY) : -1;
        if (hoveredSlot >= 0) {
            int sx = x + hoveredSlot % SLOTS_PER_ROW * SLOT;
            int sy = y + hoveredSlot / SLOTS_PER_ROW * NETWORK_ROW_STEP + NETWORK_SLOT_Y;
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 250);
            gfx.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0x80FFFFFF);
            gfx.pose().popPose();
        }
    }

    private int[] displayedNetworkSlots() {
        int count = networkCount();
        int[] slots = new int[count];
        if (draggedNetwork < 0) {
            for (int i = 0; i < count; i++) slots[i] = i;
            return slots;
        }
        List<Integer> preview = new ArrayList<>();
        for (int i = 0; i < count; i++)
            if (i != draggedNetwork) preview.add(i);
        preview.add(hoveredNetworkDrop >= 0 ? hoveredNetworkDrop : draggedNetwork, -1);
        for (int i = 0; i < count; i++) slots[i] = preview.get(i);
        return slots;
    }

    private void renderHeldNetwork(GuiGraphics gfx, int mouseX, int mouseY) {
        if (draggedNetwork < 0 || draggedNetwork >= networkCount()) return;
        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 300);
        renderNetworkSlotIcon(gfx, draggedNetwork, mouseX - SLOT / 2, mouseY - SLOT / 2);
        gfx.pose().popPose();
    }

    @Override
    protected void renderForeground(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        Component title = getTitle();
        gfx.drawString(font, title, panelX + PANEL_W / 2 - font.width(title) / 2, panelY + 4, 0x3D3C48, false);
        if (errorMessage != null) {
            var lines = font.split(errorMessage, PANEL_W - 64);
            if (!lines.isEmpty())
                gfx.drawString(font, lines.getFirst(), panelX + 8, panelY + panelH - 19, 0xD04040, false);
        }
        super.renderForeground(gfx, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics gfx, int mouseX, int mouseY) {}

    private int materialAt(double mouseX, double mouseY) {
        int x = (int) mouseX - (panelX + ELEMENT_X);
        int y = (int) mouseY - (viewportY + materialBoxY);
        if (x < 0 || x >= ELEMENT_W || y < 0 || y >= materialBoxH) return -1;
        int index = y / SLOT * SLOTS_PER_ROW + x / SLOT;
        return index < materials().size() ? index : -1;
    }

    private int networkAt(double mouseX, double mouseY) {
        if (networkCount() == 0) return -1;
        int x = (int) mouseX - (panelX + ELEMENT_X);
        int y = (int) mouseY - (viewportY + networkBoxY);
        if (x < 0 || x >= ELEMENT_W || y < 0 || y >= networkBoxH) return -1;
        int row = y / NETWORK_ROW_STEP;
        int rowY = y % NETWORK_ROW_STEP;
        if (rowY < NETWORK_SLOT_Y || rowY == NETWORK_CELL_H) return -1;
        int index = row * SLOTS_PER_ROW + x / SLOT;
        return index < networkCount() ? index : -1;
    }

    private class BlueprintScrollContent implements VerticalScrollView.Content {
        @Override
        public int getHeight() {
            return contentHeight;
        }

        @Override
        public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            renderContent(gfx, mouseX, mouseY, partialTick);
        }

        @Override
        public List<FormattedCharSequence> tooltip(int mouseX, int mouseY) {
            if (draggedNetwork >= 0) return List.of();
            Component nameTitle = Component.translatable("createfactorycontroller.gui.blueprint.name");
            if (overwriteExisting && iconBounds(nameTitle, nameLabelY).contains(mouseX, mouseY))
                return TooltipBuilder.of(font)
                        .line(Component.translatable("createfactorycontroller.gui.blueprint.overwrite_existing"))
                        .build();
            Component networkTitle = Component.translatable("createfactorycontroller.gui.blueprint.networks");
            if (networkCount() > 0 && iconBounds(networkTitle, networkLabelY).contains(mouseX, mouseY))
                return TooltipBuilder.of(font)
                        .line(Component.translatable("createfactorycontroller.gui.blueprint.network_info_1"))
                        .line(Component.translatable("createfactorycontroller.gui.blueprint.network_info_2"))
                        .line(Component.translatable("createfactorycontroller.gui.blueprint.network_info_3"))
                        .build();
            int material = materialAt(mouseX, mouseY);
            if (material >= 0) return BlueprintMaterialDisplay.tooltip(font, materials().get(material));
            int network = networkAt(mouseX, mouseY);
            return network >= 0 ? networkTooltip(network) : List.of();
        }

        private net.minecraft.client.renderer.Rect2i iconBounds(Component label, int labelY) {
            return new net.minecraft.client.renderer.Rect2i(
                    panelX + LABEL_X + font.width(label) + 2,
                    viewportY + labelY + (font.lineHeight - 8) / 2,
                    8, 8);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && networksDraggable()) {
                int network = networkAt(mouseX, mouseY);
                if (network >= 0) {
                    clearTextFocus();
                    BlueprintFormScreen.this.setFocused(scrollView);
                    draggedNetwork = hoveredNetworkDrop = network;
                    return true;
                }
            }
            if (editable() && nameBox.mouseClicked(mouseX, mouseY, button)) {
                noteBox.setFocused(false);
                nameBox.setFocused(true);
                BlueprintFormScreen.this.setFocused(scrollView);
                return true;
            }
            if (editable() && noteBox.mouseClicked(mouseX, mouseY, button)) {
                nameBox.setFocused(false);
                noteBox.setFocused(true);
                BlueprintFormScreen.this.setFocused(scrollView);
                return true;
            }
            clearTextFocus();
            return false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (button == 0 && draggedNetwork >= 0) {
                hoveredNetworkDrop = networkAt(mouseX, mouseY);
                return true;
            }
            if (editable() && nameBox.isFocused()) return nameBox.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            if (editable() && noteBox.isFocused()) return noteBox.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (button == 0 && draggedNetwork >= 0) {
                int releasedTarget = networkAt(mouseX, mouseY);
                if (releasedTarget >= 0 && releasedTarget != draggedNetwork)
                    moveNetwork(draggedNetwork, releasedTarget);
                draggedNetwork = hoveredNetworkDrop = -1;
                return true;
            }
            if (editable() && nameBox.isFocused()) return nameBox.mouseReleased(mouseX, mouseY, button);
            return editable() && noteBox.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            int network = networkAt(mouseX, mouseY);
            return network >= 0 && scrollNetworkSlot(network, scrollY);
        }
    }

    private void clearTextFocus() {
        if (nameBox == null || noteBox == null) return;
        nameBox.setFocused(false);
        noteBox.setFocused(false);
        setFocused(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (scrollView != null && !scrollView.isMouseOver(mouseX, mouseY)) clearTextFocus();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (noteBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                noteBox.setFocused(false);
                setFocused(null);
            } else {
                noteBox.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        if (nameBox.isFocused() && (keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            nameBox.setFocused(false);
            setFocused(null);
            return true;
        }
        if (nameBox.isFocused()) return nameBox.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (noteBox.isFocused()) return noteBox.charTyped(codePoint, modifiers);
        if (nameBox.isFocused()) return nameBox.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        discard();
    }

    private static class CenteredEditBox extends EditBox {
        CenteredEditBox(Font font, int x, int y, int width, int height, Component message) {
            super(font, x, y, width, height, message);
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            int originalX = getX();
            int originalY = getY();
            int originalWidth = getWidth();
            setX(originalX + 4);
            setY(originalY + (getHeight() - 8) / 2);
            setWidth(originalWidth - 8);
            try {
                super.renderWidget(gfx, mouseX, mouseY, partialTick);
            } finally {
                setX(originalX);
                setY(originalY);
                setWidth(originalWidth);
            }
        }
    }

    private class SpacedMultiLineEditBox extends AbstractWidget {
        private final Font font;
        private final Component placeholder;
        private final MultilineTextField textField;
        private long focusedTime;

        SpacedMultiLineEditBox(Font font, int x, int y, int width, int height,
                               Component message, Component placeholder) {
            super(x, y, width, height, message);
            this.font = font;
            this.placeholder = placeholder;
            this.textField = new MultilineTextField(font, width - NOTE_PADDING * 2);
        }

        void setCharacterLimit() {
            textField.setCharacterLimit(BlueprintStorage.MAX_NOTE_LENGTH);
        }

        void setValueListener(java.util.function.Consumer<String> listener) {
            textField.setValueListener(listener);
        }

        void setValue(String value) {
            textField.setValue(value);
        }

        String getValue() {
            return textField.value();
        }

        int getLineCount() {
            return textField.getLineCount();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;
            textField.setSelecting(Screen.hasShiftDown());
            seekCursor(mouseX, mouseY);
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;
            textField.setSelecting(true);
            seekCursor(mouseX, mouseY);
            textField.setSelecting(Screen.hasShiftDown());
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            textField.setSelecting(false);
            return super.mouseReleased(mouseX, mouseY, button);
        }

        private void seekCursor(double mouseX, double mouseY) {
            double localX = mouseX - getX() - NOTE_PADDING;
            double visualY = mouseY - getY() - NOTE_PADDING;
            textField.seekCursorToPoint(localX, visualY * font.lineHeight / NOTE_LINE_H);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return textField.keyPressed(keyCode);
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            if (!visible || !isFocused() || !StringUtil.isAllowedChatCharacter(codePoint)) return false;
            textField.insertText(Character.toString(codePoint));
            return true;
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (focused) focusedTime = Util.getMillis();
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            String value = textField.value();
            int textX = getX() + NOTE_PADDING;
            int firstTextY = getY() + NOTE_PADDING + (NOTE_LINE_H - font.lineHeight) / 2;
            if (value.isEmpty() && !isFocused() && editable()) {
                gfx.drawString(font, placeholder, textX, firstTextY, 0xCCDFDFDF, false);
                return;
            }

            List<LineRange> lines = displayLines(value);
            int selectionStart = -1, selectionEnd = -1;
            if (textField.hasSelection()) {
                String selected = textField.getSelectedText();
                int cursor = textField.cursor();
                if (cursor + selected.length() <= value.length()
                        && value.regionMatches(cursor, selected, 0, selected.length())) {
                    selectionStart = cursor;
                    selectionEnd = cursor + selected.length();
                } else {
                    selectionStart = Math.max(0, cursor - selected.length());
                    selectionEnd = cursor;
                }
            }
            int lineIndex = 0;
            for (LineRange line : lines) {
                int textY = firstTextY + lineIndex * NOTE_LINE_H;
                if (selectionStart >= 0)
                    renderSelection(gfx, value, line.begin(), line.end(),
                            selectionStart, selectionEnd, textX, textY);
                gfx.drawString(font, value.substring(line.begin(), line.end()), textX, textY,
                        0xFFFFFFFF, false);
                lineIndex++;
            }

            if (isFocused() && (Util.getMillis() - focusedTime) / 300L % 2L == 0L) {
                int cursor = textField.cursor();
                int cursorLine = Mth.clamp(textField.getLineAtCursor(), 0, lines.size() - 1);
                LineRange line = lines.get(cursorLine);
                int columnEnd = Mth.clamp(cursor, line.begin(), line.end());
                int cursorX = textX + font.width(value.substring(line.begin(), columnEnd));
                int cursorY = firstTextY + cursorLine * NOTE_LINE_H;
                gfx.fill(cursorX, cursorY - 1, cursorX + 1, cursorY + font.lineHeight + 1, 0xFFFFFFFF);
            }
        }

        private List<LineRange> displayLines(String value) {
            List<LineRange> lines = new ArrayList<>();
            if (value.isEmpty()) {
                lines.add(new LineRange(0, 0));
                return lines;
            }
            font.getSplitter().splitLines(value, getWidth() - NOTE_PADDING * 2, Style.EMPTY, false,
                    (style, begin, end) -> lines.add(new LineRange(begin, end)));
            if (value.charAt(value.length() - 1) == '\n')
                lines.add(new LineRange(value.length(), value.length()));
            return lines;
        }

        private void renderSelection(GuiGraphics gfx, String value, int lineStart, int lineEnd,
                                     int selectionStart, int selectionEnd, int textX, int textY) {
            int start = Math.max(lineStart, selectionStart);
            int end = Math.min(lineEnd, selectionEnd);
            if (start >= end) return;
            int x0 = textX + font.width(value.substring(lineStart, start));
            int x1 = textX + font.width(value.substring(lineStart, end));
            gfx.fill(x0, textY - 1, x1, textY + font.lineHeight + 1, 0x806060D0);
        }

        @Override
        protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {}

        private record LineRange(int begin, int end) {}
    }
}
