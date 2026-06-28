package io.github.nbcss.createfactorycontroller.content.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import io.github.nbcss.createfactorycontroller.ClientConfig;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.CreateFactoryControllerClient;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.component.*;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionResolver;
import io.github.nbcss.createfactorycontroller.content.gui.widget.ComponentWidgetRegistry;
import io.github.nbcss.createfactorycontroller.content.gui.widget.NetworkSelectorWidget;
import io.github.nbcss.createfactorycontroller.content.gui.widget.TexturedButton;
import io.github.nbcss.createfactorycontroller.content.gui.widget.VirtualComponentWidget;
import io.github.nbcss.createfactorycontroller.content.packet.CycleArrowModePacket;
import io.github.nbcss.createfactorycontroller.content.packet.CycleOperationModePacket;
import net.minecraft.core.registries.BuiltInRegistries;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import io.github.nbcss.createfactorycontroller.content.render.VirtualConnectionRenderer;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.ChatFormatting;
import io.github.nbcss.createfactorycontroller.content.packet.AddConnectionPacket;
import io.github.nbcss.createfactorycontroller.content.packet.AttachComponentPacket;
import io.github.nbcss.createfactorycontroller.content.packet.BatchMoveComponentPacket;
import io.github.nbcss.createfactorycontroller.content.packet.MoveComponentPacket;
import io.github.nbcss.createfactorycontroller.content.packet.RenameControllerPacket;
import io.github.nbcss.createfactorycontroller.content.packet.RetuneCarriedPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FactoryControllerScreen extends AbstractSimiContainerScreen<FactoryControllerMenu> implements PanelSyncListener {

    // Responsive sizing — all in GUI-scaled pixels.
    private static final int SIDE_MARGIN = 160;
    private static final int VERTICAL_MARGIN = 10;
    private static final int MIN_IMAGE_W = 215;
    private static final int MIN_IMAGE_H = 200;
    static final int CANVAS_SIDE_PADDING = 9;     // package-visible: used by SetItemOverlay layout
    static final int CANVAS_TOP_PADDING = 16;
    static final int CANVAS_BOTTOM_PADDING = 9;
    private static final int CANVAS_COMPONENT_SIZE = 16;
    /** Off-screen render-cull slack (canvas-world px) around a component's own cell: a gauge's count label is anchored
     *  at the cell's bottom-right and can extend a few cells left/up beyond it, so cull a little loosely to never drop
     *  a component whose label still reaches the viewport. Everything is scissor-clipped, so this only gates draws. */
    private static final int COMPONENT_CULL_MARGIN = CANVAS_COMPONENT_SIZE;

    private static final int BOARD_MIN_PX = -FactoryControllerBlockEntity.BOARD_LIMIT * CANVAS_COMPONENT_SIZE;
    private static final int BOARD_MAX_PX = (FactoryControllerBlockEntity.BOARD_LIMIT + 1) * CANVAS_COMPONENT_SIZE;

    // Canvas view state
    private static final int MAX_ZOOM_LEVEL = 10;
    private static final int MIN_ZOOM_LEVEL = -20;
    // On-screen pan speed (px per second) when holding a movement key (WASD by default). Applied
    // per-frame against the real frame delta so panning stays smooth at any framerate.
    private static final double KEY_PAN_SPEED = 160.0;
    /** Wall-clock time (ms) of the previous keyboard-pan frame; 0 until the first frame. */
    private long lastPanFrameMs = 0;
    private double viewX = 0;
    private double viewY = 0;
    private int zoomLevel = 0;

    /** Session-only (never serialized) per-controller camera memory: reopening a controller restores its last
     *  pan/zoom. Keyed by dimension + block position (see {@link #viewKey()}); cleared on disconnect / restart. */
    private record ViewState(double x, double y, int zoom) {}
    private static final Map<String, ViewState> VIEW_CACHE = new HashMap<>();

    /** Drops all cached camera views — called on disconnect so a different world/server starts clean. */
    public static void clearViewCache() {
        VIEW_CACHE.clear();
    }

    /** Session-cache key: dimension id + controller position (e.g. {@code minecraft:overworld@10, 64, -20}). */
    private String viewKey() {
        var level = Minecraft.getInstance().level;
        String dim = level != null ? level.dimension().location().toString() : "?";
        return dim + "@" + menu.controllerPos.toShortString();
    }

    // Inventory panel state — all in menu-relative coords (relative to leftPos/topPos).
    private static final int INV_BOTTOM_MARGIN = 28;
    private static final int HOTBAR_H = 18;
    private static final int MAIN_INV_H = 54;
    private static final int INV_GAP = 4;
    private static final int SLOT_ROW_W = 162;
    private static final int EXPAND_BTN_SIZE = 12;
    private int invOriginX;
    private int invHotbarY;
    private boolean inventoryExpanded = false;
    @Nullable private Button expandButton = null;

    // Settings button (top-right of the board); opens the client-side background-picker overlay.
    private static final int SETTINGS_BTN_W = 23;
    private static final int SETTINGS_BTN_H = 24;
    private static final ResourceLocation SETTINGS_BTN_SPRITE =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/tool_bar/settings");
    private static final ResourceLocation SETTINGS_BTN_HOVER_SPRITE =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/tool_bar/settings_hover");
    @Nullable private TexturedButton settingsButton = null;

    // Decorative controller block model in the board's bottom-left corner (purely cosmetic).
    private static final int CONTROLLER_MODEL_SCALE = 4;

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
    private static final ResourceLocation PLAYER_INVENTORY_TEX = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/player_inventory.png");
    private static final int INV_TEX_W         = 176;
    private static final int INV_TEX_H         = 108;
    private static final int INV_TEX_SLOT_LEFT = 8;
    private static final int INV_TEX_TITLE_H   = 18;
    private static final int INV_TEX_HOTBAR_Y  = 76;

    // Interaction state
    @Nullable private VirtualComponentPosition hoveredPosition = null;

    private final Map<VirtualComponentPosition, VirtualComponentWidget> componentWidgets = new LinkedHashMap<>();

    private final Map<VirtualComponentPosition, LerpedFloat> bulbs = new HashMap<>();
    private final Map<VirtualComponentPosition, Long> bulbSeenRequest = new HashMap<>();

    // Board action mode (e.g. connecting).
    @Nullable private VirtualComponentPosition pendingConnectionTarget = null;
    @Nullable private VirtualComponentPosition pendingRelocateTarget = null;
    @Nullable private Component actionPrompt = null;
    // When the prompt should disappear (millis). Long.MAX_VALUE for the persistent mode prompts;
    // a finite value for transient messages (e.g. the arrow-mode chime), which fade out near expiry.
    private long actionPromptExpiry = Long.MAX_VALUE;

    // Pan drag state (middle mouse)
    private boolean isDragging = false;

    // ── Selection mode (client-only) ─────────────────────────────────────────
    /** Components currently marked "selected" (gold reticle). Client-only; never serialized or synced. */
    private final Set<VirtualComponentPosition> selected = new LinkedHashSet<>();
    // Rubber-band drag (only while the Selection-Mode key is held): screen-space start/current + a moved flag that
    // distinguishes a drag (rectangle select) from a click (toggle / clear).
    private boolean rubberBanding = false;
    private boolean rubberMoved = false;
    private boolean rubberCtrl = false;   // was the Selection-Mode key held when the rubber-band started?
    private double rubberStartX, rubberStartY, rubberCurX, rubberCurY;
    // Batch relocate drag (normal mode, started by pressing a selected component): the press cell + the live cell delta.
    private boolean batchRelocating = false;
    @Nullable private VirtualComponentPosition batchAnchor = null;
    private int batchDx = 0, batchDy = 0;

    // Pending batch relocate: after sending the move, the selection must follow the components across the old→new
    // position transition, which can span several syncs (a pre-move/old-position sync may arrive before the move's own).
    // rebuildGaugeWidgets re-keys the selection from these until the destinations appear (or it times out), instead of
    // letting its retainAll drop the not-yet-present positions.
    @Nullable private Set<VirtualComponentPosition> pendingMoveSources = null;
    @Nullable private Set<VirtualComponentPosition> pendingMoveDestinations = null;
    private int pendingMoveSyncs = 0;

    // Network selector widget
    private NetworkSelectorWidget networkSelector;

    // Hover bounds (screen coords) of the top-left status labels, refreshed each renderBoard so render()
    // can show their one-line tooltips. {x0, y0, x1, y1}; null until first drawn.
    @Nullable private int[] capacityLabelBounds = null;
    @Nullable private int[] zoomLabelBounds = null;

    // Inline, station-style rename field shown in the title bar. Blank ⇒ the default block name is
    // drawn instead (see the title section of renderBoard). Edits commit on Enter / screen close.
    private static final int NAME_COLOR = 0x582424;
    @Nullable private EditBox nameBox;

    public FactoryControllerScreen(FactoryControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // Restore this controller's last-session pan/zoom (clampView in init() re-fits it to the window).
        ViewState saved = VIEW_CACHE.get(viewKey());
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

        invHotbarY = scaledH - INV_BOTTOM_MARGIN - HOTBAR_H - topPos;
        invOriginX = (imageWidth - SLOT_ROW_W) / 2 + 1;
        menu.repositionSlots(invOriginX, invHotbarY, inventoryExpanded);

        if (expandButton != null) removeWidget(expandButton);
        expandButton = Button.builder(Component.literal(inventoryExpanded ? "-" : "+"), btn -> toggleInventory())
                .pos(expandButtonX(), expandButtonY())
                .size(EXPAND_BTN_SIZE, EXPAND_BTN_SIZE)
                .build();
        // Event-only: rendered manually in renderBg at the inventory panel's elevated z.
        addWidget(expandButton);

        // Settings button, top-right corner of the board. Event-only (rendered manually in renderBoard);
        // its area is excluded from the canvas hit-test so clicks reach the widget instead of panning.
        if (settingsButton != null) removeWidget(settingsButton);
        settingsButton = new TexturedButton(settingsButtonX(), settingsButtonY(), SETTINGS_BTN_W, SETTINGS_BTN_H,
                SETTINGS_BTN_SPRITE, SETTINGS_BTN_HOVER_SPRITE,
                () -> {
                    clearSelection();   // entering an overlay clears the selection
                    Minecraft.getInstance().setScreen(new ControllerSettingScreen(this));
                    return true;
                })
            .withTooltip(Component.translatable("createfactorycontroller.gui.controller_settings"));
        addWidget(settingsButton);

        int selectorX = leftPos + CANVAS_SIDE_PADDING + 6;
        int selectorY = topPos + CANVAS_TOP_PADDING + 6;
        if (networkSelector == null)
            networkSelector = new NetworkSelectorWidget(selectorX, selectorY, menu, this::retuneCarried, this::onNetworkSelected);
        else
            networkSelector.setPosition(selectorX, selectorY);

        // Rebuild the rename field at the new layout, preserving any in-progress edit across resize.
        // Geometry mirrors Create's station name box (y+4, height 10, unbordered) so the text baseline
        // is identical whether or not it's focused (the box is always rendered — no idle/focus shift).
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

        lastPanFrameMs = 0;   // fresh frame-delta base on (re)entry, so the first pan frame doesn't jump
    }

    /** Sends the edited controller name to the server (if changed) and leaves edit mode. */
    private void commitName() {
        if (nameBox == null) return;
        nameBox.setFocused(false);
        collapseNameSelection();   // drop the selection highlight now that we're no longer editing
        String value = nameBox.getValue().strip();
        if (value.equals(menu.controllerName)) return;
        menu.controllerName = value;   // optimistic; the server clamps and re-syncs
        PacketDistributor.sendToServer(new RenameControllerPacket(menu.controllerPos, value));
    }

    /** Collapses the name field's text selection (cursor to end, highlight = cursor) so no highlight
     *  lingers once the box is unfocused — mirrors Create's station tick() behaviour. */
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

    private ResourceLocation getBackgroundTexture() {
        return ResourceLocation.fromNamespaceAndPath("createfactorycontroller",
                "textures/gui/controller_background/" + ClientConfig.getControllerBackground() + ".png");
    }

    /** Re-tunes the carried component item (selector scroll): optimistic client update + server packet. */
    private void retuneCarried(boolean clear, @Nullable UUID network) {
        ItemStack carried = menu.getCarried();
        if (!ComponentRegistry.containsNetworkItem(carried)) return;   // networkless items (links) carry no frequency
        RetuneCarriedPacket.apply(carried, clear ? null : network);   // immediate cursor feedback
        menu.setCarried(carried);
        PacketDistributor.sendToServer(new RetuneCarriedPacket(clear, network));
        // (the scroll sound is played by the selector for both holding and non-holding scrolls)
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

    private int expandButtonX() {
        return leftPos + invOriginX + SLOT_ROW_W - 13;
    }

    private int expandButtonY() {
        int topOfInv = invHotbarY - (inventoryExpanded ? INV_GAP + MAIN_INV_H : 0);
        return topPos + topOfInv - EXPAND_BTN_SIZE - 2;
    }

    private void toggleInventory() {
        inventoryExpanded = !inventoryExpanded;
        menu.repositionSlots(invOriginX, invHotbarY, inventoryExpanded);
        if (expandButton != null) removeWidget(expandButton);
        expandButton = Button.builder(Component.literal(inventoryExpanded ? "-" : "+"), btn -> toggleInventory())
                .pos(expandButtonX(), expandButtonY())
                .size(EXPAND_BTN_SIZE, EXPAND_BTN_SIZE)
                .build();
        addWidget(expandButton);
    }

    // ── Indicator bulb animation ─────────────────────────────────────────────

    @Override
    protected void containerTick() {
        super.containerTick();
        if (nameBox != null && !nameBox.isFocused())
            collapseNameSelection();
        tickBulbs();
    }

    /**
     * Advance each gauge's indicator bulb (mirrors FactoryPanelBehaviour#tick): chase satisfied, and
     * flash to full on every fresh request attempt (detected via the synced lastRequestTick).
     *
     * <p>Public so a sub-screen ({@link SetItemScreen}, {@link ConfigureRecipeScreen}) that renders this
     * board as its background can keep the bulbs ticking while it is the active screen — otherwise the
     * parent's {@code containerTick} never runs and the bulbs freeze.</p>
     */
    public void tickBulbs() {
        for (VirtualComponentWidget w : componentWidgets.values()) {
            if (!(w.behaviour() instanceof VirtualGaugeBehaviour g)) continue;   // only gauges have bulbs
            VirtualComponentPosition pos = g.position();
            boolean firstSeen = !bulbSeenRequest.containsKey(pos);
            float steady = g.satisfied || g.redstonePowered ? 1 : 0;
            LerpedFloat bulb = bulbs.computeIfAbsent(pos,
                    p -> LerpedFloat.linear().startWithValue(steady).chase(0, 0.175, Chaser.EXP));
            if (firstSeen) {
                bulbSeenRequest.put(pos, g.lastRequestTick);
            } else if (g.lastRequestTick > bulbSeenRequest.get(pos)) {
                bulb.setValue(1);                 // request fired → flash
                bulbSeenRequest.put(pos, g.lastRequestTick);
            }
            bulb.updateChaseTarget(steady);
            bulb.tickChaser();
        }
        bulbs.keySet().removeIf(p -> !componentWidgets.containsKey(p));
        bulbSeenRequest.keySet().removeIf(p -> !componentWidgets.containsKey(p));
    }

    /** Current interpolated bulb glow [0,1] for the gauge at {@code pos} (0 if none). */
    private float bulbGlow(VirtualComponentPosition pos, float partialTick) {
        LerpedFloat bulb = bulbs.get(pos);
        return bulb == null ? 0f : bulb.getValue(partialTick);
    }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tickKeyboardPan();   // pan from held movement keys before the board is drawn this frame
        super.render(graphics, mouseX, mouseY, partialTick);
        VirtualComponentWidget hovered = componentWidgetAt(hoveredPosition);
        // No hover tooltips while a selection drag (rubber-band rectangle or batch relocate) is in progress.
        boolean selectionDragging = rubberBanding || batchRelocating;
        if (pendingConnectionTarget == null && pendingRelocateTarget == null && !selectionDragging) {
            if (hovered != null)
                graphics.renderComponentTooltip(font, hovered.getTooltip(menu, selected.contains(hoveredPosition)), mouseX, mouseY);
            else if (networkSelector.isMouseOver(mouseX, mouseY))
                graphics.renderComponentTooltip(font, networkSelector.getTooltipLines(), mouseX, mouseY);
            else if (inBounds(capacityLabelBounds, mouseX, mouseY))
                graphics.renderTooltip(font, Component.translatable("createfactorycontroller.gui.capacity"), mouseX, mouseY);
            else if (inBounds(zoomLabelBounds, mouseX, mouseY))
                graphics.renderTooltip(font, Component.translatable("createfactorycontroller.gui.zoom"), mouseX, mouseY);
            // Drawn here (after super.render) rather than by the button itself, so it stacks above the board
            // frame and JEI's item overlay instead of being painted under them during renderBg.
            else if (settingsButton != null && settingsButton.isMouseOver(mouseX, mouseY)
                    && settingsButton.getTooltipText() != null)
                graphics.renderTooltip(font, settingsButton.getTooltipText(), mouseX, mouseY);
        }
    }

    /** True if {@code (mx, my)} lies within a cached {x0, y0, x1, y1} label box (null box ⇒ false). */
    private static boolean inBounds(int @Nullable [] b, double mx, double my) {
        return b != null && mx >= b[0] && mx < b[2] && my >= b[1] && my < b[3];
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
        renderInventoryBackground(graphics);
        if (expandButton != null) expandButton.render(graphics, mouseX, mouseY, partialTick);

        // Action prompt above the inventory: an active mode (white) or a fading chime (tan). Each
        // component carries its own colour; we only modulate the fade alpha here.
        // The Selection-Mode status line (persistent, yellow) takes precedence over the transient/mode action prompt;
        // the action prompt still ticks/expires underneath so it reappears once the selection status clears.
        Component selectionStatus = selectionStatusPrompt();
        Component prompt;
        int alpha = 255;
        if (selectionStatus != null) {
            prompt = selectionStatus;
        } else {
            prompt = actionPrompt;
            if (prompt != null) {
                long remaining = actionPromptExpiry - Util.getMillis();
                if (remaining <= 0) { actionPrompt = null; prompt = null; }
                else alpha = (int) (Mth.clamp(remaining / 1000f, 0f, 1f) * 255f);
            }
        }
        if (prompt != null && alpha > 4) {
            int invTop = topPos + invHotbarY - (inventoryExpanded ? INV_GAP + MAIN_INV_H : 0) - INV_TEX_TITLE_H;
            int px = leftPos + imageWidth / 2 - font.width(prompt) / 2;
            int py = invTop - 14;
            // 8-direction outline (like the gauge count labels) so the prompt reads over any canvas content;
            // the prompt keeps its own colour (white mode / tan chime), with the fade folded into the alpha.
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            Matrix4f matrix = graphics.pose().last().pose();
            font.drawInBatch8xOutline(prompt.getVisualOrderText(), px, py,
                    (alpha << 24) | 0xFFFFFF, (alpha << 24),
                    matrix, graphics.bufferSource(), LightTexture.FULL_BRIGHT);
            graphics.flush();
            graphics.pose().popPose();
        }
        RenderSystem.enableDepthTest();
    }

    /**
     * Renders the board itself — tiled background, gauges, connection arrows, frame and the network
     * selector — but <b>not</b> the player inventory. Exposed so {@link SetItemScreen} can draw the
     * live board as its (dimmed) backdrop. Pass {@code mouseX/Y = -1} to suppress hover.
     */
    public void renderBoard(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick, boolean inOverlay) {
        int x0 = leftPos + CANVAS_SIDE_PADDING;
        int y0 = topPos + CANVAS_TOP_PADDING;
        int x1 = leftPos + imageWidth - CANVAS_SIDE_PADDING;
        int y1 = topPos + imageHeight - CANVAS_BOTTOM_PADDING;
        int centerX = (x0 + x1) / 2;
        int centerY = (y0 + y1) / 2;

        graphics.enableScissor(x0, y0, x1, y1);

        // Visible canvas-world pixel bounds
        int minX = (int) Math.floor(viewX + (x0 - centerX) / getZoomFactor());
        int minY = (int) Math.floor(viewY + (y0 - centerY) / getZoomFactor());
        int maxX = (int) Math.ceil(viewX + (x1 - centerX) / getZoomFactor());
        int maxY = (int) Math.ceil(viewY + (y1 - centerY) / getZoomFactor());

        hoveredPosition = isInCanvasArea(mouseX, mouseY) ? at(mouseX, mouseY, centerX, centerY) : null;

        // Canvas-world → screen pose (translate to centre, scale by zoom, translate by the pan).
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale((float) getZoomFactor(), (float) getZoomFactor(), (float) getZoomFactor());
        graphics.pose().translate((float) -viewX, (float) -viewY, 0);

        // Background — one tile per component cell, snapped to cell boundaries (world-locked).
        int bgStartX = Math.floorDiv(minX, CANVAS_COMPONENT_SIZE) * CANVAS_COMPONENT_SIZE;
        int bgStartY = Math.floorDiv(minY, CANVAS_COMPONENT_SIZE) * CANVAS_COMPONENT_SIZE;
        int bgEndX   = Math.floorDiv(maxX, CANVAS_COMPONENT_SIZE) * CANVAS_COMPONENT_SIZE + CANVAS_COMPONENT_SIZE;
        int bgEndY   = Math.floorDiv(maxY, CANVAS_COMPONENT_SIZE) * CANVAS_COMPONENT_SIZE + CANVAS_COMPONENT_SIZE;
        TiledSpriteRenderer.create(getBackgroundTexture(), 0, 0,
                        new GuiSpriteScaling.Tile(CANVAS_COMPONENT_SIZE, CANVAS_COMPONENT_SIZE))
                .render(graphics, bgStartX, bgStartY, bgEndX - bgStartX, bgEndY - bgStartY);

        // Cull components and connections that fall outside the visible canvas rectangle (a large board may hold far
        // more than fit the viewport); everything drawn is still scissor-clipped, this just skips the off-screen draws.
        for (VirtualComponentWidget component : componentWidgets.values())
            if (isCellVisible(component.position(), minX, minY, maxX, maxY))
                component.renderBack(graphics);

        VirtualConnectionRenderer.renderConnections(graphics, menu, minX, minY, maxX, maxY);

        boolean fullOverlay = ClientConfig.fullOverlay();
        // Cursor in canvas-world coords (so a widget can hit-test sub-regions); off-board when hover is suppressed.
        double worldMouseX = viewX + (mouseX - centerX) / getZoomFactor();
        double worldMouseY = viewY + (mouseY - centerY) / getZoomFactor();
        for (VirtualComponentWidget component : componentWidgets.values())
            if (isCellVisible(component.position(), minX, minY, maxX, maxY))
                component.renderFront(graphics, worldMouseX, worldMouseY, bulbGlow(component.position(), partialTick));

        renderSelectedNetworkMask(graphics);
        renderHoverTarget(graphics);
        renderSelectionTargets(graphics);

        // Count labels last, on top of the hover/selection target marks so the reticle never covers the number.
        for (VirtualComponentWidget component : componentWidgets.values())
            if (isCellVisible(component.position(), minX, minY, maxX, maxY))
                component.renderOverlay(graphics, fullOverlay || component.position().equals(hoveredPosition));

        graphics.pose().popPose();
        graphics.disableScissor();

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
            int nameWidth = nameBox == null ? imageWidth - 16 : nameBox.getWidth();
            int x = leftPos + imageWidth / 2 - (Math.min(font.width(shownStr), nameWidth) + 10) / 2;
            nameBox.setX(x);
            nameBox.render(graphics, mouseX, mouseY, partialTick);
            if (!nameBox.isFocused()) {
                if (blank)
                    graphics.drawString(font, menu.controllerDisplayName(), x, nameBox.getY(), NAME_COLOR, false);
                // Edit-name icon after the text — indicator-only cue, vertically centred on the text.
                graphics.blitSprite(RENAME_BUTTON_SPRITE,
                        x + font.width(shownStr) + 5,
                        nameBox.getY() + (font.lineHeight - RENAME_BUTTON_SIZE) / 2 - 1,
                        RENAME_BUTTON_SIZE, RENAME_BUTTON_SIZE);
            }
        }

        // Reset depth for gauge filter icons
        graphics.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);   // 256 = GL_DEPTH_BUFFER_BIT

        networkSelector.render(graphics, mouseX, mouseY, partialTick);

        // Status labels, left-aligned just right of the network selector: an icon followed by a value,
        // capacity on top and zoom below. Each label's icon+text bounds is cached for the hover tooltip.
        int labelX = networkSelector.getX() + networkSelector.getWidth() + 3;
        int row0Y = networkSelector.getY() + 2;
        int row1Y = row0Y + font.lineHeight + 2;

        // Capacity: "<count>/<max>", reddened when full.
        int count = menu.components.size();
        int max = FactoryControllerBlockEntity.maxComponents();   // server config value (synced to client)
        String capacityStr = count + "/" + max;
        int capacityColor = count >= max ? 0xFFFF5555 : 0xFFE2E2E2;
        int capTextX = labelX + CAPACITY_ICON_SIZE + 3;
        graphics.blitSprite(CAPACITY_ICON, labelX, row0Y + (font.lineHeight - CAPACITY_ICON_SIZE) / 2,
                CAPACITY_ICON_SIZE, CAPACITY_ICON_SIZE);
        graphics.drawString(font, capacityStr, capTextX, row0Y, capacityColor, true);
        capacityLabelBounds = new int[]{labelX, row0Y, capTextX + font.width(capacityStr), row0Y + font.lineHeight};

        // Zoom: "x<factor>". The world-pixel scale is twice this displayed value, so we halve it (e.g. the
        // default 2.0 zoom shows "x1"); fractional levels show up to 2 trimmed decimals.
        double zoom = getZoomFactor() / 2.0;
        String zoomStr = String.format(java.util.Locale.ROOT, "%.2f", zoom);
        if (zoomStr.contains(".")) zoomStr = zoomStr.replaceAll("0+$", "").replaceAll("\\.$", "");
        zoomStr = "x" + zoomStr;
        int zoomTextX = labelX + ZOOM_ICON_SIZE + 1;
        graphics.blitSprite(ZOOM_ICON, labelX, row1Y + (font.lineHeight - ZOOM_ICON_SIZE) / 2,
                ZOOM_ICON_SIZE, ZOOM_ICON_SIZE);
        graphics.drawString(font, zoomStr, zoomTextX, row1Y, 0xFFE2E2E2, true);
        zoomLabelBounds = new int[]{labelX, row1Y, zoomTextX + font.width(zoomStr), row1Y + font.lineHeight};

        // Settings button, top-right corner (event-only widget; drawn here over the board frame).
        if (settingsButton != null) settingsButton.render(graphics, mouseX, mouseY, partialTick);

        // Reset depth for network icons & helper text
        graphics.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);   // 256 = GL_DEPTH_BUFFER_BIT

        // Rubber-band selection rectangle (screen space, translucent green), clipped to the canvas.
        if (rubberBanding && rubberMoved) {
            int rx0 = (int) Math.min(rubberStartX, rubberCurX);
            int ry0 = (int) Math.min(rubberStartY, rubberCurY);
            int rx1 = (int) Math.max(rubberStartX, rubberCurX);
            int ry1 = (int) Math.max(rubberStartY, rubberCurY);
            graphics.enableScissor(x0, y0, x1, y1);
            graphics.fill(rx0, ry0, rx1, ry1, 0x3333CC33);          // body
            graphics.fill(rx0, ry0, rx1, ry0 + 1, 0xAA33CC33);      // top border
            graphics.fill(rx0, ry1 - 1, rx1, ry1, 0xAA33CC33);      // bottom
            graphics.fill(rx0, ry0, rx0 + 1, ry1, 0xAA33CC33);      // left
            graphics.fill(rx1 - 1, ry0, rx1, ry1, 0xAA33CC33);      // right
            graphics.disableScissor();
        }

        if (inOverlay) {
            graphics.fill(x0, y0, x1, y1, 0xB0101010);
        }
    }

    /** Caller is responsible for any z-translation (see renderBg). */
    private void renderInventoryBackground(GuiGraphics gfx) {
        int texX    = leftPos + invOriginX - INV_TEX_SLOT_LEFT;
        int hotbarY = topPos + invHotbarY;

        if (inventoryExpanded) {
            int texY = hotbarY - INV_TEX_HOTBAR_Y;
            gfx.blit(PLAYER_INVENTORY_TEX, texX, texY, 0, 0, INV_TEX_W, INV_TEX_H);
            gfx.drawString(font, playerInventoryTitle, texX + 8, texY + 6, 0x404040, false);
        } else {
            int headerY      = hotbarY - INV_TEX_TITLE_H;
            int hotbarStripH = INV_TEX_H - INV_TEX_HOTBAR_Y;
            gfx.blit(PLAYER_INVENTORY_TEX, texX, headerY, 0, 0,                INV_TEX_W, INV_TEX_TITLE_H);
            gfx.blit(PLAYER_INVENTORY_TEX, texX, hotbarY, 0, INV_TEX_HOTBAR_Y, INV_TEX_W, hotbarStripH);
            gfx.drawString(font, playerInventoryTitle, texX + 8, headerY + 6, 0x404040, false);
        }
    }

    // Reticle tints.
    private static final int TARGET_WHITE = 0xFFFFFF;
    private static final int TARGET_RED   = 0xFF3333;
    private static final int TARGET_GREEN = 0x33CC33;   // also the selected-component mark
    private static final int TARGET_BLUE  = 0x55AAFF;

    /** Blue reticle over every component on the network currently selected in the network selector. */
    private void renderSelectedNetworkMask(GuiGraphics graphics) {
        UUID selected = networkSelector.getSelectedNetwork();
        if (selected == null) return;
        for (VirtualComponentWidget component : componentWidgets.values())
            if (component.behaviour() instanceof VirtualGaugeBehaviour g && selected.equals(g.networkId))
                renderTarget(graphics, component.position(), TARGET_BLUE);
    }

    private void renderHoverTarget(GuiGraphics graphics) {
        // Source gauge of the active action mode — green, drawn first and given priority.
        VirtualComponentPosition source = pendingConnectionTarget != null ? pendingConnectionTarget : pendingRelocateTarget;
        if (source != null) renderTarget(graphics, source, TARGET_GREEN);

        if (hoveredPosition == null || hoveredPosition.equals(source)) return;
        boolean hoverHasGauge = componentAt(hoveredPosition) != null;
        ItemStack carried = menu.getCarried();

        if (pendingConnectionTarget != null) {
            // Connect mode: hovering another component shows white if it can be wired (same validation the click and
            // the server run), red otherwise — via the shared ConnectionResolver, no pairwise instanceof here.
            if (hoverHasGauge && !hoveredPosition.equals(pendingConnectionTarget)) {
                VirtualComponentBehaviour initiator = componentAt(pendingConnectionTarget);
                VirtualComponentBehaviour hovered = componentAt(hoveredPosition);
                boolean valid = ConnectionResolver.resolve(hovered, initiator, initiator).ok();
                renderTarget(graphics, hoveredPosition, valid ? TARGET_WHITE : TARGET_RED);
            }
        } else if (pendingRelocateTarget != null) {
            // Relocate mode: white over a valid (empty) destination, red over an occupied cell.
            boolean valid = !hoverHasGauge && !FactoryControllerBlockEntity.isOutBoard(hoveredPosition);
            renderTarget(graphics, hoveredPosition, valid ? TARGET_WHITE : TARGET_RED);
            VirtualComponentBehaviour moving = componentAt(pendingRelocateTarget);
            if (valid && moving != null) renderGhostAt(graphics, hoveredPosition, moving.getItem());   // preview at destination
        } else if (ComponentRegistry.containsItem(carried)) {
            // Holding a component: white over an empty cell (valid placement), red over an occupied cell —
            // or red anywhere if a gauge placement would fail for lack of a network (links need none).
            boolean needsNet = ComponentRegistry.needsNetwork(BuiltInRegistries.ITEM.getKey(carried.getItem()));
            boolean noNetwork = needsNet && networkForAttaching(carried) == null;
            boolean valid = !hoverHasGauge && !noNetwork && !FactoryControllerBlockEntity.isOutBoard(hoveredPosition);
            renderTarget(graphics, hoveredPosition, valid ? TARGET_WHITE : TARGET_RED);
            if (valid) renderGhostAt(graphics, hoveredPosition, carried.getItem());   // translucent preview of the placement
        } else if (!carried.isEmpty()) {
            // Holding a non-component item: white over an unconfigured gauge (sets filter) or any link (sets frequency).
            if (componentAt(hoveredPosition) instanceof VirtualGaugeBehaviour g && g.filter.isEmpty())
                renderTarget(graphics, hoveredPosition, TARGET_WHITE);
            else if (componentAt(hoveredPosition) instanceof VirtualRedstoneLinkBehaviour)
                renderTarget(graphics, hoveredPosition, TARGET_WHITE);
        } else {
            // Empty cursor: white over a hovered gauge.
            if (hoverHasGauge) renderTarget(graphics, hoveredPosition, TARGET_WHITE);
        }
    }

    /** Draws a half-translucent preview of {@code item}'s component at {@code pos} (its back + front, as a fresh/blank
     *  component — no bulb, filter, or other content). Built from a throwaway client behaviour and drawn in the canvas
     *  pose, so it lands on {@code pos}. Used both for cursor placement and for relocate destinations. */
    private void renderGhostAt(GuiGraphics graphics, VirtualComponentPosition pos, net.minecraft.world.item.Item item) {
        VirtualComponentBehaviour ghost = ComponentRegistry.createFromItem(null, pos, item, null);
        VirtualComponentWidget widget = ghost == null ? null : ComponentWidgetRegistry.create(ghost);
        if (widget == null) return;
        RenderSystem.enableBlend();   // the gauge widget doesn't enable blend itself, so the alpha below would be ignored
        graphics.setColor(1f, 1f, 1f, 0.5f);
        widget.renderGhost(graphics);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /** Whether a component at {@code cell} could contribute any pixel to the visible canvas rectangle
     *  {@code [minX,maxX]×[minY,maxY]} (canvas-world px), within {@link #COMPONENT_CULL_MARGIN} slack for its label. */
    private static boolean isCellVisible(VirtualComponentPosition cell, int minX, int minY, int maxX, int maxY) {
        int x0 = cell.x() * CANVAS_COMPONENT_SIZE - COMPONENT_CULL_MARGIN;
        int y0 = cell.y() * CANVAS_COMPONENT_SIZE - COMPONENT_CULL_MARGIN;
        int x1 = (cell.x() + 1) * CANVAS_COMPONENT_SIZE + COMPONENT_CULL_MARGIN;
        int y1 = (cell.y() + 1) * CANVAS_COMPONENT_SIZE + COMPONENT_CULL_MARGIN;
        return x0 < maxX && x1 > minX && y0 < maxY && y1 > minY;
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

    // ── Selection mode ───────────────────────────────────────────────────────

    /** Drops all selected components (and any in-progress selection drag). Client-only; called when a controller
     *  overlay opens (per the feature spec) and on a left-click on empty space. */
    public void clearSelection() {
        selected.clear();
        rubberBanding = false;
        batchRelocating = false;
    }

    /** The canvas cell a screen position falls into (computes the board centre itself). */
    private VirtualComponentPosition cellAt(double posX, double posY) {
        int x0 = leftPos + CANVAS_SIDE_PADDING;
        int y0 = topPos + CANVAS_TOP_PADDING;
        int x1 = leftPos + imageWidth - CANVAS_SIDE_PADDING;
        int y1 = topPos + imageHeight - CANVAS_BOTTOM_PADDING;
        return at(posX, posY, (x0 + x1) / 2, (y0 + y1) / 2);
    }

    /** Whether a batch relocate by the current delta would put {@code to} on a valid cell (in-board and not onto a
     *  non-selected component). Mirrors the server's {@code moveComponents} validation for the drag ghosts. */
    private boolean batchDestValid(VirtualComponentPosition to) {
        boolean occupiedByOther = componentWidgets.containsKey(to) && !selected.contains(to);
        return !FactoryControllerBlockEntity.isOutBoard(to) && !occupiedByOther;
    }

    /** The inclusive {minX, minY, maxX, maxY} cell box spanned by the current rubber-band rectangle. */
    private int[] rubberCellBox() {
        VirtualComponentPosition a = cellAt(rubberStartX, rubberStartY);
        VirtualComponentPosition b = cellAt(rubberCurX, rubberCurY);
        return new int[]{Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.max(a.x(), b.x()), Math.max(a.y(), b.y())};
    }

    private static boolean inBox(int[] box, VirtualComponentPosition p) {
        return p.x() >= box[0] && p.x() <= box[2] && p.y() >= box[1] && p.y() <= box[3];
    }

    /** Green mark on selected components; white/red ghosts during a batch drag; and a live green preview of every
     *  component currently inside the rubber-band rectangle while drag-selecting. */
    private void renderSelectionTargets(GuiGraphics graphics) {
        // Green selected marks first...
        for (VirtualComponentPosition p : selected)
            if (componentWidgets.containsKey(p)) renderTarget(graphics, p, TARGET_GREEN);
        if (rubberBanding && rubberMoved) {   // live preview: components the drag would select show the mark too
            int[] box = rubberCellBox();
            for (VirtualComponentPosition p : componentWidgets.keySet())
                if (inBox(box, p)) renderTarget(graphics, p, TARGET_GREEN);
        }
        // ...then the batch-relocate ghosts ON TOP, so a white/red target landing on a still-present selected gauge
        // (one that is itself moving away) draws over its green mark instead of being hidden behind it.
        if (batchRelocating && (batchDx != 0 || batchDy != 0)) {
            for (VirtualComponentPosition p : selected) {
                VirtualComponentBehaviour moving = componentAt(p);
                if (moving == null) continue;
                VirtualComponentPosition to = new VirtualComponentPosition(p.x() + batchDx, p.y() + batchDy);
                boolean valid = batchDestValid(to);
                renderTarget(graphics, to, valid ? TARGET_WHITE : TARGET_RED);
                if (valid) renderGhostAt(graphics, to, moving.getItem());   // preview each moved component at its destination
            }
        }
    }

    /** Adds every component whose cell lies within the rubber-band rectangle to the selection (additive). */
    private void selectInRect() {
        int[] box = rubberCellBox();
        for (VirtualComponentPosition p : componentWidgets.keySet())
            if (inBox(box, p)) selected.add(p);
    }

    /** The persistent (yellow) "Selected N components" status line, or {@code null} when nothing is selected. During a
     *  drag-select the count includes the components currently inside the rubber-band rectangle. */
    @Nullable
    private Component selectionStatusPrompt() {
        int count = effectiveSelectedCount();
        return count > 0
            ? Component.translatable("createfactorycontroller.gui.selection.count", count).withStyle(ChatFormatting.GREEN)
            : null;
    }

    /** The selection count, including the live rubber-band rectangle contents while drag-selecting. */
    private int effectiveSelectedCount() {
        if (!rubberBanding || !rubberMoved) return selected.size();
        Set<VirtualComponentPosition> union = new LinkedHashSet<>(selected);
        int[] box = rubberCellBox();
        for (VirtualComponentPosition p : componentWidgets.keySet())
            if (inBox(box, p)) union.add(p);
        return union.size();
    }

    /** A selection-mode click that didn't drag: toggle the component under the cursor, or clear all on empty space. */
    private void toggleOrClearAt(double mx, double my) {
        VirtualComponentPosition cell = cellAt(mx, my);
        if (componentWidgets.containsKey(cell)) {
            if (!selected.remove(cell)) selected.add(cell);
        } else {
            clearSelection();
        }
    }

    /** Commits the batch relocate on release: validates ALL destinations (mirroring the server), sends the packet,
     *  and optimistically re-anchors the selection at the new positions. A zero-delta release is a no-op. */
    private void commitBatchRelocate() {
        if (batchDx == 0 && batchDy == 0) return;
        List<VirtualComponentPosition> sources = new ArrayList<>();
        for (VirtualComponentPosition p : selected)
            if (componentWidgets.containsKey(p)) sources.add(p);
        if (sources.isEmpty()) return;

        for (VirtualComponentPosition p : sources)
            if (!batchDestValid(new VirtualComponentPosition(p.x() + batchDx, p.y() + batchDy))) {
                playDenySound();
                return;
            }

        PacketDistributor.sendToServer(new BatchMoveComponentPacket(menu.controllerPos, sources, batchDx, batchDy));
        // Track the pending move so the selection follows it across syncs (see rebuildGaugeWidgets). The selection
        // stays on the sources for now (the client hasn't moved the components yet); it snaps to the destinations the
        // first sync that shows them. Re-anchoring eagerly here is wiped by any in-flight old-position sync.
        pendingMoveSources = new LinkedHashSet<>(sources);
        pendingMoveDestinations = new LinkedHashSet<>();
        for (VirtualComponentPosition p : sources)
            pendingMoveDestinations.add(new VirtualComponentPosition(p.x() + batchDx, p.y() + batchDy));
        pendingMoveSyncs = 20;   // bound: a rejected/lost move can't pin the selection forever
    }

    private void clearPendingMove() {
        pendingMoveSources = null;
        pendingMoveDestinations = null;
        pendingMoveSyncs = 0;
    }

    // ── Mouse interaction ──────────────────────────────────────────────────

    /** Enters "add connection" mode: the next board gauge clicked becomes an input to {@code target}. */
    public void beginConnectionMode(VirtualComponentPosition target) {
        pendingConnectionTarget = target;
        setPersistentPrompt(CreateLang.translate("factory_panel.click_second_panel").style(ChatFormatting.WHITE).component());
    }

    /** Enters "relocate" mode: the next empty cell clicked becomes {@code target}'s new position. */
    public void beginRelocateMode(VirtualComponentPosition target) {
        pendingRelocateTarget = target;
        setPersistentPrompt(CreateLang.translate("factory_panel.click_to_relocate").style(ChatFormatting.WHITE).component());
    }

    /** A prompt that stays until the mode ends (no fade). */
    private void setPersistentPrompt(Component prompt) {
        actionPrompt = prompt;
        actionPromptExpiry = Long.MAX_VALUE;
    }

    /** A transient prompt that fades out {@code durationMs} after being shown. */
    private void setTimedPrompt(Component prompt, long durationMs) {
        actionPrompt = prompt;
        actionPromptExpiry = Util.getMillis() + durationMs;
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
     *  when not carrying a component. Reached from the rubber-band release path (every empty-cell press starts one). */
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

    /**
     * Connection mode: wire the clickedPos component as the other end of the pending connection (or cancel). Always
     * consumes the click and ends the mode. A redstone-link wire (link ↔ gauge) is stored on the link and uncapped,
     * its arrow following the link's Send/Receive mode, so it works the same from either side; a gauge → gauge wire
     * feeds the source's filter into the consumer (the consumer is capped, the source must carry a filter). Two links
     * can't wire to each other. Pair logic lives here (not on a clickedWidget) so the link↔gauge rules aren't duplicated.
     */
    private void completeConnection(VirtualComponentPosition clickedPos,
                                    @Nullable VirtualComponentWidget clickedWidget) {
        VirtualComponentPosition targetPos = pendingConnectionTarget;
        pendingConnectionTarget = null;
        actionPrompt = null;
        if (clickedWidget == null || clickedPos.equals(targetPos)) {
            setTimedPrompt(CreateLang.translate("factory_panel.connection_aborted")
                    .style(ChatFormatting.WHITE).component(), 3000);
            return;
        }
        VirtualComponentBehaviour clicked = clickedWidget.behaviour();    // the clickedPos component
        VirtualComponentBehaviour initiator = componentAt(targetPos);    // the component that started the mode (= sink)
        if (initiator == null) return;

        // The single shared validator — identical to the hover preview and the server. The server re-validates and
        // performs the storage, so the client just sends the packet on success (no optimistic mutation). The result
        // carries the prompt for both outcomes (green confirmation on success, red reason on failure).
        ConnectionResolver.Result result = ConnectionResolver.resolve(clicked, initiator, initiator);
        if (!result.ok()) {
            showConnectionMessage(result);
            playDenySound();
            return;
        }
        PacketDistributor.sendToServer(new AddConnectionPacket(menu.controllerPos, result.type().name(), result.source(), result.sink()));
        showConnectionMessage(result);
    }

    /** Shows the resolver result's lazy message as a timed prompt, if any. */
    private void showConnectionMessage(ConnectionResolver.Result result) {
        if (result.validation().message() != null)
            setTimedPrompt(result.validation().message().get(), 3000);
    }

    /**
     * Relocate mode: move the pending component to the clicked cell if it's empty and in-board; otherwise cancel.
     * Always consumes the click and ends the mode.
     */
    private void completeRelocate(VirtualComponentPosition clicked, @Nullable VirtualComponentWidget widget) {
        VirtualComponentPosition from = pendingRelocateTarget;
        pendingRelocateTarget = null;
        actionPrompt = null;
        if (widget == null && !FactoryControllerBlockEntity.isOutBoard(clicked)) {
            PacketDistributor.sendToServer(new MoveComponentPacket(menu.controllerPos, from, clicked));
            setTimedPrompt(CreateLang.translate("factory_panel.relocated")
                    .style(ChatFormatting.GREEN).component(), 3000);
        } else {
            setTimedPrompt(CreateLang.translate("factory_panel.relocation_aborted")
                    .style(ChatFormatting.WHITE).component(), 3000);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Name field (title bar): clicking it begins editing with all text selected (mirrors Create's
        // station); clicking anywhere else commits the pending rename and leaves edit mode.
        if (nameBox != null) {
            boolean inNameBar = mouseY > topPos && mouseY < topPos + 14
                    && mouseX > leftPos && mouseX < leftPos + imageWidth;
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

        if (isInCanvasArea(mouseX, mouseY)) {
            int x0 = leftPos + CANVAS_SIDE_PADDING;
            int y0 = topPos + CANVAS_TOP_PADDING;
            int x1 = leftPos + imageWidth - CANVAS_SIDE_PADDING;
            int y1 = topPos + imageHeight - CANVAS_BOTTOM_PADDING;
            int centerX = (x0 + x1) / 2;
            int centerY = (y0 + y1) / 2;
            VirtualComponentPosition clicked = at(mouseX, mouseY, centerX, centerY);
            ItemStack carried = menu.getCarried();
            VirtualComponentWidget widget = componentWidgetAt(clicked);
            boolean leftOrRight = button == 0 || button == 1;

            boolean ctrl = isKeyHeld(CreateFactoryControllerClient.SELECTION_MODE);
            if (button == 0 && carried.isEmpty() && pendingConnectionTarget == null && pendingRelocateTarget == null
                    && (ctrl || widget == null)) {
                rubberBanding = true;
                rubberMoved = false;
                rubberCtrl = ctrl;
                rubberStartX = rubberCurX = mouseX;
                rubberStartY = rubberCurY = mouseY;
                return true;
            }

            // Carrying an item + left-click on an empty cell → place it (the rubber-band path above is skipped while
            // carrying, so do the placement here, mirroring the old rubber-band release behaviour).
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

            // Selection handling
            if (leftOrRight && widget != null) {
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
                    batchRelocating = true;
                    batchAnchor = widget.position();
                    batchDx = batchDy = 0;
                    return true;
                }
                return widget.onClick(this, carried, worldX, worldY, button);
            }

            // Pan-view button (rebindable, middle mouse by default) → start a drag-pan.
            if (CreateFactoryControllerClient.PAN_VIEW.matchesMouse(button)) {
                isDragging = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Rubber-band: track the current corner; a few pixels of travel promotes the click to a drag.
        if (button == 0 && rubberBanding) {
            rubberCurX = mouseX;
            rubberCurY = mouseY;
            if (Math.abs(mouseX - rubberStartX) > 3 || Math.abs(mouseY - rubberStartY) > 3) rubberMoved = true;
            return true;
        }
        // Batch relocate: the live cell delta from the anchor drives the ghost reticles.
        if (button == 0 && batchRelocating && batchAnchor != null) {
            VirtualComponentPosition cell = cellAt(mouseX, mouseY);
            batchDx = cell.x() - batchAnchor.x();
            batchDy = cell.y() - batchAnchor.y();
            return true;
        }
        if (isDragging && CreateFactoryControllerClient.PAN_VIEW.matchesMouse(button)) {
            viewX -= deltaX / getZoomFactor();
            viewY -= deltaY / getZoomFactor();
            clampView();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && rubberBanding) {
            rubberBanding = false;
            if (rubberMoved) {
                selectInRect();                     // a drag → additive rectangle select
            } else if (rubberCtrl) {
                toggleOrClearAt(mouseX, mouseY);    // a Ctrl click → toggle the component, or clear on empty
            } else {
                // a normal-mode click on empty space → drop the selection, then place a carried component if any
                clearSelection();
                attachCarriedAt(cellAt(mouseX, mouseY), menu.getCarried());
            }
            return true;
        }
        if (button == 0 && batchRelocating) {
            batchRelocating = false;
            commitBatchRelocate();
            return true;
        }
        if (CreateFactoryControllerClient.PAN_VIEW.matchesMouse(button)) isDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (networkSelector.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;

        if (isInCanvasArea(mouseX, mouseY)) {
            int x0 = leftPos + CANVAS_SIDE_PADDING;
            int y0 = topPos + CANVAS_TOP_PADDING;
            int x1 = leftPos + imageWidth - CANVAS_SIDE_PADDING;
            int y1 = topPos + imageHeight - CANVAS_BOTTOM_PADDING;
            int centerX = (x0 + x1) / 2;
            int centerY = (y0 + y1) / 2;

            // Shift+scroll over the canvas (the inventory panel and selector are already excluded by
            // isInCanvasArea) changes the network selection instead of zooming.
            if (hasShiftDown()) {
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
        if (previous == 0) return;   // first frame: establish the time base, don't jump

        if (nameBox != null && nameBox.isFocused()) return;
        Minecraft mc = Minecraft.getInstance();
        double dx = 0, dy = 0;
        if (isKeyHeld(mc.options.keyLeft))  dx -= 1;
        if (isKeyHeld(mc.options.keyRight)) dx += 1;
        if (isKeyHeld(mc.options.keyUp))    dy -= 1;   // Forward → reveal content above (view moves up)
        if (isKeyHeld(mc.options.keyDown))  dy += 1;
        if (dx == 0 && dy == 0) return;

        // Seconds since the last frame, clamped so a hitch / paused frame can't fling the view.
        double elapsedSec = Math.min((now - previous) / 1000.0, 0.1);
        double step = KEY_PAN_SPEED * elapsedSec / getZoomFactor();
        viewX += dx * step;
        viewY += dy * step;
        clampView();
    }

    /** Whether {@code mapping}'s bound keyboard key is physically held — {@link KeyMapping#isDown()}
     *  doesn't update while a screen is open, so we poll the window directly (keyboard keys only). */
    private static boolean isKeyHeld(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        if (key.getType() != InputConstants.Type.KEYSYM || key.getValue() == InputConstants.UNKNOWN.getValue())
            return false;
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key.getValue());
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
        VIEW_CACHE.put(viewKey(), new ViewState(viewX, viewY, zoomLevel));
    }

    private static double clampAxis(double view, double halfView) {
        double lower = BOARD_MIN_PX + halfView;
        double upper = BOARD_MAX_PX - halfView;
        if (lower >= upper) return (BOARD_MIN_PX + BOARD_MAX_PX) / 2.0;   // viewport ≥ board → centre
        return Mth.clamp(view, lower, upper);
    }

    /** Maps a screen position to the canvas cell it falls into. */
    private VirtualComponentPosition at(double posX, double posY, int centerX, int centerY) {
        int cellX = (int) Math.floor((viewX + (posX - centerX) / getZoomFactor()) / CANVAS_COMPONENT_SIZE);
        int cellY = (int) Math.floor((viewY + (posY - centerY) / getZoomFactor()) / CANVAS_COMPONENT_SIZE);
        return new VirtualComponentPosition(cellX, cellY);
    }

    private boolean isInCanvasArea(double x, double y) {
        int x0 = leftPos + CANVAS_SIDE_PADDING;
        int y0 = topPos + CANVAS_TOP_PADDING;
        int x1 = leftPos + imageWidth - CANVAS_SIDE_PADDING;
        int y1 = topPos + imageHeight - CANVAS_BOTTOM_PADDING;
        if (x < x0 || x >= x1 || y < y0 || y >= y1) return false;

        int invLeft = leftPos + invOriginX - INV_TEX_SLOT_LEFT;
        int invTop  = topPos  + invHotbarY - (inventoryExpanded ? INV_TEX_HOTBAR_Y : INV_TEX_TITLE_H);
        int invBot  = topPos  + invHotbarY + INV_TEX_H - INV_TEX_HOTBAR_Y - 7;
        if (x >= invLeft && x < invLeft + INV_TEX_W && y >= invTop && y < invBot) return false;

        if (networkSelector.isMouseOver(x, y)) return false;

        // Settings button — let its click reach the widget (via super.mouseClicked) instead of panning.
        if (settingsButton != null && x >= settingsButtonX() && x < settingsButtonX() + SETTINGS_BTN_W
                && y >= settingsButtonY() && y < settingsButtonY() + SETTINGS_BTN_H) return false;

        return true;
    }

    /** Called by SyncPanelStatePacket after menu.gauges/knownNetworks are refreshed. */
    @Override
    public void onPanelSync() {
        rebuildGaugeWidgets();   // components were replaced with fresh instances; re-index them
    }

    public int guiWidth()  { return imageWidth; }
    public int guiHeight() { return imageHeight; }

    /** JEI exclusion zone covering the cosmetic controller model in the bottom-left corner. */
    @Override
    public List<Rect2i> getExtraAreas() {
        return List.of(new Rect2i(leftPos - 74, topPos + imageHeight - 80, 74, 80));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // While renaming, route keys to the name field: Enter commits, Escape reverts, and every other
        // key is swallowed so it can't trigger a board shortcut or the inventory-close key.
        if (nameBox != null && nameBox.isFocused()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                commitName();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                nameBox.setValue(menu.controllerName);   // revert the in-progress edit
                nameBox.setFocused(false);
                collapseNameSelection();
                return true;
            }
            if (nameBox.keyPressed(keyCode, scanCode, modifiers) || nameBox.canConsumeInput())
                return true;
        }
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

        // Toggle "full overlay" (rebindable, Left Alt by default). Handled here — never in-world or on
        // another screen — and persisted client-side via ClientConfig so it survives controllers/sessions.
        if (CreateFactoryControllerClient.TOGGLE_FULL_OVERLAY.matches(keyCode, scanCode)) {
            ClientConfig.toggleFullOverlay();
            // todo update some indicator button
            return true;
        }

        if (CreateFactoryControllerClient.CYCLE_ARROW_MODE.matches(keyCode, scanCode) && hover != null) {
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

        if (CreateFactoryControllerClient.CYCLE_OPERATION_MODE.matches(keyCode, scanCode) && hover != null) {
            playWrenchSound();
            PacketDistributor.sendToServer(new CycleOperationModePacket(menu.controllerPos, hoveredPosition));
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * The shared arrow-bend mode {@code pos}'s cycle-arrow key would advance — read from the exact same
     * {@link VirtualComponentBehaviour#connectionsToCycle()} set the server cycles (single source of truth), so this
     * preview always matches what the server applies. {@code null} if there are no such connections; may be -1 (auto).
     */
    @Nullable
    private Integer outgoingArrowBendMode(VirtualComponentPosition pos) {
        VirtualComponentBehaviour self = componentAt(pos);
        if (self == null) return null;
        List<Connection> toCycle = self.connectionsToCycle();
        return toCycle.isEmpty() ? null : toCycle.getFirst().arrowBendMode;
    }

    /** Rebuilds the position→widget index from the synced component list (one widget per component, via the registry). */
    private void rebuildGaugeWidgets() {
        componentWidgets.clear();
        for (VirtualComponentBehaviour b : menu.components) {
            VirtualComponentWidget widget = ComponentWidgetRegistry.create(b);
            if (widget != null) componentWidgets.put(b.position(), widget);
        }
        // Follow a pending batch relocate across the old→new transition: keep the selection on the sources until the
        // destinations appear, then snap to them. Without this, the retainAll below would drop the not-yet-present set
        // (a pre-move sync prunes the new cells; the move sync then prunes the old → empty).
        if (pendingMoveDestinations != null) {
            if (componentWidgets.keySet().containsAll(pendingMoveDestinations)) {
                selected.clear();
                selected.addAll(pendingMoveDestinations);
                clearPendingMove();
            } else if (--pendingMoveSyncs <= 0) {
                clearPendingMove();   // move never landed (rejected/lost) → fall through to normal retainAll
            } else {
                selected.clear();     // still waiting: hold the selection on the sources that remain
                selected.addAll(pendingMoveSources);
            }
        }
        // Drop selected cells that no longer hold a component (removed, or relocated away — possibly by another
        // player), so the selection count and marks stay in sync with the board.
        selected.retainAll(componentWidgets.keySet());
    }

    /** The widget at {@code pos}, or {@code null} if the cell is empty (O(1)). */
    @Nullable
    VirtualComponentWidget componentWidgetAt(@Nullable VirtualComponentPosition pos) {
        return pos == null ? null : componentWidgets.get(pos);
    }

    /** The component at {@code pos} (any type), or {@code null} if the cell is empty. */
    @Nullable
    private VirtualComponentBehaviour componentAt(@Nullable VirtualComponentPosition pos) {
        VirtualComponentWidget w = componentWidgetAt(pos);
        return w == null ? null : w.behaviour();
    }
}
