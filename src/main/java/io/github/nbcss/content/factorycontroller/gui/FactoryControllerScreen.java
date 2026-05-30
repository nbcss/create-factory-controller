package io.github.nbcss.content.factorycontroller.gui;

import com.mojang.blaze3d.platform.Window;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import io.github.nbcss.content.factorycontroller.FactoryControllerMenu;
import io.github.nbcss.content.factorycontroller.VirtualPanelBehaviour;
import io.github.nbcss.content.factorycontroller.VirtualPanelPosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FactoryControllerScreen extends AbstractSimiContainerScreen<FactoryControllerMenu> {

    // Responsive sizing — all in GUI-scaled pixels.
    private static final int SIDE_MARGIN = 160;
    private static final int VERTICAL_MARGIN = 15;
    private static final int MIN_IMAGE_W = 195;      // matches vanilla creative inventory width
    private static final int MIN_IMAGE_H = 200;
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
    private static final int INV_BOTTOM_MARGIN = 30;
    private static final int HOTBAR_H = 18;
    private static final int MAIN_INV_H = 54;
    private static final int INV_GAP = 4;
    private static final int SLOT_ROW_W = 162;
    private static final int EXPAND_BTN_SIZE = 12;
    private int invOriginX;
    private int invHotbarY;
    private boolean inventoryExpanded = false;
    @Nullable private Button expandButton = null;

    private static final ResourceLocation SPRITE_FRAME = ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/frame");

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

        invHotbarY = scaledH - INV_BOTTOM_MARGIN - HOTBAR_H - topPos;
        invOriginX = (imageWidth - SLOT_ROW_W) / 2 + 2;
        menu.repositionSlots(invOriginX, invHotbarY, inventoryExpanded);

        if (expandButton != null) removeWidget(expandButton);
        expandButton = Button.builder(Component.literal(inventoryExpanded ? "-" : "+"), btn -> toggleInventory())
                .pos(expandButtonX(), expandButtonY())
                .size(EXPAND_BTN_SIZE, EXPAND_BTN_SIZE)
                .build();
        addRenderableWidget(expandButton);

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
        addRenderableWidget(expandButton);
    }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF333333);

        int x0 = leftPos + CANVAS_SIDE_PADDING;
        int y0 = topPos + CANVAS_TOP_PADDING;
        int x1 = leftPos + imageWidth - CANVAS_SIDE_PADDING;
        int y1 = topPos + imageHeight - CANVAS_BOTTOM_PADDING;
        int centerX = (x0 + x1) / 2;
        int centerY = (y0 + y1) / 2;

        graphics.enableScissor(x0, y0, x1, y1);

        // Visible canvas-world pixel bounds
        int minX = (int) Math.floor(viewX + (x0 - centerX) / zoomFactor);
        int minY = (int) Math.floor(viewY + (y0 - centerY) / zoomFactor);
        int maxX = (int) Math.ceil(viewX + (x1 - centerX) / zoomFactor);
        int maxY = (int) Math.ceil(viewY + (y1 - centerY) / zoomFactor);

        // Hovered cell
        if (isInCanvasArea(mouseX, mouseY)) {
            int hoverX = (int) Math.floor((viewX + (mouseX - centerX) / zoomFactor) / CANVAS_COMPONENT_SIZE);
            int hoverY = (int) Math.floor((viewY + (mouseY - centerY) / zoomFactor) / CANVAS_COMPONENT_SIZE);
            hoveredPosition = new VirtualPanelPosition(hoverX, hoverY);
        } else {
            hoveredPosition = null;
        }

        // Canvas background
        graphics.fill(x0, y0, x1, y1, 0xFF999999);

        // Visible gauges
        List<VirtualPanelBehaviour> components = menu.getComponentsInCanvas(
                Math.floorDiv(minX, CANVAS_COMPONENT_SIZE),
                Math.floorDiv(minY, CANVAS_COMPONENT_SIZE),
                Math.floorDiv(maxX, CANVAS_COMPONENT_SIZE),
                Math.floorDiv(maxY, CANVAS_COMPONENT_SIZE)
        );
        for (VirtualPanelBehaviour b : components) {
            VirtualGaugeWidget gauge = new VirtualGaugeWidget(b);
            boolean hovered  = b.position.equals(hoveredPosition) && findGauge(hoveredPosition) != null;
            boolean selected = b.position.equals(selectedComponent);
            gauge.renderOnCanvas(graphics, centerX, centerY, viewX, viewY, zoomFactor,
                                 hovered, selected, mouseX, mouseY, partialTick);
        }

        // Hovered empty cell highlight
        if (hoveredPosition != null && findGauge(hoveredPosition) == null) {
            int hx0 = (int)(centerX + (hoveredPosition.x() * CANVAS_COMPONENT_SIZE - viewX) * zoomFactor);
            int hy0 = (int)(centerY + (hoveredPosition.y() * CANVAS_COMPONENT_SIZE - viewY) * zoomFactor);
            int hx1 = (int)(centerX + ((hoveredPosition.x() + 1) * CANVAS_COMPONENT_SIZE - viewX) * zoomFactor);
            int hy1 = (int)(centerY + ((hoveredPosition.y() + 1) * CANVAS_COMPONENT_SIZE - viewY) * zoomFactor);
            graphics.fill(hx0, hy0, hx1, hy1, 0x6666CCFF);
        }

        graphics.disableScissor();

        networkSelector.render(graphics, mouseX, mouseY, partialTick);

        renderInventoryBackground(graphics);
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

    // ── Mouse interaction ──────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInCanvasArea(mouseX, mouseY)) {
            int x0 = leftPos + CANVAS_SIDE_PADDING;
            int y0 = topPos + CANVAS_TOP_PADDING;
            int x1 = leftPos + imageWidth - CANVAS_SIDE_PADDING;
            int y1 = topPos + imageHeight - CANVAS_BOTTOM_PADDING;
            int centerX = (x0 + x1) / 2;
            int centerY = (y0 + y1) / 2;
            for (VirtualPanelBehaviour b : menu.gauges) {
                VirtualGaugeWidget gauge = new VirtualGaugeWidget(b);
                gauge.applyTransform(centerX, centerY, viewX, viewY, zoomFactor);
                if (gauge.isMouseOver(mouseX, mouseY)) {
                    selectedComponent = b.position;
                    return true;
                }
            }
            selectedComponent = null;
            return true;
        }
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
        if (networkSelector.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;

        if (isInCanvasArea(mouseX, mouseY)) {
            int x0 = leftPos + CANVAS_SIDE_PADDING;
            int y0 = topPos + CANVAS_TOP_PADDING;
            int x1 = leftPos + imageWidth - CANVAS_SIDE_PADDING;
            int y1 = topPos + imageHeight - CANVAS_BOTTOM_PADDING;
            int centerX = (x0 + x1) / 2;
            int centerY = (y0 + y1) / 2;

            double oldZoom = zoomFactor;
            zoomFactor = Math.clamp(zoomFactor * (scrollY > 0 ? 1.1 : (1.0 / 1.1)), MIN_ZOOM_FACTOR, MAX_ZOOM_FACTOR);
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

        int invLeft = leftPos + invOriginX - INV_TEX_SLOT_LEFT;
        int invTop  = topPos  + invHotbarY - (inventoryExpanded ? INV_TEX_HOTBAR_Y : INV_TEX_TITLE_H);
        int invBot  = topPos  + invHotbarY + INV_TEX_H - INV_TEX_HOTBAR_Y - 7;
        if (x >= invLeft && x < invLeft + INV_TEX_W && y >= invTop && y < invBot) return false;

        if (networkSelector.isMouseOver(x, y)) return false;

        return true;
    }

    /** Called by SyncPanelStatePacket after menu.gauges/knownNetworks are refreshed. */
    public void onPanelSync() {
        networkSelector.onNetworksUpdated();
        if (selectedComponent != null && findGauge(selectedComponent) == null)
            selectedComponent = null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Nullable
    private VirtualPanelBehaviour findGauge(VirtualPanelPosition pos) {
        if (pos == null) return null;
        for (VirtualPanelBehaviour b : menu.gauges)
            if (b.position.equals(pos)) return b;
        return null;
    }
}
