package io.github.nbcss.createfactorycontroller.content.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.gui.screen.controller.FactoryControllerScreen;
import io.github.nbcss.createfactorycontroller.content.helper.TooltipBuilder;
import io.github.nbcss.createfactorycontroller.content.supply.ComponentSupplyCatalog;
import io.github.nbcss.createfactorycontroller.content.supply.ComponentSupplyEntry;
import io.github.nbcss.createfactorycontroller.content.supply.ComponentSupplyHotbar;
import io.github.nbcss.createfactorycontroller.content.render.SpriteNumbersRender;
import io.github.nbcss.createfactorycontroller.content.render.BatchedBlitter;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CollapsiblePlayerInventory extends AbstractWidget {
    private static final int BOTTOM_MARGIN = 28;
    private static final int HOTBAR_HEIGHT = 18;
    private static final int MAIN_INVENTORY_ROWS = 3;
    private static final int INVENTORY_GAP = 4;
    private static final int SLOT_ROW_WIDTH = 162;
    private static final int BUTTON_SIZE = 9;
    private static final int SLOT_COUNT = 9;
    private static final int SLOT_SPACING = 18;
    private static final int PANEL_WIDTH = 176;
    private static final int TITLE_HEIGHT = 18;
    private static final int SLOT_LEFT = 8;
    private static final int PANEL_BELOW_HOTBAR = 24;
    private static final int POINTER_X = 44;
    private static final int POINTER_WIDTH = 16;
    private static final int POINTER_HEIGHT = 12;
    private static final int POINTER_OVERLAP = 4;
    private static final int TAB_WIDTH = 17;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 2;
    private static final int TAB_OVERLAP = 4;
    private static final float TAB_ICON_SHADE = 0.28f;

    private static final ResourceLocation FRAME_SPRITE = sprite("factory_controller/inventory/frame");
    private static final ResourceLocation FRAME_POINTER_SPRITE = sprite("factory_controller/inventory/frame_pointer");
    private static final ResourceLocation SLOT_SPRITE = sprite("common/slot");
    private static final ResourceLocation BUTTON_BASE_SPRITE = sprite("factory_controller/tiny_button/base_general");
    private static final ResourceLocation EXPAND_SPRITE = sprite("factory_controller/tiny_button/expand");
    private static final ResourceLocation COLLAPSE_SPRITE = sprite("factory_controller/tiny_button/collapse");
    private static final ResourceLocation CONTAINER_SPRITE = sprite("factory_controller/tiny_button/container");
    private static final int BUTTON_GAP = 2;
    private static final int ADJACENT_CONTAINERS_ON = 0x429138;
    private static final ResourceLocation TAB_UNSELECTED = sprite("factory_controller/inventory/tab_unselected");
    private static final ResourceLocation TAB_SELECTED_TOP = sprite("factory_controller/inventory/tab_selected_top");
    private static final ResourceLocation TAB_SELECTED_MIDDLE = sprite("factory_controller/inventory/tab_selected_middle");
    private static final ResourceLocation TAB_SELECTED_BOTTOM = sprite("factory_controller/inventory/tab_selected_bottom");
    private static final ResourceLocation INVENTORY_ICON = sprite("icons/inventory");
    private static final ResourceLocation COMPONENT_SUPPLY_ICON = sprite("icons/component_supply");
    private static final Component COMPONENT_SUPPLY_TITLE = Component.translatable("createfactorycontroller.gui.component_supply");

    private static final int HELD_SLOT_DIM = 0xAA8B8B8B;
    private static final int SLOT_HOVER = 0x80FFFFFF;
    /** Expanded hotbar slots pulse to show they can be arranged. */
    private static final int FLASH_PERIOD_MS = 1600;
    private static final int FLASH_MAX_ALPHA = 0x70;

    private final FactoryControllerMenu menu;
    private final Font font;
    private final Component title;
    private final FactoryControllerScreen screen;
    private int menuLeft;
    private int menuTop;
    private int slotOriginX;
    private int hotbarY;
    private boolean expanded;
    private boolean componentSupply;
    private boolean hidden;
    private double lastMouseX = -1;
    private double lastMouseY = -1;

    public CollapsiblePlayerInventory(FactoryControllerMenu menu, Font font, Component title,
                                      FactoryControllerScreen screen, boolean componentSupply) {
        super(0, 0, PANEL_WIDTH, TITLE_HEIGHT + PANEL_BELOW_HOTBAR + POINTER_HEIGHT - POINTER_OVERLAP, Component.empty());
        this.menu = menu;
        this.font = font;
        this.title = title;
        this.screen = screen;
        this.componentSupply = componentSupply;
    }

    public void layout(int menuLeft, int menuTop, int menuWidth, int screenHeight) {
        this.menuLeft = menuLeft;
        this.menuTop = menuTop;
        slotOriginX = (menuWidth - SLOT_ROW_WIDTH) / 2 + 1 + TAB_WIDTH / 2;
        hotbarY = screenHeight - BOTTOM_MARGIN - HOTBAR_HEIGHT - menuTop;
        reposition();
    }

    public void setHidden(boolean hidden) {
        if (this.hidden == hidden) return;
        this.hidden = hidden;
        visible = active = !hidden;
        reposition();
    }

    public boolean isComponentSupply() {
        return componentSupply;
    }

    public boolean blocksCanvas(double mouseX, double mouseY) {
        if (hidden) return false;
        if (tabAt(mouseX, mouseY) != null) return true;
        return mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < panelBottom();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY) || (!hidden && tabAt(mouseX, mouseY) != null);
    }

    /** Slot rows above the hotbar: none when collapsed, else the main inventory or the catalog grid. */
    private int upperRows() {
        if (!expanded) return 0;
        return componentSupply ? catalogRows() : MAIN_INVENTORY_ROWS;
    }

    private static int catalogRows() {
        return Math.max(1, (ComponentSupplyCatalog.entries().size() + SLOT_COUNT - 1) / SLOT_COUNT);
    }

    private int upperHeight() {
        int rows = upperRows();
        return rows == 0 ? 0 : rows * SLOT_SPACING + INVENTORY_GAP;
    }

    /** Item y of an upper row; matches {@link FactoryControllerMenu}'s main inventory rows. */
    private int upperRowY(int row) {
        return slotY() - INVENTORY_GAP - (upperRows() - row) * SLOT_SPACING;
    }

    private void reposition() {
        setX(menuLeft + slotOriginX - SLOT_LEFT);
        setY(slotY() - TITLE_HEIGHT - upperHeight());
        height = panelBottom() + POINTER_HEIGHT - POINTER_OVERLAP - getY();
        if (hidden || componentSupply) menu.hideSlots();
        else menu.repositionSlots(slotOriginX, hotbarY, expanded);
    }

    private int expandButtonX() {
        return menuLeft + slotOriginX + SLOT_ROW_WIDTH - 10;
    }

    private int adjacentContainersButtonX() {
        return expandButtonX() - BUTTON_GAP - BUTTON_SIZE;
    }

    private boolean isAdjacentContainersButtonHovered(double mouseX, double mouseY) {
        return !hidden && componentSupply && isButtonHovered(adjacentContainersButtonX(), mouseX, mouseY);
    }

    private int buttonY() {
        return slotY() - upperHeight() - BUTTON_SIZE - 3;
    }

    private boolean isButtonHovered(int buttonX, double mouseX, double mouseY) {
        return mouseX >= buttonX && mouseX < buttonX + BUTTON_SIZE
                && mouseY >= buttonY() && mouseY < buttonY() + BUTTON_SIZE;
    }

    private int slotX(int column) {
        return menuLeft + slotOriginX + column * SLOT_SPACING;
    }

    private int slotY() {
        return menuTop + hotbarY;
    }

    /** Exclusive bottom edge of the visible frame. */
    private int panelBottom() {
        return slotY() + PANEL_BELOW_HOTBAR;
    }

    /** Tabs stack up from the frame's bottom, so they stay put when the panel expands upward. */
    private int tabY(boolean supplyTab) {
        return panelBottom() - TAB_HEIGHT - (supplyTab ? 0 : TAB_HEIGHT + TAB_GAP);
    }

    private int tabX() {
        return getX() - TAB_WIDTH;
    }

    /** {@code TRUE} for the Component Supply tab, {@code FALSE} for Inventory, {@code null} when neither is hovered. */
    @Nullable
    private Boolean tabAt(double mouseX, double mouseY) {
        if (mouseX < tabX() || mouseX >= tabX() + TAB_WIDTH) return null;
        for (boolean supplyTab : new boolean[]{false, true})
            if (mouseY >= tabY(supplyTab) && mouseY < tabY(supplyTab) + TAB_HEIGHT) return supplyTab;
        return null;
    }

    private int columnAt(double mouseX) {
        for (int i = 0; i < SLOT_COUNT; i++)
            if (mouseX >= slotX(i) - 1 && mouseX < slotX(i) + 17) return i;
        return -1;
    }

    private static boolean inRow(double mouseY, int itemY) {
        return mouseY >= itemY - 1 && mouseY < itemY + 17;
    }

    /** Index of the Component Supply hotbar slot under the mouse (0..8), or -1. */
    private int slotIndexAt(double mouseX, double mouseY) {
        if (hidden || !componentSupply || !inRow(mouseY, slotY())) return -1;
        return columnAt(mouseX);
    }

    /** Index into the catalog of the expanded grid cell under the mouse, or -1 (also for trailing empty cells). */
    private int catalogIndexAt(double mouseX, double mouseY) {
        if (hidden || !componentSupply || !expanded) return -1;
        int rows = upperRows();
        for (int row = 0; row < rows; row++) {
            if (!inRow(mouseY, upperRowY(row))) continue;
            int column = columnAt(mouseX);
            int index = row * SLOT_COUNT + column;
            return column >= 0 && index < ComponentSupplyCatalog.entries().size() ? index : -1;
        }
        return -1;
    }

    @Nullable
    private static ComponentSupplyEntry catalogEntry(int index) {
        List<ComponentSupplyEntry> entries = ComponentSupplyCatalog.entries();
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        Component panelTitle = componentSupply ? COMPONENT_SUPPLY_TITLE : title;
        RenderSystem.enableBlend();
        // Unselected under the frame (its open right end tucks beneath), selected over it (it opens into the frame).
        renderTab(graphics, !componentSupply);
        TiledSpriteRenderer.create(FRAME_SPRITE).render(graphics, getX(), getY(), PANEL_WIDTH, panelBottom() - getY());
        graphics.blitSprite(FRAME_POINTER_SPRITE, getX() + POINTER_X, panelBottom() - POINTER_OVERLAP,
                POINTER_WIDTH, POINTER_HEIGHT);
        for (int row = 0, rows = upperRows(); row < rows; row++)
            renderSlotRow(graphics, upperRowY(row));
        renderSlotRow(graphics, slotY());
        graphics.drawString(font, panelTitle, getX() + 8, getY() + 6, 0x404040, false);

        renderTab(graphics, componentSupply);
        renderTinyButton(graphics, expandButtonX(), expanded ? COLLAPSE_SPRITE : EXPAND_SPRITE,
                isButtonHovered(expandButtonX(), mouseX, mouseY), -1);
        if (componentSupply)
            renderTinyButton(graphics, adjacentContainersButtonX(), CONTAINER_SPRITE,
                    isAdjacentContainersButtonHovered(mouseX, mouseY),
                    screen.useAdjacentContainers() ? ADJACENT_CONTAINERS_ON : -1);
    }

    private void renderSlotRow(GuiGraphics graphics, int itemY) {
        BatchedBlitter slot = BatchedBlitter.forSprite(SLOT_SPRITE);
        for (int i = 0; i < SLOT_COUNT; i++)
            slot.blit(graphics.bufferSource(), graphics.pose(), slotX(i) - 1, itemY - 1, SLOT_SPACING, SLOT_SPACING);
    }

    private void renderTab(GuiGraphics graphics, boolean supplyTab) {
        boolean selected = supplyTab == componentSupply;
        int x = tabX();
        int y = tabY(supplyTab);
        ResourceLocation frame;
        if (!selected) frame = TAB_UNSELECTED;
        else if (y == getY()) frame = TAB_SELECTED_TOP;
        else if (y + TAB_HEIGHT == panelBottom()) frame = TAB_SELECTED_BOTTOM;
        else frame = TAB_SELECTED_MIDDLE;
        // Vanilla 1.21.1 nine-slice crashes on tab_unselected's zero right border.
        TiledSpriteRenderer.create(frame).render(graphics, x, y, TAB_WIDTH + TAB_OVERLAP, TAB_HEIGHT);
        float shade = selected ? TAB_ICON_SHADE : 1f;
        graphics.setColor(shade, shade, shade, 1f);
        graphics.blitSprite(supplyTab ? COMPONENT_SUPPLY_ICON : INVENTORY_ICON, x + 2, y + 2, 16, 16);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /** {@code activeRgb} >= 0 tints the icon for an enabled toggle; -1 uses the plain grey/white-on-hover icon. */
    private void renderTinyButton(GuiGraphics graphics, int x, ResourceLocation icon, boolean hovered, int activeRgb) {
        int y = buttonY();
        graphics.blitSprite(BUTTON_BASE_SPRITE, x, y, BUTTON_SIZE, BUTTON_SIZE);
        if (hovered)
            graphics.fill(x + 1, y + 1, x + 8, y + 8, 0x44FFFFFF);
        if (activeRgb >= 0) {
            graphics.setColor((activeRgb >> 16 & 0xFF) / 255f, (activeRgb >> 8 & 0xFF) / 255f, (activeRgb & 0xFF) / 255f, 1f);
        } else {
            float shade = hovered ? 1f : 0x55 / 255f;
            graphics.setColor(shade, shade, shade, 1f);
        }
        graphics.blitSprite(icon, x + 2, y + 2, 5, 5);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    /** Draws the Component Supply slot items; call after the screen's slot pass so block models get depth testing. */
    public void renderComponentSupplyItems(GuiGraphics graphics, int mouseX, int mouseY) {
        if (hidden || !componentSupply) return;
        ComponentSupplyEntry held = screen.heldEntry();
        int hoveredSlot = slotIndexAt(mouseX, mouseY);
        int flash = expanded ? flashColor() : 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (expanded) graphics.fill(slotX(i), slotY(), slotX(i) + 16, slotY() + 16, flash);
            // Expanded, the hotbar is being arranged, so it shows no stock. Key labels are hidden for now:
            // label = expanded ? SpriteNumbersRender.HASH + (i + 1) : null
            renderSupplySlot(graphics, ComponentSupplyHotbar.get(i), slotX(i), slotY(), held, i == hoveredSlot,
                    !expanded, null);
        }
        if (!expanded) return;
        List<ComponentSupplyEntry> entries = ComponentSupplyCatalog.entries();
        int hoveredCell = catalogIndexAt(mouseX, mouseY);
        for (int i = 0; i < entries.size(); i++)
            renderSupplySlot(graphics, entries.get(i), slotX(i % SLOT_COUNT), upperRowY(i / SLOT_COUNT),
                    held, i == hoveredCell, true, null);
    }

    private static int flashColor() {
        float phase = (Util.getMillis() % FLASH_PERIOD_MS) / (float) FLASH_PERIOD_MS;
        int alpha = (int) (FLASH_MAX_ALPHA * (0.5f - 0.5f * Mth.cos(phase * Mth.TWO_PI)));
        return alpha << 24 | 0xFFFFFF;
    }

    /** {@code label}, when given, replaces the stock count and is drawn on an empty slot too. */
    private void renderSupplySlot(GuiGraphics graphics, @Nullable ComponentSupplyEntry entry, int x, int y,
                                  @Nullable ComponentSupplyEntry held, boolean hovered, boolean showCount,
                                  @Nullable String label) {
        if (entry != null) {
            graphics.renderItem(new ItemStack(entry.item()), x, y);
            if (entry.equals(held)) {
                graphics.fill(RenderType.guiOverlay(), x, y, x + 16, y + 16, HELD_SLOT_DIM);
                graphics.flush();   // the batched overlay ignores depth, so draw it before the count
            }
        }
        if (label != null) renderCornerText(graphics, x, y, label);
        else if (entry != null && showCount) renderStockCount(graphics, x, y, screen.available(entry));
        if (hovered)
            graphics.fill(RenderType.guiOverlay(), x, y, x + 16, y + 16, SLOT_HOVER);
    }

    /** Compact-font count over a 16×16 item at (x, y), placed like the Stock Keeper's; always drawn, zero included. */
    public static void renderStockCount(GuiGraphics graphics, int x, int y, int count) {
        renderStockCount(graphics, x, y, count, 0xFFFFFFFF);
    }

    public static void renderStockCount(GuiGraphics graphics, int x, int y, int count, int argb) {
        renderCornerText(graphics, x, y, SpriteNumbersRender.abbreviate(count), argb);
    }

    private static void renderCornerText(GuiGraphics graphics, int x, int y, String text) {
        renderCornerText(graphics, x, y, text, 0xFFFFFFFF);
    }

    private static void renderCornerText(GuiGraphics graphics, int x, int y, String text, int argb) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        SpriteNumbersRender.drawCount(graphics, text, x, y, argb);
        graphics.pose().popPose();
    }

    /**
     * Handles a hotbar key (1-9 by default) in Component Supply: over a cell of the expanded catalog grid it puts
     * that entry in the key's slot, otherwise it takes the slot's entry. False when the key is not handled.
     */
    public boolean componentSupplyKeyPressed(int keyCode, int scanCode) {
        if (hidden || !componentSupply) return false;
        KeyMapping[] keys = Minecraft.getInstance().options.keyHotbarSlots;
        for (int i = 0; i < SLOT_COUNT && i < keys.length; i++) {
            if (!keys[i].matches(keyCode, scanCode)) continue;
            ComponentSupplyEntry hovered = catalogEntry(catalogIndexAt(lastMouseX, lastMouseY));
            if (hovered != null) {
                ComponentSupplyHotbar.assign(i, hovered);
                return true;
            }
            ComponentSupplyEntry entry = ComponentSupplyHotbar.get(i);
            if (entry == null) return false;
            screen.takeComponentSupply(entry);
            return true;
        }
        return false;
    }

    /** Tooltip for the panel's tabs and Component Supply slots under the mouse, or {@code null}. */
    @Nullable
    public List<FormattedCharSequence> tooltipAt(double mouseX, double mouseY) {
        if (hidden) return null;
        TooltipBuilder tooltip = TooltipBuilder.of(font);
        Boolean tab = tabAt(mouseX, mouseY);
        if (tab != null) return tooltip.line(tab ? COMPONENT_SUPPLY_TITLE : title).build();
        if (isAdjacentContainersButtonHovered(mouseX, mouseY)) return adjacentContainersTooltip(tooltip);
        int slot = slotIndexAt(mouseX, mouseY);
        if (slot >= 0 && expanded) return hotbarSlotTooltip(tooltip, slot);
        ComponentSupplyEntry entry = slot >= 0 ? ComponentSupplyHotbar.get(slot)
                : catalogEntry(catalogIndexAt(mouseX, mouseY));
        if (entry == null) return null;
        // With adjacent containers off there is no count to show, which "-" tells apart from an empty container.
        Component adjacent = screen.useAdjacentContainers()
                ? Component.literal(String.valueOf(screen.adjacentStock(entry))).withStyle(ChatFormatting.WHITE)
                : Component.literal("-").withStyle(ChatFormatting.DARK_GRAY);
        // A terminal that may not move items cannot use the inventory: its count is shown struck through.
        Component inventory = Component.literal(String.valueOf(screen.inventoryCount(entry))).withStyle(
                screen.inventoryUsable() ? new ChatFormatting[]{ChatFormatting.WHITE}
                        : new ChatFormatting[]{ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH});
        return tooltip
                .lines(Screen.getTooltipFromItem(Minecraft.getInstance(), new ItemStack(entry.item())))
                .line(Component.translatable("createfactorycontroller.gui.component_supply.adjacent_stock", adjacent)
                        .withStyle(ChatFormatting.GRAY))
                .line(Component.translatable("createfactorycontroller.gui.component_supply.inventory_stock", inventory)
                        .withStyle(ChatFormatting.GRAY))
                .build();
    }

    private List<FormattedCharSequence> adjacentContainersTooltip(TooltipBuilder tooltip) {
        boolean enabled = screen.useAdjacentContainers();
        return tooltip
                .line(Component.literal(enabled ? "✔ " : "✘ ")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)
                        .append(Component.translatable("createfactorycontroller.gui.component_supply.adjacent_containers")
                                .withColor(ScrollInput.HEADER_RGB.getRGB())))
                .wrapped(Component.translatable("createfactorycontroller.gui.component_supply.adjacent_containers.desc")
                        .withStyle(ChatFormatting.GRAY))
                .wrapped(Component.translatable("createfactorycontroller.gui.component_supply.adjacent_containers.warning")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .build();
    }

    /** Expanded hotbar slots are being arranged, so their tooltip is about the slot rather than the stock. */
    private List<FormattedCharSequence> hotbarSlotTooltip(TooltipBuilder tooltip, int slot) {
        ComponentSupplyEntry entry = ComponentSupplyHotbar.get(slot);
        return tooltip
                .line(entry == null
                        ? Component.translatable("createfactorycontroller.gui.component_supply.empty_slot")
                                .withStyle(ChatFormatting.DARK_GRAY)
                        : new ItemStack(entry.item()).getHoverName().copy()
                                .withStyle(style -> style.withColor(entry.type().color())))
                .line(Component.translatable("createfactorycontroller.gui.component_supply.set_slot_hint")
                        .withStyle(ChatFormatting.GRAY))
                .line(Component.translatable("createfactorycontroller.gui.component_supply.clear_slot_hint")
                        .withStyle(ChatFormatting.GRAY))
                .build();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hidden) return false;
        int slot = slotIndexAt(mouseX, mouseY);
        if (slot >= 0 && (button == 0 || button == 1)) {
            if (clickHotbarSlot(slot, button)) return true;
        }
        ComponentSupplyEntry cell = catalogEntry(catalogIndexAt(mouseX, mouseY));
        if (cell != null && (button == 0 || button == 1)) {
            screen.onComponentSupplySlotClicked(cell);
            return true;
        }
        if (!isValidClickButton(button)) return false;
        Boolean tab = tabAt(mouseX, mouseY);
        if (tab != null) {
            if (tab == componentSupply) return true;
            componentSupply = tab;
            reposition();
            screen.onComponentSupplyToggled(componentSupply);
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        if (isAdjacentContainersButtonHovered(mouseX, mouseY)) {
            screen.setUseAdjacentContainers(!screen.useAdjacentContainers());
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        if (isButtonHovered(expandButtonX(), mouseX, mouseY)) {
            expanded = !expanded;
            reposition();
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        return false;
    }

    /** Collapsed, a slot only takes or puts back; expanded it is also edited. False lets an empty slot fall through. */
    private boolean clickHotbarSlot(int slot, int button) {
        ComponentSupplyEntry entry = ComponentSupplyHotbar.get(slot);
        if (expanded) {
            if (button == 1) {
                ComponentSupplyHotbar.clear(slot);
                return true;
            }
            ComponentSupplyEntry held = screen.heldEntry();
            if (held != null && !held.equals(entry)) {
                ComponentSupplyHotbar.assign(slot, held);
                screen.releaseComponentSupply();
                return true;
            }
        }
        if (entry == null) return expanded;
        screen.onComponentSupplySlotClicked(entry);
        return true;
    }

    private static ResourceLocation sprite(String path) {
        return ResourceLocation.fromNamespaceAndPath("createfactorycontroller", path);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {
    }
}
