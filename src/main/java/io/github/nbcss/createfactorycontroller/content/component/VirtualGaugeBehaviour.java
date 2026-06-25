package io.github.nbcss.createfactorycontroller.content.component;

import com.google.common.collect.Multimap;
import com.simibubi.create.AllBlocks;
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
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.RequestMode;
import io.github.nbcss.createfactorycontroller.content.ThresholdUnit;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionCapability;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ValidationResult;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrderManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VirtualGaugeBehaviour extends AbstractVirtualComponent {

    public static final VirtualComponentBehaviour.Type TYPE = new VirtualComponentBehaviour.Type(){

        @Override
        public String id() {
            return "GAUGE";
        }

        @Override
        public List<ResourceLocation> items() {
            return List.of(AllBlocks.FACTORY_GAUGE.getId());
        }

        @Override
        public boolean isRequireNetwork() {
            return true;
        }

        @Override
        public VirtualComponentBehaviour create(FactoryControllerBlockEntity controller,
                                                VirtualComponentPosition pos,
                                                ResourceLocation itemId,
                                                UUID networkId) {
            return new VirtualGaugeBehaviour(controller, pos, networkId, itemId);
        }

        @Override
        public VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                                 CompoundTag tag,
                                                 HolderLookup.Provider registries) {
            return VirtualGaugeBehaviour.fromNBT(controller, tag, registries);
        }
    };

    public static final ResourceLocation TYPE_ID =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "gauge");
    public static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "factory_controller/factory_gauge");
    public static final ResourceLocation FRONT_TEXTURE = TEXTURE.withSuffix("/front");

    /** Max ingredient (gauge → gauge) inputs, matching Create's factory-panel limit. Redstone-link wires are stored
     *  on the link and don't count toward this. */
    public static final int MAX_INGREDIENTS = 9;

    // Identity
    public final UUID networkId;
    public UUID patternId = null;

    // Filter config
    public ItemStack filter = ItemStack.EMPTY;
    /** When true the gauge matches the {@link #filter}'s item type only, ignoring NBT/components: stock and
     *  promise counts sum every data-variant, and a downstream recipe wired to this gauge may consume any
     *  variant. Never set for a fluid filter (the set-item screen disables the toggle there). */
    public boolean ignoreData = false;
    /** Target threshold (Create's {@code count}); 0 means the gauge is inactive.*/
    public int count = 0;
    /** How the threshold is measured (items or stacks). Replaces Create's {@code upTo}. */
    public ThresholdUnit unit = ThresholdUnit.ITEMS;
    public RequestMode requestMode = RequestMode.NORMAL;
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
    /** Driven by a powered RECEIVE-mode redstone link wired to this gauge — recomputed each controller pre-pass. */
    public boolean redstonePowered = false;
    public boolean controllerPowered = false;

    // Internal tick state
    private int lastReportedUnloadedLinks = 0;
    /** Last synced {@link #count}; in passive mode count is recomputed each tick, so it must be part of the
     *  storage-monitor dirty check or a count-only change wouldn't reach the client's gray number. */
    private int lastReportedCount = -1;
    /** Identity of the last loose summary object. The loose cache builds a NEW {@link InventorySummary} when
     *  it recomputes, so a changed reference is the precise "stock reading just refreshed (authoritative)"
     *  signal the monitor uses to confirm a fresh drop. */
    private InventorySummary lastSummaryRef = null;
    /** Effective {@code stock + promised}, HELD against a transient drop — the conserved sum that feeds
     *  {@code promisedSatisfied}. At a settlement it dips a frame before the loose stock rises; holding it
     *  until the conserved sum recovers to the held value bridges that without flicker. {@link #stockLevel}
     *  is the matching held stock for {@code satisfied}. Crucially the satisfaction booleans are derived from
     *  these held QUANTITIES vs the fresh {@code demand}, so a demand change (and the passive chain that
     *  reads it) is never held — only the lag-prone reads are. */
    private int heldSum = 0;
    /** One-tick confirm flags: a fresh (refreshed) drop in {@link #stockLevel} / {@link #heldSum} is held one
     *  tick to ride out the mid-move "reads low" blip, then committed — keeps real drops responsive. */
    private boolean stockDropPending = false;
    private boolean sumDropPending = false;
    protected int timer = 0;
    /** Game tick of the last request attempt; the client decays the connection flash from it. */
    public long lastRequestTick = Long.MIN_VALUE;
    private boolean forceClearPromises = false;
    public String recipeAddress = "";
    public int recipeOutput = 1;
    /** Crafts carried per request package in mechanical-crafting mode (≥1); 1 = a single craft. */
    public int craftBatch = 1;
    public int craftDimension = 0;
    public int promiseClearingInterval = -1;

    public VirtualGaugeBehaviour(FactoryControllerBlockEntity controller, VirtualComponentPosition position,
                                 UUID networkId, ResourceLocation gaugeItemId) {
        super(controller, position, gaugeItemId);
        this.networkId = networkId;
    }

    @Override
    public ResourceLocation getTypeId() {
        return TYPE_ID;
    }

    @Override
    public ResourceLocation getTexture() {
        return TEXTURE;
    }

    public ResourceLocation getFrontTexture() {
        return FRONT_TEXTURE;
    }

    @Override
    protected Connection createConnection(VirtualComponentPosition from, VirtualComponentBehaviour source) {
        // A fluid ingredient is measured in millibuckets, so start it at one bucket (1000 mB); items start at 1.
        int amount = source instanceof VirtualGaugeBehaviour g && FluidCompat.isFluidFilter(g.filter) ? 1000 : 1;
        return new LogisticsConnection(from, position, amount);
    }

    /** A gauge caps its ingredient inputs at {@link #MAX_INGREDIENTS} grid <em>slots</em> (redstone-link wires
     *  live on the link and don't count). A single ingredient whose amount exceeds its stack size occupies
     *  several slots, so the cap is on {@link #usedInputSlots} — not the connection count — and a new
     *  connection (≥1 slot) is rejected once the grid is already full. */
    @Override
    public void addConnection(VirtualComponentPosition sourcePos) {
        if (!targetedBy().containsKey(sourcePos)
                && usedInputSlots(targetedBy(), this::filterAt) >= MAX_INGREDIENTS) return;
        super.addConnection(sourcePos);
    }

    /** The filter of the gauge at {@code pos}, resolved side-agnostically (server: controller; client: menu), or empty. */
    private ItemStack filterAt(VirtualComponentPosition pos) {
        return siblingAt(pos) instanceof VirtualGaugeBehaviour g ? g.filter : ItemStack.EMPTY;
    }

    /** A gauge outputs/accepts item-fluid ingredients (LOGISTICS) and is read/gated by redstone (REDSTONE); both
     *  channels defer their direction (BOTH), so a wired link's mode dictates the redstone arrow. */
    @Override
    public List<ConnectionCapability> ports() {
        return List.of(new ConnectionCapability(Connection.Type.LOGISTICS, ConnectionCapability.Role.BOTH),
                       new ConnectionCapability(Connection.Type.REDSTONE, ConnectionCapability.Role.BOTH));
    }

    /** As a LOGISTICS source the gauge feeds its stock, so it must carry a filter. (REDSTONE source: no rule.) */
    @Override
    public ValidationResult validateAsSource(Connection.Type channel, VirtualComponentBehaviour sink) {
        if (channel == Connection.Type.LOGISTICS && filter.isEmpty())
            return ValidationResult.fail(() -> CreateLang.translate("factory_panel.no_item")
                    .style(ChatFormatting.RED).component());
        return ValidationResult.SUCCESS;
    }

    /** As a LOGISTICS sink (the consumer) the gauge caps its ingredient grid slots. (REDSTONE sink: no rule.) */
    @Override
    public ValidationResult validateAsSink(Connection.Type channel, VirtualComponentBehaviour source) {
        if (channel == Connection.Type.LOGISTICS && usedInputSlots(targetedBy(), this::filterAt) >= MAX_INGREDIENTS)
            return ValidationResult.fail(() -> CreateLang.translate("factory_panel.cannot_add_more_inputs")
                    .style(ChatFormatting.RED).component());
        return ValidationResult.SUCCESS;
    }

    /**
     * Total grid slots a gauge's ingredient connections occupy. Mirrors the recipe screen's slot layout
     * ({@code ConfigureRecipeScreen#layoutInputSlots}): a fluid ingredient is one slot; an item ingredient
     * takes {@code ceil(amount / stackSize)} slots. The grid is capped at {@link #MAX_INGREDIENTS} slots, so
     * an ingredient whose amount exceeds its stack size consumes several — counting connections alone would
     * let the grid overflow. {@code filterResolver} maps a source position to its ingredient (server: via the
     * controller; client: via the menu snapshot), letting both sides share this logic.
     */
    public static int usedInputSlots(Map<VirtualComponentPosition, Connection> connections,
                                     java.util.function.Function<VirtualComponentPosition, ItemStack> filterResolver) {
        int used = 0;
        for (Map.Entry<VirtualComponentPosition, Connection> e : connections.entrySet()) {
            if (!(e.getValue() instanceof LogisticsConnection lc)) continue;
            ItemStack src = filterResolver.apply(e.getKey());
            if (FluidCompat.isFluidFilter(src)) { used += 1; continue; }   // a fluid ingredient is one slot
            int ss = src.isEmpty() ? 1 : Math.max(1, src.getMaxStackSize());
            used += (lc.amount() + ss - 1) / ss;   // ceil
        }
        return used;
    }

    // ── Status ───────────────────────────────────────────────────────────────

    /** The configured target threshold (Create's {@code count}). */
    public int getCount() {
        return count;
    }

    public boolean isInfiniteStock() {
        return stockLevel >= BigItemStack.INF;
    }

    /**
     * Whether the gauge has an active recipe target. A manual gauge becomes active once its target
     * {@link #count} is non-zero; a passive mode gauge is <em>always</em> active — it
     * manages a live, often-zero demand, so it must behave like a regular demand-0 gauge (satisfied,
     * green, bulb lit, labelled "stock/0") rather than an unconfigured one. Everything that used to test
     * {@code count == 0} for "inactive" goes through this instead.
     */
    public boolean isActive() {
        return count != 0 || requestMode.isPassive();
    }

    /**
     * Whether this gauge needs a restock address but has none — an active target with incoming connections
     * (or restocker role) but a blank {@link #recipeAddress}. Mirrors Create's check (minus the packager-block-entity
     * part). Redstone-link connections are stored on the link, so {@link #targetedBy()} holds only ingredient gauges.
     */
    public boolean isMissingAddress() {
        return !targetedBy().isEmpty() && isActive() && recipeAddress.isBlank();
    }

    /** SEND-mode redstone-link power source: an active target ({@code count != 0}) that is currently in stock. */
    public boolean powersLink() {
        return count != 0 && satisfied;
    }

    /**
     * Whether a powered RECEIVE-mode redstone link is wired to this gauge (which gates its requests via
     * {@link #redstonePowered}). The link holds the connection ({@code link.targetedBy[gauge]}), so this gauge's
     * {@link #targeting} lists the link. Server-only ({@code controller} is null on the client snapshot).
     */
    public boolean isGatedByLink() {
        if (controller == null) return false;
        for (VirtualComponentPosition linkPos : targeting())
            if (controller.components.get(linkPos) instanceof VirtualRedstoneLinkBehaviour link && link.gatesGauge())
                return true;
        return false;
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

        // A fluid filter's stockLevel is millibuckets — shown in the gauge's own unit (mB/B); items show
        // count/stacks + suffix.
        String inStorage = isInfiniteStock() ? "∞"
            : FluidCompat.isFluidFilter(filter) ? unit.formatInUnit(stockLevel)
            : stockLevel / unit.toCountMultiplier(filter) + unit.suffix;

        if (!isActive())
            return CreateLang.text(inStorage).color(0xF1EFE8).component();

        return CreateLang.text(inStorage).color(satisfied ? 0xD7FFA8 : promisedSatisfied ? 0xFFCD75 : 0xFFBFA8)
            .add(CreateLang.text(promisedCount == 0 ? "" : "⏶"))
            .add(CreateLang.text("/").style(ChatFormatting.WHITE))
            .add(CreateLang.text(count + unit.suffix).color(requestMode.isPassive() ? 0x9ECFFC : 0xF1EFE8))
            .component();
    }

    // ── Tick ───────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        // Passive target is recomputed inside tickStorageMonitor (it needs the same fresh stock/promise
        // figures), so it stays in sync with the satisfaction state in a single pass.
        tickStorageMonitor();
        tickRequests();
    }

    /**
     * Controller pre-pass hook: refresh the Passive Request target.
     */
    public void computeDemand() {
        if (!requestMode.isPassive() || controller == null) return;
        int demand = 0;
        for (VirtualComponentPosition parentPos : targeting()) {
            if (!(controller.components.get(parentPos) instanceof VirtualGaugeBehaviour parent)) continue;
            if (!parent.isDemandingIngredients()) continue;   // consumer satisfied/idle → needs nothing now
            if (!(parent.targetedBy().get(position) instanceof LogisticsConnection conn)) continue;
            int parentBatch = parent.activeCraftingArrangement.isEmpty() ? 1 : Math.max(1, parent.craftBatch);
            demand += conn.amount() * parentBatch;
        }
        // External demand from open Production Orders (Stock Keeper blueprints targeting THIS gauge): a player
        // order acts like another downstream consumer, so the passive gauge produces to satisfy it too. Only
        // orderable gauges have a patternId, so non-orderable ones skip this entirely.
        if (patternId != null && controller.getLevel() != null)
            demand += ProductionOrderManager.externalDemand(controller.getLevel(), networkId, patternId);
        count = demand <= 0 ? 0 : Math.ceilDiv(demand, unit.toCountMultiplier(filter));
    }

    /**
     * Whether this gauge still needs more of its ingredients — i.e. it is configured to request and isn't
     * yet covered by stock + open promises. An auto producer feeding this gauge calls it to size its own
     * demand.
     */
    private boolean isDemandingIngredients() {
        if (count == 0 || waitingForNetwork || redstonePowered || controllerPowered || isMissingAddress())
            return false;
        return !promisedSatisfied;
    }

    private void tickStorageMonitor() {
        if (filter.isEmpty()) {
            satisfied = true;
            promisedSatisfied = true;
            waitingForNetwork = false;
            stockLevel = 0;
            promisedCount = 0;
            return;
        }

        int unloadedLinkCount = getUnloadedLinks();

        if (unloadedLinkCount == 0 && lastReportedUnloadedLinks != 0)
            LogisticsManager.SUMMARIES.invalidate(networkId);

        // Apply a pending player-forced clear (side effect only). Its promised drop is absorbed by the hold
        // below exactly like a settlement or an expiry — no per-cause disambiguation is needed any more.
        if (forceClearPromises){
            forceClearPromise(networkId, filter);
            timer = getConfigRequestIntervalInTicks() / 2;
            forceClearPromises = false;
        }

        // The loose cache returns a NEW summary instance when it recomputes, so reference identity is the
        // precise "stock reading just refreshed (authoritative)" signal.
        InventorySummary summary = getRelevantSummary();
        boolean refreshed = summary != lastSummaryRef;
        lastSummaryRef = summary;

        // A fluid's network availability comes from the per-link summaries (getStockOf) — the exact path CFL's
        // own fluid logic uses — rather than the merged network summary, which may not carry the virtual tanks.
        int inStorage = networkStockOf(filter);
        int promised = getPromised();
        int rawSum = inStorage + promised;

        // Snapshot the committed state for the satisfy chime and the sync dirty-check.
        boolean wasSatisfied = satisfied;
        int prevStock = stockLevel, prevPromised = promisedCount;
        boolean prevPromiseSatisfy = promisedSatisfied, prevWait = waitingForNetwork;

        if (inStorage >= stockLevel)   { stockLevel = inStorage; stockDropPending = false; }
        else if (stockDropPending)     { stockLevel = inStorage; stockDropPending = false; }
        else if (refreshed)            { stockDropPending = true; }
        if (rawSum >= heldSum)         { heldSum = rawSum; sumDropPending = false; }
        else if (sumDropPending)       { heldSum = rawSum; sumDropPending = false; }
        else if (refreshed)            { sumDropPending = true; }

        promisedCount = promised;              // the open-promise number is always shown live (the ⏶ box)

        int demand = count * unit.toCountMultiplier(filter);
        satisfied = stockLevel >= demand;
        promisedSatisfied = heldSum >= demand;
        waitingForNetwork = unloadedLinkCount > 0;

        if (stockLevel == prevStock && promisedCount == prevPromised && satisfied == wasSatisfied
                && promisedSatisfied == prevPromiseSatisfy && waitingForNetwork == prevWait
                && lastReportedCount == count && lastReportedUnloadedLinks == unloadedLinkCount)
            return;

        lastReportedCount = count;
        lastReportedUnloadedLinks = unloadedLinkCount;

        // Bulb-update chime on the unsatisfied → satisfied transition (Create's tickStorageMonitor),
        // played at the controller block so nearby players hear the gauge light up.
        if (!wasSatisfied && satisfied && demand > 0) {
            controller.playSound(AllSoundEvents.CONFIRM, 0.3f, 1f);
            controller.playSound(AllSoundEvents.CONFIRM_2, 0.5f, 0.575f);
        }

        controller.setChanged();
        controller.sendData();
    }

    // ── Storage queries ────────────────────────────────────────────────────

    /**
     * Current network stock of {@code stack}.
     */
    protected int networkStockOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (FluidCompat.isFluidFilter(stack))
            return LogisticsManager.getStockOf(networkId, stack, null);
        // Ignore-data: count every variant of this item type. Sum only this item's bucket (getItemMap is keyed
        // by Item) rather than getTotalOfMatching, which would scan the WHOLE summary every tick — this keeps
        // the cost at O(variants of the item), the same class as getCountOf.
        if (ignoreData) {
            List<BigItemStack> variants = getRelevantSummary().getItemMap().get(stack.getItem());
            if (variants == null) return 0;
            int total = 0;
            for (BigItemStack b : variants) total += b.count;
            return total;
        }
        return getRelevantSummary().getCountOf(stack);
    }

    private InventorySummary getRelevantSummary() {
        return LogisticsManager.getSummaryOfNetwork(networkId, false);
    }

    public int getPromised() {
        if (filter.isEmpty()) return 0;
        RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(networkId);
        if (promises == null) return 0;
        int expiry = getPromiseExpiryTimeInTicks();
        if (!ignoreData || FluidCompat.isFluidFilter(filter))
            return promises.getTotalPromisedAndRemoveExpired(filter, expiry);
        // Ignore-data: sum the promises of every distinct variant of this item type. The queue matches by
        // exact components, so total each distinct variant separately (which also expires them per-variant).
        List<ItemStack> variants = new ArrayList<>();
        for (RequestPromise p : promises.flatten(false)) {
            ItemStack s = p.promisedStack.stack;
            if (!s.is(filter.getItem())) continue;
            boolean known = false;
            for (ItemStack v : variants)
                if (ItemStack.isSameItemSameComponents(v, s)) { known = true; break; }
            if (!known) variants.add(s);
        }
        int total = 0;
        for (ItemStack v : variants)
            total += promises.getTotalPromisedAndRemoveExpired(v, expiry);
        return total;
    }

    public boolean isFluidGauge() {
        return false;
    }

    public boolean supportsIgnoreData() {
        return true;
    }

    protected VirtualComponentBehaviour.Type componentType() {
        return TYPE;
    }

    public void forceClearPromise(UUID networkId, ItemStack filter) {
        RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(networkId);
        if (promises != null) promises.forceClear(filter);
    }

    public void addPromise(UUID networkId, ItemStack filter, boolean ignoreData, int amount) {
        RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(networkId);
        if (promises != null) {
            ItemStack promiseStack = filter.copy();
            // Ignore-data: mark the promise so the queue clears it when ANY variant of the item type arrives
            // (the produced output may carry data the pure-form filter doesn't). See RequestPromiseQueueMixin.
            if (ignoreData) promiseStack.set(CreateFactoryController.FUZZY_PROMISE.get(), true);
            promises.add(new RequestPromise(new BigItemStack(promiseStack, amount)));
        }
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
        // ingredient demand and the produced output are both multiplied by this batch count. Batching is
        // forced off when an ignore-data ingredient is involved (its pattern cells resolve per request).
        boolean crafting = !activeCraftingArrangement.isEmpty();
        int batch = crafting && !craftingUsesIgnoreData() ? Math.max(1, craftBatch) : 1;

        Map<UUID, List<BigItemStack>> demandByNetwork = new LinkedHashMap<>();
        // The crafting pattern actually dispatched. For an ignore-data ingredient the stored pattern holds the
        // pure-form item, but the package must carry (and the crafter pattern must match) concrete in-stock
        // variants — so resolve those cells against live stock on every request.
        List<ItemStack> craftPattern = crafting ? new ArrayList<>(activeCraftingArrangement.size()) : null;

        if (crafting) {
            Map<VirtualGaugeBehaviour, List<BigItemStack>> variantPools = new HashMap<>();
            for (ItemStack cell : activeCraftingArrangement) {
                if (cell.isEmpty()) { craftPattern.add(ItemStack.EMPTY); continue; }
                VirtualGaugeBehaviour source = findIngredientSource(cell);
                if (source != null && source.ignoreData && !FluidCompat.isFluidFilter(source.filter)) {
                    List<BigItemStack> pool = variantPools.computeIfAbsent(source, this::variantPool);
                    ItemStack chosen = takeVariant(pool, batch);
                    if (chosen.isEmpty()) { setConnectionsSuccess(false); return; }  // no single variant has enough
                    craftPattern.add(chosen.copyWithCount(1));
                    addDemand(demandByNetwork, source.networkId, chosen, batch);
                } else {
                    craftPattern.add(cell.copyWithCount(1));
                    if (source != null) addDemand(demandByNetwork, source.networkId, cell, batch);
                }
            }
        } else {
            for (Map.Entry<VirtualComponentPosition, Connection> e : targetedBy().entrySet()) {
                if (!(controller.components.get(e.getKey()) instanceof VirtualGaugeBehaviour source)) continue;
                if (!(e.getValue() instanceof LogisticsConnection conn)) continue;
                ItemStack ingredient = source.filter;
                if (ingredient.isEmpty()) continue;
                int needed = conn.amount();
                // An ignore-data ingredient may be served by any variant of its item type: resolve the demand
                // into the concrete in-stock variants (Create's request/extraction matches exact components), so
                // the recipe consumes whatever is actually on the network. Insufficient total → flash red, abort.
                if (source.ignoreData && !FluidCompat.isFluidFilter(ingredient)) {
                    InventorySummary summary = LogisticsManager.getSummaryOfNetwork(source.networkId, true);
                    List<BigItemStack> variants = summary.getItemMap().get(ingredient.getItem());
                    int remaining = needed;
                    if (variants != null) for (BigItemStack v : variants) {
                        if (remaining <= 0) break;
                        int take = Math.min(remaining, v.count);
                        if (take <= 0) continue;
                        addDemand(demandByNetwork, source.networkId, v.stack, take);
                        remaining -= take;
                    }
                    if (remaining > 0) { setConnectionsSuccess(false); return; }
                } else {
                    addDemand(demandByNetwork, source.networkId, ingredient, needed);
                }
            }
        }
        if (demandByNetwork.isEmpty()) return;

        for (Map.Entry<UUID, List<BigItemStack>> netEntry : demandByNetwork.entrySet()) {
            InventorySummary summary = LogisticsManager.getSummaryOfNetwork(netEntry.getKey(), true);
            for (BigItemStack need : netEntry.getValue()) {
                // A Repackaged generic fluid filter rides Deployer's fluid logistics, so its stock (mB) must be read
                // there — Create's item logistics knows nothing about it (would read 0 → always flash red).
                // CFL/CreateFluid fluid filters read via getStockOf (per-link), like the storage monitor — the merged
                // accurate summary may not carry the virtual tanks; items use the accurate summary.
                int available;
                if (FluidCompat.isGenericFluidFilter(need.stack))
                    available = FluidCompat.repackagedFluidStock(netEntry.getKey(), need.stack);
                else if (FluidCompat.isFluidFilter(need.stack))
                    available = LogisticsManager.getStockOf(netEntry.getKey(), need.stack, null);
                else
                    available = summary.getCountOf(need.stack);
                if (available < need.count) {        // insufficient → flash red
                    setConnectionsSuccess(false);
                    return;
                }
            }
        }

        List<PackageOrderWithCrafts.CraftingEntry> crafts = !crafting
            ? PackageOrderWithCrafts.empty().orderedCrafts()
            // CraftingEntry.count is the number of times to run this 3×3 pattern — i.e. the batch. The pattern
            // is the per-request-resolved one (concrete variants for any ignore-data cells).
            : List.of(new PackageOrderWithCrafts.CraftingEntry(
                new PackageOrder(craftPattern.stream()
                    .map(s -> new BigItemStack(s.copyWithCount(1)))
                    .toList()),
                batch));

        List<Multimap<PackagerBlockEntity, PackagingRequest>> dispatch = new ArrayList<>();
        List<Map.Entry<UUID, List<BigItemStack>>> fluidNetworks = new ArrayList<>();            // CFL/CreateFluid (Create logistics)
        for (Map.Entry<UUID, List<BigItemStack>> netEntry : demandByNetwork.entrySet()) {
            // A network with Repackaged generic fluid ingredients is dispatched as ONE unified order (its items +
            // fluids), so Repackaged's Package Shelf groups the resulting fragments under a single orderId. The fluids
            // ride Deployer's logistics; the items ride Create's — broadcastAllPackageRequest spans both with a shared
            // orderId, which dispatching them separately would not.
            List<BigItemStack> repackagedFluids = netEntry.getValue().stream()
                .filter(b -> FluidCompat.isGenericFluidFilter(b.stack)).toList();
            if (!repackagedFluids.isEmpty()) {
                List<BigItemStack> items = netEntry.getValue().stream()
                    .filter(b -> !FluidCompat.isGenericFluidFilter(b.stack)).toList();
                PackageOrderWithCrafts itemOrder = new PackageOrderWithCrafts(new PackageOrder(items), crafts);
                if (!FluidCompat.broadcastRepackagedRecipe(netEntry.getKey(), itemOrder, repackagedFluids, recipeAddress)) {
                    setConnectionsSuccess(false);
                    return;
                }
                continue;
            }
            // A virtual fluid tank ingredient (CFL/CreateFluid) must be dispatched via broadcastPackageRequest — see below.
            if (netEntry.getValue().stream().anyMatch(b -> FluidCompat.isFluidFilter(b.stack))) {
                fluidNetworks.add(netEntry);
                continue;
            }
            PackageOrderWithCrafts order =
                new PackageOrderWithCrafts(new PackageOrder(netEntry.getValue()), crafts);
            Multimap<PackagerBlockEntity, PackagingRequest> found =
                LogisticsManager.findPackagersForRequest(netEntry.getKey(), order, null, recipeAddress);
            if (found.isEmpty()) return;     // no packager could serve this network
            for (PackagerBlockEntity packager : found.keySet())
                if (packager.isTooBusyFor(RequestType.RESTOCK)) return;
            dispatch.add(found);
        }

        for (Map.Entry<UUID, List<BigItemStack>> netEntry : fluidNetworks) {
            PackageOrderWithCrafts order =
                new PackageOrderWithCrafts(new PackageOrder(netEntry.getValue()), crafts);
            if (!LogisticsManager.broadcastPackageRequest(netEntry.getKey(), RequestType.RESTOCK, order, null, recipeAddress)) {
                setConnectionsSuccess(false);
                return;
            }
        }

        // All clear — perform the item requests and promise the produced output.
        for (Multimap<PackagerBlockEntity, PackagingRequest> req : dispatch)
            LogisticsManager.performPackageRequests(req);
        setConnectionsSuccess(true);   // ingredients were dispatched → flash white
        addPromise(networkId, filter, ignoreData, Math.max(1, recipeOutput) * batch);
        controller.setChanged();
    }

    /**
     * Flags every incoming connection's last-request outcome (Create's {@code FactoryPanelConnection
     * #success}), which the connection renderer reads to pulse the line white (could supply) or red
     * (insufficient). Synced to clients via {@code toClientNBT} only when it actually changes.
     */
    private void setConnectionsSuccess(boolean value) {
        boolean changed = false;
        for (Connection conn : targetedBy().values())
            if (conn instanceof LogisticsConnection lc && lc.success != value) { lc.success = value; changed = true; }
        if (changed) {
            controller.setChanged();
            controller.sendData();
        }
    }

    /** Whether any wired-in ingredient is an (item) ignore-data gauge — disables crafting batch & grid resize. */
    private boolean craftingUsesIgnoreData() {
        for (VirtualComponentPosition p : targetedBy().keySet())
            if (controller.components.get(p) instanceof VirtualGaugeBehaviour s
                    && s.ignoreData && !FluidCompat.isFluidFilter(s.filter)) return true;
        return false;
    }

    /** The wired-in source gauge whose filter matches a crafting pattern cell (exact components), or null. */
    private VirtualGaugeBehaviour findIngredientSource(ItemStack cell) {
        for (VirtualComponentPosition p : targetedBy().keySet())
            if (controller.components.get(p) instanceof VirtualGaugeBehaviour s
                    && !s.filter.isEmpty() && ItemStack.isSameItemSameComponents(s.filter, cell))
                return s;
        return null;
    }

    /** A mutable snapshot of {@code source}'s in-stock variants of its item type (count-1 stack templates). */
    private List<BigItemStack> variantPool(VirtualGaugeBehaviour source) {
        InventorySummary summary = LogisticsManager.getSummaryOfNetwork(source.networkId, true);
        List<BigItemStack> variants = summary.getItemMap().get(source.filter.getItem());
        List<BigItemStack> pool = new ArrayList<>();
        if (variants != null) for (BigItemStack b : variants)
            pool.add(new BigItemStack(b.stack.copyWithCount(1), b.count));
        return pool;
    }

    /** Takes {@code amount} of a single variant from the pool (the first variant with enough), returning its
     *  stack template; empty if no single variant can cover the amount. */
    private static ItemStack takeVariant(List<BigItemStack> pool, int amount) {
        for (BigItemStack b : pool)
            if (b.count >= amount) { b.count -= amount; return b.stack; }
        return ItemStack.EMPTY;
    }

    /** Adds {@code count} of {@code stack} to a network's demand list, merging into an existing exact-component
     *  entry so repeated/equivalent ingredients collapse into one {@link BigItemStack}. */
    private static void addDemand(Map<UUID, List<BigItemStack>> demandByNetwork, UUID network,
                                  ItemStack stack, int count) {
        List<BigItemStack> demands = demandByNetwork.computeIfAbsent(network, k -> new ArrayList<>());
        for (BigItemStack b : demands)
            if (ItemStack.isSameItemSameComponents(b.stack, stack)) { b.count += count; return; }
        demands.add(new BigItemStack(stack.copyWithCount(1), count));
    }

    private void resetTimer() {
        timer = getConfigRequestIntervalInTicks();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    protected int getConfigRequestIntervalInTicks() {
        return AllConfigs.server().logistics.factoryGaugeTimer.get();
    }

    protected int getPromiseExpiryTimeInTicks() {
        if (promiseClearingInterval == -1) return -1;
        if (promiseClearingInterval == 0) return 20 * 30;
        return promiseClearingInterval * 20 * 60;
    }

    // ── NBT ────────────────────────────────────────────────────────────────

    @Override
    public CompoundTag toNBT(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", componentType().id());
        tag.put("Pos", position.toNBT());
        tag.putString("GaugeItem", itemId.toString());
        tag.putUUID("Network", networkId);
        if (patternId != null) tag.putUUID("PatternId", patternId);   // only orderable gauges carry an id

        tag.put("Filter", filter.saveOptional(registries));
        tag.putBoolean("IgnoreData", ignoreData);
        tag.putInt("Count", count);
        tag.putString("Unit", unit.name());
        tag.putString("RequestMode", requestMode.name());

        tag.putBoolean("Satisfied", satisfied);
        tag.putBoolean("PromisedSatisfied", promisedSatisfied);
        tag.putBoolean("Waiting", waitingForNetwork);
        tag.putInt("Timer", timer);
        tag.putString("RecipeAddress", recipeAddress);
        tag.putInt("RecipeOutput", recipeOutput);
        tag.putInt("CraftBatch", craftBatch);
        tag.putInt("CraftDimension", craftDimension);
        tag.putInt("PromiseClearingInterval", promiseClearingInterval);

        // Connections live in the controller's central graph (written there), not per-component.

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
    public CompoundTag toItemNBT(HolderLookup.Provider registries) {
        CompoundTag tags = super.toItemNBT(registries);
        tags.remove("PatternId");
        tags.remove("Satisfied");
        tags.remove("PromisedSatisfied");
        tags.remove("Waiting");
        tags.remove("Timer");
        return tags;
    }

    @Override
    public CompoundTag toClientNBT(net.minecraft.core.HolderLookup.Provider registries) {
        // Only what the canvas needs: identity/texture, filter (icon + set-item check), status
        // (indicator colour + gating), the address (status `isMissingAddress`), and incoming
        // connections (arrow rendering). Server-only tick state and detailed recipe config are
        // omitted — those are pulled on demand when a config overlay opens.
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", componentType().id());
        tag.put("Pos", position.toNBT());
        tag.putString("GaugeItem", itemId.toString());
        tag.putUUID("Network", networkId);
        // patternId is server-only — clients never need it.

        tag.put("Filter", filter.saveOptional(registries));
        tag.putBoolean("IgnoreData", ignoreData);
        tag.putInt("Count", count);
        tag.putString("Unit", unit.name());
        tag.putString("RequestMode", requestMode.name());

        tag.putBoolean("Satisfied", satisfied);
        tag.putBoolean("PromisedSatisfied", promisedSatisfied);
        tag.putBoolean("Waiting", waitingForNetwork);
        tag.putBoolean("RedstonePowered", redstonePowered);   // link-driven; synced for the gray path + bulb
        //tag.putBoolean("ControllerPowered", true);
        tag.putString("RecipeAddress", recipeAddress);
        // Recipe-config fields the ConfigureRecipeScreen edits — synced so the overlay can show
        // current values without a separate on-demand fetch.
        tag.putInt("RecipeOutput", recipeOutput);
        tag.putInt("CraftBatch", craftBatch);
        tag.putInt("CraftDimension", craftDimension);
        tag.putInt("PromiseClearingInterval", promiseClearingInterval);
        tag.putInt("Stock", stockLevel);
        tag.putInt("Promised", promisedCount);
        tag.putLong("LastRequestTick", lastRequestTick);
        tag.put("CraftingArrangement", writeStacks(activeCraftingArrangement, registries));
        // Connections live in the controller's central graph (synced separately), not per-component.

        return tag;
    }

    public static VirtualGaugeBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                                CompoundTag tag,
                                                net.minecraft.core.HolderLookup.Provider registries) {
        VirtualComponentPosition pos = VirtualComponentPosition.fromNBT(tag.getCompound("Pos"));
        ResourceLocation gaugeItemId = ResourceLocation.parse(tag.getString("GaugeItem"));
        UUID networkId = tag.getUUID("Network");

        VirtualGaugeBehaviour b = new VirtualGaugeBehaviour(controller, pos, networkId, gaugeItemId);
        b.filter = ItemStack.parseOptional(registries, tag.getCompound("Filter"));
        b.ignoreData = tag.getBoolean("IgnoreData");
        b.count = tag.getInt("Count");
        b.unit = ThresholdUnit.fromName(tag.getString("Unit"));
        if (tag.contains("RequestMode"))
            b.requestMode = RequestMode.fromName(tag.getString("RequestMode"));
        else   // legacy migration from the old Passive boolean
            b.requestMode = tag.getBoolean("Passive")
                ? RequestMode.PASSIVE : RequestMode.NORMAL;
        if (tag.hasUUID("PatternId"))
            b.patternId = tag.getUUID("PatternId");
        b.stockLevel = tag.getInt("Stock");
        b.promisedCount = tag.getInt("Promised");
        b.lastRequestTick = tag.getLong("LastRequestTick");
        b.activeCraftingArrangement = readStacks(tag.getList("CraftingArrangement", Tag.TAG_COMPOUND), registries);

        b.satisfied = tag.getBoolean("Satisfied");
        b.promisedSatisfied = tag.getBoolean("PromisedSatisfied");
        b.waitingForNetwork = tag.getBoolean("Waiting");
        b.redstonePowered = tag.getBoolean("RedstonePowered");
        b.controllerPowered = tag.getBoolean("ControllerPowered");
        b.timer = tag.getInt("Timer");
        b.recipeAddress = tag.getString("RecipeAddress");
        b.recipeOutput = tag.getInt("RecipeOutput");
        b.craftBatch = Math.max(1, tag.getInt("CraftBatch"));   // absent (legacy data) → 1
        b.craftDimension = Math.max(0, tag.getInt("CraftDimension"));   // 0 = not a large recipe / unset
        b.promiseClearingInterval = tag.getInt("PromiseClearingInterval");
        // Connections are loaded centrally by the controller / menu, not from the component tag.

        // Server-side load (placement via setup, or chunk reload): delay the first request by a full interval.
        // A freshly loaded gauge's network stock summary can read 0 for a tick or two before it populates, and
        // with timer==0 (setup strips it) tickRequests would fire immediately even though stock is sufficient.
        // Throttling the first attempt gives tickStorageMonitor time to read real stock and mark it satisfied.
        // (controller is null only on the client snapshot, which never ticks — leave its timer as-is.)
        if (controller != null) b.timer = b.getConfigRequestIntervalInTicks();

        return b;
    }
}
