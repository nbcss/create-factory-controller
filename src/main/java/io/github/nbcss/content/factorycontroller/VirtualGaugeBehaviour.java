package io.github.nbcss.content.factorycontroller;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.LogisticsManager;
import com.simibubi.create.content.logistics.packagerLink.RequestPromiseQueue;
import com.simibubi.create.infrastructure.config.AllConfigs;
import io.github.nbcss.CreateFactoryController;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

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
    public int amount = 1;
    public boolean upTo = true;

    // Computed status (server-side, synced to client)
    public boolean satisfied = false;
    public boolean promisedSatisfied = false;
    public boolean waitingForNetwork = false;

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

    // ── Tick ───────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        tickStorageMonitor();
        if (timer > 0) timer--;
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
        int demand = amount * (upTo ? 1 : filter.getMaxStackSize());

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
        tag.putInt("Amount", amount);
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
        b.amount = tag.getInt("Amount");
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
