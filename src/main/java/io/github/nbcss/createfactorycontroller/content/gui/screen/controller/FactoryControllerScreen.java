package io.github.nbcss.createfactorycontroller.content.gui.screen.controller;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import io.github.nbcss.createfactorycontroller.content.render.ComponentRenderingHelper;
import org.anti_ad.mc.ipn.api.IPNIgnore;
import io.github.nbcss.createfactorycontroller.ClientConfig;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.CreateFactoryControllerClient;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.*;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionResolver;
import io.github.nbcss.createfactorycontroller.content.gui.GhostPreview;
import io.github.nbcss.createfactorycontroller.content.gui.screen.ConnectionPathResolver;
import io.github.nbcss.createfactorycontroller.content.gui.screen.ControllerSettingScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.NetworkSettingsScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.PanelSyncListener;
import io.github.nbcss.createfactorycontroller.content.gui.screen.blueprint.BlueprintLibraryScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.blueprint.BlueprintPlaceScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.blueprint.BlueprintSaveScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.states.BatchMoveState;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.states.DragSelectionState;
import io.github.nbcss.createfactorycontroller.content.gui.widget.CollapsiblePlayerInventory;
import io.github.nbcss.createfactorycontroller.content.gui.widget.ComponentWidgetRegistry;
import io.github.nbcss.createfactorycontroller.content.gui.widget.ConnectionWidget;
import io.github.nbcss.createfactorycontroller.content.gui.widget.HelpButton;
import io.github.nbcss.createfactorycontroller.content.gui.widget.IndicatorColumnWidget;
import io.github.nbcss.createfactorycontroller.content.gui.widget.NetworkSelectorWidget;
import io.github.nbcss.createfactorycontroller.content.gui.widget.GraphicButton;
import io.github.nbcss.createfactorycontroller.content.gui.widget.VirtualComponentWidget;
import io.github.nbcss.createfactorycontroller.content.helper.Rect2i;
import io.github.nbcss.createfactorycontroller.content.packet.CycleArrowModePacket;
import io.github.nbcss.createfactorycontroller.content.packet.CycleConnectionArrowModePacket;
import io.github.nbcss.createfactorycontroller.content.packet.CycleOperationModePacket;
import net.minecraft.core.registries.BuiltInRegistries;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import io.github.nbcss.createfactorycontroller.content.render.VirtualConnectionRenderer;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.ChatFormatting;
import io.github.nbcss.createfactorycontroller.content.packet.AddConnectionPacket;
import io.github.nbcss.createfactorycontroller.content.packet.AttachComponentPacket;
import io.github.nbcss.createfactorycontroller.content.packet.GaugeSetItemPacket;
import io.github.nbcss.createfactorycontroller.content.packet.BatchMoveComponentPacket;
import io.github.nbcss.createfactorycontroller.content.packet.MoveComponentPacket;
import io.github.nbcss.createfactorycontroller.content.packet.RemoveConnectionPacket;
import io.github.nbcss.createfactorycontroller.content.packet.RenameControllerPacket;
import io.github.nbcss.createfactorycontroller.content.packet.RetuneCarriedPacket;
import io.github.nbcss.createfactorycontroller.content.packet.ReturnCarriedPacket;
import io.github.nbcss.createfactorycontroller.content.packet.ReverseConnectionPacket;
import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintPlacement;
import io.github.nbcss.createfactorycontroller.content.blueprint.BlueprintStorage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector2d;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@IPNIgnore
public class FactoryControllerScreen extends AbstractSimiContainerScreen<FactoryControllerMenu> implements PanelSyncListener {
    // Responsive sizing — all in GUI-scaled pixels.
    private static final int SIDE_MARGIN = 160;
    private static final int VERTICAL_MARGIN = 10;
    private static final int MIN_IMAGE_W = 215;
    private static final int MIN_IMAGE_H = 200;
    // package visible for other overlays
    static final int CANVAS_SIDE_PADDING = 9;
    static final int CANVAS_TOP_PADDING = 16;
    static final int CANVAS_BOTTOM_PADDING = 9;
    private static final int CANVAS_COMPONENT_SIZE = 16;

    private static final int BOARD_MIN_PX = -FactoryControllerBlockEntity.BOARD_LIMIT * CANVAS_COMPONENT_SIZE;
    private static final int BOARD_MAX_PX = (FactoryControllerBlockEntity.BOARD_LIMIT + 1) * CANVAS_COMPONENT_SIZE;

    // Canvas view state
    private static final int MAX_ZOOM_LEVEL = 10;
    private static final int MIN_ZOOM_LEVEL = -20;
    // On-screen pan speed (px per second) when holding a movement key.
    private static final double KEY_PAN_SPEED = 160.0;
    /** Wall-clock time (ms) of the previous keyboard-pan frame; 0 until the first frame. */
    private long lastPanFrameMs = 0;
    /** Movement (pan) keys whose press actually reached this screen */
    private final java.util.Set<Integer> heldPanKeys = new java.util.HashSet<>();
    private double viewX = 0;
    private double viewY = 0;
    private int zoomLevel = 0;


    /** Session-only (never serialized) per-controller camera memory: reopening a controller restores its last
     *  pan/zoom. Keyed by dimension + block position (see {@link #viewKey()}); cleared on disconnect / restart. */
    private record ViewCacheEntry(double x, double y, int zoom) {}
    private static final Cache<String, ViewCacheEntry> VIEW_CACHE = CacheBuilder.newBuilder()
            .maximumSize(64).concurrencyLevel(1).build();

    /** Drops all cached camera views — called on disconnect so a different world/server starts clean. */
    public static void clearViewCache() {
        VIEW_CACHE.invalidateAll();
    }

    /** Session-cache key: dimension id + controller position (e.g. {@code minecraft:overworld@10, 64, -20}). */
    private String viewKey() {
        var level = Minecraft.getInstance().level;
        String dim = level != null ? level.dimension().location().toString() : "?";
        return dim + "@" + menu.controllerPos.toShortString();
    }

    @Nullable private CollapsiblePlayerInventory playerInventory = null;

    // Settings button (top-right of the board); opens the client-side background-picker overlay.
    private static final int SETTINGS_BTN_W = 23;
    private static final int SETTINGS_BTN_H = 24;
    private static final ResourceLocation SETTINGS_BTN_SPRITE =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/tool_bar/settings");
    private static final ResourceLocation SETTINGS_BTN_HOVER_SPRITE =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/tool_bar/settings_hover");
    private static final ResourceLocation BP_OPEN_BTN_SPRITE =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/tool_bar/bp_open");
    private static final ResourceLocation BP_OPEN_BTN_HOVER_SPRITE =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/tool_bar/bp_open_hover");
    private static final ResourceLocation BP_SAVE_BTN_SPRITE =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/tool_bar/bp_save");
    private static final ResourceLocation BP_SAVE_BTN_HOVER_SPRITE =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/tool_bar/bp_save_hover");
    @Nullable private GraphicButton settingsButton = null;
    @Nullable private GraphicButton blueprintLoadButton = null;
    @Nullable private GraphicButton blueprintSaveButton = null;
    @Nullable private HelpButton helpButton = null;

    // Decorative controller block model in the board's bottom-left corner (purely cosmetic).
    private static final int CONTROLLER_MODEL_SCALE = 4;

    private static final ResourceLocation BACKGROUND_TEXTURE_PATH = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/controller_background/");
    private static final ResourceLocation FRAME_SPRITE = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/frame");
    // Reticle drawn over the gauge being acted on (connect/relocate). White 16×16 source, tinted.
    private static final ResourceLocation TARGET_SPRITE = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/target");
    // Edit-name cue drawn after the controller name when idle (indicator only). 9×9 sprite.
    private static final ResourceLocation RENAME_BUTTON_SPRITE = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/rename_button");
    private static final int RENAME_BUTTON_SIZE = 9;
    // Top-left status icons: capacity (count/max) and zoom (xN), drawn right of the network selector.
    private static final ResourceLocation CAPACITY_ICON = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/capacity_icon");
    private static final int CAPACITY_ICON_SIZE = 8;
    private static final ResourceLocation ZOOM_ICON = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/zoom_icon");
    private static final int ZOOM_ICON_SIZE = 10;
    // Interaction state
    @Nullable private VirtualComponentPosition hoveredPosition = null;
    @Nullable private ConnectionWidget hoveredConn = null;
    @Nullable private Connection selectedConnection = null;
    // All wires under the cursor this frame (after carried-item / pass-through filters), in stable order.
    private final List<ConnectionWidget> hoverHits = new ArrayList<>();
    /** Wall-clock deadline for the current connection tooltip; max value means no connection is hovered. */
    private long connTooltipShowAtMs = Long.MAX_VALUE;
    private boolean connArrowLocked = false;
    // Last cursor position seen by render()
    private int lastMouseX, lastMouseY;

    private final ComponentRenderingHelper componentRenderingHelper = new ComponentRenderingHelper();

    private final Map<VirtualComponentPosition, VirtualComponentWidget> componentWidgets = new LinkedHashMap<>();
    private final List<VirtualComponentWidget> visibleComponents = new ArrayList<>();

    // Board action mode (e.g. connecting).
    @Nullable private VirtualComponentPosition pendingConnectionTarget = null;
    @Nullable private VirtualComponentPosition pendingRelocateTarget = null;
    @Nullable private BlueprintPlacement pendingPlacement = null;
    /** Connection-mode preview bend override */
    private int previewArrowMode = -1;
    @Nullable private VirtualComponentPosition previewArrowTarget = null;
    /** Long-lived mode prompt (connect / relocate). */
    @Nullable private Component persistentActionPrompt = null;
    /** Most recent transient result/chime. */
    @Nullable private Component temporaryActionPrompt = null;
    /** Wall-clock expiry of {@link #temporaryActionPrompt}; it fades during its final second. */
    private long temporaryActionPromptExpiry = 0;

    //pan dragging state
    private boolean isDragging = false;
    private boolean panMoved = false;
    private boolean clearSelectionOnPanClick = false;
    private double panStartX, panStartY;

    // ── Selection mode (client-only) ─────────────────────────────────────────
    /** Components currently marked "selected" (gold reticle). Client-only; never serialized or synced. */
    private final Set<VirtualComponentPosition> selected = new LinkedHashSet<>();
    // Drag-selection origin is stored in canvas-world coordinates so pan, zoom, or GUI resizing cannot move it.
    @Nullable private DragSelectionState dragSelection = null;
    // Batch relocate drag (normal mode, started by pressing a selected component).
    private final BatchMoveState batchMove = new BatchMoveState();

    // Cached translucent preview of the components a relocate/batch-move would shift (rebuilt when the moving set
    // changes or the board re-syncs); rendered each frame under the current move delta.
    @Nullable private GhostPreview movePreview = null;
    @Nullable private Set<VirtualComponentPosition> movePreviewKey = null;

    // Network selector widget
    private NetworkSelectorWidget networkSelector;
    private IndicatorColumnWidget indicatorColumn;

