package io.github.nbcss.content.factorycontroller.production;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-runtime index of the orderable gauges (passive + allow-order + configured), grouped per owning Factory
 * Controller, used to show Production Blueprints in Stock Keepers (via {@link #patternFor}).
 *
 * <p>Freshness is heartbeat-based: each loaded controller re-publishes its orderable gauges every ~20 ticks via
 * {@link #heartbeat}, stamping {@code lastSeen}. A controller's entries count only while its heartbeat is within
 * {@link #TTL_TICKS}; stale ones are pruned ({@link #pruneStale}). This is self-healing — an unloaded controller
 * stops heartbeating and its gauges drop out, and a reload refreshes them — so no explicit chunk load/unload
 * wiring or position lookups are needed.</p>
 */
public final class OrderableGaugeRegistry {

    /** A controller heartbeats every 20 ticks; entries older than this are treated as unloaded (2-beat grace). */
    public static final long TTL_TICKS = 40;

    /** One orderable gauge: its network, stable id, and produced (display) item. */
    public record Entry(UUID network, UUID patternId, ItemStack display) {}

    private record Bucket(List<Entry> entries, long lastSeen) {}

    private static final Map<String, Bucket> BY_CONTROLLER = new ConcurrentHashMap<>();

    private OrderableGaugeRegistry() {}

    private static String key(ResourceKey<Level> dim, BlockPos pos) {
        return dim.location() + "@" + pos.asLong();
    }

    /** Controller heartbeat: replaces this controller's orderable gauges and refreshes its freshness (empty = drop). */
    public static void heartbeat(ResourceKey<Level> dim, BlockPos pos, List<Entry> entries, long now) {
        if (entries.isEmpty()) BY_CONTROLLER.remove(key(dim, pos));
        else BY_CONTROLLER.put(key(dim, pos), new Bucket(List.copyOf(entries), now));
    }

    /** Promptly drop a controller's entries (on unload/break); the TTL prune is the fallback if this is missed. */
    public static void remove(ResourceKey<Level> dim, BlockPos pos) {
        BY_CONTROLLER.remove(key(dim, pos));
    }

    /** Evicts every controller whose heartbeat is stale (or whose timestamp is in the future, e.g. after a
     *  per-world game-time reset). Driven once per server tick by the order manager so dead controllers — including
     *  ones removed in ways that bypass {@link #remove} — never accumulate. */
    public static void pruneStale(long now) {
        BY_CONTROLLER.values().removeIf(b -> !fresh(b, now));
    }

    /** Drops everything — called on server stop so this static index never bleeds across worlds in one JVM. */
    public static void clear() {
        BY_CONTROLLER.clear();
    }

    private static boolean fresh(Bucket b, long now) {
        long age = now - b.lastSeen();
        // age < 0 means lastSeen is "in the future" (a different world's clock) → treat as stale, not fresh.
        return age >= 0 && age <= TTL_TICKS;
    }

    /** Orderable patterns on {@code network} whose controller was heard from within the TTL. */
    public static List<Entry> patternFor(UUID network, long now) {
        List<Entry> out = new ArrayList<>();
        for (Bucket b : BY_CONTROLLER.values())
            if (fresh(b, now))
                for (Entry e : b.entries())
                    if (e.network().equals(network)) out.add(e);
        return out;
    }
}
