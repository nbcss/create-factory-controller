package io.github.nbcss.content.factorycontroller;

import com.google.common.collect.Multimap;
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
import com.simibubi.create.infrastructure.config.AllConfigs;
import io.github.nbcss.CreateFactoryController;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
    /** Target threshold (Create's {@code count}); 0 means the gauge is inactive. */
    public int count = 0;
    public boolean upTo = true;

    // Computed status (server-side, synced to client)
    public boolean satisfied = false;
    public boolean promisedSatisfied = false;
    public boolean waitingForNetwork = false;
    /** No redstone on the virtual board, but kept for parity with Create's status logic. */
    public boolean redstonePowered = false;

    // Internal tick state
    private int lastReportedLevelInStorage = 0;
    private int lastReportedPromises = 0;
    private int lastReportedUnloadedLinks = 0;
    private int timer = 0;
    private boolean forceClearPromises = false;
    public String recipeAddress = "";
    public int recipeOutput = 1;
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
    public int getAmount() {
        return count;
    }

    /**
     * Whether this gauge needs a restock address but has none — a non-zero target with incoming
     * connections (or restocker role) but a blank {@link #recipeAddress}. Mirrors Create's check
     * (minus the packager-block-entity part, which has no virtual-board equivalent).
     */
    public boolean isMissingAddress() {
        return !targetedBy().isEmpty() && count != 0 && recipeAddress.isBlank();
    }

    /**
     * Indicator color (RGB) for the gauge state, matching Create's {@code getIngredientStatusColor}:
     * gray when inactive/misconfigured/powered, then waiting → satisfied → promised → pending.
     */
    public int getIngredientStatusColor() {
        return count == 0 || isMissingAddress() || redstonePowered ? 0x888898
             : waitingForNetwork ? 0x5B3B3B
             : satisfied         ? 0x9EFF7F
             : promisedSatisfied ? 0x22AFAF
             :                     0x3D6EBD;
    }

    // ── Tick ───────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        tickStorageMonitor();
        tickRequests();
    }

    private void tickStorageMonitor() {
        if (filter.isEmpty()) {
            satisfied = true;
            promisedSatisfied = true;
            waitingForNetwork = false;
            return;
        }

        int unloadedLinkCount = getUnloadedLinks();

        if (unloadedLinkCount == 0 && lastReportedUnloadedLinks != 0)
            LogisticsManager.SUMMARIES.invalidate(networkId);

        int inStorage = getLevelInStorage();
        int promised = getPromised();
        int demand = count * (upTo ? 1 : filter.getMaxStackSize());

        boolean shouldSatisfy = inStorage >= demand;
        boolean shouldPromiseSatisfy = inStorage + promised >= demand;
        boolean shouldWait = unloadedLinkCount > 0;

        if (lastReportedLevelInStorage == inStorage && lastReportedPromises == promised
                && lastReportedUnloadedLinks == unloadedLinkCount && satisfied == shouldSatisfy
                && promisedSatisfied == shouldPromiseSatisfy && waitingForNetwork == shouldWait)
            return;

        lastReportedLevelInStorage = inStorage;
        lastReportedPromises = promised;
        lastReportedUnloadedLinks = unloadedLinkCount;
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
        if (timer > 0) { timer--; return; }             // throttle between attempts
        if (satisfied || promisedSatisfied || waitingForNetwork || redstonePowered) return;
        resetTimer();                                   // we're attempting now; throttle the next one
        if (recipeAddress.isBlank()) return;            // recipe mode needs a packager address

        // Sum the ingredient demand from incoming connections, grouped by each source's network.
        Map<UUID, Map<Item, Integer>> demandByNetwork = new LinkedHashMap<>();
        Map<Item, ItemStack> ingredientStacks = new HashMap<>();
        for (Map.Entry<VirtualPanelPosition, VirtualPanelConnection> e : targetedBy().entrySet()) {
            if (!(controller.components.get(e.getKey()) instanceof VirtualGaugeBehaviour source)) continue;
            ItemStack ingredient = source.filter;
            if (ingredient.isEmpty()) continue;
            int needed = Math.max(1, e.getValue().amount);
            demandByNetwork.computeIfAbsent(source.networkId, k -> new LinkedHashMap<>())
                           .merge(ingredient.getItem(), needed, Integer::sum);
            ingredientStacks.putIfAbsent(ingredient.getItem(), ingredient);
        }
        if (demandByNetwork.isEmpty()) return;

        // Verify every ingredient is in stock and build a package order per network.
        Map<UUID, List<BigItemStack>> orderByNetwork = new LinkedHashMap<>();
        List<BigItemStack> allIngredients = new ArrayList<>();
        for (Map.Entry<UUID, Map<Item, Integer>> netEntry : demandByNetwork.entrySet()) {
            InventorySummary summary = LogisticsManager.getSummaryOfNetwork(netEntry.getKey(), false);
            List<BigItemStack> order = new ArrayList<>();
            for (Map.Entry<Item, Integer> need : netEntry.getValue().entrySet()) {
                ItemStack stack = ingredientStacks.get(need.getKey());
                if (summary.getCountOf(stack) < need.getValue()) return;   // can't fulfil → abort
                BigItemStack big = new BigItemStack(stack.copy(), need.getValue());
                order.add(big);
                allIngredients.add(big);
            }
            orderByNetwork.put(netEntry.getKey(), order);
        }

        // Resolve packagers for every network up front; abort entirely if any is busy so we never
        // half-fulfil a recipe.
        List<PackageOrderWithCrafts.CraftingEntry> crafts =
            PackageOrderWithCrafts.singleRecipe(allIngredients).orderedCrafts();
        List<Multimap<PackagerBlockEntity, PackagingRequest>> dispatch = new ArrayList<>();
        for (Map.Entry<UUID, List<BigItemStack>> netEntry : orderByNetwork.entrySet()) {
            PackageOrderWithCrafts order =
                new PackageOrderWithCrafts(new PackageOrder(netEntry.getValue()), crafts);
            Multimap<PackagerBlockEntity, PackagingRequest> found =
                LogisticsManager.findPackagersForRequest(netEntry.getKey(), order, null, recipeAddress);
            if (found == null || found.isEmpty()) return;     // no packager could serve this network
            for (PackagerBlockEntity packager : found.keySet())
                if (packager.isTooBusyFor(RequestType.RESTOCK)) return;
            dispatch.add(found);
        }

        // All clear — perform the requests and promise the produced output.
        for (Multimap<PackagerBlockEntity, PackagingRequest> req : dispatch)
            LogisticsManager.performPackageRequests(req);

        RequestPromiseQueue promises = Create.LOGISTICS.getQueuedPromises(networkId);
        if (promises != null)
            promises.add(new RequestPromise(new BigItemStack(filter.copy(), Math.max(1, recipeOutput))));
        controller.setChanged();
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
        tag.putBoolean("UpTo", upTo);

        tag.putBoolean("Satisfied", satisfied);
        tag.putBoolean("PromisedSatisfied", promisedSatisfied);
        tag.putBoolean("Waiting", waitingForNetwork);
        tag.putInt("LastLevel", lastReportedLevelInStorage);
        tag.putInt("LastPromised", lastReportedPromises);
        tag.putInt("Timer", timer);
        tag.putString("RecipeAddress", recipeAddress);
        tag.putInt("RecipeOutput", recipeOutput);
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
        b.upTo = tag.getBoolean("UpTo");

        b.satisfied = tag.getBoolean("Satisfied");
        b.promisedSatisfied = tag.getBoolean("PromisedSatisfied");
        b.waitingForNetwork = tag.getBoolean("Waiting");
        b.lastReportedLevelInStorage = tag.getInt("LastLevel");
        b.lastReportedPromises = tag.getInt("LastPromised");
        b.timer = tag.getInt("Timer");
        b.recipeAddress = tag.getString("RecipeAddress");
        b.recipeOutput = tag.getInt("RecipeOutput");
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
