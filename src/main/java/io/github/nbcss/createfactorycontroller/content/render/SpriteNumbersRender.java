package io.github.nbcss.createfactorycontroller.content.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.content.logistics.BigItemStack;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.gui.screen.recipe.ConfigureRecipeScreen;
import io.github.nbcss.createfactorycontroller.content.gui.screen.ProductionOrdersScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a count/amount over a slot using the mod's dedicated compact-number sprite sheet。
 */
public final class SpriteNumbersRender {

    private static final ResourceLocation NUMBERS_TEX =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "textures/gui/compact_font.png");
    private static final int TEX = 64, GLYPH_H = 7;
    private static final int Y_NUDGE = 1;

    /** "Too big to display" indicator, drawn as ∞ (replaces the old {@code +} sentinel). */
    public static final String INFINITE = "∞";   // ∞
    // Extra symbols on row 3 after b — usable in any string passed to the draw methods.
    public static final String STACK = "▤";       // ▤
    public static final String ARROW = "^";
    public static final String MULTIPLY = "x";
    public static final String PLUS = "+";
    public static final String MINUS = "-";
    public static final String HASH = "#";
    /** Clock face (row 4) — prefixes a request-interval readout, e.g. {@code CLOCK + "2"}. */
    public static final String CLOCK = "⏱";      // ⏱

    private SpriteNumbersRender() {}

    /** Stock-Keeper-style abbreviation */
    public static String abbreviate(int count) {
        if (count >= BigItemStack.INF) return INFINITE;
        return count >= 1000000 ? count / 1000000 + "m"
            : count >= 10000 ? count / 1000 + "k"
            : count >= 1000 ? (float) (count * 10 / 1000) / 10.0F + "k"
            : count >= 100 ? count + "" : " " + count;
    }

    public static String abbreviateFluid(int mb) {
        if (mb >= BigItemStack.INF) return SpriteNumbersRender.INFINITE;
        if (mb >= 1_000_000) return compactFluid(mb, 1_000_000, "kB");
        if (mb >= 100) return compactFluid(mb, 1000, "B");
        return mb + "mB";
    }

    private static String compactFluid(int amount, int divisor, String suffix) {
        if (amount % divisor == 0) return (amount / divisor) + suffix;
        if (amount / divisor <= 10)
            return String.format(java.util.Locale.ROOT, "%.1f%s",
                    Math.floor(amount / (divisor / 10.0)) / 10.0, suffix);
        return String.format(java.util.Locale.ROOT, "%.0f%s",
                Math.floor(amount / (double) divisor), suffix);
    }

    /**
     * Blits {@code text} over the slot whose item is drawn at {@code (itemX, itemY)}, matching the Stock
     * Keeper's bottom-right, horizontally-centred count placement.
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

    /** Pixel width of {@code text} as rendered (glyphs overlap 1px; a space is 4px; unmapped chars are skipped). */
    public static int width(String text) {
        int w = 0;
        boolean first = true;
        for (char raw : text.toCharArray()) {
            if (raw == ' ') { w += 4; continue; }
            int[] g = glyph(raw);
            if (g == null) continue;
            w += first ? g[2] : g[2] - 1;
            first = false;
        }
        return w;
    }

    /** Blits each glyph left-to-right from {@code startX}; consecutive glyphs overlap by 1px, a space is 4px. */
    private static void blitGlyphs(GuiGraphics gfx, String text, int startX, int y) {
        int x = startX;
        for (char raw : text.toCharArray()) {
            if (raw == ' ') { x += 4; continue; }
            if (raw == '·') { x += 1; continue; }
            int[] g = glyph(raw);
            if (g == null) continue;
            RenderSystem.enableBlend();
            gfx.blit(NUMBERS_TEX, x, y + Y_NUDGE, 0, g[0], g[1], g[2], GLYPH_H, TEX, TEX);
            x += g[2] - 1;
        }
    }

    /** {@code {sx, sy, sw}} of {@code raw} in the sheet (case-insensitive), or {@code null} when unmapped. */
    private static int[] glyph(char raw) {
        char c = Character.toLowerCase(raw);
        if (c >= '0' && c <= '9') return new int[]{ (c - '0') * 6, 0, 5 };
        return switch (c) {
            case '.' -> new int[]{ 60, 0, 3 };
            case '∞' -> new int[]{ 0, 9, 9 };   // infinite
            case 'k' -> new int[]{ 0, 16, 5 };
            case 'm' -> new int[]{ 6, 16, 7 };
            case 'b' -> new int[]{ 14, 16, 5 };
            case '▤' -> new int[]{ 20, 16, 6 };       // stack
            case '^' -> new int[]{ 27, 16, 5 };       // arrow
            case 'x' -> new int[]{ 33, 16, 5 };       // multiply
            case '+' -> new int[]{ 39, 16, 5 };       // plus
            case '-' -> new int[]{ 45, 16, 5 };       // minus
            case '#' -> new int[]{ 51, 16, 7 };       // hash
            case '⏱' -> new int[]{ 0, 24, 7 };        // clock (row 4)
            default -> null;
        };
    }
}
