package io.github.nbcss.content.factorycontroller;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.packet.SyncPanelStatePacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FactoryControllerBlockEntity extends SmartBlockEntity implements MenuProvider {

    public final Map<VirtualPanelPosition, VirtualPanelBehaviour> gauges = new LinkedHashMap<>();
    public final Set<UUID> knownNetworks = new LinkedHashSet<>();

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
        for (VirtualPanelBehaviour gauge : gauges.values())
            gauge.tick();
    }

    // ── Gauge attach ───────────────────────────────────────────────────────

    public void attachComponent(VirtualPanelPosition pos, Player player, @Nullable UUID selectedNetwork) {
        ItemStack carried = player.containerMenu.getCarried();
        if (!GaugeHelper.isValidGauge(carried)) return;

        if (gauges.containsKey(pos)) return;

        UUID networkId;
        if (LogisticallyLinkedBlockItem.isTuned(carried)) {
            networkId = LogisticallyLinkedBlockItem.networkFromStack(carried);
            if (networkId == null) return;
            knownNetworks.add(networkId);
        } else {
            // Selection is a client-side GUI choice; validate it against networks this
            // controller actually knows (populated by previously attached tuned gauges).
            if (selectedNetwork == null || !knownNetworks.contains(selectedNetwork)) {
                player.displayClientMessage(
                    Component.translatable("factory_controller.no_network_selected")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            networkId = selectedNetwork;
        }

        ResourceLocation gaugeItemId = GaugeHelper.getGaugeItemId(carried);
        VirtualPanelBehaviour behaviour = new VirtualPanelBehaviour(this, pos, networkId, gaugeItemId);
        gauges.put(pos, behaviour);
        carried.shrink(1);

        setChanged();
        sendData();
    }

    // ── Gauge remove ───────────────────────────────────────────────────────

    public void removeComponent(VirtualPanelPosition pos, Player player) {
        VirtualPanelBehaviour behaviour = gauges.remove(pos);
        if (behaviour == null) return;

        behaviour.disconnectAll();

        // Return an untuned gauge item (no UUID written)
        ItemStack refund = new ItemStack(BuiltInRegistries.ITEM.get(behaviour.gaugeItemId));
        if (!player.getInventory().add(refund))
            player.drop(refund, false);

        setChanged();
        sendData();
    }

    // ── Configure panel ────────────────────────────────────────────────────

    public void configureGauge(VirtualPanelPosition pos, ItemStack filter, int amount) {
        VirtualPanelBehaviour behaviour = gauges.get(pos);
        if (behaviour == null) return;
        behaviour.filter = filter.copy();
        behaviour.amount = Math.max(1, amount);
        setChanged();
        sendData();
    }

    // ── Connections ────────────────────────────────────────────────────────

    public void addConnection(VirtualPanelPosition from, VirtualPanelPosition to) {
        if (!gauges.containsKey(from) || !gauges.containsKey(to)) return;
        if (from.equals(to)) return;
        gauges.get(to).addConnection(from);
    }

    public void removeConnection(VirtualPanelPosition from, VirtualPanelPosition to) {
        VirtualPanelBehaviour target = gauges.get(to);
        if (target == null) return;
        target.removeConnection(from);
    }

    public void cycleArrowBend(VirtualPanelPosition pos) {
        VirtualPanelBehaviour behaviour = gauges.get(pos);
        if (behaviour == null) return;
        behaviour.cycleArrowBend();
    }

    // ── Live menu sync ─────────────────────────────────────────────────────

    @Override
    public void sendData() {
        super.sendData();
        syncMenuToPlayers();
    }

    private void syncMenuToPlayers() {
        if (level == null || level.isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) level;
        List<CompoundTag> tags = new ArrayList<>();
        for (VirtualPanelBehaviour b : gauges.values())
            tags.add(b.toNBT(serverLevel.registryAccess()));
        SyncPanelStatePacket packet = new SyncPanelStatePacket(getBlockPos(), tags, new ArrayList<>(knownNetworks));
        for (ServerPlayer player : serverLevel.getServer().getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof FactoryControllerMenu menu
                    && menu.controllerPos.equals(getBlockPos()))
                PacketDistributor.sendToPlayer(player, packet);
        }
    }

    // ── MenuProvider ───────────────────────────────────────────────────────

    @Override
    public Component getDisplayName() {
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

        ListTag gaugeList = new ListTag();
        for (VirtualPanelBehaviour b : gauges.values())
            gaugeList.add(b.toNBT(registries));
        tag.put("Gauges", gaugeList);

        ListTag networkList = new ListTag();
        for (UUID id : knownNetworks) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            networkList.add(entry);
        }
        tag.put("KnownNetworks", networkList);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);

        gauges.clear();
        ListTag gaugeList = tag.getList("Gauges", Tag.TAG_COMPOUND);
        for (int i = 0; i < gaugeList.size(); i++) {
            VirtualPanelBehaviour b = VirtualPanelBehaviour.fromNBT(this, gaugeList.getCompound(i), registries);
            gauges.put(b.position, b);
        }

        knownNetworks.clear();
        ListTag networkList = tag.getList("KnownNetworks", Tag.TAG_COMPOUND);
        for (int i = 0; i < networkList.size(); i++)
            knownNetworks.add(networkList.getCompound(i).getUUID("Id"));
    }

    @Override
    public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        super.saveToItem(stack, registries);
        CompoundTag beData = new CompoundTag();
        write(beData, registries, false);
        stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(beData));
    }
}
