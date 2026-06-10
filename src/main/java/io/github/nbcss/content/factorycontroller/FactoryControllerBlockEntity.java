package io.github.nbcss.content.factorycontroller;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.packet.SyncPanelStatePacket;
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

    /** Hard cap on components a single controller may hold — bounds the BE/item NBT and sync size. */
    public static final int MAX_COMPONENTS = 256;
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

    // Reused each tick by the Passive Request demand pre-pass (server thread only) so the propagation walk
    // allocates nothing on the hot path.
    private final List<VirtualGaugeBehaviour> passiveOrderBuf = new ArrayList<>();
    private final Set<VirtualPanelPosition> passiveVisitedBuf = new HashSet<>();

    /** Set by {@link #sendData()}, flushed once per server tick so the heavy menu packet isn't sent
     *  multiple times in a tick (e.g. when several gauges change state in the same tick). */
    private boolean menuSyncQueued = false;

    /** Whether the controller block is currently receiving a redstone signal. While powered, every
     *  gauge stops issuing new requests (mirrors Create's factory-panel redstone gate). */
    private boolean redstonePowered = false;

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

        // Passive Request demand pre-pass: settle the passive-gauge dependency graph consumer-first in this one
        // tick, so a cleared demand doesn't crawl one chain level per tick (which would make an
        // intermediate gauge hold a stale non-zero demand for a tick and fire a spurious request / flash).
        // Only passive gauges participate: a non-passive gauge has a fixed target, so it needs no recompute and
        // demand never propagates through it — it's a constant from a producer's point of view. So we both
        // start the DFS only from passive gauges and recurse only into passive consumers. Skipped entirely when
        // no gauge is in passive mode; buffers are reused so the walk allocates nothing.
        boolean anyPassive = false;
        for (VirtualComponentBehaviour component : components.values())
            if (component instanceof VirtualGaugeBehaviour gauge && gauge.passiveMode) { anyPassive = true; break; }

        if (anyPassive) {
            passiveOrderBuf.clear();
            passiveVisitedBuf.clear();
            for (VirtualComponentBehaviour component : components.values())
                if (component instanceof VirtualGaugeBehaviour gauge && gauge.passiveMode)
                    topoVisitPassiveConsumersFirst(gauge);
            for (VirtualGaugeBehaviour gauge : passiveOrderBuf)
                gauge.computeDemand();
            passiveOrderBuf.clear();
            passiveVisitedBuf.clear();
        }

        for (VirtualComponentBehaviour component : components.values())
            component.tick();
        // Flush at most one menu snapshot per tick (gauge ticks above may have queued several).
        if (menuSyncQueued) {
            menuSyncQueued = false;
            syncMenuToPlayers();
        }
    }

    /** Post-order DFS over passive→passive {@code targeting} (feeds) edges: a passive gauge is appended only after
     *  the passive gauges it feeds, so the list is consumer-before-producer. Non-passive consumers are skipped —
     *  their target is fixed, so demand stops at them and they need no ordering. The {@code visited} set
     *  both dedupes and breaks any recipe cycles (best-effort ordering for the degenerate cyclic case). */
    private void topoVisitPassiveConsumersFirst(VirtualGaugeBehaviour gauge) {
        if (!passiveVisitedBuf.add(gauge.position())) return;
        for (VirtualPanelPosition consumerPos : gauge.targeting())
            if (components.get(consumerPos) instanceof VirtualGaugeBehaviour consumer && consumer.passiveMode)
                topoVisitPassiveConsumersFirst(consumer);
        passiveOrderBuf.add(gauge);
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level != null && !level.isClientSide())
            updateRedstonePower();
    }

    /** Called from {@link FactoryControllerBlock#neighborChanged} so redstone edges are picked up at once. */
    public void onNeighborChanged() {
        if (level != null && !level.isClientSide())
            updateRedstonePower();
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
        if (components.size() >= MAX_COMPONENTS) return;

        UUID networkId;
        if (LogisticallyLinkedBlockItem.isTuned(carried)) {
            networkId = LogisticallyLinkedBlockItem.networkFromStack(carried);
            if (networkId == null) return;
            networks.add(networkId);
        } else {
            // Selection is a client-side GUI choice; validate it against networks this
            // controller actually knows (populated by previously attached tuned gauges).
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
        setChanged();
        sendData();
    }

    // ── Component remove ───────────────────────────────────────────────────────

    public void removeComponent(VirtualPanelPosition pos, Player player) {
        VirtualComponentBehaviour behaviour = components.remove(pos);
        if (behaviour == null) return;

        behaviour.disconnectAll();

        // Return a component item
        ItemStack refund = new ItemStack(BuiltInRegistries.ITEM.get(behaviour.getItemId()));
        if (!player.isCreative() && !player.getInventory().add(refund))
            player.drop(refund, false);

        pruneEmptyNetworks();   // removing the last gauge on a network auto-forgets that network

        playBlockSound(refund.getItem(), false);
        setChanged();
        sendData();
    }

    /**
     * Forgets any known network that no longer has a component on it (and drops its nickname). A network
     * only becomes known when a tuned gauge is attached, so once its last gauge is removed it carries no
     * state worth keeping — auto-deleting it keeps the network selector free of dead entries.
     */
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

    /**
     * Moves the component at {@code from} to the empty cell {@code to}, re-keying both the component
     * map and the connection graph (every neighbour's {@code targeting}/{@code targetedBy} reference
     * to the old position is rewritten to the new one). No-op if {@code to} is occupied or {@code from}
     * holds nothing — the caller (relocate mode) treats an occupied target as an aborted move.
     */
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
        setChanged();
        sendData();
    }

    // ── Configure panel ────────────────────────────────────────────────────

    public void setComponentItem(VirtualPanelPosition pos, ItemStack filter) {
        if (!(components.get(pos) instanceof VirtualGaugeBehaviour gauge)) return;
        gauge.filter = filter.copy();
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
                                int promiseInterval, int count, ThresholdUnit mode, boolean passiveMode,
                                Map<VirtualPanelPosition, List<Integer>> inputAmounts,
                                List<ItemStack> craftingArrangement, boolean clearPromises, boolean reset) {
        if (!(components.get(pos) instanceof VirtualGaugeBehaviour gauge)) return;

        if (reset) {
            gauge.filter = ItemStack.EMPTY;
            gauge.count = 0;
            gauge.unit = ThresholdUnit.ITEMS;
            gauge.passiveMode = false;
            gauge.recipeAddress = "";
            gauge.recipeOutput = 1;
            gauge.craftBatch = 1;
            gauge.promiseClearingInterval = -1;
            gauge.activeCraftingArrangement = new ArrayList<>();
            gauge.disconnectAll();
            setChanged();
            sendData();
            return;
        }

        gauge.recipeAddress = address.length() > MAX_ADDRESS_LENGTH
            ? address.substring(0, MAX_ADDRESS_LENGTH) : address;
        gauge.recipeOutput = Math.max(1, recipeOutput);
        gauge.craftBatch = Math.max(1, craftBatch);
        gauge.promiseClearingInterval = Math.max(-1, Math.min(31, promiseInterval));
        gauge.unit = mode;
        gauge.passiveMode = passiveMode;
        // In passive mode the count is server-managed (recomputed each tick from consumer demand), so don't
        // let the client's transient value override it; otherwise take the player's target.
        if (!passiveMode) gauge.count = Math.max(0, count);
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
    // Mirror Create's factory board: played server-side at the controller so nearby players hear them.

    /** Plays a Create sound entry (e.g. {@code CONFIRM}) at the controller. */
    void playSound(AllSoundEvents.SoundEntry entry, float volume, float pitch) {
        if (level == null || level.isClientSide()) return;
        entry.playOnServer(level, getBlockPos(), volume, pitch);
    }

    /** Plays a vanilla sound event at the controller (BLOCKS category). */
    void playSound(SoundEvent sound, float volume, float pitch) {
        if (level == null || level.isClientSide()) return;
        level.playSound(null, getBlockPos(), sound, SoundSource.BLOCKS, volume, pitch);
    }

    /** Create's rejection blip — invalid placement, connection, or relocate. */
    void playDenySound() {
        if (level == null || level.isClientSide()) return;
        AllSoundEvents.DENY.playOnServer(level, getBlockPos());
    }

    /** Create's wrench-rotate sound (random pitch), used when cycling a connection's arrow-bend mode. */
    void playWrenchRotateSound() {
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
        // Only players with this controller's GUI open need the snapshot — gather them first and skip
        // building it entirely when nobody is watching.
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

        // The block-entity chunk-sync packet carries no board: nothing in the world renders from it, and
        // an open GUI gets the full snapshot via the menu (writeExtraData + SyncPanelStatePacket). This
        // keeps per-tick updates to nearby (non-GUI) players tiny regardless of board size.
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
    }

    // ── Component drops ─────────────────────────────────────────────────────

    /**
     * Drops every attached component as its own item at the controller. Called when the controller
     * block is destroyed: the board configuration is <b>not</b> stored on the dropped controller item,
     * so the gauges are returned to the world instead.
     */
    public void dropComponents() {
        if (level == null || level.isClientSide()) return;
        for (VirtualComponentBehaviour b : components.values()) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(b.getItemId()));
            if (!stack.isEmpty())
                Block.popResource(level, getBlockPos(), stack);
        }
        components.clear();
    }

    @Override
    public void saveToItem(@NotNull ItemStack stack, HolderLookup.@NotNull Provider registries) {
        // intentionally empty
    }
}
