package io.github.nbcss.createfactorycontroller.content.production;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** One orderable gauge: its network, stable id, produced (display) item, per-request ingredient list
     *  (deduped, one stack per distinct source item, count = per-request amount), and packager address. */
    public record Entry(UUID network, UUID gaugeId, ItemStack display, List<ItemStack> ingredients, String address) {}

    /** The dimension + position of the controller that owns a gauge (for resolving its live recipe graph). */
    public record Located(ResourceKey<Level> dim, BlockPos pos) {}

    private record Bucket(ResourceKey<Level> dim, BlockPos pos, List<Entry> entries, long lastSeen) {}

    private static final Map<String, Bucket> BY_CONTROLLER = new ConcurrentHashMap<>();
    /** Reverse index gaugeId → owning controller key, so {@link #locate} is O(1) instead of scanning every
     *  controller's entries. Kept in sync with {@link #BY_CONTROLLER} by the mutators below. */
    private static final Map<UUID, String> BY_GAUGE = new ConcurrentHashMap<>();

    private OrderableGaugeRegistry() {}

    private static String key(ResourceKey<Level> dim, BlockPos pos) {
        return dim.location() + "@" + pos.asLong();
    }

    /** Controller heartbeat: replaces this controller's orderable gauges and refreshes its freshness (empty = drop). */
    public static void heartbeat(ResourceKey<Level> dim, BlockPos pos, List<Entry> entries, long now) {
        String k = key(dim, pos);
        Bucket old = entries.isEmpty() ? BY_CONTROLLER.remove(k)
                                       : BY_CONTROLLER.put(k, new Bucket(dim, pos, List.copyOf(entries), now));
        // Reverse-index upkeep: drop gaugeIds this controller no longer publishes, (re)add current ones.
        Set<UUID> current = new HashSet<>();
        for (Entry e : entries) current.add(e.gaugeId());
        if (old != null)
            for (Entry e : old.entries())
                if (!current.contains(e.gaugeId())) BY_GAUGE.remove(e.gaugeId(), k);
        for (UUID id : current) BY_GAUGE.put(id, k);
    }

    /** Promptly drop a controller's entries (on unload/break); the TTL prune is the fallback if this is missed. */
    public static void remove(ResourceKey<Level> dim, BlockPos pos) {
        String k = key(dim, pos);
        Bucket b = BY_CONTROLLER.remove(k);
        if (b != null) for (Entry e : b.entries()) BY_GAUGE.remove(e.gaugeId(), k);
    }

    /** Evicts every controller whose heartbeat is stale (or whose timestamp is in the future, e.g. after a
     *  per-world game-time reset). Driven once per server tick by the order manager so dead controllers — including
     *  ones removed in ways that bypass {@link #remove} — never accumulate. */
    public static void pruneStale(long now) {
        BY_CONTROLLER.entrySet().removeIf(en -> {
            if (fresh(en.getValue(), now)) return false;
            for (Entry e : en.getValue().entries()) BY_GAUGE.remove(e.gaugeId(), en.getKey());
            return true;
        });
    }

    /** Drops everything — called on server stop so this static index never bleeds across worlds in one JVM. */
    public static void clear() {
        BY_CONTROLLER.clear();
        BY_GAUGE.clear();
    }

    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> clear());
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

    /** The loaded controller owning gauge {@code network}+{@code gaugeId}, or null if no fresh controller has it
     *  (unloaded / removed). O(1) via the reverse index, then a verify against the (small) bucket. */
    public static Located locate(UUID network, UUID gaugeId, long now) {
        String k = BY_GAUGE.get(gaugeId);
        if (k == null) return null;
        Bucket b = BY_CONTROLLER.get(k);
        if (b == null || !fresh(b, now)) return null;
        for (Entry e : b.entries())   // confirm the (possibly slightly stale) index still matches
            if (e.gaugeId().equals(gaugeId) && e.network().equals(network))
                return new Located(b.dim(), b.pos());
        return null;
    }
}
