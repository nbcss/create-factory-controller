package io.github.nbcss.content.factorycontroller;

import com.google.common.collect.Multimap;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.RequestPromise;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;
import io.github.nbcss.CreateFactoryController;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VirtualGaugeBehaviour extends AbstractVirtualComponent {

    /** Component-type discriminator for this kind. */
    public static final ResourceLocation TYPE_ID =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "gauge");

    /** GUI-sprite folder holding this gauge's {@code back}/{@code front} body textures. */
    public static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "factory_controller/factory_gauge");

    // Identity
    public final UUID networkId;

    // Filter config
    public ItemStack filter = ItemStack.EMPTY;
    /** Target threshold (Create's {@code count}); 0 means the gauge is inactive. In auto mode
     *  ({@link #autoMode}) this is recomputed every tick from the demand of the parent recipes wired to this gauge. */
    public int count = 0;
    /** How the threshold is measured (items or stacks). Replaces Create's {@code upTo}. */
    public ThresholdMode mode = ThresholdMode.ITEMS;
    /** When true the target {@link #count} is managed automatically each tick from consumer demand;
     *  the threshold is always counted in items. The player may not edit count directly while this is on. */
    public boolean autoMode = false;
    /** Current network stock of {@link #filter} — server-computed, synced for the threshold display. */
    public int stockLevel = 0;
    /** Open promised amount of {@link #filter} — server-computed, synced for the promise box. */
    public int promisedCount = 0;
    /** Mechanical-crafting arrangement (9 slots) when crafting mode is active; empty otherwise. */
    public List<ItemStack> activeCraftingArrangement = new ArrayList<>();

    // Computed status (server-side, synced to client)
    public boolean satisfied = false;
    public boolean promisedSatisfied = false;
    public boolean waitingForNetwork = false;
    /** No redstone on the virtual board, but kept for parity with Create's status logic. */
    public boolean redstonePowered = false;
    /** Set by the host controller when its block receives a redstone signal; while true this gauge
     *  issues no new requests (the whole board is paused). Distinct from {@link #redstonePowered},
     *  which mirrors a real gauge's own redstone state. */
    public boolean controllerPowered = false;

    // Internal tick state
    private int lastReportedLevelInStorage = 0;
    private int lastReportedPromises = 0;
    private int lastReportedUnloadedLinks = 0;
    /** Last synced {@link #count}; in auto mode count is recomputed each tick, so it must be part of the
     *  storage-monitor dirty check or a count-only change wouldn't reach the client's gray number. */
    private int lastReportedCount = -1;
    /** Bridges the gap between a promise settling (live) and the loose stock summary recomputing: holds the
     *  just-settled amount as "stock already here, summary hasn't caught up yet" so stock+promised stays
     *  conserved across the settlement (no satisfied/connection dip). Cleared when the summary recomputes. */
    private int creditStock = 0;
    /** Ticks the current {@link #creditStock} has been held; a safety cap in case the recomputed summary
     *  value happens to be unchanged so the "raw stock changed" clear never fires. */
    private int creditAge = 0;
    private int timer = 0;
    /** Game tick of the last request attempt; the client decays the connection flash from it. */
    public long lastRequestTick = Long.MIN_VALUE;
    private boolean forceClearPromises = false;
    public String recipeAddress = "";
    public int recipeOutput = 1;
    /** Crafts carried per request package in mechanical-crafting mode (≥1); 1 = a single craft. */
    public int craftBatch = 1;
    public int promiseClearingInterval = -1;
    public RequestPromiseQueue restockerPromises;

    public VirtualGaugeBehaviour(FactoryControllerBlockEntity controller, VirtualPanelPosition position,
                                 UUID networkId, ResourceLocation gaugeItemId) {
        super(controller, position, gaugeItemId);
        this.networkId = networkId;
        // controller is null on the client (menu snapshot); avoid binding a method ref to null.
        Runnable onChanged = controller == null ? () -> {} : controller::setChanged;
        this.restockerPromises = new RequestPromiseQueue(onChanged);
    }

    @Override
    public ResourceLocation getTypeId() {
        return TYPE_ID;
    }

    @Override
    public ResourceLocation getTexture() {
        return TEXTURE;
    }

    // ── Status ───────────────────────────────────────────────────────────────

    /** The configured target threshold (Create's {@code count}). */
    public int getCount() {
        return count;
    }

    /**
     * Whether the gauge has an active recipe target. A manual gauge becomes active once its target
     * {@link #count} is non-zero; an {@link ThresholdMode#AUTO} gauge is <em>always</em> active — it
     * manages a live, often-zero demand, so it must behave like a regular demand-0 gauge (satisfied,
     * green, bulb lit, labelled "stock/0") rather than an unconfigured one. Everything that used to test
     * {@code count == 0} for "inactive" goes through this instead.
     */
    public boolean isActive() {
        return count != 0 || autoMode;
    }

    /**
     * Whether this gauge needs a restock address but has none — an active target with incoming
     * connections (or restocker role) but a blank {@link #recipeAddress}. Mirrors Create's check
     * (minus the packager-block-entity part, which has no virtual-board equivalent).
     */
    public boolean isMissingAddress() {
        return !targetedBy().isEmpty() && isActive() && recipeAddress.isBlank();
    }

    /**
     * Connection-path color (RGB) for the gauge state, matching Create's {@code getIngredientStatusColor}:
     * gray when inactive/misconfigured/powered, then waiting → satisfied → promised → pending. (The
     * indicator bulb is coloured separately — green/red — by the screen; see VirtualGaugeWidget.)
     */
    public int getConnectionColor() {
        return !isActive() || isMissingAddress() || redstonePowered ? 0x888898
             : waitingForNetwork ? 0x5B3B3B
             : satisfied         ? 0x9EFF7F
             : promisedSatisfied ? 0x22AFAF
             :                     0x3D6EBD;
    }

    /**
     * Count label drawn on the gauge face — replica of Create's
     * {@code FactoryPanelBehaviour#getCountLabelForValueBox}. Uses the synced {@link #stockLevel} as the
     * current network stock. Shows the stock (in stacks when {@code !upTo}), and when a target
     * {@link #count} is set, "stock⏶/target" coloured by satisfaction, with the request marker.
     */
    public MutableComponent getCountLabel() {
        if (filter.isEmpty()) return Component.empty();
        if (waitingForNetwork) return Component.literal("?");

        int levelInStorage = stockLevel;
        boolean inf = levelInStorage >= BigItemStack.INF;
        int inStorage = levelInStorage / mode.toItemCount(filter);
        int promised = promisedCount;
        String stacks = mode.suffix;

        if (!isActive())
            return CreateLang.text(inf ? "∞" : inStorage + stacks).color(0xF1EFE8).component();

        // Auto with no current demand still reads "stock/0" — it's a live demand-0 gauge, not inactive.
        return CreateLang.text(inf ? "∞" : inStorage + stacks)
            .color(satisfied ? 0xD7FFA8 : promisedSatisfied ? 0xFFCD75 : 0xFFBFA8)
            .add(CreateLang.text(promised == 0 ? "" : "⏶"))
            .add(CreateLang.text("/").style(ChatFormatting.WHITE))
            .add(CreateLang.text(count + stacks).color(autoMode ? (count > 0 ? 0x06ACFF : 0x035882) : 0xF1EFE8))
            .component();
    }

    // ── Tick ───────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        // Auto target is recomputed inside tickStorageMonitor (it needs the same fresh stock/promise
        // figures), so it stays in sync with the satisfaction state in a single pass.
        tickStorageMonitor();
        tickRequests();
    }

    /**
     * Controller pre-pass hook: refresh the Auto Request target. Invoked once per tick in consumer-first
     * order (see {@code FactoryControllerBlockEntity#tick}) so a demand change propagates through a whole
     * auto chain within a single tick rather than crawling one level per tick.
     */
    public void computeDemand() {
        if (!autoMode || controller == null) return;
        int demand = 0;
        for (VirtualPanelPosition parentPos : targeting) {
            if (!(controller.components.get(parentPos) instanceof VirtualGaugeBehaviour parent)) continue;
            if (!parent.isDemandingIngredients()) continue;   // consumer satisfied/idle → needs nothing now
            VirtualPanelConnection conn = parent.targetedBy().get(position);
            if (conn == null) continue;
            int parentBatch = parent.activeCraftingArrangement.isEmpty() ? 1 : Math.max(1, parent.craftBatch);
            demand += conn.totalAmount() * parentBatch;
        }
        count = demand <= 0 ? 0 : Math.ceilDiv(demand, mode.toItemCount(filter));
    }

    /**
     * Whether this gauge still needs more of its ingredients — i.e. it is configured to request and isn't
     * yet covered by stock + open promises. An auto producer feeding this gauge calls it to size its own
     * demand.
     *
     * <p>Coverage is the gauge's last-tick conserved snapshot ({@link #stockLevel} = effectiveStock,
     * + {@link #promisedCount}) rather than a fresh loose read: both halves come from the same
     * tickStorageMonitor pass, so the credit-stock bridge keeps them conserved across a settlement. A fresh
     * loose read would dip (promise drops a frame before the summary stock rises) and make the consumer
     * falsely look "demanding" → the producer would size phantom demand and over-produce.</p>
     */
    private boolean isDemandingIngredients() {
        if (count == 0 || waitingForNetwork || redstonePowered || controllerPowered || isMissingAddress())
            return false;
        // Use the conserved snapshot (effectiveStock via stockLevel, + promisedCount) from this gauge's last
        // tickStorageMonitor — both halves from the same instant, so the credit bridge prevents the
        // settlement dip that would otherwise make a consumer falsely look "demanding" → phantom demand.
        return stockLevel + promisedCount < count * mode.toItemCount(filter);
    }

    private void tickStorageMonitor() {
        if (filter.isEmpty()) {
            satisfied = true;
            promisedSatisfied = true;
            waitingForNetwork = false;
            stockLevel = 0;
            return;
        }

        int unloadedLinkCount = getUnloadedLinks();

        if (unloadedLinkCount == 0 && lastReportedUnloadedLinks != 0)
            LogisticsManager.SUMMARIES.invalidate(networkId);

        int inStorage = getLevelInStorage();
        int promised = getPromised();

        // Credit-stock bridge. The loose summary lags the live promise queue, so at a settlement `promised`
        // drops a frame (or ~1s) before `inStorage` rises — which would dip stock+promised and flash the
        // connection / open the request gate. When `promised` drops with no matching raw-stock rise, those
        // items just entered inventory the summary hasn't recomputed yet: hold the delta as creditStock and
        // count it as stock until the summary catches up (raw stock changes → authoritative → clear).
        // Skipped under promise expiry, where a drop may be an expiry rather than a settlement.
        if (inStorage != lastReportedLevelInStorage) {
            creditStock = 0;
            creditAge = 0;
        } else {
            if (promiseClearingInterval == -1 && promised < lastReportedPromises)
                creditStock += lastReportedPromises - promised;
            if (creditStock > 0 && ++creditAge > 40) {   // safety: summary must have recomputed by now
                creditStock = 0;
                creditAge = 0;
            }
        }
        stockLevel = inStorage + creditStock;
        int effectiveStock = stockLevel;
        promisedCount = promised;

        // NB: the auto target (count) is refreshed once per tick by the controller's consumer-first
        // pre-pass (recomputeAutoCount), not here, so a chain settles in a single tick.
        int demand = count * mode.toItemCount(filter);

        // Satisfied is real-stock only (bulb stays dim / connection stays pending until items actually
        // arrive). promisedSatisfied still folds in promises so in-flight items aren't re-requested. Both
        // use effectiveStock, so the credit bridge keeps them from dipping across a settlement.
        boolean shouldSatisfy = effectiveStock >= demand;
        boolean shouldPromiseSatisfy = effectiveStock + promised >= demand;
        boolean shouldWait = unloadedLinkCount > 0;

        if (lastReportedLevelInStorage == inStorage && lastReportedPromises == promised
                && lastReportedUnloadedLinks == unloadedLinkCount && lastReportedCount == count
                && satisfied == shouldSatisfy
                && promisedSatisfied == shouldPromiseSatisfy && waitingForNetwork == shouldWait)
            return;

        lastReportedLevelInStorage = inStorage;
        lastReportedPromises = promised;
        lastReportedUnloadedLinks = unloadedLinkCount;
        lastReportedCount = count;

        // Bulb-update chime on the unsatisfied → satisfied transition (Create's tickStorageMonitor),
        // played at the controller block so nearby players hear the gauge light up.
        if (!satisfied && shouldSatisfy && demand > 0) {
            controller.playSound(AllSoundEvents.CONFIRM, 0.3f, 1f);
            controller.playSound(AllSoundEvents.CONFIRM_2, 0.5f, 0.575f);
        }

        satisfied = shouldSatisfy;
        promisedSatisfied = shouldPromiseSatisfy;
        waitingForNetwork = shouldWait;
        controller.setChanged();
        controller.sendData();
    }

    // ── Storage queries ────────────────────────────────────────────────────

    public int getLevelInStorage() {
        if (filter.isEmpty()) return 0;
        return getRelevantSummary().getCountOf(filter);
    }

    private InventorySummary getRelevantSummary() {
        // Loose (20-tick) summary, matching Create's recipe panels — cheap on big networks, no invalidation.
        // The stock half lags the live promise, so on its own stock+promised would dip across a settlement
        // (the connection flicker / spurious request). tickStorageMonitor compensates with creditStock,
        // which holds a just-settled promise as stock until this summary recomputes, keeping stock+promised
        // conserved.
        return LogisticsManager.getSummaryOfNetwork(networkId, false);
    }

    public int getPromised() {
        if (filter.isEmpty()) return 0;
        if (forceClearPromises) {
            RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(networkId);
            if (promises != null) promises.forceClear(filter);
            timer = getConfigRequestIntervalInTicks() / 2;
            forceClearPromises = false;
        }
        RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(networkId);
        if (promises == null) return 0;
        return promises.getTotalPromisedAndRemoveExpired(filter, getPromiseExpiryTimeInTicks());
    }

    private int getUnloadedLinks() {
        return Create.LOGISTICS.getUnloadedLinkCount(networkId);
    }

    // ── Recipe-mode requests ─────────────────────────────────────────────────

    /**
     * Recipe-mode request tick — reimplements Create's {@code FactoryPanelBehaviour#tickRequests} for
     * the virtual board (which only ever has recipe-mode gauges; a real gauge on a packager would be
     * restock-mode). When the produced item ({@link #filter}) is understocked, it requests this
     * recipe's ingredients — the items of the gauges wired into this one — from their logistics
     * networks through the packager addressed by {@link #recipeAddress}, then promises the produced
     * output so it doesn't request again until that promise is consumed or expires.
     */
    private void tickRequests() {
        if (controller == null) return;                 // client snapshot never ticks, but be safe
        if (targetedBy().isEmpty()) return;             // no ingredients wired in → nothing to craft
        // Check satisfaction FIRST (Create's order) so the timer is frozen while stocked/promised. This
        // is what prevents over-requesting: the timer never idles at 0 ready to fire, so the one-tick
        // stock/promise flicker as the produced item lands can't trigger an extra request — a request
        // only fires after the gauge has been continuously understocked for the whole interval.
        if (satisfied || promisedSatisfied || waitingForNetwork || redstonePowered || controllerPowered) return;
        if (isMissingAddress()) return;
        if (timer > 0) {                                // throttle between attempts
            timer = Math.min(timer, getConfigRequestIntervalInTicks());
            timer--;
            return;
        }
        resetTimer();                                   // we're attempting now; throttle the next one
        // Stamp the attempt so the client can flash the connections once per request (not continuously).
        lastRequestTick = controller.getLevel() == null ? 0 : controller.getLevel().getGameTime();
        controller.setChanged();
        controller.sendData();
        if (recipeAddress.isBlank()) return;            // recipe mode needs a packager address

        // A single request can carry several crafts in one package (crafter mode only); the per-craft
        // ingredient demand and the produced output are both multiplied by this batch count.
        int batch = activeCraftingArrangement.isEmpty() ? 1 : Math.max(1, craftBatch);

        // Sum the ingredient demand from incoming connections, grouped by each source's network.
        Map<UUID, Map<Item, Integer>> demandByNetwork = new LinkedHashMap<>();
        Map<Item, ItemStack> ingredientStacks = new HashMap<>();
        for (Map.Entry<VirtualPanelPosition, VirtualPanelConnection> e : targetedBy().entrySet()) {
            if (!(controller.components.get(e.getKey()) instanceof VirtualGaugeBehaviour source)) continue;
            ItemStack ingredient = source.filter;
            if (ingredient.isEmpty()) continue;
            int needed = e.getValue().totalAmount() * batch;   // sum across the connection's repeated slots
            demandByNetwork.computeIfAbsent(source.networkId, k -> new LinkedHashMap<>())
                           .merge(ingredient.getItem(), needed, Integer::sum);
            ingredientStacks.putIfAbsent(ingredient.getItem(), ingredient);
        }
        if (demandByNetwork.isEmpty()) return;

        // Verify every ingredient is in stock and build a package order per network.
        Map<UUID, List<BigItemStack>> orderByNetwork = new LinkedHashMap<>();
        for (Map.Entry<UUID, Map<Item, Integer>> netEntry : demandByNetwork.entrySet()) {
            // Accurate summary (Create's tickRequests does the same) so we don't dispatch ingredients
            // against a stale availability count and over-send.
            InventorySummary summary = LogisticsManager.getSummaryOfNetwork(netEntry.getKey(), true);
            List<BigItemStack> order = new ArrayList<>();
            for (Map.Entry<Item, Integer> need : netEntry.getValue().entrySet()) {
                ItemStack stack = ingredientStacks.get(need.getKey());
                if (summary.getCountOf(stack) < need.getValue()) {        // insufficient → flash red
                    setConnectionsSuccess(false);
                    return;
                }
                order.add(new BigItemStack(stack.copy(), need.getValue()));
            }
            orderByNetwork.put(netEntry.getKey(), order);
        }

        // Crafting context: when mechanical crafting is enabled, derive the crafts from the 3×3
        // arrangement so the dispatched packages carry the recipe (matching Create's tickRequests —
        // PackageOrderWithCrafts.singleRecipe(activeCraftingArrangement)); otherwise no crafts. The
        // previous code wrongly built a recipe from the flat ingredient list even for plain requests,
        // so crafter-mode packages never carried the actual 3×3 recipe and couldn't be crafted.
        List<PackageOrderWithCrafts.CraftingEntry> crafts = activeCraftingArrangement.isEmpty()
            ? PackageOrderWithCrafts.empty().orderedCrafts()
            // CraftingEntry.count is the number of times to run this 3×3 pattern — i.e. the batch.
            : List.of(new PackageOrderWithCrafts.CraftingEntry(
                new PackageOrder(activeCraftingArrangement.stream()
                    .map(s -> new BigItemStack(s.copyWithCount(1)))
                    .toList()),
                batch));
        // Resolve packagers for every network up front; abort entirely if any is busy so we never
        // half-fulfil a recipe.
        List<Multimap<PackagerBlockEntity, PackagingRequest>> dispatch = new ArrayList<>();
        for (Map.Entry<UUID, List<BigItemStack>> netEntry : orderByNetwork.entrySet()) {
            PackageOrderWithCrafts order =
                new PackageOrderWithCrafts(new PackageOrder(netEntry.getValue()), crafts);
            Multimap<PackagerBlockEntity, PackagingRequest> found =
                LogisticsManager.findPackagersForRequest(netEntry.getKey(), order, null, recipeAddress);
            if (found.isEmpty()) return;     // no packager could serve this network
            for (PackagerBlockEntity packager : found.keySet())
                if (packager.isTooBusyFor(RequestType.RESTOCK)) return;
            dispatch.add(found);
        }

        // All clear — perform the requests and promise the produced output.
        for (Multimap<PackagerBlockEntity, PackagingRequest> req : dispatch)
            LogisticsManager.performPackageRequests(req);
        setConnectionsSuccess(true);   // ingredients were dispatched → flash white

        RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(networkId);
        if (promises != null)
            promises.add(new RequestPromise(new BigItemStack(filter.copy(), Math.max(1, recipeOutput) * batch)));
        controller.setChanged();
    }

    /**
     * Flags every incoming connection's last-request outcome (Create's {@code FactoryPanelConnection
     * #success}), which the connection renderer reads to pulse the line white (could supply) or red
     * (insufficient). Synced to clients via {@code toClientNBT} only when it actually changes.
     */
    private void setConnectionsSuccess(boolean value) {
        boolean changed = false;
        for (VirtualPanelConnection conn : targetedBy().values())
            if (conn.success != value) { conn.success = value; changed = true; }
        if (changed) {
            controller.setChanged();
            controller.sendData();
        }
    }

    private void resetTimer() {
        timer = getConfigRequestIntervalInTicks();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private int getConfigRequestIntervalInTicks() {
        return AllConfigs.server().logistics.factoryGaugeTimer.get();
    }

    private int getPromiseExpiryTimeInTicks() {
        if (promiseClearingInterval == -1) return -1;
        if (promiseClearingInterval == 0) return 20 * 30;
        return promiseClearingInterval * 20 * 60;
    }

    // ── NBT ────────────────────────────────────────────────────────────────

    @Override
    public CompoundTag toNBT(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", getTypeId().toString());
        tag.put("Pos", position.toNBT());
        tag.putString("GaugeItem", itemId.toString());
        tag.putUUID("Network", networkId);

        tag.put("Filter", filter.saveOptional(registries));
        tag.putInt("Count", count);
        tag.putString("Mode", mode.name());
        tag.putBoolean("AutoMode", autoMode);

        tag.putBoolean("Satisfied", satisfied);
        tag.putBoolean("PromisedSatisfied", promisedSatisfied);
        tag.putBoolean("Waiting", waitingForNetwork);
        tag.putInt("LastLevel", lastReportedLevelInStorage);
        tag.putInt("LastPromised", lastReportedPromises);
        tag.putInt("Timer", timer);
        tag.putString("RecipeAddress", recipeAddress);
        tag.putInt("RecipeOutput", recipeOutput);
        tag.putInt("CraftBatch", craftBatch);
        tag.putInt("PromiseClearingInterval", promiseClearingInterval);

        // Connections
        ListTag targetedByList = new ListTag();
        for (VirtualPanelConnection conn : targetedBy.values())
            targetedByList.add(conn.toNBT());
        tag.put("TargetedBy", targetedByList);

        ListTag targetingList = new ListTag();
        for (VirtualPanelPosition pos : targeting)
            targetingList.add(pos.toNBT());
        tag.put("Targeting", targetingList);

        tag.put("CraftingArrangement", writeStacks(activeCraftingArrangement, registries));

        return tag;
    }

    private static ListTag writeStacks(List<ItemStack> stacks, net.minecraft.core.HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ItemStack s : stacks)
            list.add(s.saveOptional(registries));
        return list;
    }

    private static List<ItemStack> readStacks(ListTag list, net.minecraft.core.HolderLookup.Provider registries) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < list.size(); i++)
            stacks.add(ItemStack.parseOptional(registries, list.getCompound(i)));
        return stacks;
    }

    /** Server-side: flag promises for this gauge's item to be cleared on the next tick. */
    public void requestClearPromises() {
        forceClearPromises = true;
    }

    @Override
    public CompoundTag toClientNBT(net.minecraft.core.HolderLookup.Provider registries) {
        // Only what the canvas needs: identity/texture, filter (icon + set-item check), status
        // (indicator colour + gating), the address (status `isMissingAddress`), and incoming
        // connections (arrow rendering). Server-only tick state and detailed recipe config are
        // omitted — those are pulled on demand when a config overlay opens.
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", getTypeId().toString());
        tag.put("Pos", position.toNBT());
        tag.putString("GaugeItem", itemId.toString());
        tag.putUUID("Network", networkId);

        tag.put("Filter", filter.saveOptional(registries));
        tag.putInt("Count", count);
        tag.putString("Mode", mode.name());
        tag.putBoolean("AutoMode", autoMode);

        tag.putBoolean("Satisfied", satisfied);
        tag.putBoolean("PromisedSatisfied", promisedSatisfied);
        tag.putBoolean("Waiting", waitingForNetwork);
        tag.putBoolean("ControllerPowered", controllerPowered);
        tag.putString("RecipeAddress", recipeAddress);
        // Recipe-config fields the ConfigureRecipeScreen edits — synced so the overlay can show
        // current values without a separate on-demand fetch.
        tag.putInt("RecipeOutput", recipeOutput);
        tag.putInt("CraftBatch", craftBatch);
        tag.putInt("PromiseClearingInterval", promiseClearingInterval);
        tag.putInt("Stock", stockLevel);
        tag.putInt("Promised", promisedCount);
        tag.putLong("LastRequestTick", lastRequestTick);
        tag.put("CraftingArrangement", writeStacks(activeCraftingArrangement, registries));

        ListTag targetedByList = new ListTag();
        for (VirtualPanelConnection conn : targetedBy.values())
            targetedByList.add(conn.toNBT());
        tag.put("TargetedBy", targetedByList);

        return tag;
    }

    public static VirtualGaugeBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                                CompoundTag tag,
                                                net.minecraft.core.HolderLookup.Provider registries) {
        VirtualPanelPosition pos = VirtualPanelPosition.fromNBT(tag.getCompound("Pos"));
        ResourceLocation gaugeItemId = ResourceLocation.parse(tag.getString("GaugeItem"));
        UUID networkId = tag.getUUID("Network");

        VirtualGaugeBehaviour b = new VirtualGaugeBehaviour(controller, pos, networkId, gaugeItemId);
        b.filter = ItemStack.parseOptional(registries, tag.getCompound("Filter"));
        b.count = tag.getInt("Count");
        b.mode = ThresholdMode.fromName(tag.getString("Mode"));
        b.autoMode = tag.getBoolean("AutoMode");
        b.stockLevel = tag.getInt("Stock");
        b.promisedCount = tag.getInt("Promised");
        b.lastRequestTick = tag.getLong("LastRequestTick");
        b.activeCraftingArrangement = readStacks(tag.getList("CraftingArrangement", Tag.TAG_COMPOUND), registries);

        b.satisfied = tag.getBoolean("Satisfied");
        b.promisedSatisfied = tag.getBoolean("PromisedSatisfied");
        b.waitingForNetwork = tag.getBoolean("Waiting");
        b.controllerPowered = tag.getBoolean("ControllerPowered");
        b.lastReportedLevelInStorage = tag.getInt("LastLevel");
        b.lastReportedPromises = tag.getInt("LastPromised");
        b.timer = tag.getInt("Timer");
        b.recipeAddress = tag.getString("RecipeAddress");
        b.recipeOutput = tag.getInt("RecipeOutput");
        b.craftBatch = Math.max(1, tag.getInt("CraftBatch"));   // absent (legacy data) → 1
        b.promiseClearingInterval = tag.getInt("PromiseClearingInterval");

        ListTag targetedByList = tag.getList("TargetedBy", Tag.TAG_COMPOUND);
        for (int i = 0; i < targetedByList.size(); i++) {
            VirtualPanelConnection conn = VirtualPanelConnection.fromNBT(targetedByList.getCompound(i));
            b.targetedBy.put(conn.from, conn);
        }

        ListTag targetingList = tag.getList("Targeting", Tag.TAG_COMPOUND);
        for (int i = 0; i < targetingList.size(); i++)
            b.targeting.add(VirtualPanelPosition.fromNBT(targetingList.getCompound(i)));

        return b;
    }
}
