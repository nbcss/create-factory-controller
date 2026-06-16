package io.github.nbcss.createfactorycontroller.content.render;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nbcss.createfactorycontroller.content.gui.ConfigureRecipeScreen;
import io.github.nbcss.createfactorycontroller.content.gui.ProductionOrdersScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a count/amount over a slot using Create's stock-keeper number sprite sheet
 * ({@code create:textures/gui/stock_keeper.png}, NUMBERS region) — the same compact glyph font the Stock Keeper
 * uses for item counts. Shared by {@link ConfigureRecipeScreen} and {@link ProductionOrdersScreen}.
 */
public final class SpriteNumbersRender {

    private static final ResourceLocation NUMBERS_TEX =
        ResourceLocation.fromNamespaceAndPath("create", "textures/gui/stock_keeper.png");
    private static final int NUM_SX = 48, NUM_SY = 176, NUM_W = 5, NUM_H = 8;

    private SpriteNumbersRender() {}

    /** Create's {@code StockKeeperRequestScreen#drawItemCount} abbreviation: k / m / "+" for huge counts. */
    public static String abbreviate(int count) {
        if (count >= 1000000000) return "+";
        return count >= 1000000 ? count / 1000000 + "m"
            : count >= 10000 ? count / 1000 + "k"
            : count >= 1000 ? (float) (count * 10 / 1000) / 10.0F + "k"
            : count >= 100 ? count + "" : " " + count;
    }

    /**
     * Blits {@code text} (digits, {@code .}, {@code k}, {@code m}, {@code b}, {@code +}) over the slot whose item is
     * drawn at {@code (itemX, itemY)}, matching the Stock Keeper's bottom-right count placement.
     */
    public static void drawCount(GuiGraphics gfx, String text, int itemX, int itemY) {
        if (text.isBlank()) return;
        int x = (int) Math.floor(-text.length() * 2.5);
        for (char raw : text.toCharArray()) {
            char c = Character.toLowerCase(raw);
            int xOffset = (c - '0') * 6;
            int spriteWidth = NUM_W;
            switch (c) {
                case ' ': x += 4; continue;
                case '.': spriteWidth = 3; xOffset = 60; break;
                case 'k': xOffset = 64; break;
                case 'm': spriteWidth = 7; xOffset = 70; break;
                case 'b': xOffset = 78; break;
                case '+': spriteWidth = 9; xOffset = 84; break;
                default: break;
            }
            RenderSystem.enableBlend();
            gfx.blit(NUMBERS_TEX, itemX + 13 + x, itemY + 10, 0, NUM_SX + xOffset, NUM_SY, spriteWidth, NUM_H, 256, 256);
            x += spriteWidth - 1;
        }
    }
}