    @Nullable private Rect2i capacityLabelBounds = null;
    @Nullable private Rect2i zoomLabelBounds = null;
    @Nullable private Rect2i nameAreaBounds = null;

    private static final int NAME_COLOR = 0x582424;
    @Nullable private EditBox nameBox;

    public FactoryControllerScreen(FactoryControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // Restore this controller's last-session pan/zoom (clampView in init() re-fits it to the window).
        ViewCacheEntry saved = VIEW_CACHE.getIfPresent(viewKey());
        if (saved != null) { viewX = saved.x(); viewY = saved.y(); zoomLevel = saved.zoom(); }
        // Play controller UI open SFX when the screen is constructed.
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(CreateFactoryController.CONTROLLER_UI_OPEN.get(), 1f));
    }

    @Override
    protected void init() {
        Window window = Minecraft.getInstance().getWindow();
        int scaledW = window.getGuiScaledWidth();
        int scaledH = window.getGuiScaledHeight();

        int imageW = Math.max(MIN_IMAGE_W, scaledW - 2 * SIDE_MARGIN);
        int imageH = Math.max(MIN_IMAGE_H, scaledH - 2 * VERTICAL_MARGIN);

        setWindowSize(imageW, imageH);
        setWindowOffset(0, 0);

        super.init();

        clampView();   // window size changed → keep the pan inside the board

        rebuildGaugeWidgets();

        if (playerInventory == null)
            playerInventory = new CollapsiblePlayerInventory(menu, font, playerInventoryTitle);
        playerInventory.layout(leftPos, topPos, imageWidth, scaledH);
        addWidget(playerInventory);

        if (settingsButton != null) removeWidget(settingsButton);
        settingsButton = new GraphicButton(settingsButtonX(), settingsButtonY(), SETTINGS_BTN_W, SETTINGS_BTN_H,
                () -> {
                    clearSelection();   // entering an overlay clears the selection
                    Minecraft.getInstance().setScreen(new ControllerSettingScreen(this));
                    return true;
                })
                .addGraphic(GraphicButton.DISPLAY_NORMAL, SETTINGS_BTN_SPRITE)
                .addGraphic(GraphicButton.DISPLAY_HOVER, SETTINGS_BTN_HOVER_SPRITE)
                .addTooltip(Component.translatable("createfactorycontroller.gui.controller_settings"));
        addWidget(settingsButton);

        if (blueprintLoadButton != null) removeWidget(blueprintLoadButton);
        blueprintLoadButton = new GraphicButton(settingsButtonX(), blueprintButtonY(), SETTINGS_BTN_W, SETTINGS_BTN_H,
                () -> {
                    Minecraft.getInstance().setScreen(new BlueprintLibraryScreen(this));
                    return true;
                })
                .addGraphic(GraphicButton.DISPLAY_NORMAL, BP_OPEN_BTN_SPRITE)
                .addGraphic(GraphicButton.DISPLAY_HOVER, BP_OPEN_BTN_HOVER_SPRITE)
                .addTooltip(Component.translatable("createfactorycontroller.gui.blueprint.load_blueprint"));
        addWidget(blueprintLoadButton);

        if (blueprintSaveButton != null) removeWidget(blueprintSaveButton);
        blueprintSaveButton = new GraphicButton(settingsButtonX(), blueprintButtonY(), SETTINGS_BTN_W, SETTINGS_BTN_H,
                () -> {
                    Minecraft.getInstance().setScreen(new BlueprintSaveScreen(this, new LinkedHashSet<>(selected)));
                    return true;
                })
                .addGraphic(GraphicButton.DISPLAY_NORMAL, BP_SAVE_BTN_SPRITE)
                .addGraphic(GraphicButton.DISPLAY_HOVER, BP_SAVE_BTN_HOVER_SPRITE)
                .addTooltip(Component.translatable("createfactorycontroller.gui.blueprint.save_blueprint"));
        addWidget(blueprintSaveButton);
        updateBlueprintButtonVisibility();

        // Rebuild the rename field at the new layout, preserving any in-progress edit across resize.
        String currentName = nameBox != null ? nameBox.getValue() : menu.controllerName;
        boolean wasFocused = nameBox != null && nameBox.isFocused();
        nameBox = new EditBox(new NoShadowFontWrapper(font),
                leftPos + 10, topPos + 4, imageWidth - 20, 10, Component.empty());
        nameBox.setMaxLength(FactoryControllerBlockEntity.MAX_NAME_LENGTH);
        nameBox.setBordered(false);
        nameBox.setTextColor(NAME_COLOR);
        nameBox.setValue(currentName);
        if (wasFocused) nameBox.setFocused(true);
        addWidget(nameBox);

        if (helpButton != null) removeWidget(helpButton);
        helpButton = new HelpButton(leftPos + imageWidth - HelpButton.WIDTH - 5, topPos + 3,
                HelpButton.ColorPalette.BRASS, "dashboard.html");
        buildHelpTooltip().forEach(helpButton::addTooltip);
        addWidget(helpButton);

        int selectorX = leftPos + CANVAS_SIDE_PADDING + 6;
        int selectorY = topPos + CANVAS_TOP_PADDING + 6;
        if (networkSelector == null)
            networkSelector = new NetworkSelectorWidget(selectorX, selectorY, menu, this::retuneCarried, this::onNetworkSelected);
        else
            networkSelector.setPosition(selectorX, selectorY);
        if (indicatorColumn == null)
            indicatorColumn = new IndicatorColumnWidget(selectorX, selectorY, menu);
        else
            indicatorColumn.refresh();
        indicatorColumn.setPosition(
                selectorX,
                selectorY + 28);

        lastPanFrameMs = 0;
        heldPanKeys.clear();
    }

    private List<Component> buildHelpTooltip() {
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("createfactorycontroller.gui.help.allowed_components")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x528FDE))));
        for (VirtualComponentBehaviour.Type type : ComponentRegistry.types()) {
            if (type.items().isEmpty()) continue;
            for (ResourceLocation itemLocation : type.items()) {
                ItemStack item = new ItemStack(BuiltInRegistries.ITEM.get(itemLocation));
                tooltip.add(Component.literal("- ").withStyle(ChatFormatting.GRAY)
                        .append(item.getHoverName().copy().withColor(type.color())));
            }
        }
        return tooltip;
    }

    /** Sends the edited controller name to the server (if changed) and leaves edit mode. */
    private void commitName() {
        if (nameBox == null) return;
        nameBox.setFocused(false);
        collapseNameSelection();
        String value = nameBox.getValue().strip();
        if (value.equals(menu.controllerName)) return;
        menu.controllerName = value;
        PacketDistributor.sendToServer(new RenameControllerPacket(menu.controllerPos, value));
    }

    private void collapseNameSelection() {
        if (nameBox == null) return;
        nameBox.setCursorPosition(nameBox.getValue().length());
        nameBox.setHighlightPos(nameBox.getCursorPosition());
    }

    private void onNetworkSelected(@Nullable UUID network) {
        MutableComponent message = network == null
                ? Component.translatable("createfactorycontroller.gui.prompt.no_selected_network")
                : Component.translatable("createfactorycontroller.gui.prompt.selected_network", menu.networkName(network));
        setTimedPrompt(message.withStyle(ChatFormatting.WHITE), 3000);
    }

    private void retuneCarried(boolean clear, @Nullable UUID network) {
        ItemStack carried = menu.getCarried();
        if (!ComponentRegistry.containsNetworkItem(carried)) return;
        RetuneCarriedPacket.apply(carried, clear ? null : network);
        menu.setCarried(carried);
        PacketDistributor.sendToServer(new RetuneCarriedPacket(clear, network));
    }

    @Override
    public void onClose() {
        commitName();   // persist any pending rename before the screen goes away
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(CreateFactoryController.CONTROLLER_UI_CLOSE.get(), 1f));
        super.onClose();
    }

    private int settingsButtonX() {
        return leftPos + imageWidth - CANVAS_SIDE_PADDING - SETTINGS_BTN_W - 8;
    }

    private int settingsButtonY() {
        return topPos + CANVAS_TOP_PADDING + 8;
    }

    private int blueprintButtonY() {
        return settingsButtonY() + SETTINGS_BTN_H;
    }

    private void updateBlueprintButtonVisibility() {
        if (blueprintLoadButton != null) blueprintLoadButton.visible = selected.isEmpty();
        if (blueprintSaveButton != null) blueprintSaveButton.visible = !selected.isEmpty();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (nameBox != null && !nameBox.isFocused())
            collapseNameSelection();
        tickComponentWidgets();
    }

    public void tickComponentWidgets() {
        componentWidgets.values().forEach(VirtualComponentWidget::tick);
    }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft.getInstance().getProfiler().push("FactoryControllerScreen");

        tickKeyboardPan();   // pan from held movement keys before the board is drawn this frame
        updateDragSelectionMovement(mouseX, mouseY);
        batchMove.update(cellAt(mouseX, mouseY));   // keep the batch-move ghost tracking the cursor across pans
        // Keep a cycled wire pinned only while the cursor remains where it was when the key was pressed.
        if (connArrowLocked && (mouseX != lastMouseX || mouseY != lastMouseY)) connArrowLocked = false;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        updateBlueprintButtonVisibility();

        super.render(graphics, mouseX, mouseY, partialTick);

        VirtualComponentWidget hovered = hoveredConn == null ? componentWidgetAt(hoveredPosition) : null;
        // No hover tooltips while a drag selection or batch relocate is in progress.
        boolean selectionDragging = dragSelection != null || batchMove.isActive();
        if (pendingConnectionTarget == null && pendingRelocateTarget == null && pendingPlacement == null
                && !selectionDragging) {
            if (hoveredConn != null) {
                // Connection hover suppresses component hover; show its tooltip once the hover passes the delay.
                if (Util.getMillis() >= connTooltipShowAtMs)
                    graphics.renderComponentTooltip(font, connectionTooltipLines(), mouseX, mouseY);
            } else if (hovered != null)
                graphics.renderComponentTooltip(font, hovered.getTooltip(menu, selected.contains(hoveredPosition)), mouseX, mouseY);
            else if (networkSelector.isMouseOver(mouseX, mouseY))
                graphics.renderComponentTooltip(font, networkSelector.getTooltipLines(), mouseX, mouseY);
            else if (indicatorColumn.isMouseOver(mouseX, mouseY))
                graphics.renderComponentTooltip(font, indicatorColumn.getTooltipLines(mouseX, mouseY), mouseX, mouseY);
            else if (capacityLabelBounds != null
                    && capacityLabelBounds.contains(mouseX, mouseY, Rect2i.Boundary.HALF_OPEN))
                graphics.renderTooltip(font, Component.translatable("createfactorycontroller.gui.capacity"), mouseX, mouseY);
            else if (zoomLabelBounds != null
                    && zoomLabelBounds.contains(mouseX, mouseY, Rect2i.Boundary.HALF_OPEN))
                graphics.renderTooltip(font, Component.translatable("createfactorycontroller.gui.zoom"), mouseX, mouseY);
            else if (settingsButton != null && settingsButton.isMouseOver(mouseX, mouseY))
                graphics.renderTooltip(font, settingsButton.getTooltipText(), mouseX, mouseY);
            else if (blueprintLoadButton != null && blueprintLoadButton.isMouseOver(mouseX, mouseY))
                graphics.renderTooltip(font, blueprintLoadButton.getTooltipText(), mouseX, mouseY);
            else if (blueprintSaveButton != null && blueprintSaveButton.isMouseOver(mouseX, mouseY))
                graphics.renderTooltip(font, blueprintSaveButton.getTooltipText(), mouseX, mouseY);
            else if (helpButton != null && helpButton.isMouseOver(mouseX, mouseY))
                graphics.renderTooltip(font, helpButton.getTooltipText(font, HelpButton.TOOLTIP_WIDTH), mouseX, mouseY);
        }

        Minecraft.getInstance().getProfiler().pop();
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderBoard(graphics, mouseX, mouseY, partialTick, false);

        // Decorative controller block model anchored in the bottom-left corner, drawn over the board.
        RenderSystem.enableBlend();
        GuiGameElement.of(new ItemStack(CreateFactoryController.FACTORY_CONTROLLER_ITEM.get()))
                .scale(CONTROLLER_MODEL_SCALE)
                .render(graphics, leftPos - 74, topPos + imageHeight - 80);
        graphics.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);

        // Inventory panel + its expand button, lifted above canvas gauge icons.
        RenderSystem.disableDepthTest();
        if (playerInventory != null) playerInventory.render(graphics, mouseX, mouseY, partialTick);

        Component persistentPrompt = selectionStatusPrompt(mouseX, mouseY);
        if (persistentPrompt == null) persistentPrompt = persistentActionPrompt;

        Component temporaryPrompt = temporaryActionPrompt;
        int temporaryAlpha = 255;
        if (temporaryPrompt != null) {
            long remaining = temporaryActionPromptExpiry - Util.getMillis();
            if (remaining <= 0) {
                temporaryActionPrompt = null;
                temporaryPrompt = null;
            } else {
                temporaryAlpha = (int) (Mth.clamp(remaining / 1000f, 0f, 1f) * 255f);
            }
        }

        if (persistentPrompt != null || (temporaryPrompt != null && temporaryAlpha > 4)) {
            int invTop;
            invTop = playerInventory != null ? playerInventory.getY() : height;
            // 8-direction outline (like the gauge count labels) so the prompt reads over any canvas content;
            // each prompt keeps its own colour, with the transient fade folded into its alpha.
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            Matrix4f matrix = graphics.pose().last().pose();
            int baseY = invTop - 14;
            if (persistentPrompt != null)
                drawActionPrompt(graphics, matrix, persistentPrompt, baseY, 255);
            if (temporaryPrompt != null && temporaryAlpha > 4) {
                int temporaryY = persistentPrompt == null ? baseY : baseY - font.lineHeight - 2;
                drawActionPrompt(graphics, matrix, temporaryPrompt, temporaryY, temporaryAlpha);
            }
            graphics.flush();
            graphics.pose().popPose();
        }
        RenderSystem.enableDepthTest();
    }

    private void drawActionPrompt(GuiGraphics graphics, Matrix4f matrix, Component prompt, int y, int alpha) {
        int x = leftPos + imageWidth / 2 - font.width(prompt) / 2;
        font.drawInBatch8xOutline(prompt.getVisualOrderText(), x, y,
                (alpha << 24) | 0xFFFFFF, alpha << 24,
                matrix, graphics.bufferSource(), LightTexture.FULL_BRIGHT);
    }

    /**
     * Renders the board itself — tiled background, gauges, connection arrows, frame and the network
     * selector — but <b>not</b> the player inventory. Needs to be called by overlay to display controller background.
     */
    public void renderBoard(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick, boolean inOverlay) {
        var profiler = Minecraft.getInstance().getProfiler();
        profiler.push("FactoryController_renderBoard");

        Rect2i canvas = canvasArea();
        int centerX = (canvas.minX() + canvas.maxX()) / 2;
        int centerY = (canvas.minY() + canvas.maxY()) / 2;

        // Visible canvas-world pixel bounds
        int minX = (int) Math.floor(viewX + (canvas.minX() - centerX) / getZoomFactor());
        int minY = (int) Math.floor(viewY + (canvas.minY() - centerY) / getZoomFactor());
        int maxX = (int) Math.ceil(viewX + (canvas.maxX() - centerX) / getZoomFactor());
        int maxY = (int) Math.ceil(viewY + (canvas.maxY() - centerY) / getZoomFactor());
        Rect2i visibleArea = Rect2i.fromBounds(minX, minY, maxX, maxY);

        hoveredPosition = isInCanvasArea(mouseX, mouseY) ? at(mouseX, mouseY, centerX, centerY) : null;

        profiler.push("canvas");

        // Canvas-world → screen pose (translate to centre, scale by zoom, translate by the pan).
        graphics.enableScissor(canvas.minX(), canvas.minY(), canvas.maxX(), canvas.maxY());
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale((float) getZoomFactor(), (float) getZoomFactor(), (float) getZoomFactor());
        graphics.pose().translate((float) -viewX, (float) -viewY, 0);

        // Cursor in canvas-world coords (so widgets can hit-test sub-regions).
        Vector2d worldMouse = worldAt(mouseX, mouseY, centerX, centerY);

        // Background tiles rendering
        int backgroundScale = ClientConfig.upscaleBackgroundTexture() ? 2 : 1;
        int backgroundTileSize = CANVAS_COMPONENT_SIZE * backgroundScale;
        int bgStartX = Math.floorDiv(minX, backgroundTileSize) * backgroundTileSize;
        int bgStartY = Math.floorDiv(minY, backgroundTileSize) * backgroundTileSize;
        int bgEndX   = Math.floorDiv(maxX, backgroundTileSize) * backgroundTileSize + backgroundTileSize;
        int bgEndY   = Math.floorDiv(maxY, backgroundTileSize) * backgroundTileSize + backgroundTileSize;
        graphics.pose().pushPose();
        graphics.pose().scale(backgroundScale, backgroundScale, 1);
        TiledSpriteRenderer.create(BACKGROUND_TEXTURE_PATH.withSuffix(ClientConfig.getControllerBackground() + ".png"), 0, 0,
                        new GuiSpriteScaling.Tile(CANVAS_COMPONENT_SIZE, CANVAS_COMPONENT_SIZE))
                .render(graphics,
                        bgStartX / backgroundScale, bgStartY / backgroundScale,
                        (bgEndX - bgStartX) / backgroundScale, (bgEndY - bgStartY) / backgroundScale);
        graphics.pose().popPose();

        profiler.push("cull");
        updateVisibleComponents(visibleArea);
        List<ConnectionWidget> connWidgets = buildConnectionWidgets(visibleArea);

        profiler.popPush("back");
        // Cull components and connections that fall outside the visible canvas rectangle
        for (VirtualComponentWidget component : visibleComponents)
            component.renderBack(componentRenderingHelper.params(graphics, worldMouse, component.position().equals(hoveredPosition)));
        componentRenderingHelper.flushBuffers(graphics);

        profiler.popPush("connection");
        hoverHits.clear();
        boolean carrying = !menu.getCarried().isEmpty();   // holding an item skips connection hover entirely
        boolean overComponent = componentWidgetAt(hoveredPosition) != null;
        // No connection hover while dragging a selection rectangle or a batch relocate.
        if (isInCanvasArea(mouseX, mouseY) && !carrying && dragSelection == null && !batchMove.isActive()
                && pendingConnectionTarget == null && pendingRelocateTarget == null) {
            for (ConnectionWidget w : connWidgets) {
                if (overComponent && !hoveredPosition.equals(w.connection.from)
                                  && !hoveredPosition.equals(w.connection.to)) continue;
                if (w.hitTest(worldMouse.x, worldMouse.y)) hoverHits.add(w);
            }
        }

        hoveredConn = reconcileConnectionSelection(connWidgets);
        // Flush the ordinary wires first so the selected wire and its highlight are always composited on top
        for (ConnectionWidget widget : connWidgets)
            if (widget != hoveredConn) widget.render(graphics, menu);
        graphics.flush();
        if (hoveredConn != null) {
            hoveredConn.renderHighlight(graphics);
            hoveredConn.render(graphics, menu);
            graphics.flush();
        }

        profiler.popPush("front");
        for (VirtualComponentWidget component : visibleComponents)
            component.renderFront(componentRenderingHelper.params(graphics, worldMouse, component.position().equals(hoveredPosition)));
        componentRenderingHelper.flushBuffers(graphics);

        profiler.pop();
        renderSelectedNetworkMask(graphics);
        // The blueprint ghost owns the cursor cell while placing; the normal hover reticle would fight it.
        if (hoveredConn == null && pendingPlacement == null) renderHoverTarget(graphics);
        renderSelectionTargets(graphics, worldMouse);

        profiler.push("ghost");
        renderPlacementGhost(graphics);

        profiler.popPush("overlay");
        // Count labels last, on top of the hover/selection target marks so the reticle never covers the number.
        for (VirtualComponentWidget component : visibleComponents)
            component.renderOverlay(componentRenderingHelper.params(graphics, worldMouse, component.position().equals(hoveredPosition)));
        componentRenderingHelper.flushBuffers(graphics);

        profiler.pop();
        graphics.pose().popPose();
        graphics.disableScissor();

        profiler.popPush("frame");

        // Reset depth for gauge filter icons
        graphics.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);   // 256 = GL_DEPTH_BUFFER_BIT

        // Draw the drag-selection rectangle above board contents but below the frame and top-level controls.
        if (dragSelection != null && dragSelection.hasMoved()) {
            Vector2d startScreen = screenAt(
                    dragSelection.startWorldX(), dragSelection.startWorldY(), centerX, centerY);
            int rx0 = (int) Math.min(startScreen.x, mouseX);
            int ry0 = (int) Math.min(startScreen.y, mouseY);
            int rx1 = (int) Math.max(startScreen.x, mouseX);
            int ry1 = (int) Math.max(startScreen.y, mouseY);
            graphics.enableScissor(canvas.minX(), canvas.minY(), canvas.maxX(), canvas.maxY());
            graphics.fill(rx0, ry0, rx1, ry1, 0x3333CC33);          // body
            graphics.fill(rx0, ry0, rx1, ry0 + 1, 0xAA33CC33);      // top border
            graphics.fill(rx0, ry1 - 1, rx1, ry1, 0xAA33CC33);      // bottom
            graphics.fill(rx0, ry0, rx0 + 1, ry1, 0xAA33CC33);      // left
            graphics.fill(rx1 - 1, ry0, rx1, ry1, 0xAA33CC33);      // right
            graphics.disableScissor();
            graphics.flush();
        }

        // Frame
        RenderSystem.enableBlend();
        TiledSpriteRenderer.create(FRAME_SPRITE).render(graphics, leftPos, topPos, imageWidth, imageHeight);
        RenderSystem.disableBlend();

        // Title
        if (nameBox != null) {
            boolean blank = nameBox.getValue().isBlank();
            String shownStr = !nameBox.isFocused() && blank
                    ? menu.controllerDisplayName().getString()
                    : nameBox.getValue();
            int textW = font.width(shownStr);
            int x = leftPos + imageWidth / 2 - (textW + 10) / 2;
            nameBox.setX(x);
            nameBox.setWidth(Math.max(textW + (nameBox.isFocused() ? 6 : 0), 1));
            nameAreaBounds = Rect2i.fromXYWH(x, topPos, textW + 5 + RENAME_BUTTON_SIZE, 14);
            nameBox.render(graphics, mouseX, mouseY, partialTick);
            if (!nameBox.isFocused()) {
                if (blank)
                    graphics.drawString(font, menu.controllerDisplayName(), x, nameBox.getY(), NAME_COLOR, false);
                // Edit-name icon after the text — indicator-only cue, vertically centred on the text.
                if (!inOverlay)
                    graphics.blitSprite(RENAME_BUTTON_SPRITE,
                            x + font.width(shownStr) + 5,
                            nameBox.getY() + (font.lineHeight - RENAME_BUTTON_SIZE) / 2 - 1,
                            RENAME_BUTTON_SIZE, RENAME_BUTTON_SIZE);
            }
        }

        networkSelector.render(graphics, mouseX, mouseY, partialTick);
        indicatorColumn.render(graphics, mouseX, mouseY, partialTick);

        int labelX = networkSelector.getX() + networkSelector.getWidth() + 3;
        int row0Y = networkSelector.getY() + 2;
        int row1Y = row0Y + font.lineHeight + 2;

        int count = menu.components.size();
        int max = FactoryControllerBlockEntity.maxComponents();   // server config value (synced to client)
        String capacityStr = count + "/" + max;
        int capacityColor = count >= max ? 0xFFFF5555 : 0xFFE2E2E2;
        int capTextX = labelX + CAPACITY_ICON_SIZE + 3;
        graphics.blitSprite(CAPACITY_ICON, labelX, row0Y + (font.lineHeight - CAPACITY_ICON_SIZE) / 2,
                CAPACITY_ICON_SIZE, CAPACITY_ICON_SIZE);
        graphics.drawString(font, capacityStr, capTextX, row0Y, capacityColor, true);
        capacityLabelBounds = Rect2i.fromXYWH(labelX, row0Y,
                capTextX + font.width(capacityStr) - labelX, font.lineHeight);

        double zoom = getZoomFactor() / 2.0;
        String zoomStr = String.format(java.util.Locale.ROOT, "%.2f", zoom);
        if (zoomStr.contains(".")) zoomStr = zoomStr.replaceAll("0+$", "").replaceAll("\\.$", "");
        zoomStr = "x" + zoomStr;
        int zoomTextX = labelX + ZOOM_ICON_SIZE + 1;
        graphics.blitSprite(ZOOM_ICON, labelX, row1Y + (font.lineHeight - ZOOM_ICON_SIZE) / 2,
                ZOOM_ICON_SIZE, ZOOM_ICON_SIZE);
        graphics.drawString(font, zoomStr, zoomTextX, row1Y, 0xFFE2E2E2, true);
        zoomLabelBounds = Rect2i.fromXYWH(labelX, row1Y,
                zoomTextX + font.width(zoomStr) - labelX, font.lineHeight);

        if (settingsButton != null) settingsButton.render(graphics, mouseX, mouseY, partialTick);
        if (blueprintLoadButton != null) blueprintLoadButton.render(graphics, mouseX, mouseY, partialTick);
        if (blueprintSaveButton != null) blueprintSaveButton.render(graphics, mouseX, mouseY, partialTick);

        if (helpButton != null && !inOverlay) helpButton.render(graphics, mouseX, mouseY, partialTick);

        graphics.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);   // 256 = GL_DEPTH_BUFFER_BIT

        profiler.pop();

        if (inOverlay) {
            graphics.fill(canvas.minX(), canvas.minY(), canvas.maxX(), canvas.maxY(), 0xB0101010);
        }

        profiler.pop();
    }

    /**
     * Resolves which wire is hovered this frame from {@link #hoverHits}, keeping {@link #selectedConnection} stable
     */
    @Nullable
    private ConnectionWidget reconcileConnectionSelection(List<ConnectionWidget> connWidgets) {
        if (connArrowLocked && selectedConnection != null) {
            for (ConnectionWidget w : connWidgets)
                if (Connection.sameConnection(w.connection, selectedConnection)) return w;
            connArrowLocked = false;   // the wire is gone (e.g. removed) → drop the lock and fall through
        }
        if (hoverHits.isEmpty()) {
            selectedConnection = null;
            connTooltipShowAtMs = Long.MAX_VALUE;
            return null;
        }
        ConnectionWidget keep = null;
        for (ConnectionWidget w : hoverHits)
            if (Connection.sameConnection(w.connection, selectedConnection)) { keep = w; break; }
        if (keep == null) keep = hoverHits.getFirst();
        selectedConnection = keep.connection;
        if (connTooltipShowAtMs == Long.MAX_VALUE)
            connTooltipShowAtMs = Util.getMillis() + ClientConfig.connectionTooltipDelay();
        return keep;
    }

    /** Tooltip lines for the hovered wire — the widget owns the format; we supply the overlap count and selected index. */
    private List<Component> connectionTooltipLines() {
        int count = hoverHits.size();
        int sel = 0;
        for (int i = 0; i < count; i++)
            if (Connection.sameConnection(hoverHits.get(i).connection, selectedConnection)) { sel = i; break; }
        assert hoveredConn != null;
        return hoveredConn.getTooltip(menu, count, sel, connArrowLocked);
    }

    /** A connection operation is deliberate interaction, so its feedback should not wait on the hover delay. */
    private void revealConnectionTooltip() {
        if (hoveredConn != null) connTooltipShowAtMs = Util.getMillis();
    }

    // Reticle tints.
    private static final int TARGET_WHITE = 0xFFFFFF;
    private static final int TARGET_RED   = 0xFF3333;
    private static final int TARGET_GREEN = 0x33CC33;   // also the selected-component mark
    private static final int TARGET_BLUE  = 0x55AAFF;
    // Connection-neighbour tints
    private static final int TARGET_SOURCE      = 0x61d6f2;
    private static final int TARGET_SINK        = 0xfcd860;
    private static final int TARGET_SOURCE_SINK = 0xb7ed9f;
    private static final long PREVIEW_FLASH_MS = 900;

    /** Blue reticle over every component on the network currently selected in the network selector. */
    private void renderSelectedNetworkMask(GuiGraphics graphics) {
        UUID selected = networkSelector.getSelectedNetwork();
        if (selected == null) return;
        for (VirtualComponentWidget component : componentWidgets.values())
            if (component.behaviour() instanceof VirtualGaugeBehaviour g && selected.equals(g.networkId))
                renderTarget(graphics, component.position(), TARGET_BLUE);
    }

    private void renderHoverTarget(GuiGraphics graphics) {
        VirtualComponentPosition source = pendingConnectionTarget != null ? pendingConnectionTarget : pendingRelocateTarget;
        if (source != null) renderTarget(graphics, source, TARGET_GREEN);

        if (hoveredPosition == null || hoveredPosition.equals(source)) return;
        final VirtualComponentBehaviour hovered = componentAt(hoveredPosition);
        ItemStack carried = menu.getCarried();

        if (pendingConnectionTarget != null) {
            // The preview bend override only holds while hovering the same target; a change resets it to auto.
            if (!java.util.Objects.equals(hoveredPosition, previewArrowTarget)) {
                previewArrowTarget = hoveredPosition;
                previewArrowMode = -1;
            }
            // Connect mode: hovering another component shows white if it can be wired
            ConnectionResolver.Result result = connectionHoverResult();
            if (result != null) {
                if (result.ok()) {
                    assert result.source() != null;
                    List<org.joml.Vector2i> path = ConnectionPathResolver.resolvePath(
                            result.source(), result.sink(), previewArrowMode, occupiedCells());
                    if (path != null) {
                        float phase = (Util.getMillis() % PREVIEW_FLASH_MS) / (float) PREVIEW_FLASH_MS;
                        float alpha = 0.85f + 0.15f * Mth.cos(phase * Mth.TWO_PI);   // 1.0 at phase 0, 0.6 at half
                        int color = Math.round(alpha * 255) << 24 | (result.type().color() & 0xFFFFFF);
                        VirtualConnectionRenderer.create(path, color, false).drawPath(graphics);
                    }
                }
                renderTarget(graphics, hoveredPosition, result.ok() ? TARGET_WHITE : TARGET_RED);
            }
        } else if (pendingRelocateTarget != null) {
            // Relocate mode: preview the one moving component (and its wires) shifted to the cursor cell.
            boolean valid = hovered == null && !FactoryControllerBlockEntity.isOutBoard(hoveredPosition);
            GhostPreview preview = movePreviewFor(java.util.Set.of(pendingRelocateTarget));
            if (preview != null)
                preview.render(graphics, new VirtualComponentPosition(
                                hoveredPosition.x() - pendingRelocateTarget.x(),
                                hoveredPosition.y() - pendingRelocateTarget.y()),
                        componentRenderingHelper, occupiedCells());
            renderTargetAboveGhost(graphics, hoveredPosition, valid ? TARGET_WHITE : TARGET_RED);
        } else if (ComponentRegistry.containsItem(carried)) {
            // Holding a component
            boolean needsNet = ComponentRegistry.needsNetwork(BuiltInRegistries.ITEM.getKey(carried.getItem()));
            boolean noNetwork = needsNet && networkForAttaching(carried) == null;
            boolean valid = hovered == null && !noNetwork && !FactoryControllerBlockEntity.isOutBoard(hoveredPosition);
            if (valid) renderGhostAt(graphics, hoveredPosition, carried.getItem());   // ghost under the target reticle
            renderTargetAboveGhost(graphics, hoveredPosition, valid ? TARGET_WHITE : TARGET_RED);
        } else if (!carried.isEmpty()) {
            // Holding a non-component item
            if (componentAt(hoveredPosition) instanceof VirtualGaugeBehaviour g && g.filter.isEmpty())
                renderTarget(graphics, hoveredPosition, TARGET_WHITE);
            else if (componentAt(hoveredPosition) instanceof VirtualRedstoneLinkBehaviour)
                renderTarget(graphics, hoveredPosition, TARGET_WHITE);
        } else {
            // Empty cursor
            if (hovered != null) {
                if (dragSelection == null && ClientConfig.coloredConnectedComponentOutlines())
                    renderConnectionNeighbours(graphics, hovered);
                renderTarget(graphics, hoveredPosition, TARGET_WHITE);
            }
        }
    }

    private void renderConnectionNeighbours(GuiGraphics graphics, VirtualComponentBehaviour hovered) {
        Set<VirtualComponentPosition> sources = new LinkedHashSet<>();
        for (Connection conn : hovered.targetedBy().values())
            sources.add(conn.from);
        Set<VirtualComponentPosition> sinks = new LinkedHashSet<>();
        for (Connection conn : hovered.outgoingConnections())
            sinks.add(conn.to);
        for (VirtualComponentPosition p : sources)
            renderTarget(graphics, p, sinks.contains(p) ? TARGET_SOURCE_SINK : TARGET_SOURCE);
        for (VirtualComponentPosition p : sinks)
            if (!sources.contains(p)) renderTarget(graphics, p, TARGET_SINK);
    }

    /** In connection mode, the resolver result for wiring the currently-hovered component to the pending target, or
     *  {@code null} when the hover isn't a wireable partner (empty cell, or the initiator itself). {@code ok()}
     *  distinguishes a valid target from an invalid one. */
    @Nullable
    private ConnectionResolver.Result connectionHoverResult() {
        if (pendingConnectionTarget == null || hoveredPosition == null
                || hoveredPosition.equals(pendingConnectionTarget)) return null;
        VirtualComponentBehaviour initiator = componentAt(pendingConnectionTarget);
        VirtualComponentBehaviour hovered = componentAt(hoveredPosition);
        if (initiator == null || hovered == null) return null;
        return ConnectionResolver.resolve(hovered, initiator, initiator);
    }

    /** Draws a translucent single-component preview from a cursor item — a fresh component, so no configured state
     *  (a placed blueprint or a move uses {@link GhostPreview} instead, which reconstructs configured components). */
    private void renderGhostAt(GuiGraphics graphics, VirtualComponentPosition pos, net.minecraft.world.item.Item item) {
        VirtualComponentBehaviour ghost = ComponentRegistry.createFromItem(null, pos, item, null);
        VirtualComponentWidget widget = ghost == null ? null : ComponentWidgetRegistry.create(ghost);
        if (widget == null) return;
        RenderSystem.enableBlend();
        graphics.setColor(1f, 1f, 1f, 0.5f);
        widget.renderGhost(componentRenderingHelper.params(graphics, new Vector2d(Double.NaN, Double.NaN), false));
        // Batched component blits must draw while the ghost alpha is still active.
        componentRenderingHelper.flushBuffers(graphics);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /** The {@link GhostPreview} for a relocate/batch-move of {@code movingCells}, built (and cached) from the live
     *  components. Rebuilt when the moving set changes; invalidated on board re-sync. {@code null} if it can't be
     *  built (no client registries). */
    @Nullable
    private GhostPreview movePreviewFor(Set<VirtualComponentPosition> movingCells) {
        if (movePreview == null || !movingCells.equals(movePreviewKey)) {
            var connection = Minecraft.getInstance().getConnection();
            if (connection == null) return null;
            List<VirtualComponentBehaviour> moving = new ArrayList<>();
            for (VirtualComponentPosition p : movingCells) {
                VirtualComponentBehaviour b = componentAt(p);
                if (b != null) moving.add(b);
            }
            movePreview = GhostPreview.fromSelection(moving, new LinkedHashSet<>(movingCells),
                    connection.registryAccess());
            movePreviewKey = new LinkedHashSet<>(movingCells);
        }
        return movePreview;
    }

    private void updateVisibleComponents(Rect2i visibleArea) {
        visibleComponents.clear();
        for (VirtualComponentWidget component : componentWidgets.values()) {
            VirtualComponentPosition cell = component.position();
            if (Rect2i.fromBounds(
                    cell.x() * CANVAS_COMPONENT_SIZE - CANVAS_COMPONENT_SIZE,
                    cell.y() * CANVAS_COMPONENT_SIZE - CANVAS_COMPONENT_SIZE,
                    (cell.x() + 1) * CANVAS_COMPONENT_SIZE + CANVAS_COMPONENT_SIZE,
                    (cell.y() + 1) * CANVAS_COMPONENT_SIZE + CANVAS_COMPONENT_SIZE
            ).intersects(visibleArea, Rect2i.Boundary.EXCLUSIVE)) visibleComponents.add(component);
        }
    }

    /** Blits the 16×16 {@code target} sprite tinted {@code rgb}, filling the cell exactly. */
    private void renderTarget(GuiGraphics graphics, VirtualComponentPosition pos, int rgb) {
        int x0 = pos.x() * CANVAS_COMPONENT_SIZE;
        int y0 = pos.y() * CANVAS_COMPONENT_SIZE;
        RenderSystem.enableBlend();
        graphics.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        graphics.blitSprite(TARGET_SPRITE, x0, y0, CANVAS_COMPONENT_SIZE, CANVAS_COMPONENT_SIZE);
        graphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private void renderTargetAboveGhost(GuiGraphics graphics, VirtualComponentPosition pos, int rgb) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        renderTarget(graphics, pos, rgb);
        graphics.pose().popPose();
    }

    // ── Selection mode ───────────────────────────────────────────────────────

    /** Drops all selected components (and any in-progress selection drag). Client-only; called when a controller
     *  overlay opens (per the feature spec) and on a left-click on empty space. */
    public void clearSelection() {
        selected.clear();
        dragSelection = null;
        batchMove.cancel();
    }

    /** The canvas cell a screen position falls into (computes the board centre itself). */
    private VirtualComponentPosition cellAt(double posX, double posY) {
        Rect2i canvas = canvasArea();
        return at(posX, posY,
                canvas.minX() + canvas.w() / 2,
                canvas.minY() + canvas.h() / 2);
    }

    private void beginDragSelection(double mouseX, double mouseY, boolean isPreserved) {
        Vector2d startWorld = worldAt(mouseX, mouseY);
        dragSelection = new DragSelectionState(startWorld.x, startWorld.y, isPreserved);
    }

    private void updateDragSelectionMovement(double mouseX, double mouseY) {
        if (dragSelection == null) return;
        Vector2d startScreen = screenAt(dragSelection.startWorldX(), dragSelection.startWorldY());
        dragSelection.updateMovement(startScreen, mouseX, mouseY);
    }

    /** Green mark on selected components; white/red ghosts during a batch drag; and a live green preview of every
     *  component currently inside the drag-selection rectangle. */
    private void renderSelectionTargets(GuiGraphics graphics, Vector2d currentWorld) {
        if (!(dragSelection != null && dragSelection.hasMoved() && !dragSelection.isPreserved()))
            for (VirtualComponentPosition p : selected)
                if (componentWidgets.containsKey(p)) renderTarget(graphics, p, TARGET_GREEN);
        if (dragSelection != null && dragSelection.hasMoved()) {
            // Live preview: components the drag would select show the mark too.
            for (VirtualComponentPosition p : dragSelection.positionsIn(
                    componentWidgets.keySet(), currentWorld, CANVAS_COMPONENT_SIZE))
                renderTarget(graphics, p, TARGET_GREEN);
        }
        if (batchMove.isActive() && batchMove.hasMoved()) {
            Set<VirtualComponentPosition> movingCells = batchMove.movingSources(selected, componentWidgets.keySet());
            GhostPreview preview = movePreviewFor(movingCells);
            if (preview != null)
                preview.render(graphics, new VirtualComponentPosition(batchMove.dx(), batchMove.dy()),
                        componentRenderingHelper, occupiedCells());
            for (VirtualComponentPosition p : movingCells) {                        // reticle on top of each ghost
                VirtualComponentPosition to = batchMove.destinationOf(p);
                boolean valid = batchMove.destinationValid(to, selected, componentWidgets.keySet());
                renderTargetAboveGhost(graphics, to, valid ? TARGET_WHITE : TARGET_RED);
            }
            graphics.flush();
        }
    }

    @Nullable
    private Component selectionStatusPrompt(double mouseX, double mouseY) {
        int count = effectiveSelectedCount(mouseX, mouseY);
        return count > 0
            ? Component.translatable("createfactorycontroller.gui.selection.count", count).withStyle(ChatFormatting.GREEN)
            : null;
    }

    private int effectiveSelectedCount(double mouseX, double mouseY) {
        if (dragSelection == null || !dragSelection.hasMoved()) return selected.size();
        return dragSelection.effectiveSelection(
                selected, componentWidgets.keySet(), worldAt(mouseX, mouseY), CANVAS_COMPONENT_SIZE).size();
    }

    private void toggleOrClearAt(double mx, double my) {
        VirtualComponentPosition cell = cellAt(mx, my);
        if (componentWidgets.containsKey(cell)) {
            if (!selected.remove(cell)) selected.add(cell);
        } else {
            clearSelection();
        }
    }

    private void commitBatchRelocate() {
        BatchMoveState.FinishResult result = batchMove.finish(selected, componentWidgets.keySet());
        if (result.status() == BatchMoveState.FinishStatus.INVALID) {
            playDenySound();
            return;
        }
        BatchMoveState.MoveRequest request = result.request();
        if (request != null)
            PacketDistributor.sendToServer(new BatchMoveComponentPacket(
                    menu.controllerPos, request.sources(), request.dx(), request.dy()));
    }

    // ── Mouse interaction ──────────────────────────────────────────────────

    /** Enters "add connection" mode: the next board gauge clicked becomes an input to {@code target}. */
    public void beginConnectionMode(VirtualComponentPosition target) {
        pendingConnectionTarget = target;
        previewArrowMode = -1;             // fresh mode: preview starts on auto bend
        previewArrowTarget = null;
        setPersistentPrompt(Component.translatable("createfactorycontroller.connection.mode_prompt")
                .withStyle(ChatFormatting.WHITE));
    }

    /** Enters "relocate" mode: the next empty cell clicked becomes {@code target}'s new position. */
    public void beginRelocateMode(VirtualComponentPosition target) {
        pendingRelocateTarget = target;
        VirtualComponentBehaviour behaviour = componentAt(target);
        Component name = behaviour == null ? Component.empty() : behaviour.getName();
        setPersistentPrompt(Component.translatable("createfactorycontroller.gui.relocate_mode_prompt", name)
                .withStyle(ChatFormatting.WHITE));
    }

    public void beginBlueprintPlacement(BlueprintPlacement placement) {
        clearSelection();
        pendingConnectionTarget = null;
        pendingRelocateTarget = null;
        pendingPlacement = placement;
        PacketDistributor.sendToServer(new ReturnCarriedPacket());
        setPersistentPrompt(Component.translatable("createfactorycontroller.gui.blueprint.placement_prompt")
                .withStyle(ChatFormatting.WHITE));
    }

    public void abortBlueprintPlacement() {
        if (pendingPlacement == null) return;
        pendingPlacement = null;
        persistentActionPrompt = null;
    }

    private void cancelBlueprintPlacement() {
        abortBlueprintPlacement();
        setTimedPrompt(Component.translatable("createfactorycontroller.gui.blueprint.placement_aborted")
                .withStyle(ChatFormatting.RED), 3000);
        playDenySound();
    }

    private void renderPlacementGhost(GuiGraphics graphics) {
        if (pendingPlacement == null || hoveredPosition == null) return;
        VirtualComponentPosition anchor = pendingPlacement.anchorFor(hoveredPosition);
        // The reconstructed components + the blueprint's internal wires, anchored under the cursor.
        pendingPlacement.ghostPreview().render(graphics, anchor, componentRenderingHelper, occupiedCells());
        for (BlueprintStorage.Placement placement : pendingPlacement.info().placements()) {
            VirtualComponentPosition cell = BlueprintPlacement.cellFor(placement, anchor);
            renderTargetAboveGhost(graphics, cell, pendingPlacement.cellFree(cell, menu) ? TARGET_WHITE : TARGET_RED);
        }
        graphics.flush();
    }

    /** A prompt that stays until the mode ends (no fade). */
    private void setPersistentPrompt(Component prompt) {
        persistentActionPrompt = prompt;
    }

    /** Replaces the transient channel with a prompt that fades out after {@code durationMs}. */
    private void setTimedPrompt(Component prompt, long durationMs) {
        temporaryActionPrompt = prompt;
        temporaryActionPromptExpiry = Util.getMillis() + durationMs;
    }

    /** Called by the blueprint editor after its atomic file write succeeds. */
    public void showBlueprintSaved(String fileName) {
        setTimedPrompt(Component.translatable("createfactorycontroller.gui.blueprint.saved", fileName)
                .withStyle(ChatFormatting.GREEN), 4000);
    }

    /** Create's rejection blip, played client-side for board actions the client rejects before sending. */
    private void playDenySound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(AllSoundEvents.DENY.getMainEvent(), 1.0f));
    }

    private void playWrenchSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(AllSoundEvents.WRENCH_ROTATE.getMainEvent(), 1.0f));
    }

    /**
     * The network a stack gauge would attach to, or {@code null} if placement isn't possible: a
     * tuned gauge brings its own network, an untuned one needs a known network selected.
     */
    @Nullable
    private UUID networkForAttaching(ItemStack stack) {
        return LogisticallyLinkedBlockItem.isTuned(stack) ? LogisticallyLinkedBlockItem.networkFromStack(stack) : null;
    }

    /** Attaches a carried component at the empty {@code cell} (board-full / network checks, then the packet). No-op
     *  when not carrying a component. Reached from the drag-selection release path (every empty-cell press starts one). */
    private void attachCarriedAt(VirtualComponentPosition cell, ItemStack carried) {
        if (!ComponentRegistry.containsItem(carried) || componentWidgetAt(cell) != null) return;
        if (menu.components.size() >= FactoryControllerBlockEntity.maxComponents()) {
            setTimedPrompt(Component.translatable("createfactorycontroller.gui.prompt.component_limit",
                    FactoryControllerBlockEntity.maxComponents()).withStyle(ChatFormatting.RED), 3000);
            playDenySound();
            return;
        }
        boolean needsNet = ComponentRegistry.needsNetwork(BuiltInRegistries.ITEM.getKey(carried.getItem()));
        UUID network = needsNet ? networkForAttaching(carried) : null;
        if (needsNet && network == null) {   // a gauge with no usable network → surface the requirement, don't send
            setTimedPrompt(Component.translatable(menu.knownNetworks.isEmpty() ?
                    "createfactorycontroller.gui.prompt.no_first_network" :
                    "createfactorycontroller.gui.prompt.no_network_tuned").withStyle(ChatFormatting.RED), 3000);
            playDenySound();
            return;
        }
        PacketDistributor.sendToServer(new AttachComponentPacket(menu.controllerPos, cell, network));
    }

    private void completeConnection(VirtualComponentPosition clickedPos,
                                    @Nullable VirtualComponentWidget clickedWidget) {
        VirtualComponentPosition targetPos = pendingConnectionTarget;
        pendingConnectionTarget = null;
        persistentActionPrompt = null;
        if (clickedWidget == null || clickedPos.equals(targetPos)) {
            setTimedPrompt(CreateLang.translate("factory_panel.connection_aborted")
                    .style(ChatFormatting.WHITE).component(), 3000);
            return;
        }
        VirtualComponentBehaviour clicked = clickedWidget.behaviour();    // the clickedPos component
        VirtualComponentBehaviour initiator = componentAt(targetPos);    // the component that started the mode (= sink)
        if (initiator == null) {   // vanished mid-mode (removed/relocated) — a real failure, not a user abort
            playDenySound();
            return;
        }

        ConnectionResolver.Result result = ConnectionResolver.resolve(clicked, initiator, initiator);
        if (!result.ok()) {
            showConnectionMessage(result);
            playDenySound();
            return;
        }
        // Apply the previewed bend override, but only if it still belongs to this target (auto otherwise).
        int bendMode = clickedPos.equals(previewArrowTarget) ? previewArrowMode : -1;
        PacketDistributor.sendToServer(new AddConnectionPacket(menu.controllerPos, result.type().name(),
                result.source(), result.sink(), bendMode));
        showConnectionMessage(result);
    }

    private void showConnectionMessage(ConnectionResolver.Result result) {
        if (result.validation().message() != null)
            setTimedPrompt(result.validation().message().get(), 3000);
    }

    private void completeRelocate(VirtualComponentPosition clicked, @Nullable VirtualComponentWidget widget) {
        VirtualComponentPosition from = pendingRelocateTarget;
        pendingRelocateTarget = null;
        persistentActionPrompt = null;
        VirtualComponentBehaviour moving = componentAt(from);
        Component name = moving == null ? Component.empty() : moving.getName();
        if (widget == null && !FactoryControllerBlockEntity.isOutBoard(clicked)) {
            PacketDistributor.sendToServer(new MoveComponentPacket(menu.controllerPos, from, clicked));
            setTimedPrompt(Component.translatable("createfactorycontroller.gui.relocated", name)
                    .withStyle(ChatFormatting.GREEN), 3000);
        } else {
            setTimedPrompt(Component.translatable("createfactorycontroller.gui.relocation_aborted", name)
                    .withStyle(ChatFormatting.WHITE), 3000);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pendingPlacement != null) {
            if (button == 0 && isInCanvasArea(mouseX, mouseY)) {
                VirtualComponentPosition anchor = pendingPlacement.anchorFor(cellAt(mouseX, mouseY));
                if (pendingPlacement.fits(anchor, menu)) {
                    Minecraft.getInstance().setScreen(new BlueprintPlaceScreen(this, pendingPlacement, anchor));
                    return true;
                }
            }
            cancelBlueprintPlacement();
            return true;
        }

        if (nameBox != null) {
            boolean inNameBar = nameAreaBounds != null && nameAreaBounds.contains(
                    (int) mouseX, (int) mouseY, Rect2i.Boundary.HALF_OPEN);
            if (!nameBox.isFocused() && inNameBar) {
                nameBox.setFocused(true);
                nameBox.setCursorPosition(nameBox.getValue().length());
                nameBox.setHighlightPos(0);   // select all
                setFocused(nameBox);
                return true;
            }
            if (nameBox.isFocused() && !inNameBar)
                commitName();
        }

        if (menu.getCarried().isEmpty() && networkSelector.isMouseOver(mouseX, mouseY)) {
            UUID net = networkSelector.getSelectedNetwork();
            if (net != null && menu.knownNetworks.contains(net)) {
                clearSelection();
                Minecraft.getInstance().setScreen(new NetworkSettingsScreen(this, net));
                return true;
            }
        }
        if (indicatorColumn.isMouseOver(mouseX, mouseY)) return true;

        if (isInCanvasArea(mouseX, mouseY)) {
            Rect2i canvas = canvasArea();
            int centerX = canvas.minX() + canvas.w() / 2;
            int centerY = canvas.minY() + canvas.h() / 2;
            VirtualComponentPosition clicked = at(mouseX, mouseY, centerX, centerY);
            ItemStack carried = menu.getCarried();
            VirtualComponentWidget widget = componentWidgetAt(clicked);
            boolean leftOrRight = button == 0 || button == 1;

            if (hoveredConn != null && leftOrRight && hasShiftDown()) {
                revealConnectionTooltip();
                PacketDistributor.sendToServer(new RemoveConnectionPacket(menu.controllerPos,
                        hoveredConn.connection.from, hoveredConn.connection.to));
                selectedConnection = null;   // it's gone; let the hover re-resolve next frame
                playWrenchSound();
                return true;
            }

            boolean isPreserved = isKeyHeld(CreateFactoryControllerClient.SELECTION_MODE);
            if (isPreserved && CreateFactoryControllerClient.DRAG_SELECTION.matchesMouse(button) && carried.isEmpty()
                    && pendingConnectionTarget == null && pendingRelocateTarget == null) {
                beginDragSelection(mouseX, mouseY, true);
                return true;
            }

            if (leftOrRight && widget == null && carried.isEmpty() && !selected.isEmpty()
                    && pendingConnectionTarget == null && pendingRelocateTarget == null && !isPreserved) {
                if (CreateFactoryControllerClient.PAN_VIEW.matchesMouse(button))
                    beginPan(mouseX, mouseY, true);
                else
                    clearSelection();
                return true;
            }
            if (CreateFactoryControllerClient.DRAG_SELECTION.matchesMouse(button)
                    && !CreateFactoryControllerClient.PAN_VIEW.matchesMouse(button) && carried.isEmpty()
                    && pendingConnectionTarget == null && pendingRelocateTarget == null
                    && (isPreserved || widget == null)) {
                beginDragSelection(mouseX, mouseY, isPreserved);
                return true;
            }

            if (button == 0 && !carried.isEmpty() && widget == null
                    && pendingConnectionTarget == null && pendingRelocateTarget == null) {
                clearSelection();
                attachCarriedAt(clicked, carried);
                return true;
            }

            // Connection mode: the clicked cell is the other end of the pending wire (handled in completeConnection).
            if (pendingConnectionTarget != null && leftOrRight) {
                completeConnection(clicked, widget);
                return true;
            }

            // Relocate mode: the clicked cell is the pending component's destination (handled in completeRelocate).
            if (pendingRelocateTarget != null && leftOrRight) {
                completeRelocate(clicked, widget);
                return true;
            }

            // Selection handling — suppressed when hovering a connection (connection takes priority).
            if (leftOrRight && widget != null && hoveredConn == null) {

                double worldX = viewX + (mouseX - centerX) / getZoomFactor();
                double worldY = viewY + (mouseY - centerY) / getZoomFactor();
                if (hasShiftDown()) {
                    if (selected.contains(clicked)){
                        for (VirtualComponentPosition p : new ArrayList<>(selected)) {
                            VirtualComponentWidget w = componentWidgetAt(p);
                            if (w != null) w.remove(this);
                        }
                        clearSelection();
                    } else {
                        widget.remove(this);
                    }
                    return true;
                }
                if (button == 0 && selected.contains(clicked)) {
                    batchMove.begin(widget.position());
                    return true;
                }
                return widget.onClick(this, carried, worldX, worldY, button);
            }

            // Pan-view button → start a drag-pan.
            if (CreateFactoryControllerClient.PAN_VIEW.matchesMouse(button)) {
                beginPan(mouseX, mouseY, false);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // A few pixels of travel from the board-anchored origin promotes the click to a drag.
        if (CreateFactoryControllerClient.DRAG_SELECTION.matchesMouse(button) && dragSelection != null) {
            updateDragSelectionMovement(mouseX, mouseY);
            return true;
        }
        // Batch relocate: just consume the drag — the delta is recomputed every frame by BatchMoveState,
        // which also tracks camera pans made without moving the mouse.
        if (button == 0 && batchMove.isActive())
            return true;
        if (isDragging && CreateFactoryControllerClient.PAN_VIEW.matchesMouse(button)) {
            if (mouseX != panStartX || mouseY != panStartY) panMoved = true;
            viewX -= deltaX / getZoomFactor();
            viewY -= deltaY / getZoomFactor();
            clampView();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (CreateFactoryControllerClient.DRAG_SELECTION.matchesMouse(button) && dragSelection != null) {
            DragSelectionState completed = dragSelection;
            dragSelection = null;
            if (completed.hasMoved()) {
                completed.applyTo(
                        selected, componentWidgets.keySet(), worldAt(mouseX, mouseY), CANVAS_COMPONENT_SIZE);
            } else if (completed.isPreserved()) {
                // A Selection-Mode click toggles the component, or clears on empty.
                toggleOrClearAt(mouseX, mouseY);
            } else {
                // a normal-mode click on empty space → drop the selection, then place a carried component if any
                clearSelection();
                attachCarriedAt(cellAt(mouseX, mouseY), menu.getCarried());
            }
            return true;
        }
        if (button == 0 && batchMove.isActive()) {
            commitBatchRelocate();
            return true;
        }
        if (CreateFactoryControllerClient.PAN_VIEW.matchesMouse(button) && isDragging) {
            boolean shouldClearSelection = clearSelectionOnPanClick && !panMoved
                    && mouseX == panStartX && mouseY == panStartY;
            isDragging = false;
            panMoved = false;
            clearSelectionOnPanClick = false;
            if (shouldClearSelection) clearSelection();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void beginPan(double mouseX, double mouseY, boolean clearSelectionIfClicked) {
        isDragging = true;
        panMoved = false;
        clearSelectionOnPanClick = clearSelectionIfClicked;
        panStartX = mouseX;
        panStartY = mouseY;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (networkSelector.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;

        if (isInCanvasArea(mouseX, mouseY)) {
            Rect2i canvas = canvasArea();
            int centerX = canvas.minX() + canvas.w() / 2;
            int centerY = canvas.minY() + canvas.h() / 2;

            if (hasShiftDown()) {
                // A scroll releases the arrow-mode lock; reconcile then resolves the tooltip to the hovered wire.
                if (connArrowLocked) {
                    revealConnectionTooltip();
                    connArrowLocked = false;
                    return true;
                }
                if (!hoverHits.isEmpty()) {
                    int idx = 0;
                    for (int i = 0; i < hoverHits.size(); i++)
                        if (Connection.sameConnection(hoverHits.get(i).connection, selectedConnection)) { idx = i; break; }
                    idx = Math.floorMod(idx + (int) Math.signum(-scrollY), hoverHits.size());
                    selectedConnection = hoverHits.get(idx).connection;   // timer keeps running across the switch
                    revealConnectionTooltip();
                    return true;
                }
                networkSelector.scrollSelection(scrollY);
                return true;
            }

            double oldZoom = getZoomFactor();
            zoomLevel = Math.clamp(zoomLevel + (int) Math.signum(scrollY), MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL);
            viewX += (mouseX - centerX) * (1.0 / oldZoom - 1.0 / getZoomFactor());
            viewY += (mouseY - centerY) * (1.0 / oldZoom - 1.0 / getZoomFactor());
            clampView();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private double getZoomFactor() {
        return 2. * Math.pow(2., zoomLevel / 10.);
    }

    /**
     * Pans the view from the game's movement keybindings (Forward/Back/Left/Right — WASD by default)
     * currently held. Called once per frame and scaled by the real frame-delta time so movement stays
     * smooth at any framerate; the speed is constant in screen pixels (divided by the zoom to get world
     * px), so it feels the same at any zoom. Skipped while renaming so typed letters don't move the board.
     */
    private void tickKeyboardPan() {
        long now = Util.getMillis();
        long previous = lastPanFrameMs;
        lastPanFrameMs = now;
        if (previous == 0) return;

        if (nameBox != null && nameBox.isFocused()) return;
        Minecraft mc = Minecraft.getInstance();
        double dx = 0, dy = 0;
        if (panActive(mc.options.keyLeft))  dx -= 1;
        if (panActive(mc.options.keyRight)) dx += 1;
        if (panActive(mc.options.keyUp))    dy -= 1;
        if (panActive(mc.options.keyDown))  dy += 1;
        if (dx == 0 && dy == 0) return;

        // Seconds since the last frame, clamped so a hitch / paused frame can't fling the view.
        double elapsedSec = Math.min((now - previous) / 1000.0, 0.1);
        double step = KEY_PAN_SPEED * elapsedSec / getZoomFactor();
        viewX += dx * step;
        viewY += dy * step;
        clampView();
    }

    /** Whether {@code mapping}'s bound keyboard key is physically held */
    private static boolean isKeyHeld(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() == InputConstants.UNKNOWN.getValue())
            return false;
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key.getValue());
    }

    private boolean panActive(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() == InputConstants.UNKNOWN.getValue())
            return false;
        int code = key.getValue();
        if (!heldPanKeys.contains(code)) return false;
        if (!isKeyHeld(mapping)) { heldPanKeys.remove(code); return false; }
        return true;
    }

    /** Whether {@code keyCode} is bound to one of the four movement (pan) keybindings. */
    private static boolean isPanKey(int keyCode) {
        var o = Minecraft.getInstance().options;
        return isBoundTo(o.keyUp, keyCode) || isBoundTo(o.keyDown, keyCode)
            || isBoundTo(o.keyLeft, keyCode) || isBoundTo(o.keyRight, keyCode);
    }

    private static boolean isBoundTo(KeyMapping mapping, int keyCode) {
        InputConstants.Key key = mapping.getKey();
        return key.getType() == InputConstants.Type.KEYSYM && key.getValue() == keyCode
            && keyCode != InputConstants.UNKNOWN.getValue();
    }

    /**
     * Clamps the pan so the visible canvas never extends past the finite board. When the viewport is
     * wider than the board on an axis (zoomed far out), that axis is centred on the board instead.
     * Call after any change to {@link #viewX}/{@link #viewY}, the zoom, or the window size.
     */
    private void clampView() {
        double zoom = getZoomFactor();
        double halfViewW = (imageWidth - 2 * CANVAS_SIDE_PADDING) / 2.0 / zoom;
        double halfViewH = (imageHeight - CANVAS_TOP_PADDING - CANVAS_BOTTOM_PADDING) / 2.0 / zoom;
        viewX = clampAxis(viewX, halfViewW);
        viewY = clampAxis(viewY, halfViewH);
        // Remember this controller's camera for the session so reopening it restores the same view.
        VIEW_CACHE.put(viewKey(), new ViewCacheEntry(viewX, viewY, zoomLevel));
    }

    private static double clampAxis(double view, double halfView) {
        double lower = BOARD_MIN_PX + halfView;
        double upper = BOARD_MAX_PX - halfView;
        if (lower >= upper) return (BOARD_MIN_PX + BOARD_MAX_PX) / 2.0;   // viewport ≥ board → centre
        return Mth.clamp(view, lower, upper);
    }

    /** Maps a screen position to canvas-world coordinates. */
    private Vector2d worldAt(double posX, double posY) {
        Rect2i canvas = canvasArea();
        return worldAt(posX, posY,
                canvas.minX() + canvas.w() / 2,
                canvas.minY() + canvas.h() / 2);
    }

    private Vector2d worldAt(double posX, double posY, int centerX, int centerY) {
        return new Vector2d(
                viewX + (posX - centerX) / getZoomFactor(),
                viewY + (posY - centerY) / getZoomFactor());
    }

    /** Maps canvas-world coordinates back to the current screen position. */
    private Vector2d screenAt(double worldX, double worldY) {
        Rect2i canvas = canvasArea();
        return screenAt(worldX, worldY,
                canvas.minX() + canvas.w() / 2,
                canvas.minY() + canvas.h() / 2);
    }

    private Vector2d screenAt(double worldX, double worldY, int centerX, int centerY) {
        double zoom = getZoomFactor();
        return new Vector2d(
                (worldX - viewX) * zoom + centerX,
                (worldY - viewY) * zoom + centerY);
    }

    private static VirtualComponentPosition cellAtWorld(double worldX, double worldY) {
        return new VirtualComponentPosition(
                (int) Math.floor(worldX / CANVAS_COMPONENT_SIZE),
                (int) Math.floor(worldY / CANVAS_COMPONENT_SIZE));
    }

    /** Maps a screen position to the canvas cell it falls into. */
    private VirtualComponentPosition at(double posX, double posY, int centerX, int centerY) {
        Vector2d world = worldAt(posX, posY, centerX, centerY);
        return cellAtWorld(world.x, world.y);
    }

    // ── JEI ghost drop (drag an item/fluid from JEI onto an empty board gauge to set its filter) ──────────────

    /** A board gauge offered as a JEI ghost drop target: its on-screen cell rect + which ingredient kinds its filter
     *  resolver accepts. */
    public record GhostGaugeTarget(net.minecraft.client.renderer.Rect2i area, VirtualComponentPosition pos,
                                   boolean acceptsItems, boolean acceptsFluids) {}

    /** The ghost drop hitbox is the central {@value #GHOST_TARGET_SIZE}×{@value #GHOST_TARGET_SIZE} of the gauge (the
     *  gauge body), not the whole {@link #CANVAS_COMPONENT_SIZE} cell — so a drop only registers over the gauge itself. */
    private static final int GHOST_TARGET_SIZE = 8;

    /** Screen rect of the gauge's central drop area, inverting {@link #at}: {@code screen = (world − view)·zoom + center},
     *  where the world span is the middle {@link #GHOST_TARGET_SIZE} of the cell. */
    private net.minecraft.client.renderer.Rect2i cellScreenRect(VirtualComponentPosition pos) {
        Rect2i canvas = canvasArea();
        int centerX = canvas.minX() + canvas.w() / 2;
        int centerY = canvas.minY() + canvas.h() / 2;
        double zoom = getZoomFactor();
        double margin = (CANVAS_COMPONENT_SIZE - GHOST_TARGET_SIZE) / 2.0;   // centre the 8×8 area in the 16 cell
        int sx = (int) Math.round((pos.x() * (double) CANVAS_COMPONENT_SIZE + margin - viewX) * zoom) + centerX;
        int sy = (int) Math.round((pos.y() * (double) CANVAS_COMPONENT_SIZE + margin - viewY) * zoom) + centerY;
        int size = (int) Math.ceil(GHOST_TARGET_SIZE * zoom);
        return new net.minecraft.client.renderer.Rect2i(sx, sy, size, size);
    }

    /** Every empty, on-screen gauge, as a JEI drop target. Only empty gauges (setting a filter), and only those whose
     *  cell centre lies in the canvas (so off-board / panel-covered gauges get no stray target). */
    public List<GhostGaugeTarget> ghostGaugeTargets() {
        List<GhostGaugeTarget> out = new ArrayList<>();
        for (VirtualComponentWidget w : componentWidgets.values()) {
            if (!(w.behaviour() instanceof VirtualGaugeBehaviour g) || !g.filter.isEmpty()) continue;
            net.minecraft.client.renderer.Rect2i area = cellScreenRect(w.position());
            if (!isInCanvasArea(area.getX() + area.getWidth() / 2.0,
                    area.getY() + area.getHeight() / 2.0)) continue;
            GaugeFilterResolver r = g.filterResolver();
            out.add(new GhostGaugeTarget(area, w.position(), r.acceptsItemDrop(), r.acceptsFluidDrop()));
        }
        return out;
    }

    /** JEI item drop onto {@code pos}: set the gauge's filter to the dragged item if it's still an empty gauge that
     *  accepts the stack. Reuses the same {@link GaugeSetItemPacket} the carried-item click path sends. */
    public void setGaugeFilterFromJei(VirtualComponentPosition pos, ItemStack stack) {
        if (!(componentAt(pos) instanceof VirtualGaugeBehaviour g) || !g.filter.isEmpty()) return;
        if (!g.filterResolver().acceptsFilter(stack)) return;
        PacketDistributor.sendToServer(new GaugeSetItemPacket(menu.controllerPos, pos, stack.copyWithCount(1), false));
    }

    /** JEI fluid drop onto {@code pos}: convert to the gauge's fluid-filter token (addon wrapper) and set it. */
    public void setGaugeFluidFromJei(VirtualComponentPosition pos, FluidStack fluid) {
        if (!(componentAt(pos) instanceof VirtualGaugeBehaviour g) || !g.filter.isEmpty()) return;
        ItemStack stack = g.filterResolver().fromFluid(fluid);
        if (!stack.isEmpty()) setGaugeFilterFromJei(pos, stack);
    }

    private Rect2i canvasArea() {
        return Rect2i.fromXYWH(leftPos + CANVAS_SIDE_PADDING, topPos + CANVAS_TOP_PADDING,
                imageWidth - 2 * CANVAS_SIDE_PADDING,
                imageHeight - CANVAS_TOP_PADDING - CANVAS_BOTTOM_PADDING);
    }

    private boolean isInCanvasArea(double x, double y) {
        if (!canvasArea().contains((int) x, (int) y, Rect2i.Boundary.HALF_OPEN)) return false;

        if (playerInventory != null && playerInventory.blocksCanvas(x, y)) return false;

        if (networkSelector.isMouseOver(x, y)) return false;
        if (indicatorColumn.isMouseOver(x, y)) return false;

        // Settings button — let its click reach the widget (via super.mouseClicked) instead of panning.
        if (settingsButton != null && x >= settingsButtonX() && x < settingsButtonX() + SETTINGS_BTN_W
                && y >= settingsButtonY() && y < settingsButtonY() + SETTINGS_BTN_H) return false;
        if (x >= settingsButtonX() && x < settingsButtonX() + SETTINGS_BTN_W
                && y >= blueprintButtonY() && y < blueprintButtonY() + SETTINGS_BTN_H) return false;

        return true;
    }

    /** Called by SyncPanelStatePacket after menu.gauges/knownNetworks are refreshed. */
    @Override
    public void onPanelSync() {
        rebuildGaugeWidgets();   // components were replaced with fresh instances; re-index them
        if (indicatorColumn != null) indicatorColumn.refresh();
    }

    public int guiWidth()  { return imageWidth; }
    public int guiHeight() { return imageHeight; }

    /** JEI exclusion zone covering the cosmetic controller model in the bottom-left corner. */
    @Override
    public List<net.minecraft.client.renderer.Rect2i> getExtraAreas() {
        return List.of(new net.minecraft.client.renderer.Rect2i(leftPos - 74, topPos + imageHeight - 80, 74, 80));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameBox != null && nameBox.isFocused()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                commitName();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                nameBox.setValue(menu.controllerName);
                nameBox.setFocused(false);
                collapseNameSelection();
                return true;
            }
            if (nameBox.keyPressed(keyCode, scanCode, modifiers) || nameBox.canConsumeInput())
                return true;
        }

        if (pendingPlacement != null && keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            cancelBlueprintPlacement();
            return true;
        }

        if (isPanKey(keyCode)) heldPanKeys.add(keyCode);

        VirtualComponentBehaviour hover = componentAt(hoveredPosition);

        if (CreateFactoryControllerClient.START_CONNECTION.matches(keyCode, scanCode) && hover != null
                && pendingConnectionTarget == null && pendingRelocateTarget == null && selected.isEmpty()) {
            beginConnectionMode(hoveredPosition);
            return true;
        }

        if (CreateFactoryControllerClient.RELOCATE_COMPONENT.matches(keyCode, scanCode) && hover != null
                && pendingConnectionTarget == null && pendingRelocateTarget == null && selected.isEmpty()) {
            beginRelocateMode(hoveredPosition);
            return true;
        }

        if (CreateFactoryControllerClient.TOGGLE_ALWAYS_SHOW_LABEL.matches(keyCode, scanCode)) {
            ClientConfig.toggleAlwaysShowLabel();
            // todo update some indicator button
            return true;
        }

        if (CreateFactoryControllerClient.CYCLE_ARROW_MODE.matches(keyCode, scanCode)) {
            // A selected wire takes priority: cycle just that wire through all 5 bend modes (auto included), and lock it
            // so a reshaped path that moves off the cursor stays selected until the cursor moves.
            if (hoveredConn != null) {
                revealConnectionTooltip();
                connArrowLocked = true;
                playWrenchSound();
                PacketDistributor.sendToServer(new CycleConnectionArrowModePacket(menu.controllerPos,
                        hoveredConn.connection.from, hoveredConn.connection.to));
                return true;
            }

            if (pendingConnectionTarget != null) {
                ConnectionResolver.Result result = connectionHoverResult();
                if (result != null && result.ok()) {
                    if (!java.util.Objects.equals(hoveredPosition, previewArrowTarget)) {
                        previewArrowTarget = hoveredPosition;
                        previewArrowMode = -1;
                    }
                    previewArrowMode = (previewArrowMode + 1) % 4;   // auto(-1) → 0 → 1 → 2 → 3 → 0
                    playWrenchSound();
                }
                return true;
            }

            if (hover != null) {
                Integer mode = outgoingArrowBendMode(hoveredPosition);
                if (mode != null) {
                    char[] dots = {'□', '□', '□', '□'};   // □□□□
                    dots[(mode + 1) % 4] = '■';                         // ■ marks the active mode (auto -1 → 0)
                    setTimedPrompt(CreateLang.translate("factory_panel.cycled_arrow_path", new String(dots))
                            .style(ChatFormatting.WHITE).component(), 3000);
                    playWrenchSound();
                    PacketDistributor.sendToServer(new CycleArrowModePacket(menu.controllerPos, hoveredPosition));
                }
                return true;
            }
        }

        if (CreateFactoryControllerClient.CYCLE_OPERATION_MODE.matches(keyCode, scanCode)) {
            if (hoveredConn != null && hoveredConn.connection.type.reversible()) {
                revealConnectionTooltip();
                Connection conn = hoveredConn.connection;
                if (conn.canReverse(this::componentAt)) {
                    playWrenchSound();
                    PacketDistributor.sendToServer(new ReverseConnectionPacket(menu.controllerPos, conn.from, conn.to));
                } else {
                    playDenySound();
                }
                return true;
            }
            if (hover == null) return super.keyPressed(keyCode, scanCode, modifiers);
            playWrenchSound();
            PacketDistributor.sendToServer(new CycleOperationModePacket(menu.controllerPos, hoveredPosition));
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        heldPanKeys.remove(keyCode);
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Nullable
    private Integer outgoingArrowBendMode(VirtualComponentPosition pos) {
        VirtualComponentBehaviour self = componentAt(pos);
        if (self == null) return null;
        List<Connection> toCycle = self.connectionsToCycle();
        return toCycle.isEmpty() ? null : toCycle.getFirst().arrowBendMode;
    }

    /** Builds a {@link ConnectionWidget} for every visible incoming connection this frame. */
    /** Board cells holding a component — the obstacle set connection paths route around (endpoints excepted). */
    private Set<VirtualComponentPosition> occupiedCells() {
        Set<VirtualComponentPosition> occupied = new java.util.HashSet<>();
        for (VirtualComponentWidget w : componentWidgets.values()) occupied.add(w.position());
        return occupied;
    }

    private List<ConnectionWidget> buildConnectionWidgets(Rect2i visibleArea) {
        Set<VirtualComponentPosition> occupied = occupiedCells();

        List<ConnectionWidget> result = new ArrayList<>();
        for (VirtualComponentWidget sink : componentWidgets.values()) {
            for (var conn : sink.behaviour().targetedBy().values()) {
                if (!ConnectionPathResolver.spanVisible(conn.from, conn.to, visibleArea)) continue;
                List<org.joml.Vector2i> path = ConnectionPathResolver.resolvePath(conn, occupied);
                if (path != null) result.add(new ConnectionWidget(conn, path));
            }
        }
        return result;
    }

    /** Rebuilds the position→widget index from the synced component list, preserving widget-owned client state. */
    private void rebuildGaugeWidgets() {
        movePreview = null;   // the live components it was reconstructed from just changed
        movePreviewKey = null;
        Map<VirtualComponentPosition, VirtualComponentWidget> previousWidgets = new LinkedHashMap<>(componentWidgets);
        componentWidgets.clear();
        for (VirtualComponentBehaviour b : menu.components) {
            VirtualComponentWidget widget = ComponentWidgetRegistry.create(b, previousWidgets.get(b.position()));
            if (widget != null) componentWidgets.put(b.position(), widget);
        }
        batchMove.reconcile(componentWidgets.keySet()).ifPresent(reconciled -> {
            selected.clear();
            selected.addAll(reconciled);
        });
        // Drop selected cells that no longer hold a component
        selected.retainAll(componentWidgets.keySet());
    }

    /** The widget at {@code pos}, or {@code null} if the cell is empty (O(1)). */
    @Nullable
    public VirtualComponentWidget componentWidgetAt(@Nullable VirtualComponentPosition pos) {
        return pos == null ? null : componentWidgets.get(pos);
    }

    /** The component at {@code pos} (any type), or {@code null} if the cell is empty. */
    @Nullable
    private VirtualComponentBehaviour componentAt(@Nullable VirtualComponentPosition pos) {
        VirtualComponentWidget w = componentWidgetAt(pos);
        return w == null ? null : w.behaviour();
    }
}
