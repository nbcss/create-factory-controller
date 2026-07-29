package io.github.nbcss.createfactorycontroller.content.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class CollapsiblePlayerInventory extends AbstractWidget {
    private static final int BOTTOM_MARGIN = 28;
    private static final int HOTBAR_HEIGHT = 18;
    private static final int MAIN_INVENTORY_HEIGHT = 54;
    private static final int INVENTORY_GAP = 4;
    private static final int SLOT_ROW_WIDTH = 162;
    private static final int BUTTON_SIZE = 9;

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "createfactorycontroller", "textures/gui/player_inventory.png");
    private static final ResourceLocation BUTTON_BASE_SPRITE = ResourceLocation.fromNamespaceAndPath(
            "createfactorycontroller", "factory_controller/tiny_button/base_general");
    private static final ResourceLocation EXPAND_SPRITE = ResourceLocation.fromNamespaceAndPath(
            "createfactorycontroller", "factory_controller/tiny_button/expand");
    private static final ResourceLocation COLLAPSE_SPRITE = ResourceLocation.fromNamespaceAndPath(
            "createfactorycontroller", "factory_controller/tiny_button/collapse");

    private static final int TEXTURE_WIDTH = 176;
    private static final int TEXTURE_HEIGHT = 108;
    private static final int TEXTURE_SLOT_LEFT = 8;
    private static final int TEXTURE_TITLE_HEIGHT = 18;
    private static final int TEXTURE_HOTBAR_Y = 76;

    private final FactoryControllerMenu menu;
    private final Font font;
    private final Component title;
    private int menuLeft;
    private int menuTop;
    private int slotOriginX;
    private int hotbarY;
    private boolean expanded;

    public CollapsiblePlayerInventory(FactoryControllerMenu menu, Font font, Component title) {
        super(0, 0, TEXTURE_WIDTH, TEXTURE_TITLE_HEIGHT + TEXTURE_HEIGHT - TEXTURE_HOTBAR_Y, Component.empty());
        this.menu = menu;
        this.font = font;
        this.title = title;
    }

    public void layout(int menuLeft, int menuTop, int menuWidth, int screenHeight) {
        this.menuLeft = menuLeft;
        this.menuTop = menuTop;
        slotOriginX = (menuWidth - SLOT_ROW_WIDTH) / 2 + 1;
        hotbarY = screenHeight - BOTTOM_MARGIN - HOTBAR_HEIGHT - menuTop;
        reposition();
    }

    public boolean blocksCanvas(double mouseX, double mouseY) {
        int bottom = menuTop + hotbarY + TEXTURE_HEIGHT - TEXTURE_HOTBAR_Y - 7;
        return mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < bottom;
    }

    private void reposition() {
        setX(menuLeft + slotOriginX - TEXTURE_SLOT_LEFT);
        setY(menuTop + hotbarY - (expanded ? TEXTURE_HOTBAR_Y : TEXTURE_TITLE_HEIGHT));
        height = expanded ? TEXTURE_HEIGHT : TEXTURE_TITLE_HEIGHT + TEXTURE_HEIGHT - TEXTURE_HOTBAR_Y;
        menu.repositionSlots(slotOriginX, hotbarY, expanded);
    }

    private int buttonX() {
        return menuLeft + slotOriginX + SLOT_ROW_WIDTH - 10;
    }

    private int buttonY() {
        int inventoryTop = hotbarY - (expanded ? INVENTORY_GAP + MAIN_INVENTORY_HEIGHT : 0);
        return menuTop + inventoryTop - BUTTON_SIZE - 3;
    }

    private boolean isButtonHovered(double mouseX, double mouseY) {
        return mouseX >= buttonX() && mouseX < buttonX() + BUTTON_SIZE
                && mouseY >= buttonY() && mouseY < buttonY() + BUTTON_SIZE;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int hotbarScreenY = menuTop + hotbarY;
        if (expanded) {
            graphics.blit(TEXTURE, getX(), getY(), 0, 0, TEXTURE_WIDTH, TEXTURE_HEIGHT);
            graphics.drawString(font, title, getX() + 8, getY() + 6, 0x404040, false);
        } else {
            int hotbarStripHeight = TEXTURE_HEIGHT - TEXTURE_HOTBAR_Y;
            graphics.blit(TEXTURE, getX(), getY(), 0, 0, TEXTURE_WIDTH, TEXTURE_TITLE_HEIGHT);
            graphics.blit(TEXTURE, getX(), hotbarScreenY, 0, TEXTURE_HOTBAR_Y, TEXTURE_WIDTH, hotbarStripHeight);
            graphics.drawString(font, title, getX() + 8, getY() + 6, 0x404040, false);
        }

        boolean hovered = isButtonHovered(mouseX, mouseY);
        ResourceLocation icon = expanded ? COLLAPSE_SPRITE : EXPAND_SPRITE;
        RenderSystem.enableBlend();
        graphics.blitSprite(BUTTON_BASE_SPRITE, buttonX(), buttonY(), BUTTON_SIZE, BUTTON_SIZE);
        if (hovered)
            graphics.fill(buttonX() + 1, buttonY() + 1, buttonX() + 8, buttonY() + 8, 0x44FFFFFF);
        graphics.setColor(hovered ? 1f : 0x55 / 255f, hovered ? 1f : 0x55 / 255f, hovered ? 1f : 0x55 / 255f, 1f);
        graphics.blitSprite(icon, buttonX() + 2, buttonY() + 2, 5, 5);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isValidClickButton(button) || !isButtonHovered(mouseX, mouseY)) return false;
        expanded = !expanded;
        reposition();
        playDownSound(Minecraft.getInstance().getSoundManager());
        return true;
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {
    }
}
