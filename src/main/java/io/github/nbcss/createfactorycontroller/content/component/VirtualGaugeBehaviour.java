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
import io.github.nbcss.createfactorycontroller.ServerConfig;
import io.github.nbcss.createfactorycontroller.content.helper.ArrangementUnpackingHandler;
import io.github.nbcss.createfactorycontroller.content.GaugeWorkMode;
import io.github.nbcss.createfactorycontroller.content.RequestMode;
import io.github.nbcss.createfactorycontroller.content.ThresholdUnit;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionCapability;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionValue;
import io.github.nbcss.createfactorycontroller.content.component.connection.LogisticsConnection;
import io.github.nbcss.createfactorycontroller.content.component.connection.RedstoneConnection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ValidationResult;
import io.github.nbcss.createfactorycontroller.content.display.DisplayDataProvider;
import io.github.nbcss.createfactorycontroller.content.display.DisplayMode;
import io.github.nbcss.createfactorycontroller.content.production.ProductionOrderManager;
import io.github.nbcss.createfactorycontroller.content.promise.ControllerPromise;
import io.github.nbcss.createfactorycontroller.content.promise.PromiseCounts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VirtualGaugeBehaviour extends AbstractVirtualComponent implements DisplayDataProvider {
    public static final VirtualComponentBehaviour.Type TYPE = new VirtualComponentBehaviour.Type(){

        @Override
        public String id() {
            return "GAUGE";
        }

        @Override
        public List<ResourceLocation> items() {
            return List.of(AllBlocks.FACTORY_GAUGE.getId());
        }

        @Override public int color() { return 0xFBDC7D; }

        @Override
        public boolean isRequireNetwork() {
            return true;
        }

        @Override
        public VirtualComponentBehaviour create(FactoryControllerBlockEntity controller,
                                                VirtualComponentPosition pos,
                                                Item item,
                                                UUID networkId) {
            return new VirtualGaugeBehaviour(controller, pos, networkId, item);
        }

        @Override
        public VirtualComponentBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                                 CompoundTag tag,
                                                 HolderLookup.Provider registries) {
            return VirtualGaugeBehaviour.fromNBT(controller, tag, registries);
        }

        @Override
        public VirtualComponentBehaviour fromClient(net.minecraft.network.RegistryFriendlyByteBuf buf) {
            VirtualComponentPosition pos = SyncCodecs.readPos(buf);
            Item item = BuiltInRegistries.ITEM.get(buf.readResourceLocation());
            VirtualGaugeBehaviour g = new VirtualGaugeBehaviour(null, pos, buf.readUUID(), item);
            g.readClientBody(buf);
            return g;
        }
    };

    public static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "factory_controller/factory_gauge");
    public static final ResourceLocation FRONT_TEXTURE = TEXTURE.withSuffix("/front");

    private static final GaugeFilterResolver ITEM_FILTER_RESOLVER = new GaugeFilterResolver() {
        @Override public boolean acceptsFilter(ItemStack filter) { return true; }
        @Override public boolean supportsIgnoreData() { return true; }
        @Override public boolean acceptsItemDrop() { return true; }
        @Override public boolean acceptsFluidDrop() { return FluidCompat.isLoaded(); }

        @Override
        public ItemStack fromCarried(ItemStack carried, int mouseButton) {
            if (FluidCompat.isLoaded() && mouseButton == 1) {
                FluidStack fluid = FluidCompat.fluidInContainer(carried);
                if (!fluid.isEmpty()) return FluidCompat.makeFluidFilter(fluid);
            }
            return carried;
        }

        @Override
        public ItemStack fromFluid(FluidStack fluid) {
            return FluidCompat.makeFluidFilter(fluid);
        }
    };

    private static final LogisticsControl ITEM_LOGISTICS = new LogisticsControl() {
        @Override
        public int stockOf(VirtualGaugeBehaviour gauge, ItemStack stack) {
            if (stack.isEmpty()) return 0;
            if (FluidCompat.isFluidFilter(stack))
                return LogisticsManager.getStockOf(gauge.networkId, stack, null);
            if (gauge.ignoreData) {
                List<BigItemStack> variants = gauge.getRelevantSummary().getItemMap().get(stack.getItem());
                if (variants == null) return 0;
                int total = 0;
                for (BigItemStack b : variants) total += b.count;
                return total;
            }
            return gauge.getRelevantSummary().getCountOf(stack);
        }

        @Override
        public int promised(VirtualGaugeBehaviour gauge) {
            if (gauge.filter.isEmpty()) return 0;
            RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(gauge.networkId);
            if (promises == null) return 0;
            // Pass EXPIRED_TICKS as the expiry: this removes only OUR promises that have latched to that sentinel
            // (past their own ttl) — a base promise's real ticksExisted never reaches it, so we never clear another
            // gauge's or Create's live promises. Our not-yet-aged promises sit at ticksExisted 0 and are untouched.
            if (!gauge.ignoreData || FluidCompat.isFluidFilter(gauge.filter))
                return promises.getTotalPromisedAndRemoveExpired(gauge.filter, ControllerPromise.EXPIRED_TICKS);
            List<ItemStack> variants = new ArrayList<>();
            for (RequestPromise p : promises.flatten(false)) {
                ItemStack s = p.promisedStack.stack;
                if (!s.is(gauge.filter.getItem())) continue;
                boolean known = false;
                for (ItemStack v : variants)
                    if (ItemStack.isSameItemSameComponents(v, s)) { known = true; break; }
                if (!known) variants.add(s);
            }
            int total = 0;
            for (ItemStack v : variants)
                total += promises.getTotalPromisedAndRemoveExpired(v, ControllerPromise.EXPIRED_TICKS);
            return total;
        }

        @Override
        public void forceClearPromise(UUID networkId, ItemStack filter) {
            RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(networkId);
            if (promises != null) promises.forceClear(filter);
        }

        @Override
        public void addPromise(UUID networkId, ItemStack filter, boolean ignoreData, int amount,
                               String ownerKey, String targetAddress, int ttl) {
            RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(networkId);
            if (promises != null) {
                ItemStack promiseStack = filter.copy();
                if (ignoreData) promiseStack.set(CreateFactoryController.FUZZY_PROMISE.get(), true);
                promises.add(new ControllerPromise(new BigItemStack(promiseStack, amount), ownerKey, targetAddress, ttl));
            }
        }
    };

    /** Max ingredient (gauge → gauge) inputs, matching Create's factory-panel limit. Redstone-link wires are stored
     *  on the link and don't count toward this. */
    public static final int MAX_INGREDIENTS = 9;

    /** Per-ingredient millibucket cap (90 B) — one grid cell of a fluid ingredient. */
    public static final int FLUID_INGREDIENT_CAP_MB = 90_000;
    /** Produced-output millibucket cap (10 B) for a fluid gauge — mirrors the recipe screen's output cap. */
    public static final int FLUID_OUTPUT_CAP_MB = 10_000;
    /** Crafting mode: max crafts one request may carry (batch × multiplier ≤ this). */
    public static final int MAX_CRAFT_OUTPUT = 64;

    // Identity
    public final UUID networkId;
    /** Stable server-side identity for promises and orderable production patterns. Not synced/exported. */
    public UUID gaugeId = null;

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
    /** Which ingredient work mode this gauge is in (regular / crafting / custom arrangement). */
    public GaugeWorkMode mode = GaugeWorkMode.REGULAR;
    /** CUSTOM mode: the explicit 3×3 slot layout (index = grid/box slot; {@link RecipeSlot#EMPTY} for a gap).
     *  Empty in every other mode. */
    public List<RecipeSlot> recipeSlots = new ArrayList<>();

    // Computed status (server-side, synced to client)
    public boolean satisfied = false;
    public boolean promisedSatisfied = false;
    public boolean waitingForNetwork = false;
    /** Driven by a powered RECEIVE-mode redstone link wired to this gauge — recomputed each controller pre-pass. */
    public boolean redstonePowered = false;

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
    /** Total-demand strategy only: the raw target the controller's {@link io.github.nbcss.createfactorycontroller.content.production.PassiveDemandSolver}
     *  computed for this gauge this tick (in units). Folded into {@link #count} by the storage monitor, holding a
     *  DECREASE against the summary-refresh signal (see {@link #demandDropPending}) so the brief downstream-promise
     *  demand dip doesn't flicker the target. */
    public int passiveDemandTarget = 0;
    /** One-refresh hold for a {@link #passiveDemandTarget} decrease, mirroring {@link #stockDropPending}. */
    private boolean demandDropPending = false;
    /** Last redstone OUTPUT state pushed to wired send-links; gates {@link #updateRedstoneOutput} so a steady gauge
     *  never re-walks its outgoing wires. {@code null} until first computed (forces an initial publish). */
    private RedstoneConnection.State lastRedstoneOutput = null;
    protected int timer = 0;
    /** Per-gauge request-timer reset value (ticks). 0 = unspecified → use Create's {@code factoryGaugeTimer} config.
     *  Only a value &gt; 0 overrides. Not client-synced (no set/view UI yet); the animation gets it via the poll. */
    public int customRequestTimer = 0;
    public long lastRequestTick = 0;
    private boolean forceClearPromises = false;
    public String recipeAddress = "";
    public int recipeOutput = 1;
    /** Crafts carried per request package in mechanical-crafting mode (≥1); 1 = a single craft. */
    public int craftBatch = 1;
    /** User-set ceiling on how many times one request may be scaled up in a single dispatch. */
    public int maxRequestMultiplier = 1;
    public int craftDimension = 0;
    public int promiseClearingInterval = -1;
    /** Max in-flight requests before this gauge pauses dispatching; 0 = unlimited. */
    public int promiseLimit = 0;
    /** When true, the limit counts requests network-wide to this gauge's address (shared quota across gauges);
     *  when false, only this gauge's own in-flight requests. */
    public boolean promiseLimitByAddress = false;

    public VirtualGaugeBehaviour(FactoryControllerBlockEntity controller, VirtualComponentPosition position,
                                 UUID networkId, Item gaugeItem) {
        super(controller, position, gaugeItem);
        this.networkId = networkId;
    }

    @Override
    public ResourceLocation getTexture() {
        return TEXTURE;
    }

    @Override public int getColor() { return TYPE.color(); }

    public ResourceLocation getFrontTexture() {
        return FRONT_TEXTURE;
    }

    /** The filter of the gauge at {@code pos}, resolved side-agnostically (server: controller; client: menu), or empty. */
    private ItemStack filterAt(VirtualComponentPosition pos) {
        return siblingAt(pos) instanceof VirtualGaugeBehaviour g ? g.filter : ItemStack.EMPTY;
    }

    public Component getFilterName() {
        return filter.isEmpty() ? ItemStack.EMPTY.getHoverName() : FluidCompat.filterName(filter);
    }

    /** Info line: the monitored filter item (omitted when no filter is set). */
    @Override
    public List<Component> infoTooltip() {
        if (filter.isEmpty()) return List.of();
        return List.of(Component.translatable("createfactorycontroller.gui.info.monitoring",
                FluidCompat.filterName(filter).copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GRAY));
    }

    /** A gauge outputs/accepts item-fluid ingredients (LOGISTICS) and is read/gated by redstone (REDSTONE); both
     *  types defer their direction (BOTH), so a wired link's mode dictates the redstone arrow. */
    @Override
    public List<ConnectionCapability> ports() {
        return List.of(new ConnectionCapability(LogisticsConnection.TYPE, ConnectionCapability.Role.BOTH),
                       new ConnectionCapability(RedstoneConnection.TYPE, ConnectionCapability.Role.BOTH));
    }

    /** As a LOGISTICS source the gauge feeds its stock, so it must carry a filter. (REDSTONE source: no rule.) */
    @Override
    public ValidationResult validateAsSource(Connection.Type type, VirtualComponentBehaviour sink) {
        if (filter.isEmpty())
            return ValidationResult.fail(() -> CreateLang.translate("factory_panel.no_item")
                    .style(ChatFormatting.RED).component());
        return ValidationResult.SUCCESS;
    }

    /** As a LOGISTICS sink (the consumer) the gauge caps its ingredient grid slots. (REDSTONE sink: no rule.)
     *  In CUSTOM mode occupancy is the explicit cell count — the regular {@code ceil(amount / stackSize)}
     *  projection undercounts a grid holding partial stacks or copied cells, which would admit a wire that has
     *  no free cell to land in. */
    @Override
    public ValidationResult validateAsSink(Connection.Type type, VirtualComponentBehaviour source) {
        if (filter.isEmpty())
            return ValidationResult.fail(() -> CreateLang.translate("factory_panel.no_item")
                    .style(ChatFormatting.RED).component());
        int used = mode == GaugeWorkMode.CUSTOM
            ? occupiedRecipeSlots() : usedInputSlots(targetedBy(), this::filterAt);
        if (LogisticsConnection.TYPE.equals(type) && used >= MAX_INGREDIENTS)
            return ValidationResult.fail(() -> CreateLang.translate("factory_panel.cannot_add_more_inputs")
                    .style(ChatFormatting.RED).component());
        return ValidationResult.SUCCESS;
    }

    /** Occupied cells of the CUSTOM arrangement grid. */
    private int occupiedRecipeSlots() {
        int used = 0;
        for (RecipeSlot slot : recipeSlots)
            if (!slot.isEmpty()) used++;
        return used;
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

    /**
     * Grid slots this gauge's ingredients would occupy if {@code proposedAmounts} (source position → new per-input
     * amount) were applied — used to reject a recipe-config edit that would push the grid past {@link #MAX_INGREDIENTS}
     * (the creation-time {@code validateAsSink} cap only sees one wire at a time, so larger amounts could otherwise
     * overflow it). Positions absent from the map keep their current amount.
     */
    public int projectedInputSlots(Map<VirtualComponentPosition, Integer> proposedAmounts) {
        int used = 0;
        for (Map.Entry<VirtualComponentPosition, Connection> e : targetedBy().entrySet()) {
            if (!(e.getValue() instanceof LogisticsConnection lc)) continue;
            ItemStack src = filterAt(e.getKey());
            if (FluidCompat.isFluidFilter(src)) { used += 1; continue; }   // a fluid ingredient is one slot
            int amount = Math.max(1, proposedAmounts.getOrDefault(e.getKey(), lc.amount()));
            int ss = src.isEmpty() ? 1 : Math.max(1, src.getMaxStackSize());
            used += (amount + ss - 1) / ss;   // ceil
        }
        return used;
    }

    /**
     * CUSTOM mode: keeps {@link #recipeSlots} consistent with the live ingredient wires — a removed wire's
     * cells are cleared, and a newly-wired ingredient lands in the first empty cell (the same rule the recipe
     * screen applies on open, so server dispatch and the eventual screen edit agree). No-op in other modes.
     * Called after a wire is added to / removed from this gauge.
     */
    public void reconcileRecipeSlots() {
        if (mode != GaugeWorkMode.CUSTOM) return;
        while (recipeSlots.size() < MAX_INGREDIENTS) recipeSlots.add(RecipeSlot.EMPTY);
        for (int i = 0; i < recipeSlots.size(); i++) {
            RecipeSlot slot = recipeSlots.get(i);
            if (!slot.isEmpty() && !(targetedBy().get(slot.source()) instanceof LogisticsConnection))
                recipeSlots.set(i, RecipeSlot.EMPTY);   // its wire was removed
        }
        for (Map.Entry<VirtualComponentPosition, Connection> e : targetedBy().entrySet()) {
            if (!(e.getValue() instanceof LogisticsConnection lc)) continue;
            VirtualComponentPosition src = e.getKey();
            if (recipeSlots.stream().anyMatch(sl -> !sl.isEmpty() && src.equals(sl.source()))) continue;
            int empty = -1;
            for (int i = 0; i < recipeSlots.size(); i++) if (recipeSlots.get(i).isEmpty()) { empty = i; break; }
            if (empty < 0) break;   // grid full — validateAsSink prevents this for new wires
            ItemStack ingredient = filterAt(src);
            int cap = FluidCompat.isFluidFilter(ingredient) ? FLUID_INGREDIENT_CAP_MB
                : ingredient.isEmpty() ? 64 : Math.max(1, ingredient.getMaxStackSize());
            recipeSlots.set(empty, new RecipeSlot(src, Math.min(Math.max(1, lc.amount()), cap)));
        }
    }

    /** Re-keys {@link #recipeSlots} sources through {@code remap} — relocation renames the wires' endpoint
     *  positions, and a CUSTOM arrangement referencing a moved source must follow or its cells go stale. */
    public void remapRecipeSlots(java.util.function.Function<VirtualComponentPosition, VirtualComponentPosition> remap) {
        for (int i = 0; i < recipeSlots.size(); i++) {
            RecipeSlot slot = recipeSlots.get(i);
            if (slot.isEmpty()) continue;
            VirtualComponentPosition moved = remap.apply(slot.source());
            if (!moved.equals(slot.source())) recipeSlots.set(i, new RecipeSlot(moved, slot.count()));
        }
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

    /** Whether any incoming wire is an ingredient (LOGISTICS) connection. {@link #targetedBy()} now also holds redstone
     *  inputs (from RECEIVE links / logic tubes), which are NOT ingredients — so "has ingredients" must filter by type,
     *  or a redstone-only gauge would wrongly look like it has a recipe to fulfil. */
    private boolean hasIngredientInputs() {
        for (Connection c : targetedBy().values())
            if (c instanceof LogisticsConnection) return true;
        return false;
    }

    /**
     * Whether this gauge needs a restock address but has none — an active target with ingredient connections wired in
     * but a blank {@link #recipeAddress}. Mirrors Create's check (minus the packager-block-entity part). Redstone
     * inputs don't count (they need no address).
     */
    public boolean isMissingAddress() {
        return hasIngredientInputs() && isActive() && recipeAddress.isBlank();
    }

    /** A gauge's redstone OUTPUT to wired send-links: gray INACTIVE when it has no target, else POWERED/UNPOWERED by
     *  whether its stock is satisfied. (A gauge is not driven by its own redstone input — that only gates requests.) */
    @Override
    public ConnectionValue outputValue(Connection.Type type) {
        if (!RedstoneConnection.TYPE.equals(type)) return null;
        return !isActive() ? RedstoneConnection.State.INACTIVE
             : satisfied   ? RedstoneConnection.State.POWERED
             :               RedstoneConnection.State.UNPOWERED;
    }

    /** Re-fold the request gate — powered by any wired, powered RECEIVE link. Synced on change so the client gate
     *  colour follows. */
    @Override
    public void onInputChanged(Connection.Type type) {
        if (!RedstoneConnection.TYPE.equals(type)) return;
        boolean now = false;
        for (Connection c : graph().incomingConnections(position, RedstoneConnection.TYPE))
            if (c instanceof RedstoneConnection rc && rc.powered()) { now = true; break; }
        if (now == redstonePowered) return;
        redstonePowered = now;
        resetRequestTimer();
        if (controller != null) { controller.setChanged(); controller.sendData(); }
    }

    public void resetRequestTimer() { timer = 1; }

    /** Re-evaluate this gauge's redstone output and push it to wired send-links (no-op unless it changed). Called on
     *  config edits that can flip its active/satisfied output. */
    public void publishRedstoneOutput() { updateRedstoneOutput(); }

    private boolean updateRedstoneOutput() {
        RedstoneConnection.State now = (RedstoneConnection.State) outputValue(RedstoneConnection.TYPE);
        if (now == lastRedstoneOutput) return false;
        lastRedstoneOutput = now;
        publish(RedstoneConnection.TYPE);
        return true;
    }

    /**
     * Connection-path color (RGB) for the gauge state, matching Create's {@code getIngredientStatusColor}:
     * gray when inactive/misconfigured/powered, then waiting → satisfied → promised → pending. (The
     * indicator bulb is coloured separately — green/red — by the screen; see VirtualGaugeWidget.)
     */
    public int getConnectionColor() {
        return !isActive() || isMissingAddress() || isRedstonePaused() ? 0x888898
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
    public String stockText() {
        return isInfiniteStock() ? "∞"
            : FluidCompat.isFluidFilter(filter) ? unit.formatInUnit(stockLevel)
            : stockLevel / unit.toCountMultiplier(filter) + unit.suffix;
    }

    /** The target threshold as shown on the gauge face ({@code count} in the gauge's unit). */
    public String demandText() {
        return count + unit.suffix;
    }

    public MutableComponent getCountLabel() {
        if (filter.isEmpty()) return Component.empty();
        if (waitingForNetwork) return Component.literal("?");

        // A fluid filter's stockLevel is millibuckets — shown in the gauge's own unit (mB/B); items show
        // count/stacks + suffix.
        String inStorage = stockText();

        if (!isActive())
            return CreateLang.text(inStorage).color(0xF1EFE8).component();

        return CreateLang.text(inStorage).color(satisfied ? 0xD7FFA8 : promisedSatisfied ? 0xFFCD75 : 0xFFBFA8)
            .add(CreateLang.text(promisedCount == 0 ? "" : "⏶"))
            .add(CreateLang.text("/").style(ChatFormatting.WHITE))
            .add(CreateLang.text(demandText()).color(requestMode.isPassive() ? 0x9ECFFC : 0xF1EFE8))
            .component();
    }

    /** Display Link line for {@link DisplayMode#ACTIVE_REQUESTS}: an active, understocked gauge contributes
     *  "{@code <⏶ if promised><stock>/<demand> <item>}" (matching the count overlay); anything else contributes nothing. */
    @Override
    public List<MutableComponent> provideDisplayLines(DisplayMode mode) {
        if (mode != DisplayMode.ACTIVE_REQUESTS) return List.of();
        if (filter.isEmpty() || !isActive() || satisfied) return List.of();
        MutableComponent line = Component.literal((promisedCount > 0 ? "⏶" : "") + stockText() + "/" + demandText() + " ")
                .append(FluidCompat.filterName(filter));
        return List.of(line);
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
            int parentBatch = parent.mode == GaugeWorkMode.CRAFTING ? Math.max(1, parent.craftBatch) : 1;
            // Stage enough for the consumer's next maximally-scaled request: one batch capped by its multiplier
            // ceiling, but never more than its remaining deficit needs. So a multiplier-8 consumer with a large
            // deficit gets 8 request-sets staged (it can then fire an 8× request); a nearly-satisfied one gets only
            // its small deficit; and a multiplier-1 consumer collapses to the original single request-set. Bounding
            // by the ceiling (not the full deficit) keeps the intermediate buffer to one max batch. O(1) per edge.
            int parentRequests = Math.max(1, Math.min(parent.deficitRequestScaler(), parent.maxRequestMultiplier));
            demand += conn.amount() * parentBatch * parentRequests;
        }
        // External demand from open Production Orders (Stock Keeper blueprints targeting THIS gauge): a player
        // order acts like another downstream consumer, so the passive gauge produces to satisfy it too.
        if (gaugeId != null && controller.getLevel() != null)
            demand += ProductionOrderManager.externalDemand(controller.getLevel(), networkId, gaugeId);
        count = demand <= 0 ? 0 : Math.ceilDiv(demand, unit.toCountMultiplier(filter));
    }

    /**
     * Whether this gauge still needs more of its ingredients — i.e. it is configured to request and isn't
     * yet covered by stock + open promises. An auto producer feeding this gauge calls it to size its own
     * demand.
     */
    private boolean isDemandingIngredients() {
        if (count == 0 || waitingForNetwork || isRedstonePaused() || isMissingAddress())
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
            updateRedstoneOutput();
            return;
        }

        int unloadedLinkCount = Create.LOGISTICS.getUnloadedLinkCount(networkId);

        if (unloadedLinkCount == 0 && lastReportedUnloadedLinks != 0)
            LogisticsManager.SUMMARIES.invalidate(networkId);

        // Apply a pending player-forced clear (side effect only). Its promised drop is absorbed by the hold
        // below exactly like a settlement or an expiry — no per-cause disambiguation is needed any more.
        if (forceClearPromises){
            forceClearPromise(networkId, filter);
            timer = requestIntervalTicks() / 2;
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

        // Total-demand strategy: fold the solver's raw target into the gauge's count, holding a DECREASE against the
        // summary-refresh signal exactly like the stock/sum holds above. An increase (more downstream demand) applies
        // at once; a decrease is held one refresh so the transient downstream-promise dip — demand drops a few ticks
        // before the ingredient it reserved leaves stock.
        if (requestMode.isPassive() && ServerConfig.passiveTotalDemand()) {
            if (passiveDemandTarget >= count) { count = passiveDemandTarget; demandDropPending = false; }
            else if (demandDropPending)       { count = passiveDemandTarget; demandDropPending = false; }
            else if (refreshed)               { demandDropPending = true; }
            // else: hold the current (higher) count until the next refresh confirms the decrease
        }

        int demand = count * unit.toCountMultiplier(filter);
        satisfied = stockLevel >= demand;
        promisedSatisfied = heldSum >= demand;
        waitingForNetwork = unloadedLinkCount > 0;

        boolean redstoneChanged = updateRedstoneOutput();

        if (!redstoneChanged && stockLevel == prevStock && promisedCount == prevPromised
                && satisfied == wasSatisfied
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
        return logisticsControl().stockOf(this, stack);
    }

    private InventorySummary getRelevantSummary() {
        return LogisticsManager.getSummaryOfNetwork(networkId, false);
    }

    public int getPromised() {
        return logisticsControl().promised(this);
    }

    /** The conserved {@code stock + promised} held against the transient promise→inventory settlement dip (see
     *  {@link #heldSum}). The gap-safe quantity to subtract when sizing demand: when an item lands the promise clears
     *  immediately but the network summary lags up to ~20 ticks, so {@code stockLevel + promisedCount} momentarily
     *  undercounts and would over-size — {@code heldSum} bridges that, exactly as {@code promisedSatisfied} does. */
    public int effectiveHeld() {
        return heldSum;
    }

    protected VirtualComponentBehaviour.Type componentType() {
        return TYPE;
    }

    public GaugeFilterResolver filterResolver() {
        return ITEM_FILTER_RESOLVER;
    }

    public LogisticsControl logisticsControl() {
        return ITEM_LOGISTICS;
    }

    public void forceClearPromise(UUID networkId, ItemStack filter) {
        logisticsControl().forceClearPromise(networkId, filter);
    }

    public void addPromise(UUID networkId, ItemStack filter, boolean ignoreData, int amount) {
        String ownerKey = gaugeId == null ? null : gaugeId.toString();
        logisticsControl().addPromise(networkId, filter, ignoreData, amount,
                ownerKey, recipeAddress, getPromiseExpiryTimeInTicks());
        // Fold the fresh promise into this tick's count cache so a gauge firing later this tick sees it.
        if (controller != null && controller.getLevel() != null)
            PromiseCounts.onAdded(networkId, ownerKey, recipeAddress, controller.getLevel().getGameTime());
    }

    /** In-flight promises this gauge minted (its own scope). Read from the per-tick {@link PromiseCounts} cache (O(1)).
     *  {@link FluidGaugeBehaviour} overrides to read the fluid backend instead. */
    public int ownedPromiseCount(long now) {
        return PromiseCounts.owned(networkId, gaugeId == null ? null : gaugeId.toString(), now);
    }

    /** In-flight promises network-wide targeting this gauge's address (address scope) — the UNION of item and fluid
     *  promises, so an item gauge and a fluid gauge pointed at the same address share one quota. Owner scope stays
     *  kind-specific (a gauge only ever mints one kind), but the address quota spans both backends. */
    public int addressPromiseCount(long now) {
        return PromiseCounts.address(networkId, recipeAddress, now)
             + FluidCompat.fluidAddressPromises(networkId, recipeAddress, now);
    }

    /** In-flight promises counting against this gauge's limit — its own, or (address scope) all requests network-wide
     *  to its address, per {@link #promiseLimitByAddress}. */
    public int countLimitedPromises() {
        if (controller == null || controller.getLevel() == null) return 0;
        long now = controller.getLevel().getGameTime();
        return promiseLimitByAddress ? addressPromiseCount(now) : ownedPromiseCount(now);
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
        if (!hasIngredientInputs()) return;             // no ingredient wires in → nothing to craft (redstone inputs don't count)
        // Check satisfaction FIRST (Create's order) so the timer is frozen while stocked/promised. This
        // is what prevents over-requesting: the timer never idles at 0 ready to fire, so the one-tick
        // stock/promise flicker as the produced item lands can't trigger an extra request — a request
        // only fires after the gauge has been continuously understocked for the whole interval.
        if (satisfied || promisedSatisfied || waitingForNetwork || isRedstonePaused()) return;
        if (isMissingAddress()) return;
        // Limit-block FREEZES the timer (checked before the countdown), so it neither ticks nor fires while capped.
        if (promiseLimit > 0 && countLimitedPromises() >= promiseLimit) return;
        if (timer > 0) {                                // throttle between attempts
            timer = Math.min(timer, requestIntervalTicks());
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
        boolean crafting = mode == GaugeWorkMode.CRAFTING;
        boolean custom = mode == GaugeWorkMode.CUSTOM;
        int batch = effectiveBatch();
        // The whole request may be scaled up in one dispatch: the demand/order are built at 1× (base), then the
        // actual scaler multiplies every ingredient count and the produced output. The scaler is the SMALLEST that
        // still covers the outstanding deficit — min(structure, live-stock headroom, deficit) — so one larger request
        // just fills what's missing instead of over-producing to the ceiling. See REQUEST_MULTIPLIER_DESIGN.md.
        int structuralScaler = clampMultiplierToStructure(maxRequestMultiplier);
        // Requests still needed to clear the output deficit — each 1× request yields recipeOutput × batch. We only
        // reach here when this is ≥ 1 (the !promisedSatisfied gate), so the max(1) is just defensive.
        int deficitScaler = Math.max(1, deficitRequestScaler());

        Map<UUID, List<BigItemStack>> demandByNetwork = new LinkedHashMap<>();
        // CUSTOM: the exact per-slot layout (ordered, unmerged, gaps preserved) per network, with ignore-data
        // cells resolved to concrete variants. Built first so the merged availability demand below is derived
        // from precisely what ships.
        Map<UUID, List<BigItemStack>> orderByNetwork = null;
        // The crafting pattern actually dispatched. For an ignore-data ingredient the stored pattern holds the
        // pure-form item, but the package must carry (and the crafter pattern must match) concrete in-stock
        // variants — so resolve those cells against live stock on every request.
        List<ItemStack> craftPattern = crafting ? new ArrayList<>(activeCraftingArrangement.size()) : null;

        if (custom) {
            orderByNetwork = buildOrderedSlots();
            if (orderByNetwork == null) {   // an ignore-data cell no single stock variant can cover
                setConnectionsSuccess(false);
                return;
            }
            for (Map.Entry<UUID, List<BigItemStack>> e : orderByNetwork.entrySet())
                for (BigItemStack b : e.getValue())
                    if (!b.stack.isEmpty() && b.count > 0) addDemand(demandByNetwork, e.getKey(), b.stack, b.count);
        } else if (crafting) {
            Map<VirtualGaugeBehaviour, List<BigItemStack>> variantPools = new HashMap<>();
            // A >3×3 arrangement holds more than 9 cells, so per-cell variant spill for an ignore-data ingredient
            // could ship more distinct package slots than the box budget. Pin one variant per such source up front
            // (covering all its cells); a ≤3×3 grid caps at 9 cells and keeps the flexible per-cell spill.
            Map<VirtualGaugeBehaviour, ItemStack> pinned = craftDimension > 3
                ? pinCraftingVariants(variantPools, batch) : null;
            if (craftDimension > 3 && pinned == null) { setConnectionsSuccess(false); return; }
            for (ItemStack cell : activeCraftingArrangement) {
                if (cell.isEmpty()) { craftPattern.add(ItemStack.EMPTY); continue; }
                VirtualGaugeBehaviour source = findIngredientSource(cell);
                if (source != null && source.ignoreData && !FluidCompat.isFluidFilter(source.filter)) {
                    ItemStack chosen = pinned != null ? pinned.get(source)
                        : takeVariant(variantPools.computeIfAbsent(source, this::variantPool), batch);
                    if (chosen == null || chosen.isEmpty()) { setConnectionsSuccess(false); return; }  // no single variant has enough
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
                // the recipe consumes whatever is actually on the network.
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

        int scaler = Math.min(structuralScaler, deficitScaler);
        for (Map.Entry<UUID, List<BigItemStack>> netEntry : demandByNetwork.entrySet()) {
            InventorySummary summary = LogisticsManager.getSummaryOfNetwork(netEntry.getKey(), true);
            for (BigItemStack need : netEntry.getValue()) {
                if (need.stack.isEmpty() || need.count <= 0) continue;   // gap/marker placeholder
                int available = FluidCompat.isFluidFilter(need.stack)
                        ? FluidCompat.fluidStock(netEntry.getKey(), need.stack)
                        : summary.getCountOf(need.stack);
                if (available < need.count) {        // can't supply even 1× → flash red
                    setConnectionsSuccess(false);
                    return;
                }
                scaler = Math.min(scaler, available / need.count);
            }
        }
        scaler = Math.max(1, scaler);

        if (scaler > 1) {
            for (List<BigItemStack> items : demandByNetwork.values())
                for (BigItemStack b : items) b.count *= scaler;
            if (orderByNetwork != null)
                for (List<BigItemStack> items : orderByNetwork.values())
                    for (BigItemStack b : items) b.count *= scaler;
        }

        List<PackageOrderWithCrafts.CraftingEntry> crafts = !crafting
            ? PackageOrderWithCrafts.empty().orderedCrafts()
            // CraftingEntry.count is the number of times to run this pattern — batch × the request scaler. The
            // pattern is the per-request-resolved one (concrete variants for any ignore-data cells).
            : List.of(new PackageOrderWithCrafts.CraftingEntry(
                new PackageOrder(craftPattern.stream()
                    .map(s -> new BigItemStack(s.copyWithCount(1)))
                    .toList()),
                batch * scaler));

        List<Multimap<PackagerBlockEntity, PackagingRequest>> dispatch = new ArrayList<>();
        List<Map.Entry<UUID, List<BigItemStack>>> fluidNetworks = new ArrayList<>();            // CFL/CreateFluid (Create logistics)
        for (Map.Entry<UUID, List<BigItemStack>> netEntry : demandByNetwork.entrySet()) {
            UUID net = netEntry.getKey();
            // In CUSTOM mode use the ordered per-slot list (with gap placeholders); otherwise the merged demand.
            List<BigItemStack> netItems = custom ? orderByNetwork.getOrDefault(net, netEntry.getValue()) : netEntry.getValue();
            // A network with Repackaged generic fluid ingredients is dispatched as ONE unified order
            List<BigItemStack> externalFluids = netItems.stream()
                .filter(b -> !b.stack.isEmpty() && !FluidCompat.usesCreateItemLogistics(b.stack)).toList();
            if (!externalFluids.isEmpty()) {
                List<BigItemStack> items = netItems.stream()
                    .filter(b -> b.stack.isEmpty() || FluidCompat.usesCreateItemLogistics(b.stack)).toList();
                PackageOrderWithCrafts itemOrder = new PackageOrderWithCrafts(new PackageOrder(items), crafts);
                if (!FluidCompat.broadcastRepackagedRecipe(net, itemOrder, externalFluids, recipeAddress)) {
                    setConnectionsSuccess(false);
                    return;
                }
                continue;
            }
            // A virtual fluid tank ingredient (CFL/CreateFluid) must be dispatched via broadcastPackageRequest — see below.
            if (netItems.stream().anyMatch(b -> FluidCompat.isFluidFilter(b.stack))) {
                fluidNetworks.add(new java.util.AbstractMap.SimpleEntry<>(net, netItems));
                continue;
            }
            PackageOrderWithCrafts order =
                new PackageOrderWithCrafts(new PackageOrder(netItems), crafts);
            Multimap<PackagerBlockEntity, PackagingRequest> found =
                LogisticsManager.findPackagersForRequest(net, order, null, recipeAddress);
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
        addPromise(networkId, filter, ignoreData, Math.max(1, recipeOutput) * batch * scaler);
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

    /** Crafts dispatched per request — {@code craftBatch} in crafter mode, forced to 1 elsewhere. Same value
     *  {@link #tickRequests} uses, so a request's real per-craft ingredient demand is {@code connection amount ×
     *  effectiveBatch()}. An ignore-data ingredient still batches: each slot ships a single variant covering its
     *  whole batch (see {@link #takeVariant}/{@link #pinCraftingVariants}). */
    public int effectiveBatch() {
        return mode != GaugeWorkMode.CRAFTING ? 1 : Math.max(1, craftBatch);
    }

    /** Number of 1× requests (each yielding {@code recipeOutput × batch}) still needed to clear this gauge's
     *  output deficit {@code demand − (stock + promised)}; ≥ 0. Drives both the request-time scaler ceiling and,
     *  read on a consumer, how much of its ingredient an upstream passive producer must stage so the consumer can
     *  batch. Pure O(1) field arithmetic — no stock/summary reads. */
    public int deficitRequestScaler() {
        int outputPerRequest = Math.max(1, recipeOutput) * effectiveBatch();
        int deficit = Math.max(0, count * unit.toCountMultiplier(filter) - effectiveHeld());
        return (int) ceilDiv(deficit, outputPerRequest);
    }

    // ── Request multiplier ────────────────────────────────────────────────────

    /** One scaled request slot: {@code amount} at 1×, its {@code capacity} (item stack size or fluid mB cap), and
     *  whether it's a fluid (one package slot regardless of amount, but bounded by its mB cap). */
    public record ScaleSlot(int amount, int capacity, boolean fluid) {}

    /** Output-bound scaler: largest M with {@code recipeOutput · M ≤ outputCap}. */
    private static int outputMultiplierCap(int recipeOutput, int outputCap) {
        return Math.max(1, outputCap / Math.max(1, recipeOutput));
    }

    private static long ceilDiv(long a, long b) { return (a + b - 1) / b; }

    /** Whether the scaled REGULAR demand still packs into {@link #MAX_INGREDIENTS} package slots at scaler {@code m}
     *  (and no fluid exceeds its mB cap). Monotonic in {@code m}. */
    private static boolean packsRegular(List<ScaleSlot> slots, int m) {
        int used = 0;
        for (ScaleSlot s : slots) {
            if (s.fluid()) {
                if ((long) s.amount() * m > s.capacity()) return false;
                used += 1;
            } else {
                used += (int) ceilDiv((long) s.amount() * m, Math.max(1, s.capacity()));
            }
            if (used > MAX_INGREDIENTS) return false;
        }
        return true;
    }

    /** REGULAR structural cap: largest M packing the scaled demand into the 9-slot package and keeping the scaled
     *  output within {@code outputCap}. Binary search over the monotonic packing feasibility. */
    public static int regularMultiplierCap(List<ScaleSlot> slots, int recipeOutput, int outputCap, int hardCap) {
        int hi = Math.clamp(outputMultiplierCap(recipeOutput, outputCap), 1, hardCap);
        int lo = 1, best = 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (packsRegular(slots, mid)) { best = mid; lo = mid + 1; } else hi = mid - 1;
        }
        return best;
    }

    /** CUSTOM structural cap: each cell must stay within one stack/fluid cap, and the scaled output within its cap. */
    public static int customMultiplierCap(List<ScaleSlot> slots, int recipeOutput, int outputCap, int hardCap) {
        int cap = Math.min(hardCap, outputMultiplierCap(recipeOutput, outputCap));
        for (ScaleSlot s : slots)
            cap = Math.clamp(s.capacity() / Math.max(1, s.amount()), 1, cap);
        return Math.max(1, cap);
    }

    /** CRAFTING structural cap: crafts per request {@code = craftBatch · M ≤ MAX_CRAFT_OUTPUT}. */
    public static int craftingMultiplierCap(int craftBatch, int hardCap) {
        return Math.clamp(MAX_CRAFT_OUTPUT / Math.max(1, craftBatch), 1, hardCap);
    }

    /** Item output cap (max produced count) for a produced item of the given stack size — {@code max(64, 9 stacks)}. */
    public static int itemOutputCap(int stackSize) { return Math.max(64, 9 * Math.max(1, stackSize)); }

    /** This gauge's stock-independent multiplier ceiling for its current mode/config. */
    public int structuralMultiplierCap() {
        if (mode == GaugeWorkMode.CRAFTING) return craftingMultiplierCap(effectiveBatch(), 64);

        boolean fluidOut = FluidCompat.isFluidFilter(filter);
        int outputCap = fluidOut ? FLUID_OUTPUT_CAP_MB
            : itemOutputCap(filter.isEmpty() ? 64 : filter.getMaxStackSize());
        int output = Math.max(1, recipeOutput);

        List<ScaleSlot> slots = new ArrayList<>();
        if (mode == GaugeWorkMode.CUSTOM) {
            for (RecipeSlot rs : recipeSlots) {
                if (rs.isEmpty()) continue;
                VirtualGaugeBehaviour src = sourceGauge(rs);
                if (src == null || src.filter.isEmpty()) continue;
                boolean fluid = FluidCompat.isFluidFilter(src.filter);
                int capacity = fluid ? FLUID_INGREDIENT_CAP_MB : Math.max(1, src.filter.getMaxStackSize());
                slots.add(new ScaleSlot(Math.max(1, rs.count()), capacity, fluid));
            }
            return customMultiplierCap(slots, output, outputCap, 64);
        }
        // REGULAR: one entry per ingredient connection (its whole total). Server-only path (controller != null);
        // the client screen computes its own cap from edit state.
        if (controller != null)
            for (Map.Entry<VirtualComponentPosition, Connection> e : targetedBy().entrySet()) {
                if (!(e.getValue() instanceof LogisticsConnection lc)) continue;
                if (!(controller.components.get(e.getKey()) instanceof VirtualGaugeBehaviour src) || src.filter.isEmpty())
                    continue;
                boolean fluid = FluidCompat.isFluidFilter(src.filter);
                int capacity = fluid ? FLUID_INGREDIENT_CAP_MB : Math.max(1, src.filter.getMaxStackSize());
                slots.add(new ScaleSlot(Math.max(1, lc.amount()), capacity, fluid));
            }
        return regularMultiplierCap(slots, output, outputCap, 64);
    }

    /** The scaler actually used for one request: the user ceiling clamped by structure and (caller-supplied) stock. */
    public int clampMultiplierToStructure(int value) {
        return Math.clamp(value, 1, structuralMultiplierCap());
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

    /** Pins each ignore-data crafting ingredient to ONE in-stock variant covering all of its cells (× {@code
     *  batch}), reserving that amount from the shared pool. Keeps a large arrangement's shipped package to one
     *  stack per ingredient instead of one per spilled variant. Returns {@code null} when some source has no
     *  single variant with enough stock. */
    @Nullable
    private Map<VirtualGaugeBehaviour, ItemStack> pinCraftingVariants(
            Map<VirtualGaugeBehaviour, List<BigItemStack>> variantPools, int batch) {
        Map<VirtualGaugeBehaviour, Integer> cellsPerSource = new LinkedHashMap<>();
        for (ItemStack cell : activeCraftingArrangement) {
            if (cell.isEmpty()) continue;
            VirtualGaugeBehaviour source = findIngredientSource(cell);
            if (source != null && source.ignoreData && !FluidCompat.isFluidFilter(source.filter))
                cellsPerSource.merge(source, 1, Integer::sum);
        }
        Map<VirtualGaugeBehaviour, ItemStack> pinned = new HashMap<>();
        for (Map.Entry<VirtualGaugeBehaviour, Integer> e : cellsPerSource.entrySet()) {
            List<BigItemStack> pool = variantPools.computeIfAbsent(e.getKey(), this::variantPool);
            ItemStack chosen = takeVariant(pool, e.getValue() * batch);
            if (chosen.isEmpty()) return null;
            pinned.put(e.getKey(), chosen);
        }
        return pinned;
    }

    /** CUSTOM mode: builds, per source network, the ordered per-slot ingredient list — this network's slots at
     *  their grid position, every other cell (empty or another network) a gap placeholder, trailing gaps
     *  trimmed, and the {@linkplain ArrangementUnpackingHandler#marker() arrangement marker} appended last (it
     *  flags the order for positional unpacking; being zero-count and last, it never pulls stock or shifts
     *  slot indices). An ignore-data cell is resolved to ONE concrete in-stock variant covering its whole count
     *  (a package slot is a single stack — mixing variants would split the cell and break the positional
     *  pattern); a source's cells share one pool, so their combined take stays within each variant's stock.
     *  Returns {@code null} when a cell can't be covered by any single variant. */
    @Nullable
    private Map<UUID, List<BigItemStack>> buildOrderedSlots() {
        Map<UUID, List<BigItemStack>> byNet = new LinkedHashMap<>();
        Map<VirtualGaugeBehaviour, List<BigItemStack>> variantPools = new HashMap<>();
        for (RecipeSlot slot : recipeSlots) {
            VirtualGaugeBehaviour src = sourceGauge(slot);
            if (src != null && !src.filter.isEmpty()) byNet.computeIfAbsent(src.networkId, k -> new ArrayList<>());
        }
        for (UUID net : byNet.keySet()) {
            List<BigItemStack> ordered = new ArrayList<>();
            int lastFilled = 0;
            for (RecipeSlot slot : recipeSlots) {
                VirtualGaugeBehaviour src = sourceGauge(slot);
                if (src != null && net.equals(src.networkId) && !src.filter.isEmpty()) {
                    // 1 grid cell ↔ 1 package slot: item counts stay within one stack (fluids carry mB freely).
                    boolean fluid = FluidCompat.isFluidFilter(src.filter);
                    int cnt = Math.clamp(slot.count(), 1, fluid ? Integer.MAX_VALUE : Math.max(1, src.filter.getMaxStackSize()));
                    ItemStack shipped = src.filter;
                    if (src.ignoreData && !fluid) {
                        shipped = takeVariant(variantPools.computeIfAbsent(src, this::variantPool), cnt);
                        if (shipped.isEmpty()) return null;   // no single variant has enough for this cell
                    }
                    ordered.add(new BigItemStack(shipped.copyWithCount(1), cnt));
                    lastFilled = ordered.size();
                } else {
                    ordered.add(new BigItemStack(ItemStack.EMPTY, 0));   // gap (empty cell or another network)
                }
            }
            List<BigItemStack> order = new ArrayList<>(ordered.subList(0, lastFilled));
            order.add(ArrangementUnpackingHandler.marker());
            byNet.put(net, order);
        }
        return byNet;
    }

    /** The source gauge a {@link RecipeSlot} draws from, or {@code null} when the slot is empty or its wire no
     *  longer exists — a stale slot (wire removed since the layout was saved) must not ship, or the order would
     *  carry items the merged availability check never covered. */
    private VirtualGaugeBehaviour sourceGauge(RecipeSlot slot) {
        if (slot.isEmpty() || controller == null) return null;
        if (!(targetedBy().get(slot.source()) instanceof LogisticsConnection)) return null;
        return controller.components.get(slot.source()) instanceof VirtualGaugeBehaviour g ? g : null;
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
        timer = requestIntervalTicks();
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    protected int getConfigRequestIntervalInTicks() {
        return AllConfigs.server().logistics.factoryGaugeTimer.get();
    }

    /** The effective request-timer reset value: {@link #customRequestTimer} when set (&gt; 0), else the config. */
    public int requestIntervalTicks() {
        return customRequestTimer > 0 ? customRequestTimer : getConfigRequestIntervalInTicks();
    }

    /** Current request-timer countdown value (ticks remaining until the next attempt). */
    public int currentTimer() {
        return timer;
    }

    /** Whether the request timer is actively counting down this tick — i.e. the gauge passes every pre-timer gate
     *  in {@link #tickRequests} (including the promise limit, which freezes the timer). Drives the recipe screen's
     *  output-arrow animation; false ⇒ no overlay. */
    public boolean isRequestTimerTicking() {
        return controller != null && hasIngredientInputs() && !satisfied && !promisedSatisfied
            && !waitingForNetwork && !isRedstonePaused() && !isMissingAddress()
            && !(promiseLimit > 0 && countLimitedPromises() >= promiseLimit);
    }

    protected int getPromiseExpiryTimeInTicks() {
        if (promiseClearingInterval == -1) return -1;
        if (promiseClearingInterval == 0) return 20 * 30;
        return promiseClearingInterval * 20 * 60;
    }

    // ── NBT ────────────────────────────────────────────────────────────────

    @Override public String typeId() { return componentType().id(); }

    @Override
    public void writeClient(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        SyncCodecs.writePos(buf, position);
        buf.writeResourceLocation(getItemId());
        buf.writeUUID(networkId);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, filter);
        buf.writeBoolean(ignoreData);
        SyncCodecs.writeEnum(buf, unit);
        SyncCodecs.writeEnum(buf, requestMode);
        buf.writeUtf(recipeAddress);
        buf.writeVarInt(recipeOutput);
        buf.writeVarInt(craftBatch);
        buf.writeVarInt(maxRequestMultiplier);
        buf.writeVarInt(craftDimension);
        buf.writeVarInt(promiseClearingInterval + 1);   // -1..31 → 0..32 (non-negative for varint)
        buf.writeVarInt(promiseLimit);
        buf.writeBoolean(promiseLimitByAddress);
        SyncCodecs.writeEnum(buf, mode);
        buf.writeVarInt(activeCraftingArrangement.size());
        for (ItemStack s : activeCraftingArrangement) ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s);
        buf.writeVarInt(recipeSlots.size());
        for (RecipeSlot slot : recipeSlots) slot.write(buf);
        // ── runtime ──
        buf.writeVarInt(count);
        buf.writeBoolean(satisfied);
        buf.writeBoolean(promisedSatisfied);
        buf.writeBoolean(waitingForNetwork);
        buf.writeBoolean(redstonePowered);
        buf.writeVarInt(stockLevel);
        buf.writeVarInt(promisedCount);
        buf.writeVarLong(lastRequestTick);
    }

    /** Reads the {@link #writeClient} body (everything after pos/item/network, which the {@code Type.fromClient} reads
     *  to construct the instance) into this freshly-built client gauge. */
    protected void readClientBody(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        filter = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        ignoreData = buf.readBoolean();
        unit = SyncCodecs.readEnum(buf, ThresholdUnit.values());
        requestMode = SyncCodecs.readEnum(buf, RequestMode.values());
        recipeAddress = buf.readUtf();
        recipeOutput = buf.readVarInt();
        craftBatch = buf.readVarInt();
        maxRequestMultiplier = Math.max(1, buf.readVarInt());
        craftDimension = buf.readVarInt();
        promiseClearingInterval = buf.readVarInt() - 1;
        promiseLimit = buf.readVarInt();
        promiseLimitByAddress = buf.readBoolean();
        mode = SyncCodecs.readEnum(buf, GaugeWorkMode.values());
        int n = buf.readVarInt();
        activeCraftingArrangement = new ArrayList<>(n);
        for (int i = 0; i < n; i++) activeCraftingArrangement.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        int slotCount = buf.readVarInt();
        recipeSlots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) recipeSlots.add(RecipeSlot.read(buf));
        count = buf.readVarInt();
        satisfied = buf.readBoolean();
        promisedSatisfied = buf.readBoolean();
        waitingForNetwork = buf.readBoolean();
        redstonePowered = buf.readBoolean();
        stockLevel = buf.readVarInt();
        promisedCount = buf.readVarInt();
        lastRequestTick = buf.readVarLong();
    }

    @Override
    public CompoundTag toNBT(net.minecraft.core.HolderLookup.Provider registries, NbtProfile profile) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", componentType().id());
        tag.put("Pos", position.toNBT());
        tag.putString("Item", getItemId().toString());
        tag.putUUID("Network", networkId);

        tag.put("Filter", filter.saveOptional(registries));
        tag.putBoolean("IgnoreData", ignoreData);
        tag.putInt("Count", count);
        tag.putString("Unit", unit.name());
        tag.putString("RequestMode", requestMode.name());

        tag.putString("RecipeAddress", recipeAddress);
        tag.putInt("RecipeOutput", recipeOutput);
        tag.putInt("CraftBatch", craftBatch);
        tag.putInt("MaxRequestMultiplier", maxRequestMultiplier);
        tag.putInt("CraftDimension", craftDimension);
        tag.putInt("PromiseClearingInterval", promiseClearingInterval);
        tag.putInt("PromiseLimit", promiseLimit);
        tag.putBoolean("PromiseLimitByAddress", promiseLimitByAddress);
        if (customRequestTimer > 0) tag.putInt("CustomRequestTimer", customRequestTimer);   // omit when unspecified
        tag.putString("Mode", mode.name());
        tag.put("CraftingArrangement", writeStacks(activeCraftingArrangement, registries));
        ListTag slotList = new ListTag();
        for (RecipeSlot slot : recipeSlots) slotList.add(slot.toNBT());
        tag.put("RecipeSlots", slotList);

        if (profile.includesRuntime()) {
            tag.putBoolean("Satisfied", satisfied);
            tag.putBoolean("PromisedSatisfied", promisedSatisfied);
            tag.putBoolean("Waiting", waitingForNetwork);
            tag.putBoolean("RedstonePowered", redstonePowered);
            tag.putInt("Stock", stockLevel);
            tag.putInt("Promised", promisedCount);
            tag.putLong("LastRequestTick", lastRequestTick);
            tag.putInt("Timer", timer);
        }
        if (profile.includesServerOnly() && gaugeId != null)
            tag.putUUID("GaugeId", gaugeId);

        // Connections live in the controller's central graph, not per-component.
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

    public static VirtualGaugeBehaviour fromNBT(FactoryControllerBlockEntity controller,
                                                CompoundTag tag,
                                                net.minecraft.core.HolderLookup.Provider registries) {
        VirtualComponentPosition pos = VirtualComponentPosition.fromNBT(tag.getCompound("Pos"));
        ResourceLocation gaugeItemId = ResourceLocation.parse(tag.getString("Item"));
        Item gaugeItem = BuiltInRegistries.ITEM.get(gaugeItemId);
        UUID networkId = tag.getUUID("Network");

        VirtualGaugeBehaviour b = new VirtualGaugeBehaviour(controller, pos, networkId, gaugeItem);
        b.readGaugeNBT(tag, registries);
        return b;
    }

    protected void readGaugeNBT(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        filter = ItemStack.parseOptional(registries, tag.getCompound("Filter"));
        ignoreData = tag.getBoolean("IgnoreData");
        count = tag.getInt("Count");
        unit = ThresholdUnit.fromName(tag.getString("Unit"));
        requestMode = tag.contains("RequestMode")
                ? RequestMode.fromName(tag.getString("RequestMode"))
                : tag.getBoolean("Passive") ? RequestMode.PASSIVE : RequestMode.NORMAL;
        if (tag.hasUUID("GaugeId")) gaugeId = tag.getUUID("GaugeId");
        else if (tag.hasUUID("PatternId")) gaugeId = tag.getUUID("PatternId");
        else if (controller != null && !filter.isEmpty()) gaugeId = UUID.randomUUID();
        stockLevel = tag.getInt("Stock");
        promisedCount = tag.getInt("Promised");
        lastRequestTick = tag.getLong("LastRequestTick");
        activeCraftingArrangement = readStacks(tag.getList("CraftingArrangement", Tag.TAG_COMPOUND), registries);
        // Old saves have no Mode tag: derive it from the arrangement (crafting iff non-empty).
        mode = tag.contains("Mode") ? GaugeWorkMode.fromName(tag.getString("Mode"))
            : activeCraftingArrangement.isEmpty() ? GaugeWorkMode.REGULAR : GaugeWorkMode.CRAFTING;
        recipeSlots = new ArrayList<>();
        ListTag slotList = tag.getList("RecipeSlots", Tag.TAG_COMPOUND);
        for (int i = 0; i < slotList.size(); i++) recipeSlots.add(RecipeSlot.fromNBT(slotList.getCompound(i)));

        satisfied = tag.getBoolean("Satisfied");
        promisedSatisfied = tag.getBoolean("PromisedSatisfied");
        waitingForNetwork = tag.getBoolean("Waiting");
        redstonePowered = tag.getBoolean("RedstonePowered");
        timer = tag.getInt("Timer");
        recipeAddress = tag.getString("RecipeAddress");
        recipeOutput = tag.getInt("RecipeOutput");
        craftBatch = Math.max(1, tag.getInt("CraftBatch"));
        maxRequestMultiplier = Math.max(1, tag.getInt("MaxRequestMultiplier"));   // absent → 1 (back-compat)
        craftDimension = Math.max(0, tag.getInt("CraftDimension"));
        promiseClearingInterval = tag.getInt("PromiseClearingInterval");
        promiseLimit = tag.contains("PromiseLimit") ? Math.max(0, tag.getInt("PromiseLimit")) : 0;
        promiseLimitByAddress = tag.getBoolean("PromiseLimitByAddress");
        customRequestTimer = Math.max(0, tag.getInt("CustomRequestTimer"));   // absent → 0 (unspecified)

        if (controller != null) timer = requestIntervalTicks();
    }

    private boolean isControllerPowered() {
        return controller != null && controller.isRedstonePowered();
    }

    public boolean isRedstonePaused() {
        return redstonePowered || isControllerPowered();
    }
}
