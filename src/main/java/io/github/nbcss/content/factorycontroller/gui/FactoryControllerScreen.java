package io.github.nbcss.content.factorycontroller.gui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.content.factorycontroller.*;
import net.minecraft.ChatFormatting;
import io.github.nbcss.content.factorycontroller.packet.AddConnectionPacket;
import io.github.nbcss.content.factorycontroller.packet.AttachComponentPacket;
import io.github.nbcss.content.factorycontroller.packet.CycleArrowBendPacket;
import io.github.nbcss.content.factorycontroller.packet.MoveComponentPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.Util;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class FactoryControllerScreen extends AbstractSimiContainerScreen<FactoryControllerMenu> {

    // Responsive sizing — all in GUI-scaled pixels.
    private static final int SIDE_MARGIN = 160;
    private static final int VERTICAL_MARGIN = 10;
    private static final int MIN_IMAGE_W = 195;      // matches vanilla creative inventory width
    private static final int MIN_IMAGE_H = 200;
    static final int CANVAS_SIDE_PADDING = 9;     // package-visible: used by SetItemOverlay layout
    static final int CANVAS_TOP_PADDING = 16;
    static final int CANVAS_BOTTOM_PADDING = 9;
    private static final int CANVAS_COMPONENT_SIZE = 16;

    // Canvas view state
    private static final int MAX_ZOOM_LEVEL = 10;
    private static final int MIN_ZOOM_LEVEL = -10;
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

    private static final ResourceLocation FRAME_SPRITE = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/frame");
    // Reticle drawn over the gauge being acted on (connect/relocate). White 18×18 source, tinted green.
    private static final ResourceLocation TARGET_SPRITE = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/target");
    private static final ResourceLocation DEFAULT_BACKGROUND_TEX = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/background_default.png");

    // player_inventory.png layout (176×108, matching Create's convention)
    private static final ResourceLocation PLAYER_INVENTORY_TEX = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/player_inventory.png");
    private static final int INV_TEX_W         = 176;
    private static final int INV_TEX_H         = 108;
    private static final int INV_TEX_SLOT_LEFT = 8;
    private static final int INV_TEX_TITLE_H   = 18;
    private static final int INV_TEX_HOTBAR_Y  = 76;

    // Interaction state
    @Nullable private VirtualPanelPosition hoveredPosition = null;
    @Nullable private VirtualPanelPosition selectedComponent = null;

    // Gauge widgets, indexed by board position for O(1) hit-testing and lookup. Rebuilt from
    // menu.components only when the panel state syncs (see rebuildGaugeWidgets), not per frame.
    private final Map<VirtualPanelPosition, VirtualGaugeWidget> gaugeWidgets = new LinkedHashMap<>();

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

    public FactoryControllerScreen(FactoryControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
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

        int selectorX = leftPos + CANVAS_SIDE_PADDING + 4;
        int selectorY = topPos + CANVAS_TOP_PADDING + 4;
        if (networkSelector == null)
            networkSelector = new NetworkSelectorWidget(selectorX, selectorY, menu.knownNetworks);
        else
            networkSelector.setPosition(selectorX, selectorY);
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

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // Hover tooltip for an existing gauge (suppressed while a board action mode is active, where
        // the hovered cell already gives white/red reticle feedback). hoveredPosition is set in renderBoard.
        VirtualGaugeWidget hovered = gaugeWidget(hoveredPosition);
        if (pendingConnectionTarget == null && pendingRelocateTarget == null && hovered != null)
            graphics.renderComponentTooltip(font, hovered.getGaugeTooltip(), mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderBoard(graphics, mouseX, mouseY, partialTick, false);

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
            graphics.drawCenteredString(font, prompt, leftPos + imageWidth / 2, invTop - 14, (alpha << 24) | 0xFFFFFF);
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
            gauge.renderFront(graphics, gauge.position().equals(selectedComponent));

        renderHoverTarget(graphics);

        graphics.pose().popPose();
        graphics.disableScissor();

        // Frame
        RenderSystem.enableBlend();
        TiledSpriteRenderer.create(FRAME_SPRITE).render(graphics, leftPos, topPos, imageWidth, imageHeight);
        RenderSystem.disableBlend();

        // Title
        int titleX = leftPos + (imageWidth - font.width(title)) / 2;
        int titleY = topPos + 4;
        graphics.drawString(font, title, titleX, titleY, 0x582424, false);

        // Reset depth for gauge filter icons
        graphics.flush();
        RenderSystem.clear(256, Minecraft.ON_OSX);   // 256 = GL_DEPTH_BUFFER_BIT

        networkSelector.render(graphics, mouseX, mouseY, partialTick);

        // Reset depth for network icons
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
            // Holding a gauge: white over an empty cell (valid placement), red over an occupied cell.
            renderTarget(graphics, hoveredPosition, hoverHasGauge ? TARGET_RED : TARGET_WHITE);
        } else if (carried.isEmpty()) {
            // Empty cursor: white over a hovered gauge.
            if (hoverHasGauge) renderTarget(graphics, hoveredPosition, TARGET_WHITE);
        }
    }

    /** Blits the white {@code target} sprite tinted {@code rgb}, overhanging the 16-px cell by 1 px. */
    private void renderTarget(GuiGraphics graphics, VirtualPanelPosition pos, int rgb) {
        int x0 = pos.x() * CANVAS_COMPONENT_SIZE - 1;
        int y0 = pos.y() * CANVAS_COMPONENT_SIZE - 1;
        RenderSystem.enableBlend();
        graphics.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        graphics.blitSprite(TARGET_SPRITE, x0, y0, CANVAS_COMPONENT_SIZE + 2, CANVAS_COMPONENT_SIZE + 2);
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

    private void clearActionMode() {
        pendingConnectionTarget = null;
        pendingRelocateTarget = null;
        actionPrompt = null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
                if (clicked.equals(selectedComponent)) selectedComponent = null;
                return true;
            }

            // Connection mode: clicking a gauge wires it as an input (server validates and plays the
            // connect / deny sound); clicking an empty cell just cancels. Either way the mode ends.
            if (pendingConnectionTarget != null && leftOrRight) {
                if (widget != null)
                    PacketDistributor.sendToServer(new AddConnectionPacket(
                            menu.controllerPos, clicked, pendingConnectionTarget));
                clearActionMode();
                return true;
            }

            // Relocate mode: send the destination and let the server move (empty cell) or reject
            // (occupied cell) with the matching sound. Either way the mode ends.
            if (pendingRelocateTarget != null && leftOrRight) {
                PacketDistributor.sendToServer(new MoveComponentPacket(
                        menu.controllerPos, pendingRelocateTarget, clicked));
                if (widget == null && pendingRelocateTarget.equals(selectedComponent))
                    selectedComponent = clicked;
                clearActionMode();
                return true;
            }

            // Left-click with a carried gauge → attach it at the cell (server rejects if occupied).
            if (button == 0 && ComponentRegistry.containsItem(carried)) {
                PacketDistributor.sendToServer(new AttachComponentPacket(
                        menu.controllerPos, clicked, networkSelector.getSelectedNetwork()));
                return true;
            }

            // Click an existing gauge → its own interaction (set item / configure).
            if (leftOrRight && widget != null)
                return widget.onClick(this, carried);

            if (button == 2) {
                isDragging = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDragging && button == 2) {
            viewX -= deltaX / getZoomFactor();
            viewY -= deltaY / getZoomFactor();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 2) isDragging = false;
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

            double oldZoom = getZoomFactor();
            zoomLevel = Math.clamp(zoomLevel + (int) Math.signum(scrollY), MIN_ZOOM_LEVEL, MAX_ZOOM_LEVEL);
            viewX += (mouseX - centerX) * (1.0 / oldZoom - 1.0 / getZoomFactor());
            viewY += (mouseY - centerY) * (1.0 / oldZoom - 1.0 / getZoomFactor());
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private double getZoomFactor() {
        return 2. * Math.pow(2., zoomLevel / 10.);
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
    public void onPanelSync() {
        rebuildGaugeWidgets();   // components were replaced with fresh instances; re-index them
        networkSelector.onNetworksUpdated();
        if (selectedComponent != null && findGauge(selectedComponent) == null)
            selectedComponent = null;
    }

    // GUI rectangle, exposed so SetItemScreen can match it (keeps JEI's layout consistent).
    int guiWidth()  { return imageWidth; }
    int guiHeight() { return imageHeight; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // R on a hovered gauge cycles its outgoing connection bend mode (mirrors Create's wrench).
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R
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
        return w == null ? null : w.getBehaviour();
    }
}
