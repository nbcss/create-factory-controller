package io.github.nbcss.createfactorycontroller.content.production;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.item.ProductionPatternItem;
import io.github.nbcss.createfactorycontroller.content.packet.OrderNotificationPacket;
import io.github.nbcss.createfactorycontroller.content.packet.OrderNotificationPacket.RequestedItem;
import net.minecraft.server.level.ServerPlayer;
import io.github.nbcss.createfactorycontroller.content.item.ProductionTarget;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrder.Task;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import com.google.common.collect.Multimap;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-global store + driver of all open {@link ProductionOrder}s (one {@link SavedData}, kept on the overworld
 * since logistics networks are server-wide). Each tick it: contributes external demand to passive gauges (read
 * by the gauges themselves via {@link #externalDemand}), monitors each active request's produced item against
 * network stock, ships a package once stock is available, and removes completed orders. A request whose gauge
 * is verifiably gone (its controller is loaded but the gauge id is no longer live) is orphaned.
 */
public class ProductionOrderManager extends SavedData {

    private static final String FILE_ID = "createfactorycontroller_production_orders";
    /** Re-check / dispatch throttle, matching the spirit of the gauge's request timer. */
    private static final int DISPATCH_INTERVAL_TICKS = 20;
    /** How long a fully-finished order lingers in the GUI before it is removed (5s). */
    private static final int LINGER_TICKS = 100;

    /** How long a pending notification subscription survives before it's pruned (the subscribe packet arrives just
     *  before the order packet, so this only needs to cover packet-processing latency). */
    private static final int PENDING_SUB_TTL_TICKS = 100;

    /** Orders keyed by their shared orderId. */
    private final Map<Integer, ProductionOrder> orders = new LinkedHashMap<>();
    /** Game time at which an order first became all-terminal — transient, drives the {@link #LINGER_TICKS} grace. */
    private final Map<Integer, Long> completedAt = new java.util.HashMap<>();
    private final Map<OrderKey, PendingSub> pendingSubs = new java.util.HashMap<>();

    private record OrderKey(UUID network, String address) {}
    private static final class PendingSub {
        final java.util.Set<UUID> players = new java.util.LinkedHashSet<>();
        long expiry;
    }

    public ProductionOrderManager() {}

    /** Drives the manager once per server tick, independent of any loaded controller/keeper. */
    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> get(event.getServer()).tick(event.getServer()));
    }

    // ── Access ────────────────────────────────────────────────────────────────

    public static SavedData.Factory<ProductionOrderManager> factory() {
        return new SavedData.Factory<>(ProductionOrderManager::new, ProductionOrderManager::load, null);
    }

    public static ProductionOrderManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(factory(), FILE_ID);
    }

    /** Convenience for code holding a server {@link Level}; returns null on the client. */
    public static ProductionOrderManager get(Level level) {
        return level instanceof ServerLevel sl ? get(sl.getServer()) : null;
    }

    // ── Mutation ────────────────────────────────────────────────────────────────

    public void addOrder(ProductionOrder order) {
        orders.put(order.orderId(), order);
        setDirty();
    }

    /** Registers {@code player} to be toasted notification */
    public void subscribe(UUID network, String address, UUID player, long now) {
        PendingSub p = pendingSubs.computeIfAbsent(new OrderKey(network, address), k -> new PendingSub());
        p.players.add(player);
        p.expiry = now + PENDING_SUB_TTL_TICKS;
    }

    /** Takes (and clears) the pending subscribers for ({@code network}, {@code address}) */
    private java.util.Set<UUID> consumePendingSubscribers(UUID network, String address) {
        PendingSub p = pendingSubs.remove(new OrderKey(network, address));
        return p == null ? new java.util.LinkedHashSet<>() : p.players;
    }

    public List<ProductionOrder> getOrdersForNetwork(UUID network) {
        List<ProductionOrder> out = new ArrayList<>();
        for (ProductionOrder o : orders.values())
            if (o.network().equals(network)) out.add(o);
        return out;
    }

    /** Client-facing snapshots of all open orders on {@code network} (for the monitoring tab). {@code now} is the
     *  current server game time, used to fill each order's age for the mm:ss timer. */
    public List<ProductionOrderView> viewsForNetwork(UUID network, long now) {
        List<ProductionOrderView> out = new ArrayList<>();
        for (ProductionOrder o : getOrdersForNetwork(network)) {
            List<ProductionOrderView.RequestView> rs = new ArrayList<>();
            for (Task r : o.tasks()) {
                int stock = networkStockOf(r.network, r.item);
                Task.State display = r.state;
                if (display != Task.State.SENT && display != Task.State.INVALID_PATTERN) {
                    if (stock >= r.amount || networkPromisedOf(r.network, r.item) > 0)
                        display = Task.State.PROCESSING;
                    else
                        display = Task.State.WAITING;
                }
                rs.add(new ProductionOrderView.RequestView(r.item.copy(), r.amount, stock, display.ordinal()));
            }
            // A finished order's timer freezes at the moment it completed; an open one keeps counting.
            long endTime = o.isComplete() ? completedAt.getOrDefault(o.orderId(), now) : now;
            int age = (int) Math.max(0, endTime - o.createdGameTime());
            out.add(new ProductionOrderView(o.orderId(), o.address(), age, rs));
        }
        return out;
    }

    /** Marks every active task targeting {@code patternId} on {@code network} as {@link Task.State#INVALID_PATTERN}.
     *  For when a gauge is removed or stops being orderable. */
    public void invalidateTasksFor(MinecraftServer server, UUID network, UUID patternId) {
        boolean changed = false;
        List<ProductionOrder> affected = new ArrayList<>();
        for (ProductionOrder o : orders.values())
            if (o.network().equals(network))
                for (Task t : o.tasks())
                    if (t.isActive() && patternId.equals(t.patternId)) {
                        t.state = Task.State.INVALID_PATTERN;
                        changed = true;
                        if (!o.subscribers().isEmpty() && !affected.contains(o)) affected.add(o);   // once per order
                    }
        if (changed) setDirty();
        for (ProductionOrder o : affected) notifyGaugeInvalid(server, o);
    }

    /** Static convenience for controllers (server side only). */
    public static void invalidateTasksFor(Level level, UUID network, UUID patternId) {
        if (!(level instanceof ServerLevel sl)) return;
        ProductionOrderManager m = get(sl.getServer());
        if (m != null) m.invalidateTasksFor(sl.getServer(), network, patternId);
    }

    /**
     * Player-initiated cancel
     */
    public void removeOrder(int orderId) {
        boolean removed = orders.remove(orderId) != null;
        completedAt.remove(orderId);
        if (removed) setDirty();
    }

    /** Allocates an orderId not currently in use by any open promise order. */
    public int freshOrderId() {
        int id;
        java.util.Random rng = new java.util.Random();
        do { id = rng.nextInt(); } while (id == 0 || orders.containsKey(id));
        return id;
    }

    // ── Order interception (item 5) ──────────────────────────────────────────

    /** Outcome of {@link #interceptProductionOrder} */
    public enum InterceptResult {
        /** No blueprint in the order */
        NOT_A_PRODUCTION_ORDER,
        /** Blueprints present and a {@link ProductionOrder} was created */
        HANDLED,
        /** Blueprints present but at least one was invalid/stale */
        REJECTED
    }

    /**
     * If {@code order} contains any Production Blueprints, registers a {@link ProductionOrder}
     */
    public static InterceptResult interceptProductionOrder(MinecraftServer server, UUID network, RequestType type,
                                                           PackageOrderWithCrafts order, String address) {
        List<BigItemStack> inStock = new ArrayList<>();
        List<BigItemStack> patterns = new ArrayList<>();
        for (BigItemStack b : order.orderedStacks().stacks()) {
            if (ProductionPatternItem.isPattern(b.stack)) patterns.add(b);
            else inStock.add(b);
        }
        if (patterns.isEmpty()) return InterceptResult.NOT_A_PRODUCTION_ORDER;

        long now = server.overworld().getGameTime();
        for (BigItemStack b : patterns) {
            ProductionTarget t = ProductionPatternItem.getTarget(b.stack);
            if (t == null || b.count <= 0) return InterceptResult.REJECTED;
            if (OrderableGaugeRegistry.locate(t.network(), t.patternId(), now) == null) return InterceptResult.REJECTED;
        }

        ProductionOrderManager mgr = get(server);
        int orderId = mgr.freshOrderId();
        List<Task> produced = new ArrayList<>();
        int linkIndex = inStock.isEmpty() ? 0 : 1;
        for (BigItemStack b : patterns) {
            ProductionTarget t = ProductionPatternItem.getTarget(b.stack);
            // A fluid pattern is ordered in buckets (B), which is 1000mb
            int amount = FluidCompat.isFluidFilter(t.display()) ? b.count * 1000 : b.count;
            produced.add(new Task(t.network(), t.patternId(),
                t.display().copy(), amount, address, orderId, linkIndex++, false));
        }
        produced.get(produced.size() - 1).finalLink = true;

        List<Task> tasks = new ArrayList<>(produced);

        for (BigItemStack b : inStock)
            if (b.count > 0)
                tasks.add(Task.completed(network, b.stack.copy(), b.count, address, orderId, 0, false));

        // Subscribers to toast on completion
        java.util.Set<UUID> subscribers = mgr.consumePendingSubscribers(network, address);
        ServerPlayer orderer = OrderNotificationCapture.get();
        if (orderer != null) subscribers.add(orderer.getUUID());
        mgr.addOrder(new ProductionOrder(orderId, network, address, now, subscribers, tasks));

        if (!inStock.isEmpty()) {
            PackageOrderWithCrafts realOrder = new PackageOrderWithCrafts(new PackageOrder(inStock), order.orderedCrafts());
            dispatchWithOrderId(network, type, realOrder, address, orderId, 0, false);
        }
        return InterceptResult.HANDLED;
    }

    // ── Notifications (subscribed players only, online only) ────────────────────

    /** Toasts the order's subscribers (those online) that production finished. */
    private static void notifyOrderComplete(MinecraftServer server, ProductionOrder order) {
        OrderNotificationPacket packet =
            new OrderNotificationPacket(OrderNotificationPacket.KIND_ORDER_COMPLETE, requestedItems(order), order.address());
        for (ServerPlayer player : onlineSubscribers(server, order))
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
    }

    /** Toasts the order's subscribers (those online) that a gauge their order needs went invalid. */
    private static void notifyGaugeInvalid(MinecraftServer server, ProductionOrder order) {
        OrderNotificationPacket packet =
            new OrderNotificationPacket(OrderNotificationPacket.KIND_GAUGE_INVALID, requestedItems(order), order.address());
        for (ServerPlayer player : onlineSubscribers(server, order))
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, packet);
    }

    /** The order's subscriber {@link ServerPlayer}s who are currently online (offline ones are skipped). */
    private static List<ServerPlayer> onlineSubscribers(MinecraftServer server, ProductionOrder order) {
        List<ServerPlayer> out = new ArrayList<>();
        if (server == null) return out;
        for (UUID id : order.subscribers()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) out.add(player);
        }
        return out;
    }

    /** Every item in an order, paired with the amount originally requested. */
    private static List<RequestedItem> requestedItems(ProductionOrder order) {
        List<RequestedItem> items = new ArrayList<>();
        for (Task task : order.tasks())
            if (!task.item.isEmpty()) items.add(new RequestedItem(task.item.copy(), task.amount));
        return items;
    }

    // ── Demand ──────────────────────────────────────────────────────

    /** Total still-to-produce amount (item count / mB) that active requests demand of one gauge. */
    public int externalDemand(UUID network, UUID patternId) {
        int sum = 0;
        for (ProductionOrder o : orders.values())
            if (o.network().equals(network))
                for (Task r : o.tasks())
                    if (r.isActive() && patternId.equals(r.patternId)) sum += r.amount;
        return sum;
    }

    /** Static convenience for the gauge tick (server side only). */
    public static int externalDemand(Level level, UUID network, UUID patternId) {
        ProductionOrderManager m = get(level);
        return m == null ? 0 : m.externalDemand(network, patternId);
    }

    // ── Tick ──────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();   // single server clock for heartbeat freshness
        OrderableGaugeRegistry.pruneStale(now);         // evict dead controllers even when no orders are open
        pendingSubs.values().removeIf(p -> now > p.expiry);   // drop unclaimed notification subscriptions
        if (orders.isEmpty()) return;
        boolean dirty = false;
        List<Integer> completed = new ArrayList<>();

        for (ProductionOrder order : orders.values()) {
            for (Task req : order.tasks()) {
                if (req.state == Task.State.SENT) continue;

                if (req.timer > 0) {
                    req.timer--;
                    continue;
                }
                req.timer = DISPATCH_INTERVAL_TICKS;

                int stock = networkStockOf(req.network, req.item);
                if (stock < req.amount) continue;

                if (dispatch(req)) {
                    req.state = Task.State.SENT;
                    dirty = true;
                }
            }
            // Linger: once every task is terminal, keep the entry visible for LINGER_TICKS before removing it.
            if (order.isComplete()) {
                boolean firstComplete = !completedAt.containsKey(order.orderId());
                long since = completedAt.computeIfAbsent(order.orderId(), id -> now);
                if (firstComplete) notifyOrderComplete(server, order);
                if (now - since >= LINGER_TICKS) completed.add(order.orderId());
            } else if (completedAt.remove(order.orderId()) != null) {
                dirty = true;
            }
        }

        for (int id : completed) { orders.remove(id); completedAt.remove(id); }
        if (dirty || !completed.isEmpty()) setDirty();
    }

    /** Total amount of {@code stack} currently promised on {@code network} */
    private static int networkPromisedOf(UUID network, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (FluidCompat.isFluidFilter(stack))
            return FluidCompat.fluidPromised(network, stack, -1);
        RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(network);
        return promises == null ? 0 : promises.getTotalPromisedAndRemoveExpired(stack, -1);
    }

    private static int networkStockOf(UUID network, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        return FluidCompat.isFluidFilter(stack)
            ? FluidCompat.fluidStock(network, stack)
            : LogisticsManager.getSummaryOfNetwork(network, true).getCountOf(stack);
    }

    /** Ships {@code req}'s produced item to its address as its assigned link of the shared order (item 9). */
    private boolean dispatch(Task req) {
        if (FluidCompat.isFluidFilter(req.item))
            return FluidCompat.dispatchFluid(req.network, req.item, req.amount, req.address,
                    req.orderId, req.linkIndex, req.finalLink);
        PackageOrderWithCrafts order =
            PackageOrderWithCrafts.simple(List.of(new BigItemStack(req.item.copy(), req.amount)));
        return dispatchWithOrderId(req.network, RequestType.RESTOCK, order, req.address, req.orderId,
            req.linkIndex, req.finalLink);
    }

    /**
     * Ships {@code order} to {@code address} as one <b>link</b> of a shared multi-link order so a Re-Packager merges
     * it with the order's other links. Every produced package is stamped with the shared {@code orderId}
     * and the given {@code linkIndex}; only the last-arriving link sets {@code finalLink}. Create's repackager
     * (`PackageRepackageHelper.isOrderComplete`) walks links 0..final and won't release the order until <em>every</em>
     * link is present — so a non-final instant package waits in the repackager for the later promise packages.
     */
    public static boolean dispatchWithOrderId(UUID network,
                                              RequestType type,
                                              PackageOrderWithCrafts order,
                                              String address,
                                              int orderId, int linkIndex, boolean finalLink) {
        if (order.isEmpty()) return false;
        Multimap<PackagerBlockEntity, PackagingRequest> found =
            LogisticsManager.findPackagersForRequest(network, order, null, address);
        if (found.isEmpty()) return false;
        for (var packager : found.keySet())
            if (packager.isTooBusyFor(type)) return false;

        // One shared final-link flag for this dispatch's link.
        MutableBoolean finalFlag = new MutableBoolean(finalLink);
        Multimap<PackagerBlockEntity, PackagingRequest> stamped =
            com.google.common.collect.HashMultimap.create();
        for (var e : found.entries()) {
            var o = e.getValue();
            stamped.put(e.getKey(), PackagingRequest.create(
                o.item(), o.getCount(), o.address(), linkIndex, finalFlag,
                o.packageCounter().intValue(), orderId, o.context()));
        }
        LogisticsManager.performPackageRequests(stamped);
        return true;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag,
                                     HolderLookup.@NotNull Provider registries) {
        ListTag list = new ListTag();
        for (ProductionOrder o : orders.values()) list.add(o.save(registries));
        tag.put("Orders", list);
        return tag;
    }

    public static ProductionOrderManager load(CompoundTag tag, HolderLookup.Provider registries) {
        ProductionOrderManager m = new ProductionOrderManager();
        ListTag list = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ProductionOrder o = ProductionOrder.load(list.getCompound(i), registries);
            m.orders.put(o.orderId(), o);
        }
        return m;
    }
}
