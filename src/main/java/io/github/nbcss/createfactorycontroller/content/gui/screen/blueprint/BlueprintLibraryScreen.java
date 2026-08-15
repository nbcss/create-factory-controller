package io.github.nbcss.createfactorycontroller.content.gui.screen.blueprint;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import org.anti_ad.mc.ipn.api.IPNIgnore;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintPlacement;
import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintStorage;
import io.github.nbcss.createfactorycontroller.content.blueprint.SchematicImport;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.PanelSyncListener;
import io.github.nbcss.createfactorycontroller.content.gui.widget.ActionPromptWidget;
import io.github.nbcss.createfactorycontroller.content.gui.widget.HelpButton;
import io.github.nbcss.createfactorycontroller.content.gui.widget.TooltipIconButton;
import io.github.nbcss.createfactorycontroller.content.packet.BlueprintPlacePacket;
import io.github.nbcss.createfactorycontroller.content.render.SpriteNumbersRender;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Displays the reusable component blueprints available to the controller. */
@IPNIgnore
public class BlueprintLibraryScreen extends AbstractSimiContainerScreen<FactoryControllerMenu>
        implements PanelSyncListener {
    private static final ResourceLocation FRAME = resource("blueprint/frame");
    private static final ResourceLocation BOTTOM_BAR = resource("common/bottom_bar");
    private static final ResourceLocation BOTTOM_VDIV = resource("common/bottom_bar_vdiv");
    private static final ResourceLocation ENTRY_BG = resource("blueprint/library_item_bg");
    private static final ResourceLocation DISPLAY_SLOT = resource("common/display_slot_blue");
    private static final ResourceLocation PLACE_ICON = resource("icons/bp_place");
    private static final ResourceLocation EDIT_ICON = resource("icons/edit");
    private static final ResourceLocation ELLIPSIS_ICON = resource("icons/ellipsis");

    private static final int PANEL_W = 204;
    private static final int HEADER_H = 16;
    private static final int BOTTOM_H = 30;
    private static final int SCROLLBAR_X = 198;
    private static final int ENTRY_W = 162;
    private static final int ENTRY_H = 41;
    private static final int ENTRY_MARGIN = 10;
    private static final int ENTRY_GAP = 6;
    private static final int ENTRY_X = (PANEL_W - ENTRY_W) / 2;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_COUNT = 6;
    private static final int ENTRY_CONTROL_Y = 20;
    private static final int PLACE_BUTTON_X = 119;
    private static final int EDIT_BUTTON_X = 141;
    private static final int MATERIAL_HELD_COLOR = BlueprintPlacement.MATERIAL_HELD_COLOR;
    private static final int MATERIAL_MISSING_COLOR = BlueprintPlacement.MATERIAL_MISSING_COLOR;

    private final FactoryControllerScreen controller;
    private final LerpedFloat scroll = LerpedFloat.linear().startWithValue(0);
    private final List<EntryWidget> entryWidgets = new ArrayList<>();
    private final ActionPromptWidget actionPrompt = new ActionPromptWidget();
    private final Map<Item, Integer> inventoryCounts = new HashMap<>();
    private List<LibraryEntry> blueprints = List.of();

    private HelpButton helpButton;
    private TooltipIconButton openFolderButton;
    private TooltipIconButton closeButton;
    private SchematicImport.Scan importScan;
    private int importIconX;
    private int importIconY;
    private int panelX;
    private int panelY;
    private int panelH;
    private int viewportY;
    private int viewportH;
    private int contentHeight;
    private float renderedScroll;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private int firstEntryWidgetIndex;

    public BlueprintLibraryScreen(FactoryControllerScreen controller) {
        super(controller.getMenu(), Minecraft.getInstance().player.getInventory(),
                Component.translatable("createfactorycontroller.gui.blueprint.library_title"));
        this.controller = controller;
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
        importScan = SchematicImport.scan(Minecraft.getInstance().level);
        blueprints = buildEntries();
        entryWidgets.clear();
        refreshInventoryCounts();

        openFolderButton = new TooltipIconButton(0, 0, AllIcons.I_OPEN_FOLDER);
        openFolderButton.withCallback(this::openBlueprintFolder);
        openFolderButton.setToolTip(Component.translatable("createfactorycontroller.gui.blueprint.open_folder"));
        addWidget(openFolderButton);

        closeButton = new TooltipIconButton(0, 0, AllIcons.I_MTD_CLOSE);
        closeButton.withCallback(this::closeLibrary);
        closeButton.setToolTip(Component.translatable("createfactorycontroller.gui.blueprint.close_library"));
        addWidget(closeButton);

        relayout();

        helpButton = new HelpButton(panelX + PANEL_W - HelpButton.WIDTH - 5, panelY + 3,
                HelpButton.ColorPalette.GENERAL, "blueprint.html");
        addWidget(helpButton);
    }

    private void relayout() {
        int wanted = HEADER_H + contentHeight() + BOTTOM_H + 1;
        panelH = Math.min(height - 48, wanted);
        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;
        viewportY = panelY + HEADER_H;
        viewportH = panelH - HEADER_H - BOTTOM_H - 1;
        contentHeight = contentHeight();
        scroll.setValue(0);
        scroll.chase(0, 0.5, Chaser.EXP);
        renderedScroll = 0;

        openFolderButton.setX(panelX + PANEL_W - 47);
        openFolderButton.setY(panelY + panelH - 24);
        closeButton.setX(panelX + PANEL_W - 25);
        closeButton.setY(panelY + panelH - 24);
        importIconX = panelX + 7;
        importIconY = panelY + panelH - 24;
        updateEntryWidgets(renderedScroll);
    }

    private int contentHeight() {
        if (blueprints.isEmpty()) return ENTRY_MARGIN * 2 + font.lineHeight;
        return ENTRY_MARGIN * 2 + blueprints.size() * ENTRY_H
                + Math.max(0, blueprints.size() - 1) * ENTRY_GAP;
    }

    private void updateEntryWidgets(float currentScroll) {
        updateEntryWidgets(currentScroll, currentScroll);
    }

    private void updateEntryWidgets(float currentScroll, float targetScroll) {
        int scrollTop = (int) Math.min(currentScroll, targetScroll);
        int scrollBottom = (int) Math.max(currentScroll, targetScroll) + viewportH;
        int step = ENTRY_H + ENTRY_GAP;
        int first = 0;
        while (first < blueprints.size() && ENTRY_MARGIN + first * step + ENTRY_H <= scrollTop) first++;
        int end = first;
        while (end < blueprints.size() && ENTRY_MARGIN + end * step < scrollBottom) end++;
        int wantedWidgets = end - first;

        while (entryWidgets.size() < wantedWidgets) {
            EntryWidget widget = new EntryWidget();
            entryWidgets.add(widget);
            addWidget(widget);
        }
        while (entryWidgets.size() > wantedWidgets) {
            EntryWidget widget = entryWidgets.removeLast();
            removeWidget(widget);
        }

        for (int i = 0; i < entryWidgets.size(); i++) {
            int blueprintIndex = first + i;
            EntryWidget widget = entryWidgets.get(i);
            widget.bind(blueprints.get(blueprintIndex));
        }
        firstEntryWidgetIndex = first;
        positionEntryWidgets(currentScroll);
    }

    private void positionEntryWidgets(float currentScroll) {
        int x = panelX + ENTRY_X;
        int step = ENTRY_H + ENTRY_GAP;
        for (int i = 0; i < entryWidgets.size(); i++) {
            int blueprintIndex = firstEntryWidgetIndex + i;
            entryWidgets.get(i).moveTo(x,
                    viewportY + ENTRY_MARGIN + blueprintIndex * step - (int) currentScroll);
        }
    }

    private List<LibraryEntry> buildEntries() {
        List<LibraryEntry> entries = new ArrayList<>();
        if (importScan.ready()) {
            SchematicEntry paste = buildSchematicEntry(importScan.board());
            if (paste != null) entries.add(paste);
        }
        entries.addAll(enumerateBlueprints());
        return entries;
    }

    @Nullable
    private SchematicEntry buildSchematicEntry(SchematicImport.ImportedBoard board) {
        try {
            String note = Component.translatable("createfactorycontroller.gui.blueprint.schematic_note",
                    coords(board.boxMin), coords(board.boxMax)).getString();
            BlueprintStorage.Paste paste = BlueprintStorage.buildPaste(board, board.positions, board.networks,
                    note, Minecraft.getInstance().level.registryAccess());
            return new SchematicEntry(paste, board);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static String coords(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private List<FileEntry> enumerateBlueprints() {
        Path directory = BlueprintStorage.blueprintDirectory();
        if (!Files.isDirectory(directory)) return List.of();
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)
                            .endsWith(BlueprintStorage.EXTENSION))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .map(path -> {
                        String fileName = path.getFileName().toString();
                        return new FileEntry(fileName.substring(0,
                                fileName.length() - BlueprintStorage.EXTENSION.length()));
                    })
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
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
        controller.tickComponentWidgets();
        scroll.tickChaser();
        float clamped = Mth.clamp(scroll.getChaseTarget(), 0, (float) maxScroll());
        if (clamped != scroll.getChaseTarget()) scroll.chase(clamped, 0.5, Chaser.EXP);
        refreshInventoryCounts();
    }

    private void refreshInventoryCounts() {
        inventoryCounts.clear();
        assert Minecraft.getInstance().player != null;
        Inventory inventory = Minecraft.getInstance().player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) inventoryCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
    }

    private boolean isMaterialSufficient(BlueprintStorage.Material material) {
        assert Minecraft.getInstance().player != null;
        return Minecraft.getInstance().player.isCreative() ||
                inventoryCounts.getOrDefault(BuiltInRegistries.ITEM.get(material.item()), 0)
                        >= material.count();
    }

    @Override
    public void onPanelSync() {
        controller.onPanelSync();
    }

    private void openBlueprintFolder() {
        Path directory = BlueprintStorage.blueprintDirectory();
        try {
            Files.createDirectories(directory);
        } catch (IOException ignored) {
            return;
        }
        Util.getPlatform().openUri(directory.toUri());
    }

    private void closeLibrary() {
        Minecraft.getInstance().setScreen(controller);
    }

    private boolean overImportIcon(double x, double y) {
        return x >= importIconX && x < importIconX + 16 && y >= importIconY && y < importIconY + 16;
    }

    /** A small green tick badge at the icon's bottom-right, shown when the selection is importable. */
    private void renderReadyTick(GuiGraphics gfx, int x, int y) {
        gfx.pose().pushPose();
        gfx.pose().translate(x + 7, y + 7, 300);
        gfx.pose().scale(0.65f, 0.65f, 1f);
        gfx.setColor(0.35f, 1f, 0.35f, 1f);
        AllIcons.I_CONFIRM.render(gfx, 0, 0);
        gfx.setColor(1f, 1f, 1f, 1f);
        gfx.pose().popPose();
    }

    /** The import button's tooltip: a description plus a green/red checklist of the import requirements. */
    private List<Component> importTooltip() {
        SchematicImport.Requirements req = importScan.requirements();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("createfactorycontroller.gui.blueprint.import"));
        lines.add(Component.translatable("createfactorycontroller.gui.blueprint.import.tooltip.desc1")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("createfactorycontroller.gui.blueprint.import.tooltip.desc2")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.empty());
        lines.add(Component.translatable("createfactorycontroller.gui.blueprint.import.tooltip.requirements")
                .withColor(0x528FDE));
        lines.add(requirementLine("two_positions", req.twoPositions()));
        lines.add(requirementLine("planar", req.planar()));
        lines.add(requirementLine("within_size", req.withinSize()));
        lines.add(requirementLine("has_component", req.hasComponent()));
        lines.add(requirementLine("uniform_facing", req.uniformFacing()));
        return lines;
    }

    private static Component requirementLine(String key, boolean met) {
        return Component.literal("- ").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("createfactorycontroller.gui.blueprint.import.req." + key)
                        .withStyle(met ? ChatFormatting.GREEN : ChatFormatting.RED));
    }

    @Override
    public void render(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        actionPrompt.render(gfx, font, panelX + PANEL_W / 2, panelY + panelH / 3 * 2);
        if (insideViewport(mouseX, mouseY)) {
            for (EntryWidget widget : entryWidgets)
                if (widget.renderTooltip(gfx, mouseX, mouseY)) return;
        }
        TooltipIconButton.renderFirstTooltip(gfx, font, mouseX, mouseY, openFolderButton, closeButton);
        helpButton.renderTooltip(gfx, font, mouseX, mouseY);
        if (overImportIcon(mouseX, mouseY))
            gfx.renderComponentTooltip(font, importTooltip(), mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        controller.renderBoard(gfx, -1, -1, partialTick, true);
        TiledSpriteRenderer.create(FRAME).render(gfx, panelX, panelY, PANEL_W, panelH - BOTTOM_H + 1);
        TiledSpriteRenderer.create(BOTTOM_BAR).render(gfx, panelX, panelY + panelH - BOTTOM_H, PANEL_W, BOTTOM_H);
        TiledSpriteRenderer.create(BOTTOM_VDIV).render(gfx, panelX + PANEL_W - 53,
                panelY + panelH - BOTTOM_H, 2, BOTTOM_H);

        renderedScroll = Mth.clamp(scroll.getValue(partialTick), 0, (float) maxScroll());
        positionEntryWidgets(renderedScroll);
        gfx.enableScissor(panelX + 7, viewportY, panelX + PANEL_W - 7, viewportY + viewportH);
        renderContent(gfx, mouseX, mouseY, partialTick, renderedScroll);
        gfx.disableScissor();

        renderScrollbar(gfx, renderedScroll, mouseX, mouseY);
        openFolderButton.render(gfx, mouseX, mouseY, partialTick);
        closeButton.render(gfx, mouseX, mouseY, partialTick);
        helpButton.render(gfx, mouseX, mouseY, partialTick);
        AllIcons.I_SCHEMATIC.render(gfx, importIconX, importIconY);
        if (importScan.ready()) {
            gfx.pose().pushPose();
            gfx.pose().translate(importIconX + 10, importIconY + 10, 300);
            gfx.pose().scale(0.65f, 0.65f, 1f);
            gfx.drawString(font, "✔", 0, 0, 0x33FF33);
            gfx.pose().popPose();
        }
    }

    private void renderContent(GuiGraphics gfx, int mouseX, int mouseY, float partialTick, float currentScroll) {
        int top = viewportY + ENTRY_MARGIN - (int) currentScroll;
        if (blueprints.isEmpty()) {
            Component emptyMessage = Component.translatable("createfactorycontroller.gui.blueprint.empty_library");
            gfx.drawString(font, emptyMessage, panelX + (PANEL_W - font.width(emptyMessage)) / 2,
                    top, 0xFFFFFF, false);
            return;
        }
        entryWidgets.forEach(widget -> widget.render(gfx, mouseX, mouseY, partialTick));
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingScrollbar) {
            dragScrollbarTo(mouseY);
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
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (insideViewport(mouseX, mouseY) && maxScroll() > 0) {
            double target = Mth.clamp(scroll.getChaseTarget() - scrollY * 18, 0, maxScroll());
            scroll.chase(target, 0.5, Chaser.EXP);
            updateEntryWidgets(scroll.getValue(), (float) target);
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
        updateEntryWidgets(value);
    }

    private boolean overScrollbar(double x, double y) {
        return maxScroll() > 0 && x >= panelX + SCROLLBAR_X - 2 && x < panelX + SCROLLBAR_X + 5
                && y >= viewportY && y < viewportY + viewportH;
    }

    private boolean insideViewport(double x, double y) {
        return x >= panelX + 7 && x < panelX + PANEL_W - 7 && y >= viewportY && y < viewportY + viewportH;
    }

    @Override
    public void onClose() {
        closeLibrary();
    }

    @Override
    public void removed() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(CreateFactoryController.GAUGE_UI_CLOSE.get(), 1f));
        super.removed();
    }

    private class EntryWidget extends AbstractWidget {
        private LibraryEntry entry;
        private BlueprintStorage.Info info = BlueprintStorage.Info.EMPTY;
        private final TooltipIconButton placeButton;
        private final TooltipIconButton editButton;

        private EntryWidget() {
            super(0, 0, ENTRY_W, ENTRY_H, Component.empty());
            this.placeButton = createButton(PLACE_ICON,
                    Component.translatable("createfactorycontroller.gui.blueprint.place"), this::place);
            this.placeButton.withDeferredTooltip(() -> {
                Component blocked = placeBlockedReason();
                return blocked == null
                        ? List.of(Component.translatable("createfactorycontroller.gui.blueprint.place"))
                        : List.of(Component.translatable("createfactorycontroller.gui.blueprint.place"),
                                blocked.copy().withStyle(ChatFormatting.RED));
            });
            this.editButton = new TooltipIconButton(0, 0, (gfx, x, y) -> entry.secondaryIcon().render(gfx, x, y));
            this.editButton.withCallback(this::edit);
            this.editButton.withDeferredTooltip(() -> List.of(entry.secondaryTooltip()));
        }

        @Nullable
        private Component placeBlockedReason() {
            if (info.placements().isEmpty() || entry.oversized())
                return Component.translatable("createfactorycontroller.gui.blueprint.unplaceable");
            if (menu.components.size() + info.placements().size()
                    > FactoryControllerBlockEntity.maxComponents())
                return Component.translatable("createfactorycontroller.gui.blueprint.capacity_reached");
            if (!hasAllMaterials())
                return Component.translatable("createfactorycontroller.gui.blueprint.missing_materials");
            return null;
        }

        private boolean hasAllMaterials() {
            for (BlueprintStorage.Material material : info.materials())
                if (!isMaterialSufficient(material)) return false;   // already creative-aware
            return true;
        }

        private void place() {
            entry.place();
        }

        private void edit() {
            entry.edit();
        }

        private void bind(LibraryEntry entry) {
            if (this.entry == entry) return;
            this.entry = entry;
            this.info = entry.info();
            setMessage(Component.literal(entry.name()));
        }

        private TooltipIconButton createButton(ResourceLocation icon, Component tooltip, Runnable callback) {
            TooltipIconButton button = new TooltipIconButton(0, 0,
                    (gfx, x, y) -> gfx.blitSprite(icon, x, y, 16, 16));
            button.withCallback(callback);
            button.setToolTip(tooltip);
            return button;
        }

        private void moveTo(int x, int y) {
            setX(x);
            setY(y);
            placeButton.setX(x + PLACE_BUTTON_X);
            placeButton.setY(y + ENTRY_CONTROL_Y);
            editButton.setX(x + EDIT_BUTTON_X);
            editButton.setY(y + ENTRY_CONTROL_Y);
        }

        @Override
        protected void renderWidget(@NotNull GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            TiledSpriteRenderer.create(ENTRY_BG).render(gfx, x - 1, y - 1, ENTRY_W + 2, ENTRY_H + 2);
            if (!entry.icon().isEmpty()) gfx.renderItem(entry.icon(), x + 4, y + 1);
            gfx.drawString(font, ellipsize(entry.name(), nameWidth()), x + nameX(), y + 6, entry.nameColor(), true);
            gfx.fill(x + 3, y + 17, x + ENTRY_W - 3, y + 18, 0xFF576080);
            for (int slot = 0; slot < SLOT_COUNT; slot++)
                gfx.blitSprite(DISPLAY_SLOT, x + 3 + slot * SLOT_SIZE, y + ENTRY_CONTROL_Y,
                        SLOT_SIZE, SLOT_SIZE);
            renderMaterials(gfx);
            placeButton.active = placeBlockedReason() == null;
            editButton.active = entry.editable();
            boolean mouseInsideViewport = insideViewport(mouseX, mouseY);
            int buttonMouseX = mouseInsideViewport ? mouseX : Integer.MIN_VALUE;
            int buttonMouseY = mouseInsideViewport ? mouseY : Integer.MIN_VALUE;
            placeButton.render(gfx, buttonMouseX, buttonMouseY, partialTick);
            editButton.render(gfx, buttonMouseX, buttonMouseY, partialTick);
        }

        private String ellipsize(String text, int maxWidth) {
            if (font.width(text) <= maxWidth) return text;
            String ellipsis = "\u2026";
            return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
        }

        /** Local X where the name starts \u2014 after the 16px icon (+gap) when the entry has one. */
        private int nameX() { return entry.icon().isEmpty() ? 5 : 22; }

        private int nameWidth() { return ENTRY_W - nameX() - 5; }

        private void renderMaterials(GuiGraphics gfx) {
            List<BlueprintStorage.Material> materials = info.materials();
            int x = getX() + 3;
            int y = getY() + ENTRY_CONTROL_Y;
            for (int i = 0; i < visibleMaterialCount(); i++) {
                int slotX = x + i * SLOT_SIZE;
                BlueprintStorage.Material material = materials.get(i);
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(material.item()));
                gfx.renderItem(stack, slotX + 1, y + 1);
                gfx.pose().pushPose();
                gfx.pose().translate(0, 0, 200);
                SpriteNumbersRender.drawCountRightAligned(gfx, Integer.toString(material.count()),
                        slotX + 17, y + 10,
                        isMaterialSufficient(material) ? MATERIAL_HELD_COLOR : MATERIAL_MISSING_COLOR);
                gfx.pose().popPose();
            }
            if (materials.size() > SLOT_COUNT) {
                int slotX = x + (SLOT_COUNT - 1) * SLOT_SIZE;
                gfx.blitSprite(ELLIPSIS_ICON, slotX + 1, y + 1, 16, 16);
            }
        }

        /** Materials with a slot of their own; the last slot becomes an ellipsis once they overflow. */
        private int visibleMaterialCount() {
            int size = info.materials().size();
            return size > SLOT_COUNT ? SLOT_COUNT - 1 : size;
        }

        private boolean renderTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
            if (!isMouseOver(mouseX, mouseY)) return false;
            return renderNameTooltip(gfx, mouseX, mouseY)
                    || renderMaterialTooltip(gfx, mouseX, mouseY)
                    || TooltipIconButton.renderFirstTooltip(gfx, font, mouseX, mouseY,
                    placeButton, editButton);
        }

        private boolean renderNameTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
            int localX = mouseX - getX();
            int localY = mouseY - getY();
            if (localX < nameX() || localX >= nameX() + font.width(ellipsize(entry.name(), nameWidth()))
                    || localY < 6 || localY >= 6 + font.lineHeight) return false;
            gfx.renderComponentTooltip(font, List.of(
                    Component.literal(entry.name()).withStyle(ChatFormatting.BLUE),
                    Component.translatable("createfactorycontroller.gui.blueprint.dimension",
                                    Component.literal(info.width() + "x" + info.height())
                                            .withStyle(ChatFormatting.WHITE))
                            .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            return true;
        }

        private boolean renderMaterialTooltip(GuiGraphics gfx, int mouseX, int mouseY) {
            int localX = mouseX - getX();
            int localY = mouseY - getY();
            if (localX < 3 || localX >= 3 + SLOT_COUNT * SLOT_SIZE
                    || localY < ENTRY_CONTROL_Y || localY >= ENTRY_CONTROL_Y + SLOT_SIZE) return false;
            int slot = (localX - 3) / SLOT_SIZE;
            if (slot >= visibleMaterialCount()) return false;
            BlueprintStorage.Material material = info.materials().get(slot);
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(material.item()));
            gfx.renderTooltip(font, stack, mouseX, mouseY);
            return true;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!insideViewport(mouseX, mouseY)) return false;
            return placeButton.mouseClicked(mouseX, mouseY, button)
                    || editButton.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (!insideViewport(mouseX, mouseY)) return false;
            return placeButton.mouseReleased(mouseX, mouseY, button)
                    || editButton.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return insideViewport(mouseX, mouseY) && super.isMouseOver(mouseX, mouseY);
        }

        @Override
        protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {}
    }

    private interface LibraryEntry {
        String name();
        default int nameColor() { return 0xFFFFFF; }
        /** An item icon drawn before the name, or empty for none. */
        default ItemStack icon() { return ItemStack.EMPTY; }
        BlueprintStorage.Info info();
        default boolean oversized() { return false; }
        default boolean editable() { return true; }
        /** Icon + tooltip of the secondary button — Edit for a file, Save for the schematic paste. */
        default ScreenElement secondaryIcon() { return (gfx, x, y) -> gfx.blitSprite(EDIT_ICON, x, y, 16, 16); }
        default Component secondaryTooltip() {
            return Component.translatable("createfactorycontroller.gui.blueprint.edit");
        }
        void place();
        /** Runs the secondary action — open the editor (file) or the import/save screen (schematic paste). */
        void edit();
    }

    private class FileEntry implements LibraryEntry {
        private final String name;
        private WeakReference<BlueprintStorage.Info> infoCache = new WeakReference<>(null);
        private long fileSize = -1;

        private FileEntry(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public BlueprintStorage.Info info() {
            BlueprintStorage.Info cached = infoCache.get();
            if (cached != null) return cached;
            Path path = BlueprintStorage.blueprintPath(name);
            try {
                cached = BlueprintStorage.read(path);
                fileSize = Files.size(path);
            } catch (IOException | RuntimeException ignored) {
                cached = BlueprintStorage.Info.EMPTY;
                fileSize = -1;
            }
            infoCache = new WeakReference<>(cached);
            return cached;
        }

        @Override
        public boolean oversized() {
            info();   // ensure fileSize is populated
            return fileSize > BlueprintPlacePacket.MAX_PAYLOAD_BYTES;
        }

        @Override
        public void place() {
            byte[] payload;
            try {
                payload = BlueprintStorage.payload(BlueprintStorage.blueprintPath(name));
            } catch (IOException | RuntimeException exception) {
                actionPrompt.show(BlueprintErrors.describe(
                        "createfactorycontroller.gui.blueprint.load_failed", exception));
                return;
            }
            controller.beginBlueprintPlacement(new BlueprintPlacement(name, info(), payload));
            Minecraft.getInstance().setScreen(controller);
        }

        @Override
        public void edit() {
            BlueprintStorage.Info loaded;
            try {
                loaded = BlueprintStorage.read(BlueprintStorage.blueprintPath(name));
            } catch (IOException | RuntimeException exception) {
                actionPrompt.show(BlueprintErrors.describe(
                        "createfactorycontroller.gui.blueprint.load_failed", exception));
                return;
            }
            Minecraft.getInstance().setScreen(new BlueprintEditScreen(controller, name, loaded));
        }
    }

    private class SchematicEntry implements LibraryEntry {
        private final BlueprintStorage.Paste paste;
        private final SchematicImport.ImportedBoard board;

        private SchematicEntry(BlueprintStorage.Paste paste, SchematicImport.ImportedBoard board) {
            this.paste = paste;
            this.board = board;
        }

        @Override
        public String name() {
            return Component.translatable("createfactorycontroller.gui.blueprint.schematic_entry").getString();
        }

        @Override public int nameColor() { return 0xFFAA00; }   // gold
        @Override public ItemStack icon() { return AllItems.SCHEMATIC_AND_QUILL.asStack(); }
        @Override public BlueprintStorage.Info info() { return paste.info(); }
        @Override public ScreenElement secondaryIcon() { return AllIcons.I_CONFIG_SAVE; }

        @Override
        public Component secondaryTooltip() {
            return Component.translatable("createfactorycontroller.gui.blueprint.save");
        }

        @Override
        public boolean oversized() {
            return paste.payload().length > BlueprintPlacePacket.MAX_PAYLOAD_BYTES;
        }

        @Override
        public void place() {
            controller.beginBlueprintPlacement(BlueprintPlacement.schematic(
                    name(), paste.info(), paste.payload(), board.networks, board.boxMin, board.boxMax));
            Minecraft.getInstance().setScreen(controller);
        }

        /** "Save": open the import save screen (name / note / materials) — the old import button's action. */
        @Override
        public void edit() {
            Minecraft.getInstance().setScreen(new BlueprintImportScreen(controller, board));
        }
    }
}
