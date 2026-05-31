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

    public final Map<VirtualPanelPosition, VirtualComponentBehaviour> components = new LinkedHashMap<>();
    public final Set<UUID> networks = new LinkedHashSet<>();

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
    }

    // ── Gauge attach ───────────────────────────────────────────────────────

    public void attachComponent(VirtualPanelPosition pos, Player player, @Nullable UUID selectedNetwork) {
        ItemStack carried = player.containerMenu.getCarried();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(carried.getItem());

        if (!ComponentRegistry.contains(itemId)) return;

        if (components.containsKey(pos)) return;

        UUID networkId;
        if (LogisticallyLinkedBlockItem.isTuned(carried)) {
            networkId = LogisticallyLinkedBlockItem.networkFromStack(carried);
            if (networkId == null) return;
            networks.add(networkId);
        } else {
            // Selection is a client-side GUI choice; validate it against networks this
            // controller actually knows (populated by previously attached tuned gauges).
            if (selectedNetwork == null || !networks.contains(selectedNetwork)) {
                player.displayClientMessage(
                    Component.translatable("factory_controller.no_network_selected")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            networkId = selectedNetwork;
        }

        VirtualGaugeBehaviour behaviour = new VirtualGaugeBehaviour(this, pos, networkId, itemId);
        components.put(pos, behaviour);
        carried.shrink(1);

        // TEST: auto-connect the new gauge to any one existing component, so the connection
        // rendering can be exercised without a wiring UI yet. Flow is new → existing, so the
        // arrowhead lands on the existing (destination) gauge.
        for (VirtualPanelPosition existing : components.keySet()) {
            if (!existing.equals(pos)) {
                behaviour.addConnection(existing);
                break;
            }
        }

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
        if (!player.getInventory().add(refund))
            player.drop(refund, false);

        setChanged();
        sendData();
    }

    // ── Configure panel ────────────────────────────────────────────────────

    public void configureGauge(VirtualPanelPosition pos, ItemStack filter, int amount) {
        if (!(components.get(pos) instanceof VirtualGaugeBehaviour gauge)) return;
        gauge.filter = filter.copy();
        gauge.amount = Math.max(1, amount);
        setChanged();
        sendData();
    }

    // ── Connections ────────────────────────────────────────────────────────

    public void addConnection(VirtualPanelPosition from, VirtualPanelPosition to) {
        if (!components.containsKey(from) || !components.containsKey(to)) return;
        if (from.equals(to)) return;
        components.get(to).addConnection(from);
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
        for (VirtualComponentBehaviour b : components.values())
            tags.add(b.toNBT(serverLevel.registryAccess()));
        SyncPanelStatePacket packet = new SyncPanelStatePacket(getBlockPos(), tags, new ArrayList<>(networks));
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
        for (VirtualComponentBehaviour b : components.values())
            gaugeList.add(b.toNBT(registries));
        tag.put("Components", gaugeList);

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

        components.clear();
        ListTag componentList = tag.getList("Components", Tag.TAG_COMPOUND);
        for (int i = 0; i < componentList.size(); i++) {
            VirtualComponentBehaviour b = ComponentRegistry.fromNBT(this, componentList.getCompound(i), registries);
            if (b != null) components.put(b.position(), b);
        }

        networks.clear();
        ListTag networkList = tag.getList("Networks", Tag.TAG_COMPOUND);
        for (int i = 0; i < networkList.size(); i++)
            networks.add(networkList.getCompound(i).getUUID("Id"));
    }

    @Override
    public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        super.saveToItem(stack, registries);
        CompoundTag beData = new CompoundTag();
        write(beData, registries, false);
        stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(beData));
    }
}
