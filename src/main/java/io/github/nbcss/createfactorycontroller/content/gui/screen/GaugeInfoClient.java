package io.github.nbcss.createfactorycontroller.content.gui.screen;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.packet.GaugeInfoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side holder for the latest {@link GaugeInfoPacket} state
 */
@OnlyIn(Dist.CLIENT)
public final class GaugeInfoClient {

    @org.jetbrains.annotations.Nullable private static VirtualComponentPosition pos;
    private static int owned;
    private static int address;
    private static int timer;
    private static int maxTimer;
    private static boolean ticking;
    private static long receivedAt;

    private GaugeInfoClient() {}

    public static void update(VirtualComponentPosition gaugePos, int ownedCount, int addressCount,
                              int timerTicks, int maxTimerTicks, boolean timerTicking) {
        pos = gaugePos;
        owned = ownedCount;
        address = addressCount;
        timer = timerTicks;
        maxTimer = maxTimerTicks;
        ticking = timerTicking;
        receivedAt = clientGameTime();
    }

    /** Owned in-flight promises for {@code gaugePos}, or -1 if no reply for that gauge has arrived yet. */
    public static int owned(VirtualComponentPosition gaugePos) {
        return gaugePos.equals(pos) ? owned : -1;
    }

    /** Address-wide in-flight promises for {@code gaugePos}, or -1 if no reply for that gauge has arrived yet. */
    public static int address(VirtualComponentPosition gaugePos) {
        return gaugePos.equals(pos) ? address : -1;
    }

    /** Whether {@code gaugePos}'s request timer is actively counting down (per the last reply). */
    public static boolean timerTicking(VirtualComponentPosition gaugePos) {
        return gaugePos.equals(pos) && ticking && maxTimer > 0;
    }

    /** request timer */
    public static float timerProgress(VirtualComponentPosition gaugePos, long lastRequestTick) {
        if (!gaugePos.equals(pos) || maxTimer <= 0) return 0f;
        long now = clientGameTime();
        long fireBased = maxTimer - (now - lastRequestTick);
        long pollBased = timer - (now - receivedAt);
        long estimated = Mth.clamp(Math.max(fireBased, pollBased), 0, maxTimer);
        return Mth.clamp(1f - (float) estimated / maxTimer, 0f, 1f);
    }

    private static long clientGameTime() {
        return Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
    }

    public static void clear() {
        pos = null;
    }
}
