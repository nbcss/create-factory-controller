package me.nbcss.github.content.factorycontroller;

import com.mojang.blaze3d.systems.RenderSystem;
import me.nbcss.github.content.factorycontroller.packet.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class FactoryControllerScreen extends AbstractContainerScreen<FactoryControllerMenu> {

    // Layout constants
    private static final int BOARD_X = 7;
    private static final int BOARD_Y = 28;
    private static final int CELL_W = 54;
    private static final int CELL_H = 54;
    private static final int COLS_VISIBLE = 5;
    private static final int ROWS_VISIBLE = 4;
    private static final int BOARD_W = COLS_VISIBLE * CELL_W;
    private static final int BOARD_H = ROWS_VISIBLE * CELL_H;
    private static final int GUI_W = BOARD_X * 2 + BOARD_W + 18; // +18 for scrollbar
    private static final int GUI_H = BOARD_Y + BOARD_H + 6 + 90; // board + gap + inventory

    // Scroll state
    private int scrollCol = 0;
    private int scrollRow = 0;

    // Interaction state
    @Nullable private VirtualPanelPosition hoveredCell = null;
    @Nullable private VirtualPanelPosition selectedCell = null; // configure overlay target
    @Nullable private VirtualPanelPosition connectFromCell = null; // connection draw mode

    // Configure overlay widgets
    @Nullable private EditBox amountBox = null;
    @Nullable private ItemStack ghostFilter = ItemStack.EMPTY;

    public FactoryControllerScreen(FactoryControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
    }

    @Override
    protected void init() {
        super.init();
        // Amount input for the configure overlay. Positioned next to the "Amount:" label
        // (overlay origin is leftPos+30, topPos+40). Hidden until a gauge is selected.
        amountBox = new EditBox(font, leftPos + 30 + 72, topPos + 40 + 17, 60, 14, Component.literal("Amount"));
        amountBox.setMaxLength(9);
        amountBox.setFilter(s -> s.matches("\\d*"));
        amountBox.setValue("1");
        amountBox.setVisible(false);
        addRenderableWidget(amountBox);
    }

    private void closeConfigureOverlay() {
        selectedCell = null;
        if (amountBox != null) {
            amountBox.setVisible(false);
            amountBox.setFocused(false);
        }
    }

    // ── Render ─────────────────────────────────────────────────────────────

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // Background panel
        gfx.fill(x, y, x + imageWidth, y + imageHeight - 90, 0xFF2D2D2D);
        gfx.fill(x, y + imageHeight - 90, x + imageWidth, y + imageHeight, 0xFF1E1E1E);
        gfx.renderOutline(x, y, imageWidth, imageHeight - 90, 0xFF555555);

        // Network selector
        renderNetworkSelector(gfx, x + BOARD_X, y + 6, mouseX, mouseY);

        // Board grid
        int boardX = x + BOARD_X;
        int boardY = y + BOARD_Y;
        for (int col = 0; col < COLS_VISIBLE; col++) {
            for (int row = 0; row < ROWS_VISIBLE; row++) {
                int cellX = boardX + col * CELL_W;
                int cellY = boardY + row * CELL_H;
                renderCell(gfx, cellX, cellY, col + scrollCol, row + scrollRow, mouseX, mouseY);
            }
        }

        // Connection arrows
        renderConnections(gfx, boardX, boardY);

        // Configure overlay
        if (selectedCell != null) renderConfigureOverlay(gfx, x, y, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        updateHoveredCell(mouseX, mouseY);
        super.render(gfx, mouseX, mouseY, partialTick);
        renderTooltip(gfx, mouseX, mouseY);
    }

    private void renderNetworkSelector(GuiGraphics gfx, int x, int y, int mouseX, int mouseY) {
        int w = BOARD_W;
        gfx.fill(x, y, x + w, y + 16, 0xFF3A3A3A);
        gfx.renderOutline(x, y, w, 16, 0xFF666666);

        String label;
        if (menu.selectedNetwork == null || menu.knownNetworks.isEmpty()) {
            label = "No network selected";
        } else {
            label = menu.selectedNetwork.toString().substring(0, 8) + "...";
        }

        gfx.drawString(font, "< " + label + " >", x + 4, y + 4, 0xFFAAAAAA, false);
    }

    private void renderCell(GuiGraphics gfx, int cellX, int cellY, int col, int row, int mouseX, int mouseY) {
        VirtualPanelPosition pos = new VirtualPanelPosition(col, row);
        VirtualPanelBehaviour gauge = findGauge(pos);

        boolean hovered = hoveredCell != null && hoveredCell.equals(pos);
        int border = hovered ? 0xFF888888 : 0xFF444444;
        gfx.fill(cellX, cellY, cellX + CELL_W, cellY + CELL_H, 0xFF1A1A1A);
        gfx.renderOutline(cellX, cellY, CELL_W, CELL_H, border);

        if (gauge == null) {
            // Empty cell — show hint if carrying a valid gauge
            ItemStack carried = minecraft.player.containerMenu.getCarried();
            if (hovered && GaugeHelper.isValidGauge(carried)) {
                gfx.drawString(font, "+", cellX + CELL_W / 2 - 3, cellY + CELL_H / 2 - 4, 0xFF55FF55, false);
            }
            return;
        }

        // Gauge item icon (top-left)
        ItemStack gaugeStack = new ItemStack(BuiltInRegistries.ITEM.get(gauge.gaugeItemId));
        gfx.renderFakeItem(gaugeStack, cellX + 2, cellY + 2);

        // Filter item icon (centered)
        if (!gauge.filter.isEmpty()) {
            gfx.renderFakeItem(gauge.filter, cellX + CELL_W / 2 - 8, cellY + CELL_H / 2 - 10);
            // Item name
            String name = gauge.filter.getHoverName().getString();
            if (name.length() > 7) name = name.substring(0, 6) + "..";
            gfx.drawString(font, name, cellX + 2, cellY + CELL_H - 22, 0xFFCCCCCC, false);
        }

        // Amount
        gfx.drawString(font, String.valueOf(gauge.amount), cellX + 2, cellY + CELL_H - 12, 0xFF888888, false);

        // Status symbol
        String status;
        int statusColor;
        if (gauge.satisfied) {
            status = "✔"; statusColor = 0xFF55FF55;
        } else if (gauge.promisedSatisfied) {
            status = "↑"; statusColor = 0xFFFFFF55;
        } else {
            status = "▪"; statusColor = 0xFF888888;
        }
        gfx.drawString(font, status, cellX + CELL_W - 10, cellY + 2, statusColor, false);

        // Network badge
        String networkBadge = getNetworkBadge(gauge.networkId);
        gfx.drawString(font, networkBadge, cellX + CELL_W - 10, cellY + CELL_H - 12, 0xFFAAAAAA, false);

        // Stock bar
        if (!gauge.filter.isEmpty() && gauge.amount > 0) {
            int barW = CELL_W - 4;
            int level = gauge.getLevelInStorage();
            int filled = (int) Math.min(barW, (long) barW * level / gauge.amount);
            gfx.fill(cellX + 2, cellY + CELL_H - 4, cellX + 2 + barW, cellY + CELL_H - 2, 0xFF333333);
            int barColor = gauge.satisfied ? 0xFF44BB44 : 0xFFBB4444;
            if (filled > 0)
                gfx.fill(cellX + 2, cellY + CELL_H - 4, cellX + 2 + filled, cellY + CELL_H - 2, barColor);
        }
    }

    private void renderConnections(GuiGraphics gfx, int boardX, int boardY) {
        for (VirtualPanelBehaviour gauge : menu.gauges) {
            for (VirtualPanelConnection conn : gauge.targetedBy.values()) {
                VirtualPanelPosition from = conn.from;
                VirtualPanelPosition to = gauge.position;

                int[] fromScreen = cellCenter(boardX, boardY, from);
                int[] toScreen = cellCenter(boardX, boardY, to);

                if (fromScreen == null || toScreen == null) continue;

                int color = conn.success ? 0xFF44BB44 : 0xFF886644;
                drawArrow(gfx, fromScreen[0], fromScreen[1], toScreen[0], toScreen[1], color);
            }
        }
    }

    private void drawArrow(GuiGraphics gfx, int x1, int y1, int x2, int y2, int color) {
        // Simple line via horizontal + vertical segments
        gfx.fill(Math.min(x1, x2), y1 - 1, Math.max(x1, x2) + 1, y1 + 1, color);
        gfx.fill(x2 - 1, Math.min(y1, y2), x2 + 1, Math.max(y1, y2) + 1, color);
    }

    private void renderConfigureOverlay(GuiGraphics gfx, int baseX, int baseY, int mouseX, int mouseY) {
        int ox = baseX + 30;
        int oy = baseY + 40;
        int ow = 160, oh = 100;

        gfx.fill(ox, oy, ox + ow, oy + oh, 0xFF3A3A3A);
        gfx.renderOutline(ox, oy, ow, oh, 0xFF888888);

        VirtualPanelBehaviour gauge = findGauge(selectedCell);
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
        // Network selector arrows
        if (handleNetworkSelectorClick((int) mouseX, (int) mouseY, button)) return true;

        // Configure overlay buttons
        if (selectedCell != null) {
            if (handleConfigureOverlayClick((int) mouseX, (int) mouseY, button)) return true;
        }

        // Board cell click
        VirtualPanelPosition clicked = cellAt((int) mouseX, (int) mouseY);
        if (clicked != null && button == 0) {
            handleBoardCellClick(clicked);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleBoardCellClick(VirtualPanelPosition pos) {
        // Connection draw mode
        if (connectFromCell != null) {
            if (!connectFromCell.equals(pos) && findGauge(pos) != null) {
                VirtualPanelBehaviour source = findGauge(connectFromCell);
                int connAmount = source != null ? source.amount : 1;
                PacketDistributor.sendToServer(new DrawConnectionPacket(menu.controllerPos, connectFromCell, pos, connAmount));
            }
            connectFromCell = null;
            return;
        }

        VirtualPanelBehaviour gauge = findGauge(pos);
        if (gauge == null) {
            // Empty cell — place if carrying valid gauge
            ItemStack carried = minecraft.player.containerMenu.getCarried();
            if (GaugeHelper.isValidGauge(carried)) {
                PacketDistributor.sendToServer(new AttachGaugePacket(menu.controllerPos, pos.col(), pos.row()));
            }
        } else {
            // Open configure overlay
            selectedCell = pos;
            ghostFilter = gauge.filter.copy();
            if (amountBox != null) {
                amountBox.setValue(String.valueOf(gauge.amount));
                amountBox.setVisible(true);
                setFocused(amountBox);
                amountBox.setFocused(true);
            }
        }
    }

    private boolean handleNetworkSelectorClick(int mouseX, int mouseY, int button) {
        if (menu.knownNetworks.isEmpty()) return false;
        int nx = leftPos + BOARD_X;
        int ny = topPos + 6;
        if (mouseX >= nx && mouseX < nx + BOARD_W && mouseY >= ny && mouseY < ny + 16) {
            if (button == 0) cycleNetwork(1);
            else if (button == 1) cycleNetwork(-1);
            return true;
        }
        return false;
    }

    private void cycleNetwork(int dir) {
        if (menu.knownNetworks.isEmpty()) return;
        List<UUID> list = new ArrayList<>(menu.knownNetworks);
        int idx = menu.selectedNetwork == null ? 0 : list.indexOf(menu.selectedNetwork);
        idx = (idx + dir + list.size()) % list.size();
        UUID next = list.get(idx);
        menu.selectedNetwork = next;
        PacketDistributor.sendToServer(new SelectNetworkPacket(menu.controllerPos, next));
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
            VirtualPanelBehaviour gauge = findGauge(selectedCell);
            if (gauge != null) {
                int amt = parseAmount();
                PacketDistributor.sendToServer(new ConfigureGaugePacket(
                    menu.controllerPos, selectedCell.col(), selectedCell.row(),
                    ghostFilter == null ? ItemStack.EMPTY : ghostFilter, amt));
            }
            closeConfigureOverlay();
            return true;
        }

        // Remove button
        if (mouseX >= ox + ow - 54 && mouseX < ox + ow - 4 && mouseY >= oy + oh - 16 && mouseY < oy + oh - 4) {
            PacketDistributor.sendToServer(new RemoveGaugePacket(
                menu.controllerPos, selectedCell.col(), selectedCell.row()));
            closeConfigureOverlay();
            return true;
        }

        // Connect button
        if (mouseX >= ox + 60 && mouseX < ox + 110 && mouseY >= oy + oh - 16 && mouseY < oy + oh - 4) {
            VirtualPanelPosition from = selectedCell;
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
        // Network selector scroll
        int nx = leftPos + BOARD_X;
        int ny = topPos + 6;
        if (mouseX >= nx && mouseX < nx + BOARD_W && mouseY >= ny && mouseY < ny + 16) {
            cycleNetwork(scrollY > 0 ? -1 : 1);
            return true;
        }

        // Board scroll
        int bx = leftPos + BOARD_X;
        int by = topPos + BOARD_Y;
        if (mouseX >= bx && mouseX < bx + BOARD_W && mouseY >= by && mouseY < by + BOARD_H) {
            scrollRow = Math.max(0, scrollRow + (int) -scrollY);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        if (keyCode == GLFW.GLFW_KEY_R && hoveredCell != null && findGauge(hoveredCell) != null) {
            PacketDistributor.sendToServer(new CycleArrowBendPacket(menu.controllerPos, hoveredCell));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && selectedCell != null) {
            closeConfigureOverlay();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void updateHoveredCell(int mouseX, int mouseY) {
        hoveredCell = cellAt(mouseX, mouseY);
    }

    @Nullable
    private VirtualPanelPosition cellAt(int mouseX, int mouseY) {
        int bx = leftPos + BOARD_X;
        int by = topPos + BOARD_Y;
        if (mouseX < bx || mouseX >= bx + BOARD_W || mouseY < by || mouseY >= by + BOARD_H) return null;
        int col = (mouseX - bx) / CELL_W + scrollCol;
        int row = (mouseY - by) / CELL_H + scrollRow;
        return new VirtualPanelPosition(col, row);
    }

    @Nullable
    private int[] cellCenter(int boardX, int boardY, VirtualPanelPosition pos) {
        int screenCol = pos.col() - scrollCol;
        int screenRow = pos.row() - scrollRow;
        if (screenCol < 0 || screenCol >= COLS_VISIBLE || screenRow < 0 || screenRow >= ROWS_VISIBLE) return null;
        return new int[]{
            boardX + screenCol * CELL_W + CELL_W / 2,
            boardY + screenRow * CELL_H + CELL_H / 2
        };
    }

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

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(font, title, 7, 14, 0xFFFFFFFF, false);
        // Render overlay buttons text if overlay is open
        if (selectedCell != null) {
            int ox = 30;
            int oy = 40;
            int oh = 100;
            gfx.drawString(font, "[OK]", ox + 4, oy + oh - 14, 0xFF55FF55, false);
            gfx.drawString(font, "[Link]", ox + 60, oy + oh - 14, 0xFF5599FF, false);
            gfx.drawString(font, "[Remove]", imageWidth - 30 - 54, oy + oh - 14, 0xFFFF5555, false);
        }
    }
}
