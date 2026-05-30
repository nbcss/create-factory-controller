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

    // Configure overlay widgets
    @Nullable private EditBox amountBox = null;
    @Nullable private ItemStack ghostFilter = ItemStack.EMPTY;

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

        // ── Configure overlay amount input ─────────────────────────────────
        amountBox = new EditBox(font, leftPos + 30 + 72, topPos + 40 + 17, 60, 14, Component.literal("Amount"));
        amountBox.setMaxLength(9);
        amountBox.setFilter(s -> s.matches("\\d*"));
        amountBox.setValue("1");
        amountBox.setVisible(false);
        // addWidget instead of addRenderableWidget: we render it manually in renderForeground
        // so it appears on top of the configure overlay background rather than behind it.
        addWidget(amountBox);

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

    private void closeConfigureOverlay() {
        selectedComponent = null;
        if (amountBox != null) {
            amountBox.setVisible(false);
            amountBox.setFocused(false);
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

    private void renderConfigureOverlay(GuiGraphics gfx, int baseX, int baseY, int mouseX, int mouseY) {
        int ox = baseX + 30;
        int oy = baseY + 40;
        int ow = 160, oh = 100;

        gfx.fill(ox, oy, ox + ow, oy + oh, 0xFF3A3A3A);
        gfx.renderOutline(ox, oy, ow, oh, 0xFF888888);

        VirtualPanelBehaviour gauge = findGauge(selectedComponent);
        if (gauge == null) { closeConfigureOverlay(); return; }

        gfx.drawString(font, "Configure Panel", ox + 4, oy + 4, 0xFFFFFFFF, false);

        // Filter slot
        gfx.fill(ox + 4, oy + 18, ox + 22, oy + 36, 0xFF222222);
        gfx.renderOutline(ox + 4, oy + 18, 18, 18, 0xFF666666);
        ItemStack displayFilter = (ghostFilter != null && !ghostFilter.isEmpty()) ? ghostFilter : gauge.filter;
        if (!displayFilter.isEmpty())
            gfx.renderFakeItem(displayFilter, ox + 5, oy + 19);

        // Amount label
        gfx.drawString(font, "Amount:", ox + 28, oy + 22, 0xFFAAAAAA, false);

        // Network (read-only)
        String netLabel = "Net: " + gauge.networkId.toString().substring(0, 8) + "...";
        gfx.drawString(font, netLabel, ox + 4, oy + 40, 0xFF7777FF, false);
    }

    // ── Mouse interaction ──────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        //TODO
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleBoardCellClick(VirtualPanelPosition pos) {
        if (connectFromCell != null) {
            if (!connectFromCell.equals(pos) && findGauge(pos) != null) {
                VirtualPanelBehaviour source = findGauge(connectFromCell);
                PacketDistributor.sendToServer(new AddConnectionPacket(menu.controllerPos, connectFromCell, pos));
            }
            connectFromCell = null;
            return;
        }

        VirtualPanelBehaviour gauge = findGauge(pos);
        if (gauge == null) {
            // Empty cell — place if carrying valid gauge
            ItemStack carried = minecraft.player.containerMenu.getCarried();

            if (GaugeHelper.isValidGauge(carried)) {
                PacketDistributor.sendToServer(new AttachComponentPacket(menu.controllerPos, pos.col(), pos.row(), selectedNetwork));
            }
        } else {
            // Open configure overlay
            selectedComponent = pos;
            ghostFilter = gauge.filter.copy();
            if (amountBox != null) {
                amountBox.setValue(String.valueOf(gauge.amount));
                amountBox.setVisible(true);
                setFocused(amountBox);
                amountBox.setFocused(true);
            }
        }
    }

    private void cycleNetwork(int dir) {
        if (menu.knownNetworks.isEmpty()) return;
        List<UUID> list = new ArrayList<>(menu.knownNetworks);
        int idx = selectedNetwork == null ? 0 : list.indexOf(selectedNetwork);
        idx = (idx + dir + list.size()) % list.size();
        selectedNetwork = list.get(idx);
    }

    private boolean handleConfigureOverlayClick(int mouseX, int mouseY, int button) {
        int ox = leftPos + 30;
        int oy = topPos + 40;
        int ow = 160, oh = 100;

        // Amount input box (takes precedence so clicks focus it)
        if (amountBox != null && amountBox.isVisible() && amountBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(amountBox);
            return true;
        }

        // Click on filter slot
        if (mouseX >= ox + 4 && mouseX < ox + 22 && mouseY >= oy + 18 && mouseY < oy + 36) {
            ItemStack carried = minecraft.player.containerMenu.getCarried();
            ghostFilter = carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);
            return true;
        }

        // Confirm button area (bottom of overlay)
        if (mouseX >= ox + 4 && mouseX < ox + 54 && mouseY >= oy + oh - 16 && mouseY < oy + oh - 4) {
            // Confirm
            VirtualPanelBehaviour gauge = findGauge(selectedComponent);
            if (gauge != null) {
                int amt = parseAmount();
                PacketDistributor.sendToServer(new ConfigureGaugePacket(
                    menu.controllerPos, selectedComponent.col(), selectedComponent.row(),
                    ghostFilter == null ? ItemStack.EMPTY : ghostFilter, amt));
            }
            closeConfigureOverlay();
            return true;
        }

        // Remove button
        if (mouseX >= ox + ow - 54 && mouseX < ox + ow - 4 && mouseY >= oy + oh - 16 && mouseY < oy + oh - 4) {
            PacketDistributor.sendToServer(new RemoveComponentPacket(
                menu.controllerPos, selectedComponent.col(), selectedComponent.row()));
            closeConfigureOverlay();
            return true;
        }

        // Connect button
        if (mouseX >= ox + 60 && mouseX < ox + 110 && mouseY >= oy + oh - 16 && mouseY < oy + oh - 4) {
            VirtualPanelPosition from = selectedComponent;
            closeConfigureOverlay();
            connectFromCell = from;
            return true;
        }

        // Close overlay by clicking outside
        if (mouseX < ox || mouseX > ox + ow || mouseY < oy || mouseY > oy + oh) {
            closeConfigureOverlay();
            return false;
        }

        return true;
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
        // While typing in the amount box, let it (and the widget system) consume input first
        // so e.g. the "R" arrow-bend shortcut doesn't fire while editing.
        if (amountBox != null && amountBox.isVisible() && amountBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeConfigureOverlay();
                return true;
            }
            if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (amountBox.canConsumeInput()) return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R && hoveredComponent != null && findGauge(hoveredComponent) != null) {
            PacketDistributor.sendToServer(new CycleArrowBendPacket(menu.controllerPos, hoveredComponent));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && selectedComponent != null) {
            closeConfigureOverlay();
            return true;
        }
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

    private String getNetworkBadge(UUID networkId) {
        int i = 0;
        for (UUID id : menu.knownNetworks) {
            if (id.equals(networkId)) return String.valueOf((char) ('A' + i));
            i++;
        }
        return "?";
    }

    private int parseAmount() {
        if (amountBox == null) return 1;
        try { return Math.max(1, Integer.parseInt(amountBox.getValue())); }
        catch (NumberFormatException e) { return 1; }
    }
}
