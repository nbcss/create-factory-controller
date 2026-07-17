package io.github.nbcss.createfactorycontroller.content.gui.screen;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.packet.GaugeInfoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side holder for the latest {@link GaugeInfoPacket}
 * reply, so the open {@link io.github.nbcss.createfactorycontroller.content.gui.screen.recipe.ConfigureRecipeScreen}
 * can show a gauge's live in-flight promise count without that count being synced every tick. Keyed by gauge cell so a
 * reply for a different gauge is ignored.
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

    /** Charge-up progress [0,1] of {@code gaugePos}'s request timer — 0 just after a request fires, rising to 1 as
     *  the next fire nears. Returns 0 when no reply for that gauge has arrived.
     *
     *  <p>Two estimates are combined and the higher (more-recently-reset) wins: the <b>fire-based</b> one, anchored
     *  to the promptly-synced {@code lastRequestTick} (so a fresh cycle snaps to frame 0 immediately, not on the
     *  next ≤10-tick poll — which is why the animation used to skip the first frame); and the <b>poll-based</b> one,
     *  which stays correct across a satisfied-pause where the timer freezes without a new fire.</p> */
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

    /** Forget the cached reply (called when the recipe screen opens for a gauge, so a stale value isn't shown). */
    public static void clear() {
        pos = null;
    }
}
