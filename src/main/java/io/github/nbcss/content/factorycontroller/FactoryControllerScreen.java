package io.github.nbcss.content.factorycontroller;

import com.mojang.blaze3d.platform.Window;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FactoryControllerScreen extends AbstractSimiContainerScreen<FactoryControllerMenu> {

    // Responsive sizing — all in GUI-scaled pixels.
    private static final int SIDE_MARGIN = 160;      // blank space at the left AND right of the image
    private static final int VERTICAL_MARGIN = 15;   // blank space at the top AND bottom of the image
    private static final int MIN_IMAGE_W = 195;      // matches vanilla creative inventory width
    private static final int MIN_IMAGE_H = 200;      // image never shorter than this
    private static final int CANVAS_SIDE_PADDING = 6;
    private static final int CANVAS_TOP_PADDING = 18;
    private static final int CANVAS_BOTTOM_PADDING = 6;
    private static final int CANVAS_COMPONENT_SIZE = 16;

    // Canvas view state
    private static final double MAX_ZOOM_FACTOR = 3.0;
    private static final double MIN_ZOOM_FACTOR = 0.5;
    private double viewX = 0;
    private double viewY = 0;
    private double zoomFactor = 1.0;

    // Inventory panel state — all in menu-relative coords (relative to leftPos/topPos).
    // Computed in init() from scaledH so the hotbar bottom is always 50px from screen bottom.
    private static final int INV_BOTTOM_MARGIN = 30;  // px from screen bottom to hotbar bottom edge
    private static final int HOTBAR_H = 18;           // one slot row
    private static final int MAIN_INV_H = 54;         // 3 rows × 18px
    private static final int INV_GAP = 4;             // gap between main inv and hotbar
    private static final int SLOT_ROW_W = 162;        // 9 slots × 18px
    private static final int EXPAND_BTN_SIZE = 12;    // square expand/collapse button
    private int invOriginX;   // left edge of the slot grid, menu-relative
    private int invHotbarY;   // top edge of the hotbar row, menu-relative
    private boolean inventoryExpanded = false;
    @Nullable private Button expandButton = null;

    private static final ResourceLocation SPRITE_FRAME = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/frame");

    // player_inventory.png layout (176×108, matching Create's convention)
    private static final ResourceLocation PLAYER_INVENTORY_TEX = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "textures/gui/player_inventory.png");
    private static final int INV_TEX_W         = 176;
    private static final int INV_TEX_H         = 108;
    private static final int INV_TEX_SLOT_LEFT = 8;   // slot grid starts at x=9 in the texture
    private static final int INV_TEX_TITLE_H   = 18;  // header region above the main-inv rows
    private static final int INV_TEX_HOTBAR_Y  = 76;  // hotbar row top in texture (19 + 54 + 4)

    // Interaction state
    @Nullable private VirtualPanelPosition hoveredPosition = null;
    @Nullable private VirtualPanelPosition selectedComponent = null; // configure overlay target

    // Pan drag state (middle mouse)
    private boolean isDragging = false;

    // Network selector widget
    private NetworkSelectorWidget networkSelector;

    public FactoryControllerScreen(FactoryControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        // Work entirely in GUI-scaled pixels — the same units gfx.fill / leftPos / mouse use.
        // getGuiScaledWidth/Height already fold in the GUI Scale option, so this adapts to any
        // scale automatically (higher scale => fewer scaled px => image hits its minimum sooner).
        Window window = Minecraft.getInstance().getWindow();
        int scaledW = window.getGuiScaledWidth();
        int scaledH = window.getGuiScaledHeight();

        // Image = screen minus the fixed side margins, but never below the minimum.
        int imageW = Math.max(MIN_IMAGE_W, scaledW - 2 * SIDE_MARGIN);
        int imageH = Math.max(MIN_IMAGE_H, scaledH - 2 * VERTICAL_MARGIN);

        setWindowSize(imageW, imageH);
        setWindowOffset(0, 0);

        super.init();

        // ── Inventory slot layout ──────────────────────────────────────────
        // Hotbar bottom = scaledH - INV_BOTTOM_MARGIN (screen coords).
        // Convert to menu-relative (relative to topPos) for slot positioning.
        invHotbarY  = scaledH - INV_BOTTOM_MARGIN - HOTBAR_H - topPos;
        invOriginX  = (imageWidth - SLOT_ROW_W) / 2 + 2;
        menu.repositionSlots(invOriginX, invHotbarY, inventoryExpanded);

        // ── Expand/collapse button ─────────────────────────────────────────
        expandButton = Button.builder(Component.literal("+"), btn -> toggleInventory())
                .pos(expandButtonX(), expandButtonY())
                .size(EXPAND_BTN_SIZE, EXPAND_BTN_SIZE)
                .build();
        addRenderableWidget(expandButton);

        // Network selector — right edge inside canvas area, top-aligned.
        int selectorX = leftPos + CANVAS_SIDE_PADDING + 4;
        int selectorY = topPos + CANVAS_TOP_PADDING + 4;
        if (networkSelector == null)
            networkSelector = new NetworkSelectorWidget(selectorX, selectorY, menu.knownNetworks);
        else
            networkSelector.setPosition(selectorX, selectorY);
    }

    /** Screen-coord x of the expand button (right of the slot grid). */
    private int expandButtonX() {
        return leftPos + invOriginX + SLOT_ROW_W - 13;
    }

    /** Screen-coord y of the expand button (top of the currently visible inventory area). */
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
        addRenderableWidget(expandButton);
    }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // FIXME remove once have proper background boundary texture rendering
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF333333);
        int x0 = leftPos + CANVAS_SIDE_PADDING;
        int y0 = topPos + CANVAS_TOP_PADDING;
        int x1 = leftPos + imageWidth - CANVAS_SIDE_PADDING;
        int y1 = topPos + imageHeight - CANVAS_BOTTOM_PADDING;
        // Start canvas rendering
        graphics.enableScissor(x0, y0, x1, y1);

        // Canvas center in screen coords — viewX/viewY maps to this point.
        int centerX = (x0 + x1) / 2;
        int centerY = (y0 + y1) / 2;

        // Visible canvas-world pixel bounds (screen edge → world via inverse transform).
        int minX = (int) Math.floor(viewX + (x0 - centerX) / zoomFactor);
        int minY = (int) Math.floor(viewY + (y0 - centerY) / zoomFactor);
        int maxX = (int) Math.ceil(viewX + (x1 - centerX) / zoomFactor);
        int maxY = (int) Math.ceil(viewY + (y1 - centerY) / zoomFactor);

        // Hovered position
        if (isInCanvasArea(mouseX, mouseY)) {
            int hoverX = (int) Math.floor((viewX + (mouseX - centerX) / zoomFactor) / CANVAS_COMPONENT_SIZE);
            int hoverY = (int) Math.floor((viewY + (mouseY - centerY) / zoomFactor) / CANVAS_COMPONENT_SIZE);
            hoveredPosition = new VirtualPanelPosition(hoverX, hoverY);
        } else {
            hoveredPosition = null;
        }

        // render background
        graphics.fill(x0, y0, x1, y1, 0xFF999999); // FIXME replace stub

        // render connection & gauge
        List<VirtualPanelBehaviour> components = getMenu().getComponentsInCanvas(
                Math.floorDiv(minX, CANVAS_COMPONENT_SIZE),
                Math.floorDiv(minY, CANVAS_COMPONENT_SIZE),
                Math.floorDiv(maxX, CANVAS_COMPONENT_SIZE),
                Math.floorDiv(maxY, CANVAS_COMPONENT_SIZE)
        );

        // render hovered component
        if (hoveredPosition != null) {
            int hx0 = (int) (centerX + (hoveredPosition.x() * CANVAS_COMPONENT_SIZE - viewX) * zoomFactor);
            int hy0 = (int) (centerY + (hoveredPosition.y() * CANVAS_COMPONENT_SIZE - viewY) * zoomFactor);
            int hx1 = (int) (centerX + ((hoveredPosition.x() + 1) * CANVAS_COMPONENT_SIZE - viewX) * zoomFactor);
            int hy1 = (int) (centerY + ((hoveredPosition.y() + 1) * CANVAS_COMPONENT_SIZE - viewY) * zoomFactor);
            graphics.fill(hx0, hy0, hx1, hy1, 0xFF66CCFF);
        }

        graphics.disableScissor();

        // render boundary
        // TODO

        networkSelector.render(graphics, mouseX, mouseY, partialTick);

        //graphics.blitSprite(SPRITE_FRAME, x0, y0, imageWidth, imageHeight);

        // Inventory backgrounds
        renderInventoryBackground(graphics);
        // Configure overlay rendered in renderForeground so it layers above the board.
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int titleX = leftPos + (imageWidth - font.width(title)) / 2;
        int titleY = topPos + 6;
        graphics.drawString(font, title, titleX, titleY, 0xC8C8C8, false);

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        renderTooltip(gfx, mouseX, mouseY);
    }

    private void renderInventoryBackground(GuiGraphics gfx) {
        // Align the texture so its slot grid matches the actual slot positions.
        // Slot grid starts at x = INV_TEX_SLOT_LEFT in the texture and hotbar at y = INV_TEX_HOTBAR_Y.
        int texX      = leftPos + invOriginX - INV_TEX_SLOT_LEFT;
        int hotbarY   = topPos + invHotbarY;

        if (inventoryExpanded) {
            // Full texture: position so the hotbar row in the texture lands on invHotbarY.
            int texY = hotbarY - INV_TEX_HOTBAR_Y;
            gfx.blit(PLAYER_INVENTORY_TEX, texX, texY, 0, 0, INV_TEX_W, INV_TEX_H);
            gfx.drawString(font, playerInventoryTitle, texX + 8, texY + 6, 0x404040, false);
        } else {
            // Collapsed: render the header strip (title area) just above the hotbar,
            // then the hotbar + bottom-border strip at the hotbar position.
            int headerY      = hotbarY - INV_TEX_TITLE_H;
            int hotbarStripH = INV_TEX_H - INV_TEX_HOTBAR_Y;
            gfx.blit(PLAYER_INVENTORY_TEX, texX, headerY, 0, 0, INV_TEX_W, INV_TEX_TITLE_H);
            gfx.blit(PLAYER_INVENTORY_TEX, texX, hotbarY, 0, INV_TEX_HOTBAR_Y, INV_TEX_W, hotbarStripH);
            gfx.drawString(font, playerInventoryTitle, texX + 8, headerY + 6, 0x404040, false);
        }
    }

    // ── Mouse interaction ──────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 2 && isInCanvasArea(mouseX, mouseY)) {
            isDragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDragging && button == 2) {
            viewX -= deltaX / zoomFactor;
            viewY -= deltaY / zoomFactor;
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
        // Network Selector
        if (networkSelector.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;

        // Canvas
        if (isInCanvasArea(mouseX, mouseY)) {
            int x0 = leftPos + CANVAS_SIDE_PADDING;
            int y0 = topPos + CANVAS_TOP_PADDING;
            int x1 = leftPos + imageWidth - CANVAS_SIDE_PADDING;
            int y1 = topPos + imageHeight - CANVAS_BOTTOM_PADDING;
            int centerX = (x0 + x1) / 2;
            int centerY = (y0 + y1) / 2;

            double oldZoom = zoomFactor;
            zoomFactor = Math.clamp(zoomFactor * (scrollY > 0 ? 1.1 : (1.0 / 1.1)), MIN_ZOOM_FACTOR, MAX_ZOOM_FACTOR);

            // Zoom toward the cursor: keep the canvas-world point under the mouse fixed.
            viewX += (mouseX - centerX) * (1.0 / oldZoom - 1.0 / zoomFactor);
            viewY += (mouseY - centerY) * (1.0 / oldZoom - 1.0 / zoomFactor);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean isInCanvasArea(double x, double y) {
        int x0 = leftPos + CANVAS_SIDE_PADDING;
        int y0 = topPos + CANVAS_TOP_PADDING;
        int x1 = leftPos + imageWidth - CANVAS_SIDE_PADDING;
        int y1 = topPos + imageHeight - CANVAS_BOTTOM_PADDING;
        if (x < x0 || x >= x1 || y < y0 || y >= y1) return false;

        // Exclude the inventory panel — its extent depends on whether it is expanded.
        int invLeft = leftPos + invOriginX - INV_TEX_SLOT_LEFT;
        int invTop  = topPos  + invHotbarY - (inventoryExpanded ? INV_TEX_HOTBAR_Y : INV_TEX_TITLE_H);
        int invBot  = topPos  + invHotbarY + INV_TEX_H - INV_TEX_HOTBAR_Y - 7;
        if (x >= invLeft && x < invLeft + INV_TEX_W && y >= invTop && y < invBot) return false;

        // Exclude the network selector widget.
        if (networkSelector.isMouseOver(x, y)) return false;

        return true;
    }

    /** Called by SyncPanelStatePacket after menu.gauges/knownNetworks are refreshed. */
    public void onPanelSync() {
        networkSelector.onNetworksUpdated();
    }

    // ── Keyboard ───────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        //
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    @Nullable
    private VirtualPanelBehaviour findGauge(VirtualPanelPosition pos) {
        if (pos == null) return null;
        for (VirtualPanelBehaviour b : menu.gauges)
            if (b.position.equals(pos)) return b;
        return null;
    }
}
