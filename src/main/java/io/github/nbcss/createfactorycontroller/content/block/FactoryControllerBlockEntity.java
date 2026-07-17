package io.github.nbcss.createfactorycontroller.content.block;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.ServerConfig;
import io.github.nbcss.createfactorycontroller.content.*;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.*;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionGraph;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionResolver;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import io.github.nbcss.createfactorycontroller.content.helper.ControllerDataFixer;
import io.github.nbcss.createfactorycontroller.content.production.OrderableGaugeRegistry;
import io.github.nbcss.createfactorycontroller.content.production.PassiveDemandSolver;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrderManager;
import io.github.nbcss.createfactorycontroller.content.packet.SyncPanelStatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettings;
import io.github.nbcss.createfactorycontroller.content.network.NetworkSettingsStore;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FactoryControllerBlockEntity extends SmartBlockEntity implements MenuProvider, ComponentHolder {

    /** Cap on components a single controller may hold — bounds the BE/item NBT and sync size.
     *  Configurable via {@link ServerConfig#maxComponents()} (default 256, max 1024). */
    public static int maxComponents() {
        return ServerConfig.maxComponents();
    }
    /** Max characters of a gauge's packager address (clamped server-side, authoritative). */
    public static final int MAX_ADDRESS_LENGTH = 25;
    /** Max characters of the controller's custom display name (clamped server-side, authoritative). */
    public static final int MAX_NAME_LENGTH = 25;
    public static final int BOARD_LIMIT = 64;

    public static final int DATA_VERSION = 1;
    /** Version of the data most recently {@link #read}; {@code 0} = pre-versioning. For future migration logic. */

    /** Whether the given position lies on the finite ±{@link #BOARD_LIMIT}-cell board. */
    public static boolean isOutBoard(VirtualComponentPosition pos) {
        return Math.abs(pos.x()) > BOARD_LIMIT || Math.abs(pos.y()) > BOARD_LIMIT;
    }

    public final Map<VirtualComponentPosition, VirtualComponentBehaviour> components = new LinkedHashMap<>();
    /** Controller-level store of every connection on the board; components are live views into it (Phase 2). */
    private final ConnectionGraph connectionGraph = new ConnectionGraph();
    public ConnectionGraph connectionGraph() { return connectionGraph; }

    /** Sinks whose incoming signal value changed since the last drain (per connection type); folded once each by
     *  {@link #settleConnections}. Server-only — the client folds directly on sync. */
    private final Set<DirtySink> dirtySinks = new LinkedHashSet<>();
    private record DirtySink(VirtualComponentPosition pos, Connection.Type type) {}

    /** Flags {@code pos}'s sink-side state stale for {@code type}; it is re-folded at the next {@link #settleConnections}. */
    public void markSinkDirty(VirtualComponentPosition pos, Connection.Type type) {
        dirtySinks.add(new DirtySink(pos, type));
    }

    /**
     * Folds every sink flagged since the last drain, exactly once each. This coalesces a sink fed by several sources
     * that all changed in the same tick into a single fold (each source's {@code publish} has already written its edge
     * value), so there is no per-source double-fold and no mid-tick glitch. The loop lets a fold that cascades — a SEND
     * link folding fires its network transmit, which pushes a wired RECEIVE link, which publishes — settle in the same
     * pass; the iteration cap breaks any redstone feedback cycle (interim — full cycle handling is deferred). Called in
     * the controller tick and after each structural mutation, before its sync.
     */
    public void settleConnections() {
        int guard = components.size() + 1;
        while (!dirtySinks.isEmpty() && guard-- > 0) {
            List<DirtySink> batch = new ArrayList<>(dirtySinks);
            dirtySinks.clear();
            for (DirtySink d : batch) {
                VirtualComponentBehaviour c = components.get(d.pos());
                if (c != null) c.onInputChanged(d.type());
            }
        }
    }
    public final Set<UUID> networks = new LinkedHashSet<>();

    /** Player-assigned display name; blank means use the default translated block name. */
    public String customName = "";

    /** Set by {@link #sendData()}, flushed once per server tick so the heavy menu packet isn't sent
     *  multiple times in a tick (e.g. when several gauges change state in the same tick). */
    private boolean menuSyncQueued = false;

    /** Whether the controller block is currently receiving a redstone signal. While powered, every
     *  gauge stops issuing new requests (mirrors Create's factory-panel redstone gate). */
    private boolean redstonePowered = false;

    /** Set whenever an orderable gauge's state may have changed (and once after load); flushed in {@link #tick()}
     *  to promptly heartbeat this controller's entries to {@link OrderableGaugeRegistry} (needs a non-null level,
     *  not guaranteed during {@link #read}). A periodic heartbeat also runs in {@link #lazyTick()} every 20t. */
    private boolean orderableDirty = true;

    /** Used by BlockEntityType.Builder registration (2-arg supplier form). */
    public FactoryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(CreateFactoryController.FACTORY_CONTROLLER_BE.get(), pos, state);
        setLazyTickRate(20);
    }

    public FactoryControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide()) return;
        // Sequential components (logic tubes) commit the output they computed last tick, before any settle this tick —
        // this is what gives them their deterministic one-tick delay. No-op for combinational components.
        for (VirtualComponentBehaviour component : components.values())
            component.preTick();
        // Drain signal changes from preTick commits, network events, and the lazy tick (e.g. RECEIVE-link power) before
        // gauges run, so their request gate is fresh this tick.
        settleConnections();
        // Pre-pass: refresh passive demand from a single consistent (pre-tick) snapshot, so a multi-stage production
        // chain's demand all derives from the same state, before any gauge ticks/requests. (The redstone-link power +
        // gauge gate are refreshed on the lazy tick — see lazyTick — like Create's redstone links.)
        // With the total-demand strategy a single controller-wide topological solve sizes every passive gauge at once;
        // otherwise each gauge sizes itself to one recipe set per demanding consumer.
        if (ServerConfig.passiveTotalDemand()) {
            PassiveDemandSolver.solve(this);
        } else {
            for (VirtualComponentBehaviour component : components.values())
                if (component instanceof VirtualGaugeBehaviour gauge && gauge.requestMode.isPassive())
                    gauge.computeDemand();
        }

        for (VirtualComponentBehaviour component : components.values())
            component.tick();
        // Drain signal changes published during this tick (a gauge becoming satisfied → its SEND links, etc.).
        settleConnections();
        // Promptly heartbeat when an orderable gauge's state may have changed (the 20t lazyTick keeps it fresh).
        if (orderableDirty) {
            orderableDirty = false;
            heartbeatOrderableGauges();
        }
        // Flush at most one menu snapshot per tick (gauge ticks above may have queued several).
        if (menuSyncQueued) {
            menuSyncQueued = false;
            syncMenuToPlayers();
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level != null && !level.isClientSide()) {
            updateRedstonePower();
            heartbeatOrderableGauges();   // 20t heartbeat keeps this controller's gauges "live" in the registry

            // Redstone-link power + the gauge gate it drives refresh on the lazy tick (like Create's redstone links).
            // Links first, then gauges, so each gauge reads freshly-updated link power.
            for (VirtualComponentBehaviour component : components.values())
                if (component instanceof VirtualRedstoneLinkBehaviour link) {
                    link.addToNetwork();   // lazy (re)registration after load; no-op once registered
                    link.updatePower();
                }
        }
    }

    /** Called from {@link FactoryControllerBlock#neighborChanged} so redstone edges are picked up at once. */
    public void onNeighborChanged() {
        if (level != null && !level.isClientSide())
            updateRedstonePower();
    }

    /** Marks the orderable-gauge index stale; the next server tick heartbeats to {@link OrderableGaugeRegistry}. */
    public void markOrderableDirty() {
        orderableDirty = true;
    }

    /** Heartbeats this controller's orderable gauges (allow-order + configured)
     *  to {@link OrderableGaugeRegistry}, refreshing their freshness so their tasks stay live. */
    private void heartbeatOrderableGauges() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        long now = serverLevel.getServer().overworld().getGameTime();
        List<OrderableGaugeRegistry.Entry> entries = new ArrayList<>();
        for (VirtualComponentBehaviour c : components.values())
            if (c instanceof VirtualGaugeBehaviour g && g.gaugeId != null && isOrderable(g))
                entries.add(new OrderableGaugeRegistry.Entry(g.networkId, g.gaugeId, g.filter.copy(),
                    gaugeIngredients(g), g.recipeAddress));
        OrderableGaugeRegistry.heartbeat(level.dimension(), getBlockPos(), entries, now);
    }

    private static boolean isOrderable(VirtualGaugeBehaviour g) {
        return g.requestMode.allowsOrder() && !g.filter.isEmpty() && !g.recipeAddress.isBlank();
    }

    private List<ItemStack> gaugeIngredients(VirtualGaugeBehaviour g) {
        // Amounts are per request, so scale the per-craft connection amount by the crafter batch (as tickRequests does).
        int batch = g.effectiveBatch();
        List<ItemStack> ingredients = new ArrayList<>();
        for (Map.Entry<VirtualComponentPosition, Connection> e : g.targetedBy().entrySet()) {
            if (!(e.getValue() instanceof LogisticsConnection conn)) continue;
            if (!(components.get(e.getKey()) instanceof VirtualGaugeBehaviour source)) continue;
            ItemStack item = source.filter;
            if (item.isEmpty()) continue;
            int amount = conn.amount() * batch;
            boolean merged = false;
            for (ItemStack existing : ingredients)
                if (ItemStack.isSameItemSameComponents(existing, item)) {
                    existing.grow(amount);
                    merged = true;
                    break;
                }
            if (!merged) ingredients.add(item.copyWithCount(amount));
        }
        return ingredients;
    }

    private void invalidateGaugeTasks(VirtualGaugeBehaviour g) {
        if (level != null && !level.isClientSide() && g.gaugeId != null)
            ProductionOrderManager.invalidateTasksFor(level, g.networkId, g.gaugeId);
    }

    private void refreshGaugeIdentity(VirtualGaugeBehaviour g, boolean filterIdentityChanged) {
        if (g.filter.isEmpty()) {
            invalidateGaugeTasks(g);
            g.gaugeId = null;
            return;
        }
        if (filterIdentityChanged || g.gaugeId == null) {
            invalidateGaugeTasks(g);
            g.gaugeId = java.util.UUID.randomUUID();
        }
    }

    /** Ensures a filtered gauge has a stable server-side id, and invalidates open production tasks when it is no
     *  longer orderable. */
    private void updateGaugeOrderable(VirtualGaugeBehaviour g) {
        if (level == null || level.isClientSide()) return;
        refreshGaugeIdentity(g, false);
        if (!isOrderable(g)) invalidateGaugeTasks(g);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        // Called from setRemoved() on both block break and chunk unload — drop our entries promptly (the
        // registry's TTL is the fallback). On reload, the first tick/lazyTick heartbeats again.
        if (level != null && !level.isClientSide()) {
            OrderableGaugeRegistry.remove(level.dimension(), getBlockPos());
            // Unregister every link from Create's frequency network (re-registered lazily on reload's first tick).
            for (VirtualComponentBehaviour c : components.values())
                if (c instanceof VirtualRedstoneLinkBehaviour link) link.removeFromNetwork();
        }
    }

    private void updateRedstonePower() {
        assert level != null;
        boolean powered = level.hasNeighborSignal(getBlockPos());
        if (powered == redstonePowered) return;   // no edge → nothing to propagate
        redstonePowered = powered;
        // Every managed gauge froze its request throttle while paused; kick it so unpausing doesn't
        // resume from a stale countdown (mirrors VirtualGaugeBehaviour.onInputChanged for its own link).
        for (VirtualComponentBehaviour c : components.values())
            if (c instanceof VirtualGaugeBehaviour g) g.resetRequestTimer();
        sendData();   // push the new powered state to any open GUI
    }

    public boolean isRedstonePowered() {
        return redstonePowered;
    }

    // ── Gauge attach ───────────────────────────────────────────────────────

    public void attachComponent(VirtualComponentPosition pos, Player player, @Nullable UUID selectedNetwork) {
        ItemStack carried = player.containerMenu.getCarried();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(carried.getItem());

        if (!ComponentRegistry.contains(itemId)) return;

        if (components.containsKey(pos)) return;

        if (isOutBoard(pos)) return;   // outside the finite board

        // board full — bounds NBT/packet size
        if (components.size() >= maxComponents()) return;

        UUID networkId = null;
        if (ComponentRegistry.needsNetwork(itemId)) {
            if (LogisticallyLinkedBlockItem.isTuned(carried)) {
                networkId = LogisticallyLinkedBlockItem.networkFromStack(carried);
                if (networkId == null) return;
                networks.add(networkId);
            } else {
                if (selectedNetwork == null || !networks.contains(selectedNetwork)) {
                    return;
                }
                networkId = selectedNetwork;
            }
        }

        VirtualComponentBehaviour behaviour = ComponentRegistry.createFromItem(this, pos, carried.getItem(), networkId);
        if (behaviour == null) return;
        if (behaviour instanceof VirtualRedstoneLinkBehaviour link)
            link.addToNetwork();                          // join Create's frequency network
        components.put(pos, behaviour);

        if (!player.isCreative())
            carried.shrink(1);

        playBlockSound(carried.getItem(), true);
        markOrderableDirty();
        setChanged();
        sendData();
    }

    // ── Component remove ───────────────────────────────────────────────────────

    public void removeComponent(VirtualComponentPosition pos, Player player) {
        VirtualComponentBehaviour behaviour = components.remove(pos);
        if (behaviour == null) return;

        // The gauge is gone → abort any production tasks targeting it.
        if (behaviour instanceof VirtualGaugeBehaviour g && g.gaugeId != null && level != null)
            ProductionOrderManager.invalidateTasksFor(level, g.networkId, g.gaugeId);
        // A link is gone → leave Create's frequency network.
        if (behaviour instanceof VirtualRedstoneLinkBehaviour link)
            link.removeFromNetwork();

        behaviour.disconnectAll();

        // Return a component item
        ItemStack refund = new ItemStack(behaviour.getItem());
        if (!player.isCreative() && !player.getInventory().add(refund))
            player.drop(refund, false);

        pruneEmptyNetworks();   // removing the last gauge on a network auto-forgets that network

        playBlockSound(refund.getItem(), false);
        markOrderableDirty();
        setChanged();
        sendData();
    }

    private void pruneEmptyNetworks() {
        // Forget networks this controller no longer uses.
        networks.removeIf(net -> !isNetworkInUse(net));
    }

    private boolean isNetworkInUse(UUID network) {
        for (VirtualComponentBehaviour c : components.values())
            if (c instanceof VirtualGaugeBehaviour g && network.equals(g.networkId))
                return true;
        return false;
    }

    // ── Component relocate ─────────────────────────────────────────────────────

    public void moveComponent(VirtualComponentPosition from, VirtualComponentPosition to) {
        if (from.equals(to)) return;
        VirtualComponentBehaviour behaviour = components.get(from);
        if (behaviour == null) return;
        if (isOutBoard(to)) return;     // can't relocate off the finite board
        if (components.containsKey(to)) {   // destination occupied → aborted relocate
            playDenySound();
            return;
        }

        components.remove(from);
        connectionGraph.rename(from, to);   // re-key every wire touching `from` (both indexes + payload endpoints)
        behaviour.setPosition(to);
        components.put(to, behaviour);
        // CUSTOM arrangements reference sources by position — follow the rename or their cells go stale.
        for (VirtualComponentBehaviour c : components.values())
            if (c instanceof VirtualGaugeBehaviour g) g.remapRecipeSlots(p -> p.equals(from) ? to : p);

        playSound(SoundEvents.COPPER_BREAK, 1f, 1f);
        markOrderableDirty();   // controllerPos is unchanged, but republish keeps the cached locator fresh
        setChanged();
        sendData();
    }

    /**
     * Batch-relocates {@code sources} by a uniform cell delta {@code (dx, dy)} — the Selection-Mode relocate.
     * <b>Atomic</b>: commits only if EVERY moving component lands on an in-board cell that is either empty or vacated
     * by another moving component; otherwise nothing moves (deny blip). Connection references to any moved cell are
     * remapped through {@code f(p) = moving ? p+delta : p}, so wires survive whether one or both endpoints moved.
     */
    public void moveComponents(List<VirtualComponentPosition> sources, int dx, int dy) {
        if (dx == 0 && dy == 0) return;

        // Live moving set (skip stale positions sent by the client).
        Set<VirtualComponentPosition> moving = new LinkedHashSet<>();
        for (VirtualComponentPosition p : sources)
            if (components.containsKey(p)) moving.add(p);
        if (moving.isEmpty()) return;

        // Validate ALL destinations first (atomic): in-board, and not occupied by a NON-moving component.
        for (VirtualComponentPosition p : moving) {
            VirtualComponentPosition to = new VirtualComponentPosition(p.x() + dx, p.y() + dy);
            if (isOutBoard(to) || (components.containsKey(to) && !moving.contains(to))) {
                playDenySound();
                return;
            }
        }

        java.util.function.Function<VirtualComponentPosition, VirtualComponentPosition> remap = p ->
            moving.contains(p) ? new VirtualComponentPosition(p.x() + dx, p.y() + dy) : p;

        // Rewire every connection reference (owner/source keys + payload endpoints) in one atomic pass.
        connectionGraph.remap(remap);
        // CUSTOM arrangements reference sources by position — follow the remap or their cells go stale.
        for (VirtualComponentBehaviour c : components.values())
            if (c instanceof VirtualGaugeBehaviour g) g.remapRecipeSlots(remap);

        // Move the components themselves: re-key the map and update each stored position.
        List<VirtualComponentBehaviour> movers = new ArrayList<>();
        for (VirtualComponentPosition p : moving) movers.add(components.remove(p));
        for (VirtualComponentBehaviour mover : movers) {
            VirtualComponentPosition to = new VirtualComponentPosition(mover.position().x() + dx, mover.position().y() + dy);
            mover.setPosition(to);
            components.put(to, mover);
        }

        playSound(SoundEvents.COPPER_BREAK, 1f, 1f);
        markOrderableDirty();
        setChanged();
        sendData();
    }

    // ── Configure panel ────────────────────────────────────────────────────

    public void setComponentItem(VirtualComponentPosition pos, ItemStack filter, boolean ignoreData) {
        if (!(components.get(pos) instanceof VirtualGaugeBehaviour gauge)) return;
        if (!gauge.filterResolver().acceptsFilter(filter)) return;
        boolean fluid = FluidCompat.isFluidFilter(filter);
        ItemStack oldFilter = gauge.filter.copy();
        // Ignore-data never applies to a fluid filter (the set-item screen hides the toggle there).
        gauge.ignoreData = ignoreData && !fluid;
        // With ignore-data on, store the filter as a "pure" item (a fresh stack with no NBT/components) so
        // its identity is the item type alone — matching the type-only stock/promise/ingredient matching.
        gauge.filter = gauge.ignoreData ? new ItemStack(filter.getItem()) : filter.copy();
        refreshGaugeIdentity(gauge, !ItemStack.isSameItemSameComponents(oldFilter, gauge.filter));
        if (fluid && !gauge.unit.isFluid())
            gauge.unit = ThresholdUnit.FLUID_BUCKET;
        else if (!fluid && gauge.unit.isFluid())
            gauge.unit = ThresholdUnit.ITEMS;
        updateGaugeOrderable(gauge);   // filter change can make it (in)eligible
        gauge.publishRedstoneOutput();
        settleConnections();   // fold any SEND links this gauge's (in)activation just flagged
        markOrderableDirty();   // filter (the blueprint's display item) changed
        setChanged();
        sendData();
    }

    /**
     * Applies a recipe-config edit from {@code ConfigureRecipeScreen}. {@code reset} wipes the gauge's
     * whole recipe config (filter, threshold, address, output, promise interval, connections);
     * otherwise the address / output-per-craft / promise interval and per-connection ingredient
     * amounts are updated.
     */
    /** Sets a gauge's request interval (ticks; 0 = clear the override) and restarts its countdown at the new value,
     *  so the recipe screen's scroll/reset takes effect immediately rather than at the next confirm. */
    public void setGaugeRequestInterval(VirtualComponentPosition pos, int customRequestTimer) {
        if (!(components.get(pos) instanceof VirtualGaugeBehaviour gauge)) return;
        gauge.customRequestTimer = customRequestTimer <= 0 ? 0 : Math.clamp(customRequestTimer, 20, 1200);
        gauge.restartRequestTimer();
        setChanged();
        sendData();
    }

    public void configureRecipe(VirtualComponentPosition pos, String address, int recipeOutput, int craftBatch,
                                int maxRequestMultiplier, int customRequestTimer,
                                int craftDimension, int promiseInterval, int promiseLimit,
                                boolean promiseLimitByAddress, int count, ThresholdUnit mode,
                                RequestMode requestMode, GaugeWorkMode workMode,
                                Map<VirtualComponentPosition, Integer> inputAmounts,
                                List<ItemStack> craftingArrangement, List<RecipeSlot> recipeSlots,
                                boolean clearPromises, boolean reset) {
        if (!(components.get(pos) instanceof VirtualGaugeBehaviour gauge)) return;

        if (reset) {
            gauge.filter = ItemStack.EMPTY;
            gauge.count = 0;
            gauge.unit = ThresholdUnit.ITEMS;
            gauge.requestMode = RequestMode.NORMAL;
            gauge.recipeAddress = "";
            gauge.recipeOutput = 1;
            gauge.craftBatch = 1;
            gauge.maxRequestMultiplier = 1;
            gauge.customRequestTimer = 0;
            gauge.craftDimension = 0;
            gauge.promiseClearingInterval = -1;
            gauge.promiseLimit = 0;
            gauge.promiseLimitByAddress = false;
            gauge.activeCraftingArrangement = new ArrayList<>();
            gauge.recipeSlots = new ArrayList<>();
            gauge.mode = GaugeWorkMode.REGULAR;
            gauge.disconnectAll();
            refreshGaugeIdentity(gauge, true);
            updateGaugeOrderable(gauge);   // no longer orderable → abort tasks
            markOrderableDirty();
            setChanged();
            sendData();
            return;
        }

        gauge.recipeAddress = address.length() > MAX_ADDRESS_LENGTH
            ? address.substring(0, MAX_ADDRESS_LENGTH) : address;
        // Item output is capped at MAX(64, 9 stacks of the produced item); fluids keep their own (client) cap.
        int outputCap = FluidCompat.isFluidFilter(gauge.filter) ? Integer.MAX_VALUE
            : Math.max(64, 9 * Math.max(1, gauge.filter.isEmpty() ? 64 : gauge.filter.getMaxStackSize()));
        gauge.recipeOutput = Math.clamp(recipeOutput, 1, outputCap);
        gauge.craftBatch = Math.max(1, craftBatch);
        gauge.customRequestTimer = customRequestTimer <= 0 ? 0 : Math.clamp(customRequestTimer, 20, 1200);
        gauge.craftDimension = Math.max(0, craftDimension);
        gauge.promiseClearingInterval = Math.clamp(promiseInterval, -1, 31);
        gauge.promiseLimit = Math.clamp(promiseLimit, 0, 999);
        gauge.promiseLimitByAddress = promiseLimitByAddress;
        gauge.unit = mode;
        gauge.requestMode = requestMode;
        if (!requestMode.isPassive()) gauge.count = Math.max(0, count);
        gauge.activeCraftingArrangement = new ArrayList<>(craftingArrangement);
        gauge.mode = workMode;
        gauge.recipeSlots = workMode == GaugeWorkMode.CUSTOM
            ? new ArrayList<>(recipeSlots.subList(0, Math.min(recipeSlots.size(), VirtualGaugeBehaviour.MAX_INGREDIENTS)))
            : new ArrayList<>();
        // Apply per-input amounts only if they keep the ingredient grid within its slot cap. A larger amount can take
        // several grid slots (ceil(amount / stackSize)), so an unchecked edit could overflow the cap the connection
        // create-time check enforces one wire at a time. Reject the whole amount set on overflow (other config stands).
        if (gauge.projectedInputSlots(inputAmounts) <= VirtualGaugeBehaviour.MAX_INGREDIENTS)
            for (Map.Entry<VirtualComponentPosition, Integer> e : inputAmounts.entrySet())
                if (gauge.targetedBy().get(e.getKey()) instanceof LogisticsConnection conn)
                    conn.amount = Math.max(1, e.getValue());
        // Clamp last — the structural cap must see the just-applied mode, amounts, output, and batch.
        gauge.maxRequestMultiplier = gauge.clampMultiplierToStructure(maxRequestMultiplier);
        if (clearPromises) gauge.requestClearPromises();
        updateGaugeOrderable(gauge);   // mode/address change can make it (in)eligible
        gauge.publishRedstoneOutput();
        settleConnections();   // fold any SEND links this gauge's (in)activation just flagged
        markOrderableDirty();   // request mode / filter / address may have changed
        setChanged();
        sendData();
    }

    @Override
    public VirtualComponentBehaviour componentAt(VirtualComponentPosition pos) {
        return components.get(pos);
    }

    // ── Connections ────────────────────────────────────────────────────────

    public void addConnection(String typeName, VirtualComponentPosition sourcePos, VirtualComponentPosition sinkPos,
                              int arrowBendMode) {
        Connection.Type type = Connection.Type.get(typeName);
        VirtualComponentBehaviour source = components.get(sourcePos);
        VirtualComponentBehaviour sink = components.get(sinkPos);
        if (type == null || source == null || sink == null || sourcePos.equals(sinkPos)) {
            playDenySound();
            return;
        }
        // The client resolved type/source/sink; the server only validates that exact setup is still legal.
        if (!ConnectionResolver.validate(type, source, sink).isSuccess()) {
            playDenySound();
            return;
        }
        Connection conn = type.create(source, sink);
        conn.arrowBendMode = arrowBendMode < 0 ? -1 : arrowBendMode % 4;   // client-chosen preview bend (or -1 = auto)
        connectionGraph.add(conn);
        if (sink instanceof VirtualGaugeBehaviour g) g.reconcileRecipeSlots();   // CUSTOM: new wire → first empty cell
        source.publish(type);          // write the new edge's value + flag the sink (new edge enters at fold identity)
        settleConnections();           // fold the sink once
        playSound(SoundEvents.AMETHYST_BLOCK_PLACE, 0.5f, 0.5f);
        setChanged();
        sendData();
    }

    /** Reconciles each sink's reactive state from the edge values after load/sync. Edges are authoritative — they carry
     *  their own value in NBT — and propagation is data-driven (no callbacks to rebind), so we only re-fold every sink.
     *  Sources need no bind action: their edges arrive correct, and a source self-heals them on its next tick if its
     *  output has since changed. (Folds directly, not via the dirty set — this is a one-off reconcile, not a tick.) */
    private void bindConnectionHooks() {
        for (Connection conn : connectionGraph.connections()) {
            VirtualComponentBehaviour sink = components.get(conn.to);
            if (sink != null) sink.onInputChanged(conn.type);
        }
    }

    public void removeConnection(VirtualComponentPosition from, VirtualComponentPosition to) {
        Connection conn = connectionGraph.get(from, to);
        if (conn == null) return;
        connectionGraph.remove(to, from);
        if (components.get(to) instanceof VirtualGaugeBehaviour g) g.reconcileRecipeSlots();   // CUSTOM: clear its cells
        markSinkDirty(to, conn.type);   // re-fold without the removed edge (the source is unaffected)
        settleConnections();
        setChanged();
        sendData();
    }

    /** Cycles one specific wire's arrow-bend mode through the four fixed bends (0 → 1 → 2 → 3 → 0; auto excluded, and
     *  exited on the first press). Purely visual, so (like {@link #cycleArrowMode}) it only re-syncs — no re-fold/settle. */
    public void cycleConnectionArrowMode(VirtualComponentPosition from, VirtualComponentPosition to) {
        Connection conn = connectionGraph.get(from, to);
        if (conn == null) return;
        conn.arrowBendMode = (conn.arrowBendMode + 1) % 4;   // auto (-1) → 0 on the first press
        setChanged();
        sendData();
    }

    /** Swaps the direction of the wire {@code from → to} (→ {@code to → from}), if the reversed orientation is legal
     *  (rejected for a redstone link, whose role fixes direction) and doesn't collide with an existing {@code to → from}
     *  edge. {@code connectionGraph.reverse} keeps the rendered path shape stable (only the arrow flips). */
    public void reverseConnection(VirtualComponentPosition from, VirtualComponentPosition to) {
        Connection conn = connectionGraph.get(from, to);
        if (conn == null) return;
        if (!conn.canReverse(this)) {   // one authority (shared with the UI): kind is reversible + reversed edge is legal
            playDenySound();
            return;
        }
        connectionGraph.reverse(conn);
        // Re-evaluate both endpoints in their new roles (publish output + re-fold input), then settle once.
        for (VirtualComponentPosition p : List.of(from, to)) {
            VirtualComponentBehaviour c = components.get(p);
            if (c != null) { c.publish(conn.type); markSinkDirty(p, conn.type); }
        }
        settleConnections();
        playSound(SoundEvents.AMETHYST_BLOCK_PLACE, 0.5f, 0.5f);
        setChanged();
        sendData();
    }

    /** Disconnects every NON-logistics wire touching the gauge at {@code gaugePos} (the "link reset" slot): redstone
     *  links and logic tubes, in either direction. Leaves ingredient (logistics) wires intact. Each removal re-folds
     *  the surviving sink. Does not touch the gauge's recipe config. */
    public void disconnectLinks(VirtualComponentPosition gaugePos) {
        if (!(components.get(gaugePos) instanceof VirtualGaugeBehaviour)) return;
        List<Connection> toRemove = new ArrayList<>();
        for (Connection conn : connectionGraph.incomingConnections(gaugePos))
            if (!LogisticsConnection.TYPE.equals(conn.type)) toRemove.add(conn);
        for (Connection conn : connectionGraph.outgoingConnections(gaugePos))
            if (!LogisticsConnection.TYPE.equals(conn.type)) toRemove.add(conn);
        for (Connection conn : toRemove) {
            connectionGraph.remove(conn.to, conn.from);
            markSinkDirty(conn.to, conn.type);   // the gauge (its gate) or the wire's sink re-folds
        }
        settleConnections();
        setChanged();
        sendData();
    }

    /** Cycles a component's operation mode, if it has one. */
    public void cycleOperationMode(VirtualComponentPosition pos) {
        VirtualComponentBehaviour behaviour = components.get(pos);
        if (behaviour != null) behaviour.cycleOperationMode();
    }

    /** Applies Logical Tube settings (currently the operation {@code mode}). The output follows one tick later (preTick).
     *  {@code setMode} syncs only when it actually changes. */
    public void configureLogicalTube(VirtualComponentPosition pos, String modeName) {
        if (components.get(pos) instanceof LogicalTubeBehaviour tube)
            tube.setMode(LogicalTubeBehaviour.Mode.fromName(modeName));
    }

    /** Cycles a component's connection arrow-bend mode. */
    public void cycleArrowMode(VirtualComponentPosition pos) {
        VirtualComponentBehaviour behaviour = components.get(pos);
        if (behaviour != null) behaviour.cycleArrowMode();
    }

    // ── Rename ───────────────────────────────────────────────────────────────

    /** Sets the controller's custom name (blank clears it). Clamped to {@link #MAX_NAME_LENGTH}. */
    public void setCustomName(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.length() > MAX_NAME_LENGTH) trimmed = trimmed.substring(0, MAX_NAME_LENGTH);
        if (trimmed.equals(customName)) return;
        customName = trimmed;
        setChanged();
        sendData();
    }

    // ── Sounds ──────────────────────────────────────────────────────────────

    /** Plays a Create sound entry (e.g. {@code CONFIRM}) at the controller. */
    public void playSound(AllSoundEvents.SoundEntry entry, float volume, float pitch) {
        if (level == null || level.isClientSide()) return;
        entry.playOnServer(level, getBlockPos(), volume, pitch);
    }

    /** Plays a vanilla sound event at the controller (BLOCKS category). */
    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (level == null || level.isClientSide()) return;
        level.playSound(null, getBlockPos(), sound, SoundSource.BLOCKS, volume, pitch);
    }

    /** Create's rejection blip — invalid placement, connection, or relocate. */
    public void playDenySound() {
        if (level == null || level.isClientSide()) return;
        AllSoundEvents.DENY.playOnServer(level, getBlockPos());
    }

    /** Place/break sound of the component's underlying block (matches Create placing/breaking a gauge). */
    private void playBlockSound(Item item, boolean place) {
        if (level == null || level.isClientSide()) return;
        Block block = Block.byItem(item);
        SoundType type = block.defaultBlockState().getSoundType(level, getBlockPos(), null);
        if (place)
            playSound(type.getPlaceSound(), 1f, 1f);
        else
            playSound(type.getBreakSound(), (type.volume + 1f) / 2f, type.pitch * 0.8f);
    }

    // ── Live menu sync ─────────────────────────────────────────────────────

    @Override
    public void sendData() {
        super.sendData();          // vanilla BE chunk update (coalesced per tick; carries no board, see write())
        menuSyncQueued = true;     // defer the heavy menu snapshot to the tick flush (coalesced)
    }

    private void syncMenuToPlayers() {
        if (level == null || level.isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) level;
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers())
            if (player.containerMenu instanceof FactoryControllerMenu menu
                    && menu.controllerPos.equals(getBlockPos()))
                viewers.add(player);
        if (viewers.isEmpty()) return;

        List<VirtualComponentBehaviour> comps = new ArrayList<>(components.values());
        List<Connection> conns = connectionGraph.connections();
        NetworkSettingsStore settingsStore = NetworkSettingsStore.get(level);
        List<NetworkSettings> netList = new ArrayList<>(networks.size());
        for (UUID id : networks)
            netList.add(settingsStore != null ? settingsStore.get(id) : NetworkSettings.defaultFor(id));
        SyncPanelStatePacket packet = new SyncPanelStatePacket(getBlockPos(), comps, conns, netList, customName, redstonePowered);
        for (ServerPlayer player : viewers)
            PacketDistributor.sendToPlayer(player, packet);
    }

    /** Re-syncs every open controller that uses {@code network} — called after its shared settings change,
     *  since one edit is visible to every controller on that network. */
    public static void resyncNetworkViewers(net.minecraft.server.MinecraftServer server, UUID network) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers())
            if (player.containerMenu instanceof FactoryControllerMenu menu
                    && player.level().getBlockEntity(menu.controllerPos) instanceof FactoryControllerBlockEntity be
                    && be.networks.contains(network))
                be.sendData();
    }

    // ── MenuProvider ───────────────────────────────────────────────────────

    @Override
    public @NotNull Component getDisplayName() {
        return customName.isBlank()
            ? Component.translatable("block.createfactorycontroller.factory_controller")
            : Component.literal(customName);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, @NotNull Inventory inventory, @NotNull Player player) {
        return new FactoryControllerMenu(syncId, inventory, this);
    }

    // ── NBT ────────────────────────────────────────────────────────────────

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);

        if (clientPacket) return;

        tag.putInt("Ver", DATA_VERSION);
        if (!customName.isBlank()) tag.putString("CustomName", customName);

        ListTag gaugeList = new ListTag();
        for (VirtualComponentBehaviour b : components.values())
            gaugeList.add(b.toNBT(registries, VirtualComponentBehaviour.NbtProfile.SERVER));
        tag.put("Components", gaugeList);
        tag.put("Connections", connectionGraph.toNBT());   // central edge list (Phase 2)

        ListTag networkList = new ListTag();
        for (UUID id : networks) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            networkList.add(entry);
        }
        tag.put("Networks", networkList);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        clearCopiedSetupComponent();
        tag = ControllerDataFixer.fixControllerBE(tag);

        customName = tag.getString("CustomName");   // "" when absent

        components.clear();
        ListTag componentList = tag.getList("Components", Tag.TAG_COMPOUND);
        for (int i = 0; i < componentList.size(); i++) {
            VirtualComponentBehaviour b = ComponentRegistry.fromNBT(this, componentList.getCompound(i), registries);
            if (b != null) components.put(b.position(), b);
        }
        readConnections(tag);

        networks.clear();
        ListTag networkList = tag.getList("Networks", Tag.TAG_COMPOUND);
        for (int i = 0; i < networkList.size(); i++) {
            UUID id = networkList.getCompound(i).getUUID("Id");
            networks.add(id);
        }

        markOrderableDirty();   // republish to the registry on the next server tick (level is set by then)
    }

    /**
     * Older placed controllers could keep the item-only setup payload in the block entity's component map.
     * Drop it on load so future saves/drops don't duplicate the controller setup data.
     */
    private void clearCopiedSetupComponent() {
        if (components().has(CreateFactoryController.CONTROLLER_SETUP.get()))
            setComponents(components().filter(type -> !type.equals(CreateFactoryController.CONTROLLER_SETUP.get())));
    }

    /** Loads the central connection graph from {@code tag}'s {@code "Connections"} edge list (old saves are upgraded to
     *  that format by {@link ControllerDataFixer} before read). Call after components load. */
    private void readConnections(CompoundTag tag) {
        connectionGraph.clear();
        ListTag connections = tag.getList("Connections", Tag.TAG_COMPOUND);
        for (int i = 0; i < connections.size(); i++) {
            Connection conn = Connection.fromNBT(connections.getCompound(i));
            if (conn != null && components.containsKey(conn.from) && components.containsKey(conn.to))
                connectionGraph.add(conn);
        }
        bindConnectionHooks();
    }

    /** Aborts the open production tasks of every orderable gauge (called when the controller is destroyed —
     *  whether the board is dropped as items or preserved on the controller item). */
    public void abortAllTasks() {
        if (level == null || level.isClientSide()) return;
        for (VirtualComponentBehaviour b : components.values())
            if (b instanceof VirtualGaugeBehaviour g && g.gaugeId != null)
                ProductionOrderManager.invalidateTasksFor(level, g.networkId, g.gaugeId);
    }

    // ── Setup preservation (controller item carries the board on break) ─────────

    /** True when there is any configured board state worth carrying on the dropped item. */
    public boolean hasSetup() {
        return !components.isEmpty() || !networks.isEmpty() || !customName.isBlank();
    }

    /**
     * The minimal board setup to persist on a dropped controller item: each component's config with the
     * runtime/bulb state and the runtime-minted gauge id stripped, plus the tuned networks and name.
     */
    public CompoundTag writeSetup(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Ver", DATA_VERSION);
        if (!customName.isBlank()) tag.putString("CustomName", customName);

        ListTag components = new ListTag();
        for (VirtualComponentBehaviour b : this.components.values()) {
            components.add(b.toNBT(registries, VirtualComponentBehaviour.NbtProfile.EXPORT));
        }
        tag.put("Components", components);
        tag.put("Connections", connectionGraph.toNBT());   // central edge list carried on the item

        ListTag networkList = new ListTag();
        for (UUID id : networks) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            networkList.add(entry);
        }
        tag.put("Networks", networkList);
        return tag;
    }

    /** Restores a board from {@link #writeSetup} onto a freshly placed controller (server side). */
    public void applySetup(CompoundTag tag, HolderLookup.Provider registries) {
        if (level == null || level.isClientSide()) return;
        tag = ControllerDataFixer.fixControllerBE(tag);

        customName = tag.getString("CustomName");

        components.clear();
        ListTag componentList = tag.getList("Components", Tag.TAG_COMPOUND);
        for (int i = 0; i < componentList.size(); i++) {
            VirtualComponentBehaviour b = ComponentRegistry.fromNBT(this, componentList.getCompound(i), registries);
            if (b != null) {
                components.put(b.position(), b);
                if (b instanceof VirtualGaugeBehaviour gauge && gauge.requestMode.allowsOrder())
                    updateGaugeOrderable(gauge);
            }
        }
        readConnections(tag);

        networks.clear();
        ListTag networkList = tag.getList("Networks", Tag.TAG_COMPOUND);
        for (int i = 0; i < networkList.size(); i++) {
            UUID id = networkList.getCompound(i).getUUID("Id");
            networks.add(id);
        }
        reinitSignalGraph();
        setChanged();
        sendData();
        markOrderableDirty();
    }

    /**
     * Re-derives the whole redstone signal graph from a clean baseline after a setup restore. The imported edges still
     * carry the pre-break signal snapshot (edge {@code State} is serialized unconditionally), but the EXPORT profile
     * stripped every component's runtime power — so a source whose power reset to its default while its edge kept the
     * opposite value can never reconcile: its change-detected publish/commit sees no change and the stale edge sticks
     * (a RECEIVE link or logic tube frozen powered, gating gauges that shouldn't be). Unlike a world reload — where the
     * full save profile keeps edges and components consistent — an export snapshot must be re-derived, not trusted.
     */
    private void reinitSignalGraph() {
        for (VirtualComponentBehaviour c : components.values())
            for (Connection.Type type : Connection.Type.values())
                c.publish(type);   // overwrite the stale imported edges with each source's clean baseline output
        for (VirtualComponentBehaviour c : components.values())
            if (c instanceof VirtualRedstoneLinkBehaviour link) {
                link.addToNetwork();   // idempotent; the lazy tick would also do this
                link.updatePower();    // RECEIVE pulls real network power (may re-publish POWERED); SEND re-notifies
            }
        settleConnections();
    }

    /** Stamps the controller's setup onto {@code stack} (if any) — used when it's dropped on break. */
    public void writeSetupToItem(ItemStack stack) {
        if (level == null || !hasSetup()) return;
        stack.set(CreateFactoryController.CONTROLLER_SETUP.get(), writeSetup(level.registryAccess()));
    }

    @Override
    public void saveToItem(@NotNull ItemStack stack, HolderLookup.@NotNull Provider registries) {
        // intentionally empty
    }
}
