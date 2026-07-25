package io.github.nbcss.createfactorycontroller.content.gui.widget;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * A transient status/error message render
 */
@OnlyIn(Dist.CLIENT)
public class ActionPromptWidget {
    private static final long DEFAULT_DURATION_MS = 3000; //3s
    private static final long FADE_MS = 1000;
    private static final int TEXT_RGB = 0xFFFFFF;
    private static final int BACKGROUND_ALPHA = 0xC0;
    private static final int PADDING_X = 4;
    private static final int PADDING_Y = 2;

    @Nullable private Component message;
    private long expiry;

    public void show(Component message) {
        show(message, DEFAULT_DURATION_MS);
    }

    public void show(Component message, long durationMs) {
        this.message = message;
        this.expiry = Util.getMillis() + durationMs;
    }

    public void clear() {
        this.message = null;
    }

    /**
     * Draws the live message
     */
    public void render(GuiGraphics gfx, Font font, int centerX, int y) {
        if (message == null) return;
        long remaining = expiry - Util.getMillis();
        if (remaining <= 0) {
            message = null;
            return;
        }
        draw(gfx, font, message, centerX, y, Mth.clamp(remaining / (float) FADE_MS, 0f, 1f));
    }

    /** Centred text over a translucent rectangle at {@code opacity} (0..1). The message keeps its own text colour. */
    public static void draw(GuiGraphics gfx, Font font, Component message, int centerX, int y, float opacity) {
        int textAlpha = (int) (opacity * 255f);
        if (textAlpha <= 4) return;   // below this the font renderer forces full opacity, so skip the frame entirely
        int width = font.width(message);
        int x = centerX - width / 2;
        int backgroundAlpha = (int) (opacity * BACKGROUND_ALPHA);
        gfx.pose().pushPose();
        gfx.pose().translate(0, 0, 250);
        gfx.fill(x - PADDING_X, y - PADDING_Y, x + width + PADDING_X, y + font.lineHeight + PADDING_Y,
                backgroundAlpha << 24);
        gfx.drawString(font, message, x, y, (textAlpha << 24) | TEXT_RGB, false);
        gfx.pose().popPose();
    }
}
