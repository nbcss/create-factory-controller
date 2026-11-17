package io.github.nbcss.createfactorycontroller.content.packet;

import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBlockItem;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerMenu;
import io.github.nbcss.createfactorycontroller.content.supply.ComponentItemTier;
import io.github.nbcss.createfactorycontroller.content.supply.PlayerInventorySource;
import io.github.nbcss.createfactorycontroller.content.supply.ComponentSupplyCatalog;
import io.github.nbcss.createfactorycontroller.content.supply.ComponentSupplyEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Puts a component item on the cursor, tuned to {@code network} when the component needs one.
 */
public record PickComponentPacket(ResourceLocation itemId, @Nullable UUID network) implements CustomPacketPayload {

    public static final Type<PickComponentPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "pick_component"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PickComponentPacket> STREAM_CODEC =
        StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, PickComponentPacket::itemId,
            AttachComponentPacket.NULLABLE_UUID, PickComponentPacket::network,
            PickComponentPacket::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(PickComponentPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof FactoryControllerMenu menu)) return;
            FactoryControllerBlockEntity controller = menu.blockEntity();
            if (controller == null) return;
            ComponentSupplyEntry entry = ComponentSupplyCatalog.byId(packet.itemId());
            if (entry == null) return;
            UUID network = entry.networked() ? packet.network() : null;
            if (entry.networked() && (network == null || !controller.networks.contains(network))) return;

            ItemStack carried = menu.getCarried();
            if (isPicked(carried, entry, network)) return;
            Inventory inventory = player.getInventory();
            int worstUsable = player.isCreative() ? ComponentItemTier.UNTUNED : ComponentItemTier.FOREIGN;
            ComponentItemTier tier = new ComponentItemTier(network, controller.networks);
            int slot = PlayerInventorySource.bestSlot(inventory, entry.item(), tier);
            int slotRank = slot < 0 ? ComponentItemTier.UNUSABLE : tier.rank(inventory.items.get(slot), entry.item());
            if (slotRank > worstUsable) slotRank = ComponentItemTier.UNUSABLE;
            int carriedRank = tier.rank(carried, entry.item());
            if (carriedRank > worstUsable) carriedRank = ComponentItemTier.UNUSABLE;
            ItemStack picked;
            if (carriedRank != ComponentItemTier.UNUSABLE
                    && (slotRank == ComponentItemTier.UNUSABLE || carriedRank <= slotRank)) {
                picked = carried;   // the cursor is already the best candidate: just tune it
                carried = ItemStack.EMPTY;
            } else if (slotRank != ComponentItemTier.UNUSABLE) {
                picked = inventory.removeItemNoUpdate(slot);
            } else if (player.isCreative()) {
                picked = new ItemStack(entry.item());
            } else {
                return;
            }
            if (network != null) RetuneCarriedPacket.apply(picked, network);
            menu.setCarried(picked);
            if (!carried.isEmpty() && !inventory.add(carried) && !player.isCreative())
                player.drop(carried, false);
            menu.broadcastChanges();
        });
    }

    /** Whether {@code carried} already is the pick result, so picking again would change nothing. */
    public static boolean isPicked(ItemStack carried, ComponentSupplyEntry entry, @Nullable UUID network) {
        if (!carried.is(entry.item())) return false;
        if (!entry.networked()) return true;
        return LogisticallyLinkedBlockItem.isTuned(carried)
            && Objects.equals(LogisticallyLinkedBlockItem.networkFromStack(carried), network);
    }
}
