package io.github.nbcss.createfactorycontroller.content.block;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.ServerConfig;
import io.github.nbcss.createfactorycontroller.content.*;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.production.OrderableGaugeRegistry;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FactoryControllerBlockEntity extends SmartBlockEntity implements MenuProvider {

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

    /** Whether the given position lies on the finite ±{@link #BOARD_LIMIT}-cell board. */
    public static boolean isOutBoard(VirtualPanelPosition pos) {
        return Math.abs(pos.x()) > BOARD_LIMIT || Math.abs(pos.y()) > BOARD_LIMIT;
    }

    public final Map<VirtualPanelPosition, VirtualComponentBehaviour> components = new LinkedHashMap<>();
    public final Set<UUID> networks = new LinkedHashSet<>();
    public final Map<UUID, String> networkNicknames = new HashMap<>();

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

        for (VirtualComponentBehaviour component : components.values())
            if (component instanceof VirtualGaugeBehaviour gauge && gauge.requestMode.isPassive())
                gauge.computeDemand();

        for (VirtualComponentBehaviour component : components.values())
            component.tick();
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

    /** Heartbeats this controller's orderable gauges (those with a {@code patternId}, i.e. allow-order + configured)
     *  to {@link OrderableGaugeRegistry}, refreshing their freshness so their tasks stay live. */
    private void heartbeatOrderableGauges() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        long now = serverLevel.getServer().overworld().getGameTime();
        List<OrderableGaugeRegistry.Entry> entries = new ArrayList<>();
        for (VirtualComponentBehaviour c : components.values())
            if (c instanceof VirtualGaugeBehaviour g && g.patternId != null)
                entries.add(new OrderableGaugeRegistry.Entry(g.networkId, g.patternId, g.filter.copy()));
        OrderableGaugeRegistry.heartbeat(level.dimension(), getBlockPos(), entries, now);
    }

    /** Mints/clears a gauge's {@code patternId} as it gains/loses orderability (allow-order + filter + address). On
     *  losing orderability, its active production tasks are aborted (the gauge can no longer fulfil them). */
    private void updateGaugeOrderable(VirtualGaugeBehaviour g) {
        if (level == null || level.isClientSide()) return;
        boolean eligible = g.requestMode.allowsOrder() && !g.filter.isEmpty() && !g.recipeAddress.isBlank();
        if (eligible && g.patternId == null) {
            g.patternId = java.util.UUID.randomUUID();
        } else if (!eligible && g.patternId != null) {
            ProductionOrderManager.invalidateTasksFor(level, g.networkId, g.patternId);
            g.patternId = null;
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        // Called from setRemoved() on both block break and chunk unload — drop our entries promptly (the
        // registry's TTL is the fallback). On reload, the first tick/lazyTick heartbeats again.
        if (level != null && !level.isClientSide())
            OrderableGaugeRegistry.remove(level.dimension(), getBlockPos());
    }

    private void updateRedstonePower() {
        assert level != null;
        boolean powered = level.hasNeighborSignal(getBlockPos());
        if (powered == redstonePowered) return;   // no edge → nothing to propagate
        redstonePowered = powered;
        for (VirtualComponentBehaviour component : components.values())
            if (component instanceof VirtualGaugeBehaviour gauge)
                gauge.controllerPowered = powered;
        sendData();   // push the new powered state to any open GUI
    }

    // ── Gauge attach ───────────────────────────────────────────────────────

    public void attachComponent(VirtualPanelPosition pos, Player player, @Nullable UUID selectedNetwork) {
        ItemStack carried = player.containerMenu.getCarried();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(carried.getItem());

        if (!ComponentRegistry.contains(itemId)) return;

        if (components.containsKey(pos)) return;

        if (isOutBoard(pos)) return;   // outside the finite board

        // board full — bounds NBT/packet size
        if (components.size() >= maxComponents()) return;

        UUID networkId;
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

        VirtualGaugeBehaviour behaviour = new VirtualGaugeBehaviour(this, pos, networkId, itemId);
        behaviour.controllerPowered = redstonePowered;   // inherit the live redstone state
        components.put(pos, behaviour);

        if (!player.isCreative())
            carried.shrink(1);

        playBlockSound(carried.getItem(), true);
        markOrderableDirty();
        setChanged();
        sendData();
    }

    // ── Component remove ───────────────────────────────────────────────────────

    public void removeComponent(VirtualPanelPosition pos, Player player) {
        VirtualComponentBehaviour behaviour = components.remove(pos);
        if (behaviour == null) return;

        // The gauge is gone → abort any production tasks targeting it.
        if (behaviour instanceof VirtualGaugeBehaviour g && g.patternId != null && level != null)
            ProductionOrderManager.invalidateTasksFor(level, g.networkId, g.patternId);

        behaviour.disconnectAll();

        // Return a component item
        ItemStack refund = new ItemStack(BuiltInRegistries.ITEM.get(behaviour.getItemId()));
        if (!player.isCreative() && !player.getInventory().add(refund))
            player.drop(refund, false);

        pruneEmptyNetworks();   // removing the last gauge on a network auto-forgets that network

        playBlockSound(refund.getItem(), false);
        markOrderableDirty();
        setChanged();
        sendData();
    }

    private void pruneEmptyNetworks() {
        networks.removeIf(net -> !isNetworkInUse(net));
        networkNicknames.keySet().removeIf(net -> !networks.contains(net));
    }

    private boolean isNetworkInUse(UUID network) {
        for (VirtualComponentBehaviour c : components.values())
            if (c instanceof VirtualGaugeBehaviour g && network.equals(g.networkId))
                return true;
        return false;
    }

    // ── Component relocate ─────────────────────────────────────────────────────

    public void moveComponent(VirtualPanelPosition from, VirtualPanelPosition to) {
        if (from.equals(to)) return;
        VirtualComponentBehaviour behaviour = components.get(from);
        if (behaviour == null) return;
        if (isOutBoard(to)) return;     // can't relocate off the finite board
        if (components.containsKey(to)) {   // destination occupied → aborted relocate
            playDenySound();
            return;
        }

        components.remove(from);

        // Incoming sources point at us via their `targeting` set.
        for (VirtualPanelConnection conn : behaviour.targetedBy().values()) {
            VirtualComponentBehaviour source = components.get(conn.from);
            if (source != null) {
                source.targeting().remove(from);
                source.targeting().add(to);
            }
        }
        // Outgoing targets key our connection in their `targetedBy` map by our old position.
        for (VirtualPanelPosition targetPos : behaviour.targeting()) {
            VirtualComponentBehaviour target = components.get(targetPos);
            if (target == null) continue;
            VirtualPanelConnection conn = target.targetedBy().remove(from);
            if (conn != null) {
                conn.from = to;
                target.targetedBy().put(to, conn);
            }
        }

        behaviour.setPosition(to);
        components.put(to, behaviour);

        playSound(SoundEvents.COPPER_BREAK, 1f, 1f);
        markOrderableDirty();   // controllerPos is unchanged, but republish keeps the cached locator fresh
        setChanged();
        sendData();
    }

    // ── Configure panel ────────────────────────────────────────────────────

    public void setComponentItem(VirtualPanelPosition pos, ItemStack filter) {
        if (!(components.get(pos) instanceof VirtualGaugeBehaviour gauge)) return;
        gauge.filter = filter.copy();
        // Keep the threshold unit in the right group for the new filter so the count label/tooltip read
        // correctly immediately, before the recipe screen is ever opened: a fluid filter defaults to B,
        // an item filter to items. Only switch when crossing groups, so a later mB/B (or stacks) choice survives.
        boolean fluid = FluidCompat.isFluidFilter(gauge.filter);
        if (fluid && !gauge.unit.fluid) gauge.unit = ThresholdUnit.FLUID_BUCKET;
        else if (!fluid && gauge.unit.fluid) gauge.unit = ThresholdUnit.ITEMS;
        updateGaugeOrderable(gauge);   // filter change can make it (in)eligible → mint/abort patternId
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
    public void configureRecipe(VirtualPanelPosition pos, String address, int recipeOutput, int craftBatch,
                                int craftDimension, int promiseInterval, int count, ThresholdUnit mode,
                                RequestMode requestMode,
                                Map<VirtualPanelPosition, List<Integer>> inputAmounts,
                                List<ItemStack> craftingArrangement, boolean clearPromises, boolean reset) {
        if (!(components.get(pos) instanceof VirtualGaugeBehaviour gauge)) return;

        if (reset) {
            gauge.filter = ItemStack.EMPTY;
            gauge.count = 0;
            gauge.unit = ThresholdUnit.ITEMS;
            gauge.requestMode = RequestMode.NORMAL;
            gauge.recipeAddress = "";
            gauge.recipeOutput = 1;
            gauge.craftBatch = 1;
            gauge.craftDimension = 0;
            gauge.promiseClearingInterval = -1;
            gauge.activeCraftingArrangement = new ArrayList<>();
            gauge.disconnectAll();
            updateGaugeOrderable(gauge);   // no longer orderable → abort tasks + clear patternId
            markOrderableDirty();
            setChanged();
            sendData();
            return;
        }

        gauge.recipeAddress = address.length() > MAX_ADDRESS_LENGTH
            ? address.substring(0, MAX_ADDRESS_LENGTH) : address;
        gauge.recipeOutput = Math.max(1, recipeOutput);
        gauge.craftBatch = Math.max(1, craftBatch);
        gauge.craftDimension = Math.max(0, craftDimension);
        gauge.promiseClearingInterval = Math.max(-1, Math.min(31, promiseInterval));
        gauge.unit = mode;
        gauge.requestMode = requestMode;
        if (!requestMode.isPassive()) gauge.count = Math.max(0, count);
        gauge.activeCraftingArrangement = new ArrayList<>(craftingArrangement);
        for (Map.Entry<VirtualPanelPosition, List<Integer>> e : inputAmounts.entrySet()) {
            VirtualPanelConnection conn = gauge.targetedBy().get(e.getKey());
            if (conn == null) continue;
            List<Integer> amts = new ArrayList<>();
            for (int a : e.getValue()) amts.add(Math.max(1, a));
            if (amts.isEmpty()) amts.add(1);
            conn.amounts = amts;
        }
        if (clearPromises) gauge.requestClearPromises();
        updateGaugeOrderable(gauge);   // mode/address change can make it (in)eligible → mint/abort patternId
        markOrderableDirty();   // request mode / filter / address may have changed
        setChanged();
        sendData();
    }

    // ── Connections ────────────────────────────────────────────────────────

    public void addConnection(VirtualPanelPosition from, VirtualPanelPosition to) {
        VirtualComponentBehaviour target = components.get(to);
        if (!components.containsKey(from) || target == null || from.equals(to)) {
            playDenySound();
            return;
        }
        int before = target.targetedBy().size();
        target.addConnection(from);
        // addConnection silently no-ops on duplicate / max-reached / missing source: a grown map = success.
        if (target.targetedBy().size() > before)
            playSound(SoundEvents.AMETHYST_BLOCK_PLACE, 0.5f, 0.5f);
        else
            playDenySound();
    }

    public void removeConnection(VirtualPanelPosition from, VirtualPanelPosition to) {
        VirtualComponentBehaviour target = components.get(to);
        if (target == null) return;
        target.removeConnection(from);
    }

    public void cycleArrowBend(VirtualPanelPosition pos) {
        VirtualComponentBehaviour behaviour = components.get(pos);
        if (behaviour == null) return;
        behaviour.cycleArrowBend();
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

    /** Create's wrench-rotate sound (random pitch), used when cycling a connection's arrow-bend mode. */
    public void playWrenchRotateSound() {
        if (level == null || level.isClientSide()) return;
        AllSoundEvents.WRENCH_ROTATE.playOnServer(level, getBlockPos(), 1f, level.getRandom().nextFloat() + 0.5f);
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

        List<CompoundTag> tags = new ArrayList<>();
        for (VirtualComponentBehaviour b : components.values())
            tags.add(b.toClientNBT(serverLevel.registryAccess()));
        List<UUID> netList = new ArrayList<>(networks);
        List<String> nameList = new ArrayList<>(netList.size());
        for (UUID id : netList) nameList.add(networkNicknames.getOrDefault(id, ""));
        SyncPanelStatePacket packet = new SyncPanelStatePacket(getBlockPos(), tags, netList, nameList, customName);
        for (ServerPlayer player : viewers)
            PacketDistributor.sendToPlayer(player, packet);
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

        if (!customName.isBlank()) tag.putString("CustomName", customName);

        ListTag gaugeList = new ListTag();
        for (VirtualComponentBehaviour b : components.values())
            gaugeList.add(b.toNBT(registries));
        tag.put("Components", gaugeList);

        ListTag networkList = new ListTag();
        for (UUID id : networks) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            String nick = networkNicknames.get(id);
            if (nick != null && !nick.isBlank()) entry.putString("Name", nick);
            networkList.add(entry);
        }
        tag.put("Networks", networkList);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        customName = tag.getString("CustomName");   // "" when absent

        components.clear();
        ListTag componentList = tag.getList("Components", Tag.TAG_COMPOUND);
        for (int i = 0; i < componentList.size(); i++) {
            VirtualComponentBehaviour b = ComponentRegistry.fromNBT(this, componentList.getCompound(i), registries);
            if (b != null) components.put(b.position(), b);
        }

        networks.clear();
        networkNicknames.clear();
        ListTag networkList = tag.getList("Networks", Tag.TAG_COMPOUND);
        for (int i = 0; i < networkList.size(); i++) {
            CompoundTag entry = networkList.getCompound(i);
            UUID id = entry.getUUID("Id");
            networks.add(id);
            if (entry.contains("Name")) networkNicknames.put(id, entry.getString("Name"));
        }

        markOrderableDirty();   // republish to the registry on the next server tick (level is set by then)
    }

    // ── Component drops ─────────────────────────────────────────────────────

    /** Aborts the open production tasks of every orderable gauge (called when the controller is destroyed —
     *  whether the board is dropped as items or preserved on the controller item). */
    public void abortAllTasks() {
        if (level == null || level.isClientSide()) return;
        for (VirtualComponentBehaviour b : components.values())
            if (b instanceof VirtualGaugeBehaviour g && g.patternId != null)
                ProductionOrderManager.invalidateTasksFor(level, g.networkId, g.patternId);
    }

    // ── Setup preservation (controller item carries the board on break) ─────────

    /** True when there is any configured board state worth carrying on the dropped item. */
    public boolean hasSetup() {
        return !components.isEmpty() || !networks.isEmpty() || !customName.isBlank();
    }

    /**
     * The minimal board setup to persist on a dropped controller item: each component's config with the
     * runtime/bulb state and the runtime-minted {@code PatternId} stripped, plus the tuned networks and name.
     */
    public CompoundTag writeSetup(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!customName.isBlank()) tag.putString("CustomName", customName);

        ListTag gaugeList = new ListTag();
        for (VirtualComponentBehaviour b : components.values()) {
            gaugeList.add(b.toItemNBT(registries));
        }
        tag.put("Components", gaugeList);

        ListTag networkList = new ListTag();
        for (UUID id : networks) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            String nick = networkNicknames.get(id);
            if (nick != null && !nick.isBlank()) entry.putString("Name", nick);
            networkList.add(entry);
        }
        tag.put("Networks", networkList);
        return tag;
    }

    /** Restores a board from {@link #writeSetup} onto a freshly placed controller (server side). */
    public void applySetup(CompoundTag tag, HolderLookup.Provider registries) {
        if (level == null || level.isClientSide()) return;

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

        networks.clear();
        networkNicknames.clear();
        ListTag networkList = tag.getList("Networks", Tag.TAG_COMPOUND);
        for (int i = 0; i < networkList.size(); i++) {
            CompoundTag entry = networkList.getCompound(i);
            UUID id = entry.getUUID("Id");
            networks.add(id);
            if (entry.contains("Name")) networkNicknames.put(id, entry.getString("Name"));
        }
        setChanged();
        sendData();
        markOrderableDirty();
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
