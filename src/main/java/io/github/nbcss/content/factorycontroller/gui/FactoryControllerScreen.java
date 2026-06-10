package io.github.nbcss.content.factorycontroller.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.content.trains.station.NoShadowFontWrapper;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.CreateFactoryControllerClient;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.content.factorycontroller.*;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.ChatFormatting;
import io.github.nbcss.content.factorycontroller.packet.AddConnectionPacket;
import io.github.nbcss.content.factorycontroller.packet.AttachComponentPacket;
import io.github.nbcss.content.factorycontroller.packet.CycleArrowBendPacket;
import io.github.nbcss.content.factorycontroller.packet.MoveComponentPacket;
import io.github.nbcss.content.factorycontroller.packet.RenameControllerPacket;
import io.github.nbcss.content.factorycontroller.packet.RetuneCarriedPacket;
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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // Finite board bounds in canvas-world px: cells |x|,|y| ≤ BOARD_HALF_CELLS, so the region runs from
    // the left edge of the most-negative cell to the right edge of the most-positive one. Panning is
    // clamped so the visible canvas never leaves this rectangle (server enforces placement separately).
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

    // Decorative controller block model in the board's bottom-left corner (purely cosmetic).
    private static final int CONTROLLER_MODEL_SCALE = 4;

    private static final ResourceLocation FRAME_SPRITE = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/frame");
    // Reticle drawn over the gauge being acted on (connect/relocate). White 16×16 source, tinted.
    private static final ResourceLocation TARGET_SPRITE = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/target");
    // Edit-name cue drawn after the controller name when idle (indicator only). 9×9 sprite.
    private static final ResourceLocation RENAME_BUTTON_SPRITE = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/rename_button");
    private static final int RENAME_BUTTON_SIZE = 9;
    private static final ResourceLocation DEFAULT_BACKGROUND_TEX = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/background/create_bricks.png");

    // player_inventory.png layout (176×108, matching Create's convention)
    private static final ResourceLocation PLAYER_INVENTORY_TEX = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/player_inventory.png");
    private static final int INV_TEX_W         = 176;
    private static final int INV_TEX_H         = 108;
    private static final int INV_TEX_SLOT_LEFT = 8;
    private static final int INV_TEX_TITLE_H   = 18;
    private static final int INV_TEX_HOTBAR_Y  = 76;

    // Interaction state
    @Nullable private VirtualPanelPosition hoveredPosition = null;

    // Gauge widgets, indexed by board position for O(1) hit-testing and lookup. Rebuilt from
    // menu.components only when the panel state syncs (see rebuildGaugeWidgets), not per frame.
    private final Map<VirtualPanelPosition, VirtualGaugeWidget> gaugeWidgets = new LinkedHashMap<>();

    // Per-gauge indicator-bulb animation (Create's FactoryPanelBehaviour#bulb), kept here so it
    // survives the per-sync widget rebuild. Chases satisfied→1, and flashes to 1 on each request.
    private final Map<VirtualPanelPosition, LerpedFloat> bulbs = new HashMap<>();
    private final Map<VirtualPanelPosition, Long> bulbSeenRequest = new HashMap<>();

    // Board action mode (e.g. connecting). When active, actionPrompt is shown above the inventory and
    // board clicks are routed to the action instead of normal selection.
    @Nullable private VirtualPanelPosition pendingConnectionTarget = null;
    @Nullable private VirtualPanelPosition pendingRelocateTarget = null;
    @Nullable private Component actionPrompt = null;
    // When the prompt should disappear (millis). Long.MAX_VALUE for the persistent mode prompts;
    // a finite value for transient messages (e.g. the arrow-mode chime), which fade out near expiry.
    // Colour lives on the component itself (style); only the fade alpha is applied at draw time.
    private long actionPromptExpiry = Long.MAX_VALUE;

    // Pan drag state (middle mouse)
    private boolean isDragging = false;

    // Network selector widget
    private NetworkSelectorWidget networkSelector;

    // Inline, station-style rename field shown in the title bar. Blank ⇒ the default block name is
    // drawn instead (see the title section of renderBoard). Edits commit on Enter / screen close.
    private static final int NAME_COLOR = 0x582424;
    @Nullable private EditBox nameBox;

    public FactoryControllerScreen(FactoryControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
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

        int selectorX = leftPos + CANVAS_SIDE_PADDING + 6;
        int selectorY = topPos + CANVAS_TOP_PADDING + 8;
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

    /**
     * Left edge that centres a name of text {@code s} (and its trailing edit icon) in the title bar,
     * mirroring Create's {@code StationScreen#nameBoxX}: the text width is capped at the box width and a
     * 10px allowance leaves room for the icon.
     */
    private int nameBoxX(String s) {
        int width = nameBox == null ? imageWidth - 16 : nameBox.getWidth();
        return leftPos + imageWidth / 2 - (Math.min(font.width(s), width) + 10) / 2;
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

    /** Re-tunes the carried component item (selector scroll): optimistic client update + server packet. */
    private void retuneCarried(boolean clear, @Nullable UUID network) {
        ItemStack carried = menu.getCarried();
        if (!ComponentRegistry.containsItem(carried)) return;
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
        for (VirtualGaugeWidget w : gaugeWidgets.values()) {
            VirtualGaugeBehaviour g = w.behaviour();
            VirtualPanelPosition pos = g.position();
            LerpedFloat bulb = bulbs.computeIfAbsent(pos,
                    p -> LerpedFloat.linear().startWithValue(0).chase(0, 0.175, Chaser.EXP));
            long seen = bulbSeenRequest.getOrDefault(pos, Long.MIN_VALUE);
            if (g.lastRequestTick > seen) {
                bulb.setValue(1);                 // request fired → flash
                bulbSeenRequest.put(pos, g.lastRequestTick);
            }
            bulb.updateChaseTarget(g.satisfied || g.redstonePowered ? 1 : 0);
            bulb.tickChaser();
        }
        bulbs.keySet().removeIf(p -> !gaugeWidgets.containsKey(p));
        bulbSeenRequest.keySet().removeIf(p -> !gaugeWidgets.containsKey(p));
    }

    /** Current interpolated bulb glow [0,1] for the gauge at {@code pos} (0 if none). */
    private float bulbGlow(VirtualPanelPosition pos, float partialTick) {
        LerpedFloat bulb = bulbs.get(pos);
        return bulb == null ? 0f : (float) bulb.getValue(partialTick);
    }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        tickKeyboardPan();   // pan from held movement keys before the board is drawn this frame
        super.render(graphics, mouseX, mouseY, partialTick);
        // Hover tooltip for an existing gauge (suppressed while a board action mode is active, where
        // the hovered cell already gives white/red reticle feedback). hoveredPosition is set in renderBoard.
        VirtualGaugeWidget hovered = gaugeWidget(hoveredPosition);
        if (pendingConnectionTarget == null && pendingRelocateTarget == null) {
            if (hovered != null)
                graphics.renderComponentTooltip(font, hovered.getGaugeTooltip(menu), mouseX, mouseY);
            else if (networkSelector.isMouseOver(mouseX, mouseY))
                graphics.renderComponentTooltip(font, networkSelector.getTooltipLines(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderBoard(graphics, mouseX, mouseY, partialTick, false);

        // Decorative controller block model anchored in the bottom-left corner, drawn over the board.
        RenderSystem.enableBlend();
        GuiGameElement.of(new ItemStack(CreateFactoryController.FACTORY_CONTROLLER_ITEM.get()))
                .scale(CONTROLLER_MODEL_SCALE)
                .render(graphics, leftPos - 74, topPos + imageHeight - 80);

        // Inventory panel + its expand button, lifted above canvas gauge icons.
        RenderSystem.disableDepthTest();
        renderInventoryBackground(graphics);
        if (expandButton != null) expandButton.render(graphics, mouseX, mouseY, partialTick);

        // Action prompt above the inventory: an active mode (white) or a fading chime (tan). Each
        // component carries its own colour; we only modulate the fade alpha here.
        Component prompt = actionPrompt;
        int alpha = 255;
        if (prompt != null) {
            long remaining = actionPromptExpiry - Util.getMillis();
            if (remaining <= 0) { actionPrompt = null; prompt = null; }
            else alpha = (int) (Mth.clamp(remaining / 1000f, 0f, 1f) * 255f);
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
        TiledSpriteRenderer.create(DEFAULT_BACKGROUND_TEX, 0, 0, new GuiSpriteScaling.Tile(CANVAS_COMPONENT_SIZE, CANVAS_COMPONENT_SIZE))
                .render(graphics, bgStartX, bgStartY, bgEndX - bgStartX, bgEndY - bgStartY);

        for (VirtualGaugeWidget gauge : gaugeWidgets.values())
            gauge.renderBack(graphics);

        VirtualConnectionRenderer.renderConnections(graphics, menu);

        for (VirtualGaugeWidget gauge : gaugeWidgets.values())
            gauge.renderFront(graphics, bulbGlow(gauge.position(), partialTick));

        renderSelectedNetworkMask(graphics);
        renderHoverTarget(graphics);

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
            int x = nameBoxX(shownStr);
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

        // Component count
        int count = menu.components.size();
        Component countText = Component.translatable("createfactorycontroller.gui.capacity",
                count, FactoryControllerBlockEntity.MAX_COMPONENTS);
        int countColor = count >= FactoryControllerBlockEntity.MAX_COMPONENTS ? 0xFFFF5555 : 0xFFFFFFFF;
        graphics.drawString(font, countText, x1 - font.width(countText) - 4, y0 + 4, countColor, true);

        // Zoom factor just below it. The world-pixel scale is twice this displayed value, so we halve
        // it (e.g. the default 2.0 zoom shows "x1"); fractional levels show up to 2 trimmed decimals.
        double zoom = getZoomFactor() / 2.0;
        String zoomStr = String.format(java.util.Locale.ROOT, "%.2f", zoom);
        if (zoomStr.contains(".")) zoomStr = zoomStr.replaceAll("0+$", "").replaceAll("\\.$", "");
        Component zoomText = Component.translatable("createfactorycontroller.gui.zoom", zoomStr);
        graphics.drawString(font, zoomText, x1 - font.width(zoomText) - 4, y0 + 6 + font.lineHeight, 0xFFFFFFFF, true);

        // Reset depth for network icons & helper text
        graphics.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);   // 256 = GL_DEPTH_BUFFER_BIT

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
    private static final int TARGET_GREEN = 0x33CC33;
    private static final int TARGET_BLUE  = 0x55AAFF;

    /** Blue reticle over every component on the network currently selected in the network selector. */
    private void renderSelectedNetworkMask(GuiGraphics graphics) {
        UUID selected = networkSelector.getSelectedNetwork();
        if (selected == null) return;
        for (VirtualGaugeWidget gauge : gaugeWidgets.values())
            if (selected.equals(gauge.behaviour().networkId))
                renderTarget(graphics, gauge.position(), TARGET_BLUE);
    }

    /**
     * Draws the {@code target} reticle over the hovered cell, tinted by context: white over a gauge
     * with an empty cursor, gold over an empty cell when placing/relocating a gauge, and white/red
     * over a valid/invalid target while in connect mode. The gauge being connected/relocated is
     * always marked green, taking priority over the hover feedback on that cell. Must run inside the
     * canvas-world pose.
     */
    private void renderHoverTarget(GuiGraphics graphics) {
        // Source gauge of the active action mode — green, drawn first and given priority.
        VirtualPanelPosition source = pendingConnectionTarget != null ? pendingConnectionTarget : pendingRelocateTarget;
        if (source != null) renderTarget(graphics, source, TARGET_GREEN);

        if (hoveredPosition == null || hoveredPosition.equals(source)) return;
        boolean hoverHasGauge = findGauge(hoveredPosition) != null;
        ItemStack carried = menu.getCarried();

        if (pendingConnectionTarget != null) {
            // Connect mode: only gauges are valid targets — white if connectable, red otherwise.
            if (hoverHasGauge) {
                VirtualComponentBehaviour target = findGauge(pendingConnectionTarget);
                boolean valid = target != null
                        && !hoveredPosition.equals(pendingConnectionTarget)
                        && !target.targetedBy().containsKey(hoveredPosition);
                renderTarget(graphics, hoveredPosition, valid ? TARGET_WHITE : TARGET_RED);
            }
        } else if (pendingRelocateTarget != null) {
            // Relocate mode: white over a valid (empty) destination, red over an occupied cell.
            renderTarget(graphics, hoveredPosition, hoverHasGauge ? TARGET_RED : TARGET_WHITE);
        } else if (ComponentRegistry.containsItem(carried)) {
            // Holding a gauge: white over an empty cell (valid placement), red over an occupied cell —
            // or red anywhere if placement would fail for lack of a network.
            boolean noNetwork = networkForAttaching(carried) == null;
            renderTarget(graphics, hoveredPosition, (hoverHasGauge || noNetwork) ? TARGET_RED : TARGET_WHITE);
        } else if (!carried.isEmpty()) {
            // Holding a non-gauge item: white over an unconfigured gauge (clicking sets its filter).
            if (findGauge(hoveredPosition) instanceof VirtualGaugeBehaviour g && g.filter.isEmpty())
                renderTarget(graphics, hoveredPosition, TARGET_WHITE);
        } else {
            // Empty cursor: white over a hovered gauge.
            if (hoverHasGauge) renderTarget(graphics, hoveredPosition, TARGET_WHITE);
        }
    }

    /** Blits the 16×16 {@code target} sprite tinted {@code rgb}, filling the cell exactly. */
    private void renderTarget(GuiGraphics graphics, VirtualPanelPosition pos, int rgb) {
        int x0 = pos.x() * CANVAS_COMPONENT_SIZE;
        int y0 = pos.y() * CANVAS_COMPONENT_SIZE;
        RenderSystem.enableBlend();
        graphics.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        graphics.blitSprite(TARGET_SPRITE, x0, y0, CANVAS_COMPONENT_SIZE, CANVAS_COMPONENT_SIZE);
        graphics.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    // ── Mouse interaction ──────────────────────────────────────────────────

    /** Enters "add connection" mode: the next board gauge clicked becomes an input to {@code target}. */
    public void beginConnectionMode(VirtualPanelPosition target) {
        pendingConnectionTarget = target;
        setPersistentPrompt(CreateLang.translate("factory_panel.click_second_panel").style(ChatFormatting.WHITE).component());
    }

    /** Enters "relocate" mode: the next empty cell clicked becomes {@code target}'s new position. */
    public void beginRelocateMode(VirtualPanelPosition target) {
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

    /** Shows a timed error prompt and plays the deny sound. Called from sub-screens (e.g. configure overlay). */
    public void denyWithMessage(Component message, long durationMs) {
        setTimedPrompt(message, durationMs);
        playDenySound();
    }

    /**
     * The network a stack gauge would attach to, or {@code null} if placement isn't possible: a
     * tuned gauge brings its own network, an untuned one needs a known network selected.
     */
    @Nullable
    private UUID networkForAttaching(ItemStack stack) {
        return LogisticallyLinkedBlockItem.isTuned(stack) ? LogisticallyLinkedBlockItem.networkFromStack(stack) : null;
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
            VirtualPanelPosition clicked = at(mouseX, mouseY, centerX, centerY);
            ItemStack carried = menu.getCarried();
            VirtualGaugeWidget widget = gaugeWidget(clicked);
            boolean leftOrRight = button == 0 || button == 1;

            // Shift + left/right-click a gauge → remove it from the board (server refunds if survival).
            if (hasShiftDown() && leftOrRight && widget != null) {
                widget.remove(this);
                return true;
            }

            // Connection mode: clicking a gauge wires it as an input; clicking an empty cell cancels.
            // Client rejects a gauge with no filter set before sending anything.
            if (pendingConnectionTarget != null && leftOrRight) {
                actionPrompt = null;
                if (widget != null) {
                    if (widget.behaviour().filter.isEmpty()) {
                        setTimedPrompt(CreateLang.translate("factory_panel.no_item")
                                .style(ChatFormatting.RED).component(), 3000);
                        playDenySound();
                    } else {
                        PacketDistributor.sendToServer(new AddConnectionPacket(
                                menu.controllerPos, clicked, pendingConnectionTarget));
                    }
                }
                pendingConnectionTarget = null;
                return true;
            }

            // Relocate mode: send the destination and let the server move (empty cell) or reject
            // (occupied cell) with the matching sound. Either way the mode ends.
            if (pendingRelocateTarget != null && leftOrRight) {
                PacketDistributor.sendToServer(new MoveComponentPacket(
                        menu.controllerPos, pendingRelocateTarget, clicked));
                pendingRelocateTarget = null;
                actionPrompt = null;
                return true;
            }

            // Left-click with a carried gauge → attach it at the cell (server rejects if occupied).
            if (button == 0 && ComponentRegistry.containsItem(carried)) {
                // Board full (placing on an empty cell would add a component) → prompt, don't send.
                if (widget == null && menu.components.size() >= FactoryControllerBlockEntity.MAX_COMPONENTS) {
                    setTimedPrompt(Component.translatable("createfactorycontroller.gui.prompt.component_limit",
                            FactoryControllerBlockEntity.MAX_COMPONENTS).withStyle(ChatFormatting.RED), 3000);
                    playDenySound();
                    return true;
                }
                UUID network = networkForAttaching(carried);
                // No usable network → surface the requirement as a 3s board prompt, don't send.
                if (network == null) {
                    setTimedPrompt(Component.translatable(menu.knownNetworks.isEmpty() ?
                            "createfactorycontroller.gui.prompt.no_first_network" :
                            "createfactorycontroller.gui.prompt.no_network_tuned")
                            .withStyle(ChatFormatting.RED), 3000);
                    playDenySound();
                    return true;
                }
                PacketDistributor.sendToServer(new AttachComponentPacket(menu.controllerPos, clicked, network));
                return true;
            }

            // Click an existing gauge → its own interaction (set item / configure).
            if (leftOrRight && widget != null)
                return widget.onClick(this, carried);

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
    }

    private static double clampAxis(double view, double halfView) {
        double lower = BOARD_MIN_PX + halfView;
        double upper = BOARD_MAX_PX - halfView;
        if (lower >= upper) return (BOARD_MIN_PX + BOARD_MAX_PX) / 2.0;   // viewport ≥ board → centre
        return Mth.clamp(view, lower, upper);
    }

    /** Maps a screen position to the canvas cell it falls into. */
    private VirtualPanelPosition at(double posX, double posY, int centerX, int centerY) {
        int cellX = (int) Math.floor((viewX + (posX - centerX) / getZoomFactor()) / CANVAS_COMPONENT_SIZE);
        int cellY = (int) Math.floor((viewY + (posY - centerY) / getZoomFactor()) / CANVAS_COMPONENT_SIZE);
        return new VirtualPanelPosition(cellX, cellY);
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

        // The cycle-arrow key (rebindable, R by default) on a hovered gauge cycles its outgoing
        // connection bend mode (mirrors Create's wrench).
        if (CreateFactoryControllerClient.CYCLE_ARROW.matches(keyCode, scanCode)
                && hoveredPosition != null && findGauge(hoveredPosition) != null) {
            // Optimistically reflect the new mode (server cycles to (mode+1)%4) as a fading prompt,
            // reusing Create's "Cycled arrow pathing mode □□□□" message with the active mode filled.
            Integer mode = outgoingArrowBendMode(hoveredPosition);
            if (mode != null) {
                char[] dots = {'□', '□', '□', '□'};   // □□□□
                dots[(mode + 1) % 4] = '■';                         // ■ marks the active mode (auto -1 → 0)
                setTimedPrompt(CreateLang.translate("factory_panel.cycled_arrow_path", new String(dots))
                        .style(ChatFormatting.WHITE).component(), 3000);
            }
            PacketDistributor.sendToServer(new CycleArrowBendPacket(menu.controllerPos, hoveredPosition));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Current shared arrow-bend mode of {@code pos}'s outgoing connections (which may legitimately be
     * -1, the "auto" mode), or {@code null} if it has no outgoing connection. The client snapshot only
     * carries incoming connections ({@code targetedBy}), so we find the outgoing ones by scanning every
     * gauge for a connection whose source is {@code pos}.
     */
    @Nullable
    private Integer outgoingArrowBendMode(VirtualPanelPosition pos) {
        for (VirtualComponentBehaviour b : menu.components) {
            VirtualPanelConnection conn = b.targetedBy().get(pos);
            if (conn != null) return conn.arrowBendMode;
        }
        return null;
    }

    /** Rebuilds the position→widget index from the synced component list. */
    private void rebuildGaugeWidgets() {
        gaugeWidgets.clear();
        for (VirtualComponentBehaviour b : menu.components)
            if (b instanceof VirtualGaugeBehaviour gauge)
                gaugeWidgets.put(gauge.position(), new VirtualGaugeWidget(gauge));
    }

    /** The widget at {@code pos}, or {@code null} if the cell is empty (O(1)). */
    @Nullable
    VirtualGaugeWidget gaugeWidget(@Nullable VirtualPanelPosition pos) {
        return pos == null ? null : gaugeWidgets.get(pos);
    }

    @Nullable
    private VirtualComponentBehaviour findGauge(@Nullable VirtualPanelPosition pos) {
        VirtualGaugeWidget w = gaugeWidget(pos);
        return w == null ? null : w.behaviour();
    }
}
