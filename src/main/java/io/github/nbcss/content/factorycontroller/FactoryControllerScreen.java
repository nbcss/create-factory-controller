package io.github.nbcss.content.factorycontroller;

import com.mojang.blaze3d.platform.Window;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import io.github.nbcss.content.factorycontroller.packet.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class FactoryControllerScreen extends AbstractSimiContainerScreen<FactoryControllerMenu> {

    // Responsive sizing — all in GUI-scaled pixels.
    private static final int SIDE_MARGIN = 160;      // blank space at the left AND right of the image
    private static final int VERTICAL_MARGIN = 15;   // blank space at the top AND bottom of the image
    private static final int MIN_IMAGE_W = 176 + 18; // image never narrower than this
    private static final int MIN_IMAGE_H = 200;      // image never shorter than this

    // Interaction state
    @Nullable private VirtualPanelPosition hoveredComponent = null;
    @Nullable private VirtualPanelPosition selectedComponent = null; // configure overlay target
    @Nullable private VirtualPanelPosition connectFromCell = null; // connection draw mode

    // GUI-only client-side network selection (not synced; sent with AttachComponentPacket)
    @Nullable private UUID selectedNetwork = null;

    // Inventory panel state — all in menu-relative coords (relative to leftPos/topPos).
    // Computed in init() from scaledH so the hotbar bottom is always 50px from screen bottom.
    private static final int INV_BOTTOM_MARGIN = 50;  // px from screen bottom to hotbar bottom edge
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
        super.init();   // sets leftPos / topPos

        // ── Inventory slot layout ──────────────────────────────────────────
        // Hotbar bottom = scaledH - INV_BOTTOM_MARGIN (screen coords).
        // Convert to menu-relative (relative to topPos) for slot positioning.
        invHotbarY  = scaledH - INV_BOTTOM_MARGIN - HOTBAR_H - topPos;
        invOriginX  = (imageWidth - SLOT_ROW_W) / 2;
        menu.repositionSlots(invOriginX, invHotbarY, inventoryExpanded);

        // ── Expand/collapse button ─────────────────────────────────────────
        expandButton = Button.builder(Component.literal("+"), btn -> toggleInventory())
                .pos(expandButtonX(), expandButtonY())
                .size(EXPAND_BTN_SIZE, EXPAND_BTN_SIZE)
                .build();
        addRenderableWidget(expandButton);

        // Default the GUI network selection to the first known network.
        if (selectedNetwork == null && !menu.knownNetworks.isEmpty())
            selectedNetwork = menu.knownNetworks.iterator().next();
    }

    /** Screen-coord x of the expand button (right of the slot grid). */
    private int expandButtonX() {
        return leftPos + invOriginX + SLOT_ROW_W + 2;
    }

    /** Screen-coord y of the expand button (top of the currently visible inventory area). */
    private int expandButtonY() {
        int topOfInv = invHotbarY - (inventoryExpanded ? INV_GAP + MAIN_INV_H : 0);
        return topPos + topOfInv - EXPAND_BTN_SIZE;
    }

    private void toggleInventory() {
        inventoryExpanded = !inventoryExpanded;
        menu.repositionSlots(invOriginX, invHotbarY, inventoryExpanded);
        if (expandButton != null) {
            expandButton.setPosition(expandButtonX(), expandButtonY());
            expandButton.setMessage(Component.literal(inventoryExpanded ? "-" : "+"));
        }
    }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos, y0 = topPos;
        int x1 = leftPos + imageWidth, y1 = topPos + imageHeight;
        // enable scissor

        // render background

        // render connection & gauge

        // disable scissor

        // render boundary

        graphics.blitSprite(SPRITE_FRAME, x0, y0, imageWidth, imageHeight);

        // Inventory backgrounds (texture placeholder — replaced by proper texture later)
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

    /**
     * Draws placeholder backgrounds for the inventory area.
     * Hotbar is always drawn; main inventory only when expanded.
     * Replaced by proper textures once artwork is ready.
     */
    private void renderInventoryBackground(GuiGraphics gfx) {
        int slotX = leftPos + invOriginX;
        int hotbarTop = topPos + invHotbarY;

        // Hotbar background (always visible)
        gfx.fill(slotX - 1, hotbarTop - 1, slotX + SLOT_ROW_W + 1, hotbarTop + HOTBAR_H + 1, 0xFF444444);
        gfx.renderOutline(slotX - 1, hotbarTop - 1, SLOT_ROW_W + 2, HOTBAR_H + 2, 0xFF888888);

        if (inventoryExpanded) {
            // Main inventory background (3 rows, with gap above hotbar)
            int mainTop = hotbarTop - INV_GAP - MAIN_INV_H;
            gfx.fill(slotX - 1, mainTop - 1, slotX + SLOT_ROW_W + 1, mainTop + MAIN_INV_H + 1, 0xFF444444);
            gfx.renderOutline(slotX - 1, mainTop - 1, SLOT_ROW_W + 2, MAIN_INV_H + 2, 0xFF888888);
        }
    }

    // ── Mouse interaction ──────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        //TODO
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void cycleNetwork(int dir) {
        if (menu.knownNetworks.isEmpty()) return;
        List<UUID> list = new ArrayList<>(menu.knownNetworks);
        int idx = selectedNetwork == null ? 0 : list.indexOf(selectedNetwork);
        idx = (idx + dir + list.size()) % list.size();
        selectedNetwork = list.get(idx);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        //Network Selector
        //TODO

        //Board
        //TODO

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** Called by SyncPanelStatePacket after menu.gauges/knownNetworks are refreshed. */
    public void onPanelSync() {
        if (selectedNetwork != null && !menu.knownNetworks.contains(selectedNetwork))
            selectedNetwork = null;
        if (selectedNetwork == null && !menu.knownNetworks.isEmpty())
            selectedNetwork = menu.knownNetworks.iterator().next();
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
