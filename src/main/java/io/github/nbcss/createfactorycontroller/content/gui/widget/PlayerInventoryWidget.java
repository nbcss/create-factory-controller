package io.github.nbcss.createfactorycontroller.content.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nbcss.createfactorycontroller.content.render.BatchedBlitter;
import io.github.nbcss.createfactorycontroller.content.render.TiledSpriteRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * The player inventory panel built from sprites
 */
public class PlayerInventoryWidget extends AbstractWidget {
    public static final int WIDTH = 176;
    /** Height of the standard panel (three rows over the hotbar), pointer included. */
    public static final int HEIGHT = 108;
    /** Item x of the first column, relative to the panel's left edge. */
    public static final int SLOT_LEFT = 8;
    /** Item y of the hotbar, relative to the standard panel's top edge. */
    public static final int HOTBAR_Y = 76;
    private static final int SLOT_SPACING = 18;
    /** Gap between the main inventory rows and the hotbar. */
    private static final int INVENTORY_GAP = 4;
    /** Frame below the hotbar's item y. */
    private static final int BELOW_HOTBAR = 24;
    private static final int POINTER_HEIGHT = 12;
    private static final int POINTER_OVERLAP = 4;
    private static final int POINTER_X = 44;
    private static final int POINTER_WIDTH = 16;
    private static final int SLOT_COUNT = 9;
    private static final int MAIN_INVENTORY_ROWS = 3;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int TITLE_COLOR = 0x404040;

    private static final ResourceLocation FRAME_SPRITE =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/inventory/frame");
    private static final ResourceLocation FRAME_POINTER_SPRITE =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "factory_controller/inventory/frame_pointer");
    private static final ResourceLocation SLOT_SPRITE =
            ResourceLocation.fromNamespaceAndPath("createfactorycontroller", "common/slot");

    private final Font font;

    /** The standard panel (three rows over the hotbar) with its top-left at ({@code x}, {@code y}). */
    public PlayerInventoryWidget(int x, int y, Font font, Component title) {
        super(x, y, WIDTH, HEIGHT, title);
        this.font = font;
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        RenderSystem.enableBlend();
        int x = getX();
        int hotbarY = getY() + HOTBAR_Y;
        renderFrame(graphics, x, getY(), hotbarY + BELOW_HOTBAR);
        for (int row = 0; row < MAIN_INVENTORY_ROWS; row++)
            renderSlotRow(graphics, x + SLOT_LEFT, hotbarY - INVENTORY_GAP - (MAIN_INVENTORY_ROWS - row) * SLOT_SPACING);
        renderSlotRow(graphics, x + SLOT_LEFT, hotbarY);
        graphics.drawString(font, getMessage(), x + TITLE_X, getY() + TITLE_Y, TITLE_COLOR, false);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {}

    /** The frame from {@code top} to {@code bottom} (exclusive), with the pointer hanging below it. */
    private static void renderFrame(GuiGraphics graphics, int x, int top, int bottom) {
        TiledSpriteRenderer.create(FRAME_SPRITE).render(graphics, x, top, WIDTH, bottom - top);
        graphics.blitSprite(FRAME_POINTER_SPRITE, x + POINTER_X, bottom - POINTER_OVERLAP, POINTER_WIDTH, POINTER_HEIGHT);
    }

    /** Nine slot cells for items at ({@code itemX} + column × 18, {@code itemY}). */
    private static void renderSlotRow(GuiGraphics graphics, int itemX, int itemY) {
        BatchedBlitter slot = BatchedBlitter.forSprite(SLOT_SPRITE);
        for (int i = 0; i < SLOT_COUNT; i++)
            slot.blit(graphics.bufferSource(), graphics.pose(), itemX + i * SLOT_SPACING - 1, itemY - 1,
                    SLOT_SPACING, SLOT_SPACING);
    }
}
