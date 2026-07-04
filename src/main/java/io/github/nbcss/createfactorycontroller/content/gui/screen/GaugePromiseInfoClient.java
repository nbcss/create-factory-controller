package io.github.nbcss.createfactorycontroller.content.gui.screen;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-side holder for the latest {@link io.github.nbcss.createfactorycontroller.content.packet.GaugePromiseInfoPacket}
 * reply, so the open {@link ConfigureRecipeScreen} can show a gauge's live in-flight promise count without that count
 * being synced every tick. Keyed by gauge cell so a reply for a different gauge is ignored.
 */
@OnlyIn(Dist.CLIENT)
public final class GaugePromiseInfoClient {

    @org.jetbrains.annotations.Nullable private static VirtualComponentPosition pos;
    private static int owned;
    private static int address;

    private GaugePromiseInfoClient() {}

    public static void update(VirtualComponentPosition gaugePos, int ownedCount, int addressCount) {
        pos = gaugePos;
        owned = ownedCount;
        address = addressCount;
    }

    /** Owned in-flight promises for {@code gaugePos}, or -1 if no reply for that gauge has arrived yet. */
    public static int owned(VirtualComponentPosition gaugePos) {
        return gaugePos.equals(pos) ? owned : -1;
    }

    /** Address-wide in-flight promises for {@code gaugePos}, or -1 if no reply for that gauge has arrived yet. */
    public static int address(VirtualComponentPosition gaugePos) {
        return gaugePos.equals(pos) ? address : -1;
    }

    /** Forget the cached reply (called when the recipe screen opens for a gauge, so a stale value isn't shown). */
    public static void clear() {
        pos = null;
    }
}
