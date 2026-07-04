package io.github.nbcss.createfactorycontroller.content.promise;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-network, per-tick cache of active {@link ControllerPromise} counts, bucketed by owner ({@code gaugeId}) and by
 * target address. A network's queue is {@code flatten}ed at most once per tick, and only if something asks for its
 * counts that tick — so the promise-limit gate stays O(1) per gauge even when many gauges fire the same tick, and it
 * costs nothing on ticks/networks nobody queries.
 *
 * <p>Server-only. Rebuilt lazily when the game tick advances; a just-dispatched promise is folded in via
 * {@link #onAdded} so a gauge firing later in the same tick sees a peer's fresh promise (no same-tick quota overshoot).</p>
 */
public final class PromiseCounts {

    private static long tick = Long.MIN_VALUE;
    private static final Map<UUID, Net> byNetwork = new HashMap<>();

    private PromiseCounts() {}

    private static final class Net {
        final Map<String, Integer> owned = new HashMap<>();
        final Map<String, Integer> address = new HashMap<>();
    }

    private static Net net(UUID network, long gameTime) {
        if (gameTime != tick) { tick = gameTime; byNetwork.clear(); }
        return byNetwork.computeIfAbsent(network, PromiseCounts::build);
    }

    private static Net build(UUID network) {
        Net n = new Net();
        RequestPromiseQueue q = Create.LOGISTICS.getQueuedPromises(network);
        if (q != null)
            for (RequestPromise p : q.flatten(false))
                if (p instanceof ControllerPromise cp) {
                    if (cp.ownerKey != null && !cp.ownerKey.isBlank())
                        n.owned.merge(cp.ownerKey, 1, Integer::sum);
                    if (cp.targetAddress != null && !cp.targetAddress.isBlank())
                        n.address.merge(cp.targetAddress, 1, Integer::sum);
                }
        return n;
    }

    /** Active promises this tick minted by the gauge {@code ownerKey} on {@code network}. */
    public static int owned(UUID network, String ownerKey, long gameTime) {
        return ownerKey == null || ownerKey.isBlank() ? 0 : net(network, gameTime).owned.getOrDefault(ownerKey, 0);
    }

    /** Active promises this tick targeting {@code address} on {@code network}, across every gauge/controller. */
    public static int address(UUID network, String address, long gameTime) {
        return address == null || address.isBlank() ? 0 : net(network, gameTime).address.getOrDefault(address, 0);
    }

    /** Fold a promise dispatched this tick into the cache, if it's already built — so a later gate read in the same
     *  tick counts it. If not yet built, the next build reads it straight from the queue. */
    public static void onAdded(UUID network, String ownerKey, String address, long gameTime) {
        if (gameTime != tick) return;
        Net n = byNetwork.get(network);
        if (n == null) return;
        if (ownerKey != null && !ownerKey.isBlank()) n.owned.merge(ownerKey, 1, Integer::sum);
        if (address != null && !address.isBlank()) n.address.merge(address, 1, Integer::sum);
    }
}
