package io.github.nbcss.createfactorycontroller.content.render;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.nbcss.createfactorycontroller.content.gui.screen.recipe.ConfigureRecipeScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.ProductionOrdersScreen;
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
     * drawn at {@code (itemX, itemY)}, matching the Stock Keeper's bottom-right, horizontally-centred count placement.
     */
    public static void drawCount(GuiGraphics gfx, String text, int itemX, int itemY) {
        if (text.isBlank()) return;
        // Centred around itemX+13 via the -length*2.5 offset (the Stock Keeper convention).
        blitGlyphs(gfx, text, itemX + 13 + (int) Math.floor(-text.length() * 2.5), itemY + 10);
    }

    /** Same glyphs as {@link #drawCount}, but right-aligned so the string's right edge lands at {@code rightX}
     *  (drawn at {@code y}). Used where a slot count should sit at the bottom-right like the vanilla item count. */
    public static void drawCountRightAligned(GuiGraphics gfx, String text, int rightX, int y) {
        if (text.isBlank()) return;
        blitGlyphs(gfx, text, rightX - width(text), y);
    }

    /** {@link #drawCountRightAligned} tinted with {@code argb} (the white glyph sprites are multiplied by it). */
    public static void drawCountRightAligned(GuiGraphics gfx, String text, int rightX, int y, int argb) {
        if (text.isBlank()) return;
        gfx.setColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f,
                ((argb >>> 24) & 0xFF) / 255f);
        blitGlyphs(gfx, text, rightX - width(text), y);
        gfx.setColor(1f, 1f, 1f, 1f);
    }

    /** Pixel width of {@code text} as rendered (glyphs overlap 1px; a space is 4px). */
    public static int width(String text) {
        int w = 0;
        boolean first = true;
        for (char raw : text.toCharArray()) {
            char c = Character.toLowerCase(raw);
            if (c == ' ') { w += 4; continue; }
            int sw = spriteWidthOf(c);
            w += first ? sw : sw - 1;
            first = false;
        }
        return w;
    }

    /** Blits each glyph left-to-right starting at {@code startX}; consecutive glyphs overlap by 1px, a space is 4px. */
    private static void blitGlyphs(GuiGraphics gfx, String text, int startX, int y) {
        int x = startX;
        for (char raw : text.toCharArray()) {
            char c = Character.toLowerCase(raw);
            if (c == ' ') { x += 4; continue; }
            int sw = spriteWidthOf(c);
            RenderSystem.enableBlend();
            gfx.blit(NUMBERS_TEX, x, y, 0, NUM_SX + xOffsetOf(c), NUM_SY, sw, NUM_H, 256, 256);
            x += sw - 1;
        }
    }

    private static int spriteWidthOf(char c) {
        return switch (c) {
            case '.' -> 3;
            case 'm' -> 7;
            case '+' -> 9;
            default -> NUM_W;   // digits, k, b
        };
    }

    private static int xOffsetOf(char c) {
        return switch (c) {
            case '.' -> 60;
            case 'k' -> 64;
            case 'm' -> 70;
            case 'b' -> 78;
            case '+' -> 84;
            default -> (c - '0') * 6;   // digits
        };
    }
}
