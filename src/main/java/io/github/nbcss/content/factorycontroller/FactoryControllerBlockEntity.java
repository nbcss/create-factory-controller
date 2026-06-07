package io.github.nbcss.content.factorycontroller;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.packet.SyncPanelStatePacket;
import net.minecraft.ChatFormatting;
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

    public final Map<VirtualPanelPosition, VirtualComponentBehaviour> components = new LinkedHashMap<>();
    public final Set<UUID> networks = new LinkedHashSet<>();
    /** Optional per-network nicknames (blank/absent → a default name is shown). Assignment UI is TBD. */
    public final Map<UUID, String> networkNicknames = new HashMap<>();

    /** Set by {@link #sendData()}, flushed once per server tick so the heavy menu packet isn't sent
     *  multiple times in a tick (e.g. when several gauges change state in the same tick). */
    private boolean menuSyncQueued = false;

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
            component.tick();
        // Flush at most one menu snapshot per tick (gauge ticks above may have queued several).
        if (menuSyncQueued) {
            menuSyncQueued = false;
            syncMenuToPlayers();
        }
    }

    // ── Gauge attach ───────────────────────────────────────────────────────

    public void attachComponent(VirtualPanelPosition pos, Player player, @Nullable UUID selectedNetwork) {
        ItemStack carried = player.containerMenu.getCarried();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(carried.getItem());

        if (!ComponentRegistry.contains(itemId)) return;

        if (components.containsKey(pos)) return;

        if (components.size() >= MAX_COMPONENTS) {   // board full — bounds NBT/packet size
            player.displayClientMessage(
                Component.translatable("createfactorycontroller.message.component_limit", MAX_COMPONENTS)
                    .withStyle(ChatFormatting.RED), true);
            playDenySound();
            return;
        }

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

        playBlockSound(refund.getItem(), false);
        setChanged();
        sendData();
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

    public void configureGauge(VirtualPanelPosition pos, ItemStack filter) {
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
                                int promiseInterval, int count, boolean upTo,
                                Map<VirtualPanelPosition, List<Integer>> inputAmounts,
                                List<ItemStack> craftingArrangement, boolean clearPromises, boolean reset) {
        if (!(components.get(pos) instanceof VirtualGaugeBehaviour gauge)) return;

        if (reset) {
            gauge.filter = ItemStack.EMPTY;
            gauge.count = 0;
            gauge.upTo = true;
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
        gauge.count = Math.max(0, count);
        gauge.upTo = upTo;
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
        SyncPanelStatePacket packet = new SyncPanelStatePacket(getBlockPos(), tags, netList, nameList);
        for (ServerPlayer player : viewers)
            PacketDistributor.sendToPlayer(player, packet);
    }

    // ── MenuProvider ───────────────────────────────────────────────────────

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("block.createfactorycontroller.factory_controller");
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

    /**
     * The picked/dropped controller item carries no board data — components drop separately on break.
     * Overridden to a no-op so vanilla's default (which embeds {@code saveCustomOnly} into
     * {@code BLOCK_ENTITY_DATA}) doesn't bake the whole board into the item.
     */
    @Override
    public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        // intentionally empty
    }
}
