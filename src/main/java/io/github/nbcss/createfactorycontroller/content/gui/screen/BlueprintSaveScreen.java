package io.github.nbcss.createfactorycontroller.content.gui.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.blueprint.ComponentBlueprintStorage;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.gui.widget.TooltipIconButton;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettings;
import io.github.nbcss.createfactorycontroller.content.render.SpriteNumbersRender;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Editor and preview for saving the controller's current component selection as a reusable blueprint. */
public class BlueprintSaveScreen extends AbstractSimiContainerScreen<FactoryControllerMenu>
        implements PanelSyncListener {
    private static final ResourceLocation FRAME = resource("blueprint_edit/frame");
    private static final ResourceLocation BOTTOM_BAR = resource("common/bottom_bar");
    private static final ResourceLocation BOTTOM_VDIV = resource("common/bottom_bar_vdiv");
    private static final ResourceLocation ELEMENT_BORDER = resource("blueprint_edit/element_border");
    private static final ResourceLocation INPUT_FIELD = resource("common/input_field");
    private static final ResourceLocation TEXTBOX = resource("common/textbox");
    private static final ResourceLocation MATERIAL_SLOT = resource("common/display_slot_blue");
    private static final ResourceLocation NETWORK_SLOT = resource("common/network_slot");
    private static final ResourceLocation NETWORK_HEADER = resource("blueprint_edit/network_slot_header");
    private static final ResourceLocation DEFAULT_NETWORK_ICON = resource("factory_controller/network_selector/network");
    private static final ResourceLocation INFO_ICON = resource("icons/info");

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
    private static final int COMPACT_FONT_COLOR = 0xFFDDE5FF;
    private static final int SCROLLBAR_X = 198;

    private final FactoryControllerScreen controller;
    private final List<VirtualComponentPosition> selected;
    private final List<ComponentBlueprintStorage.Material> materials;
    private final List<UUID> networks;

    private CenteredEditBox nameBox;
    private SpacedMultiLineEditBox noteBox;
    private TooltipIconButton discardButton;
    private TooltipIconButton saveButton;

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
    private final LerpedFloat scroll = LerpedFloat.linear().startWithValue(0);
    private float renderedScroll;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private int draggedNetwork = -1;
    private int hoveredNetworkDrop = -1;
    private boolean validName;
    private boolean nameWasFocused;
    private boolean overwriteExisting;
    private Component saveError;

    public BlueprintSaveScreen(FactoryControllerScreen controller, Set<VirtualComponentPosition> selection) {
        super(controller.getMenu(), Minecraft.getInstance().player.getInventory(),
                Component.translatable("createfactorycontroller.gui.blueprint.save_title"));
        this.controller = controller;
        this.selected = selection.stream().filter(p -> menu.componentAt(p) != null).toList();
        this.materials = ComponentBlueprintStorage.materials(menu, this.selected);
        this.networks = new ArrayList<>(ComponentBlueprintStorage.networks(menu, this.selected));
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

        String oldName = nameBox == null ? defaultBlueprintName() : nameBox.getValue();
        String oldNote = noteBox == null ? "" : noteBox.getValue();

        nameBox = new CenteredEditBox(font, 0, 0, ELEMENT_W, NAME_H, Component.empty());
        nameBox.setBordered(false);
        nameBox.setTextColor(0xFFFFFF);
        nameBox.setMaxLength(50);
        nameBox.setValue(oldName);
        addWidget(nameBox);

        noteBox = new SpacedMultiLineEditBox(font, 0, 0, ELEMENT_W, 32, Component.empty(),
                Component.translatable("createfactorycontroller.gui.blueprint.note_hint"));
        noteBox.setCharacterLimit(500);
        noteBox.setValue(oldNote);
        noteBox.setValueListener(value -> relayout());
        addWidget(noteBox);

        discardButton = new TooltipIconButton(0, 0, AllIcons.I_TRASH);
        discardButton.withCallback(this::discard);
        discardButton.setToolTip(Component.translatable("createfactorycontroller.gui.blueprint.discard"));
        addWidget(discardButton);

        saveButton = new TooltipIconButton(0, 0, AllIcons.I_CONFIG_SAVE);
        saveButton.withCallback(this::save);
        saveButton.setToolTip(Component.translatable("createfactorycontroller.gui.blueprint.save"));
        addWidget(saveButton);

        nameBox.setResponder(value -> {
            overwriteExisting = false;
            saveError = null;
            updateNameValidity();
        });
        updateNameValidity();
        refreshOverwriteState();
        nameWasFocused = nameBox.isFocused();
        relayout();
    }

    private String defaultBlueprintName() {
        return Component.translatable("createfactorycontroller.gui.blueprint.default_name").getString();
    }

    private void updateNameValidity() {
        validName = nameBox != null && ComponentBlueprintStorage.isValidBlueprintName(nameBox.getValue());
        if (nameBox != null) nameBox.setTextColor(validName ? 0xFFFFFF : 0xFF5555);
        if (saveButton != null) saveButton.active = validName;
        if (!validName) overwriteExisting = false;
    }

    private void refreshOverwriteState() {
        updateNameValidity();
        overwriteExisting = validName && ComponentBlueprintStorage.blueprintExists(nameBox.getValue());
    }

    private boolean overOverwriteInfo(double mouseX, double mouseY) {
        if (!overwriteExisting || !insideViewport(mouseX, mouseY)) return false;
        Component nameTitle = Component.translatable("createfactorycontroller.gui.blueprint.name");
        int x = panelX + LABEL_X + font.width(nameTitle) + 2;
        int y = viewportY - (int) renderedScroll + nameLabelY + (font.lineHeight - 8) / 2;
        return mouseX >= x && mouseX < x + 8 && mouseY >= y && mouseY < y + 8;
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
        int materialRows = Math.max(1, Mth.ceil(materials.size() / (double) SLOTS_PER_ROW));
        materialBoxH = materialRows * SLOT;

        int next = labelYAfter(materialBoxY, materialBoxH);
        if (networks.isEmpty()) {
            networkLabelY = networkBoxY = networkBoxH = -1;
            contentHeight = materialBoxY + materialBoxH + 1 + CONTENT_BOTTOM_GAP;
        } else {
            networkLabelY = next;
            networkBoxY = boxYAfter(networkLabelY);
            int rows = Mth.ceil(networks.size() / (double) SLOTS_PER_ROW);
            networkBoxH = rows * NETWORK_CELL_H + (rows - 1) * NETWORK_ROW_SEPARATOR_H;
            contentHeight = networkBoxY + networkBoxH + 1 + CONTENT_BOTTOM_GAP;
        }

        int wanted = HEADER_H + contentHeight + BOTTOM_H + 1;
        panelH = Math.clamp(height - 48, 96, wanted);
        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;
        viewportY = panelY + HEADER_H;
        viewportH = panelH - HEADER_H - BOTTOM_H - 1;
        float clamped = Mth.clamp(scroll.getChaseTarget(), 0, (float) maxScroll());
        scroll.chase(clamped, 0.5, Chaser.EXP);
        if (maxScroll() == 0) scroll.setValue(0);
        renderedScroll = Mth.clamp(scroll.getValue(), 0, (float) maxScroll());
        positionWidgets(renderedScroll);
    }

    private int boxYAfter(int labelY) {
        // The returned coordinate is the fill's top; the outline itself begins one pixel earlier.
        return labelY + font.lineHeight + LABEL_TO_ELEMENT_GAP + 1;
    }

    private int labelYAfter(int boxY, int boxHeight) {
        // +1 is the exclusive bottom of the one-pixel outline.
        return boxY + boxHeight + 1 + ELEMENT_TO_LABEL_GAP;
    }

    private void positionWidgets(float currentScroll) {
        if (nameBox == null) return;
        int contentTop = viewportY - (int) currentScroll;
        nameBox.setX(panelX + ELEMENT_X);
        nameBox.setY(contentTop + nameY);
        noteBox.setX(panelX + ELEMENT_X);
        noteBox.setY(contentTop + noteY);
        noteBox.setHeight(noteHeight);
        discardButton.setX(panelX + PANEL_W - 47);
        discardButton.setY(panelY + panelH - 24);
        saveButton.setX(panelX + PANEL_W - 25);
        saveButton.setY(panelY + panelH - 24);
    }

    private double maxScroll() {
        return Math.max(0, contentHeight - viewportH);
    }

    @Override
    public void resize(@NotNull Minecraft minecraft, int width, int height) {
        controller.resize(minecraft, width, height);
        super.resize(minecraft, width, height);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        controller.tickBulbs();
        scroll.tickChaser();
        float clamped = Mth.clamp(scroll.getChaseTarget(), 0, (float) maxScroll());
        if (clamped != scroll.getChaseTarget()) scroll.chase(clamped, 0.5, Chaser.EXP);
        if (Math.abs(scroll.getValue() - scroll.getChaseTarget()) < 0.5F)
            scroll.setValue(scroll.getChaseTarget());
        positionWidgets(scroll.getValue());
        boolean nameFocused = nameBox.isFocused();
        if (nameFocused && !nameWasFocused) overwriteExisting = false;
        if (!nameFocused && nameWasFocused) refreshOverwriteState();
        nameWasFocused = nameFocused;
        saveButton.active = validName;
    }

    @Override
    public void onPanelSync() {
        controller.onPanelSync();
    }

    private void save() {
        updateNameValidity();
        if (!validName) return;
        try {
            Path saved = ComponentBlueprintStorage.save(menu, selected, networks, nameBox.getValue(),
                    noteBox.getValue(), Minecraft.getInstance().level.registryAccess());
            controller.showBlueprintSaved(saved.getFileName().toString());
            Minecraft.getInstance().setScreen(controller);
        } catch (IOException | RuntimeException exception) {
            saveError = Component.translatable("createfactorycontroller.gui.blueprint.save_failed",
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage())
                    .withStyle(ChatFormatting.RED);
        }
    }

    private void discard() {
        Minecraft.getInstance().setScreen(controller);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        if (draggedNetwork >= 0) {
            renderHeldNetwork(gfx, mouseX, mouseY);
        } else if (overOverwriteInfo(mouseX, mouseY)) {
            gfx.renderTooltip(font,
                    Component.translatable("createfactorycontroller.gui.blueprint.overwrite_existing"),
                    mouseX, mouseY);
        } else if (insideViewport(mouseX, mouseY)) {
            int material = materialAt(mouseX, mouseY);
            if (material >= 0) {
                ComponentBlueprintStorage.Material entry = materials.get(material);
                ItemStack stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(entry.item()));
                gfx.renderTooltip(font, stack, mouseX, mouseY);
            } else {
                int network = networkAt(mouseX, mouseY);
                if (network >= 0)
                    gfx.renderTooltip(font, menu.networkName(networks.get(network)), mouseX, mouseY);
            }
        }
        if (draggedNetwork < 0)
            TooltipIconButton.renderFirstTooltip(gfx, font, mouseX, mouseY, discardButton, saveButton);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        controller.renderBoard(gfx, -1, -1, partialTick, true);
        TiledSpriteRenderer.create(FRAME).render(gfx, panelX, panelY, PANEL_W, panelH - BOTTOM_H + 1);
        TiledSpriteRenderer.create(BOTTOM_BAR).render(gfx, panelX, panelY + panelH - BOTTOM_H, PANEL_W, BOTTOM_H);
        TiledSpriteRenderer.create(BOTTOM_VDIV).render(gfx, panelX + PANEL_W - 53,
                panelY + panelH - BOTTOM_H, 2, BOTTOM_H);

        renderedScroll = Mth.clamp(scroll.getValue(partialTick), 0, (float) maxScroll());
        positionWidgets(renderedScroll);
        gfx.enableScissor(panelX + 7, viewportY, panelX + PANEL_W - 7, viewportY + viewportH);
        renderContent(gfx, mouseX, mouseY, partialTick, renderedScroll);
        gfx.disableScissor();

        renderScrollbar(gfx, renderedScroll, mouseX, mouseY);
        discardButton.render(gfx, mouseX, mouseY, partialTick);
        saveButton.render(gfx, mouseX, mouseY, partialTick);
    }

    private void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick, float currentScroll) {
        int x = panelX + ELEMENT_X;
        int labelX = panelX + LABEL_X;
        int top = viewportY - (int) currentScroll;
        int textColor = 0xFFFFFF;

        Component nameTitle = Component.translatable("createfactorycontroller.gui.blueprint.name");
        gfx.drawString(font, nameTitle, labelX, top + nameLabelY, textColor, false);
        if (overwriteExisting) {
            RenderSystem.enableBlend();
            gfx.blitSprite(INFO_ICON, labelX + font.width(nameTitle) + 2,
                    top + nameLabelY + (font.lineHeight - 8) / 2, 8, 8);
        }
        borderedBox(gfx, INPUT_FIELD, x, top + nameY, ELEMENT_W, NAME_H);

        gfx.drawString(font, Component.translatable("createfactorycontroller.gui.blueprint.note"),
                labelX, top + noteLabelY, textColor, false);
        borderedBox(gfx, TEXTBOX, x, top + noteY, ELEMENT_W, noteHeight);

        gfx.drawString(font, Component.translatable("createfactorycontroller.gui.blueprint.materials"),
                labelX, top + materialLabelY, textColor, false);
        renderMaterialBox(gfx, x, top + materialBoxY);

        if (!networks.isEmpty()) {
            gfx.drawString(font, Component.translatable("createfactorycontroller.gui.blueprint.networks"),
                    labelX, top + networkLabelY, textColor, false);
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
        for (int i = 0; i < materials.size(); i++) {
            int sx = x + i % SLOTS_PER_ROW * SLOT;
            int sy = y + i / SLOTS_PER_ROW * SLOT;
            ComponentBlueprintStorage.Material material = materials.get(i);
            ItemStack stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(material.item()));
            gfx.renderItem(stack, sx + 1, sy + 1);
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 200);
            SpriteNumbersRender.drawCountRightAligned(gfx, Integer.toString(material.count()),
                    sx + 17, sy + 10, COMPACT_FONT_COLOR);
            gfx.pose().popPose();
        }
    }

    private void renderNetworkBox(GuiGraphics gfx, int x, int y, int mouseX, int mouseY) {
        TiledSpriteRenderer.create(ELEMENT_BORDER).render(gfx, x - 1, y - 1, ELEMENT_W + 2, networkBoxH + 2);
        int rows = Mth.ceil(networks.size() / (double) SLOTS_PER_ROW);
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
        List<UUID> displayedSlots = displayedNetworkSlots();
        for (int i = 0; i < displayedSlots.size(); i++) {
            int sx = x + i % SLOTS_PER_ROW * SLOT;
            int sy = y + i / SLOTS_PER_ROW * NETWORK_ROW_STEP;
            String index = Integer.toString(i + 1);
            SpriteNumbersRender.drawCountRightAligned(gfx, index,
                    sx + (SLOT + SpriteNumbersRender.width(index)) / 2, sy + 1, COMPACT_FONT_COLOR);
            UUID displayed = displayedSlots.get(i);
            if (displayed != null)
                renderNetworkIcon(gfx, menu.networkSettings(displayed), sx, sy + NETWORK_SLOT_Y);
        }

        int hoveredSlot = networkAt(mouseX, mouseY);
        if (hoveredSlot >= 0) {
            int sx = x + hoveredSlot % SLOTS_PER_ROW * SLOT;
            int sy = y + hoveredSlot / SLOTS_PER_ROW * NETWORK_ROW_STEP + NETWORK_SLOT_Y;
            gfx.pose().pushPose();
            gfx.pose().translate(0, 0, 250);
            gfx.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0x80FFFFFF);
            gfx.pose().popPose();
        }
    }

    /** Network order with a movable empty gap while the dragged icon itself is rendered at the cursor. */
    private List<UUID> displayedNetworkSlots() {
        if (draggedNetwork < 0)
            return networks;
        int gap = hoveredNetworkDrop >= 0 ? hoveredNetworkDrop : draggedNetwork;
        List<UUID> preview = new ArrayList<>(networks);
        preview.remove(draggedNetwork);
        preview.add(gap, null);
        return preview;
    }

    private void renderHeldNetwork(GuiGraphics gfx, int mouseX, int mouseY) {
        if (draggedNetwork < 0 || draggedNetwork >= networks.size()) return;
        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 300);
        renderNetworkIcon(gfx, menu.networkSettings(networks.get(draggedNetwork)),
                mouseX - SLOT / 2, mouseY - SLOT / 2);
        gfx.pose().popPose();
    }

    private void renderNetworkIcon(GuiGraphics gfx, NetworkSettings settings, int x, int y) {
        gfx.fill(x, y, x + SLOT, y + SLOT,
                settings.hasCustomIcon() ? 0xFF8B8B8B : settings.backgroundColor());
        RenderSystem.enableBlend();
        gfx.blitSprite(NETWORK_SLOT, x, y, SLOT, SLOT);
        if (settings.hasCustomIcon()) {
            gfx.renderItem(settings.icon(), x + 1, y + 1);
        } else {
            int rgb = settings.color();
            gfx.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f,
                    (rgb & 0xFF) / 255f, 1f);
            gfx.blitSprite(DEFAULT_NETWORK_ICON, x + 1, y + 1, 16, 16);
            gfx.setColor(1f, 1f, 1f, 1f);
        }
    }

    private void renderScrollbar(GuiGraphics gfx, float currentScroll, int mouseX, int mouseY) {
        if (maxScroll() <= 0) return;
        int thumbY = scrollbarThumbY(currentScroll);
        int thumbH = scrollbarThumbHeight();
        gfx.fill(panelX + SCROLLBAR_X, viewportY,
                panelX + SCROLLBAR_X + 3, viewportY + viewportH, 0x503D3C48);
        int thumbColor = overScrollbar(mouseX, mouseY) ? 0xFFE2E2E2 : 0xFFC6C6C6;
        gfx.fill(panelX + SCROLLBAR_X, thumbY,
                panelX + SCROLLBAR_X + 3, thumbY + thumbH, thumbColor);
    }

    @Override
    protected void renderForeground(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        Component title = getTitle();
        gfx.drawString(font, title, panelX + PANEL_W / 2 - font.width(title) / 2, panelY + 4, 0x3D3C48, false);
        if (saveError != null) {
            var lines = font.split(saveError, PANEL_W - 64);
            if (!lines.isEmpty())
                gfx.drawString(font, lines.getFirst(), panelX + 8, panelY + panelH - 19, 0xD04040, false);
        }
        super.renderForeground(gfx, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics gfx, int mouseX, int mouseY) {}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            int thumbY = scrollbarThumbY(renderedScroll);
            int thumbH = scrollbarThumbHeight();
            if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                scrollbarGrabOffset = mouseY - thumbY;
            } else {
                scrollbarGrabOffset = thumbH / 2.0;
                dragScrollbarTo(mouseY);
            }
            return true;
        }
        if (button == 0 && insideViewport(mouseX, mouseY)) {
            int network = networkAt(mouseX, mouseY);
            if (network >= 0) {
                nameBox.setFocused(false);
                noteBox.setFocused(false);
                setFocused(null);
                draggedNetwork = hoveredNetworkDrop = network;
                return true;
            }
        }
        boolean inBottomBar = mouseY >= panelY + panelH - BOTTOM_H && mouseY < panelY + panelH
                && mouseX >= panelX && mouseX < panelX + PANEL_W;
        if (insideViewport(mouseX, mouseY) || inBottomBar)
            return super.mouseClicked(mouseX, mouseY, button);
        nameBox.setFocused(false);
        noteBox.setFocused(false);
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingScrollbar) {
            dragScrollbarTo(mouseY);
            return true;
        }
        if (button == 0 && draggedNetwork >= 0) {
            int target = networkAt(mouseX, mouseY);
            hoveredNetworkDrop = target;
            return true;
        }
        return insideViewport(mouseX, mouseY) && super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        if (button == 0 && draggedNetwork >= 0) {
            int releasedTarget = networkAt(mouseX, mouseY);
            if (releasedTarget >= 0 && releasedTarget != draggedNetwork) {
                UUID moved = networks.remove(draggedNetwork);
                networks.add(releasedTarget, moved);
            }
            draggedNetwork = hoveredNetworkDrop = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (insideViewport(mouseX, mouseY) && maxScroll() > 0) {
            double target = Mth.clamp(scroll.getChaseTarget() - scrollY * 18, 0, maxScroll());
            scroll.chase(target, 0.5, Chaser.EXP);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int scrollbarThumbHeight() {
        return Math.max(12, (int) (viewportH * (viewportH / (double) contentHeight)));
    }

    private int scrollbarTravel() {
        return Math.max(0, viewportH - scrollbarThumbHeight());
    }

    private int scrollbarThumbY(float currentScroll) {
        double max = maxScroll();
        if (max <= 0) return viewportY;
        return viewportY + (int) Math.round(scrollbarTravel() * (currentScroll / max));
    }

    private void dragScrollbarTo(double mouseY) {
        double max = maxScroll();
        int travel = scrollbarTravel();
        if (max <= 0 || travel <= 0) return;
        double thumbTop = Mth.clamp(mouseY - scrollbarGrabOffset, viewportY, viewportY + travel);
        float value = (float) ((thumbTop - viewportY) / travel * max);
        scroll.setValue(value);
        scroll.chase(value, 0.5, Chaser.EXP);
        renderedScroll = value;
        positionWidgets(value);
    }

    private boolean overScrollbar(double x, double y) {
        return maxScroll() > 0 && x >= panelX + SCROLLBAR_X - 2 && x < panelX + SCROLLBAR_X + 5
                && y >= viewportY && y < viewportY + viewportH;
    }

    private boolean insideViewport(double x, double y) {
        return x >= panelX + 7 && x < panelX + PANEL_W - 7 && y >= viewportY && y < viewportY + viewportH;
    }

    private int materialAt(double mouseX, double mouseY) {
        int x = (int) mouseX - (panelX + ELEMENT_X);
        int y = (int) mouseY - (viewportY - (int) renderedScroll + materialBoxY);
        if (x < 0 || x >= ELEMENT_W || y < 0 || y >= materialBoxH) return -1;
        int index = y / SLOT * SLOTS_PER_ROW + x / SLOT;
        return index < materials.size() ? index : -1;
    }

    private int networkAt(double mouseX, double mouseY) {
        if (networks.isEmpty() || !insideViewport(mouseX, mouseY)) return -1;
        int x = (int) mouseX - (panelX + ELEMENT_X);
        int y = (int) mouseY - (viewportY - (int) renderedScroll + networkBoxY);
        if (x < 0 || x >= ELEMENT_W || y < 0 || y >= networkBoxH) return -1;
        int row = y / NETWORK_ROW_STEP;
        int rowY = y % NETWORK_ROW_STEP;
        if (rowY < NETWORK_SLOT_Y || rowY >= NETWORK_CELL_H) return -1;
        int index = row * SLOTS_PER_ROW + x / SLOT;
        return index < networks.size() ? index : -1;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (noteBox.isFocused()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                noteBox.setFocused(false);
                setFocused(null);
            } else {
                noteBox.keyPressed(keyCode, scanCode, modifiers);
            }
            // Consume every key while editing. Printable characters arrive through charTyped(), but allowing an
            // unhandled inventory key such as E to reach the container screen closes the editor.
            return true;
        }
        if (nameBox.isFocused() && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
            nameBox.setFocused(false);
            setFocused(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        discard();
    }

    @Override
    public void removed() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_CLOSE.get(), 1f));
        super.removed();
    }

    /** Keeps the full field clickable while drawing an unbordered EditBox vertically centred within it. */
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

    /** Multiline editor using Minecraft's text model with blueprint-specific 12px visual rows. */
    private static class SpacedMultiLineEditBox extends AbstractWidget {
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

        void setCharacterLimit(int limit) {
            textField.setCharacterLimit(limit);
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
            if (value.isEmpty() && !isFocused()) {
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
