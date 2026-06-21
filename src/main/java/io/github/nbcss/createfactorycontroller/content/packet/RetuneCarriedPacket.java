package io.github.nbcss.createfactorycontroller.content.packet;

import com.simibubi.create.foundation.block.IBE;
import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.component.ComponentRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Client → server: re-tunes (or clears) the player's carried component item's logistics frequency,
 * driven by scrolling the network selector. Writes the same data Create's {@code assignFrequency}
 * does (BLOCK_ENTITY_DATA "Freq"), but without its "Tuned to frequency" chat message — so scrolling
 * doesn't spam. {@code clear == true} fully untunes the item.
 */
public record RetuneCarriedPacket(boolean clear, @Nullable UUID network) implements CustomPacketPayload {

    public static final Type<RetuneCarriedPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "retune_carried"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RetuneCarriedPacket> STREAM_CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBoolean(pkt.clear);
                buf.writeBoolean(pkt.network != null);
                if (pkt.network != null) {
                    buf.writeLong(pkt.network.getMostSignificantBits());
                    buf.writeLong(pkt.network.getLeastSignificantBits());
                }
            },
            buf -> {
                boolean clear = buf.readBoolean();
                UUID network = buf.readBoolean() ? new UUID(buf.readLong(), buf.readLong()) : null;
                return new RetuneCarriedPacket(clear, network);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RetuneCarriedPacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ItemStack carried = player.containerMenu.getCarried();
            if (!ComponentRegistry.containsNetworkItem(carried)) return;   // never retune a networkless item (link)
            apply(carried, packet.clear ? null : packet.network);
            player.containerMenu.setCarried(carried);
            player.containerMenu.broadcastChanges();   // sync the modified cursor item back
        });
    }

    /** Sets ({@code network != null}) or clears the component item's tuned frequency in place. */
    public static void apply(ItemStack stack, @Nullable UUID network) {
        if (network == null) {
            stack.remove(DataComponents.BLOCK_ENTITY_DATA);   // fully untuned (isTuned → false)
            return;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        tag.putUUID("Freq", network);
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof IBE<?> ibe)
            BlockEntity.addEntityType(tag, ibe.getBlockEntityType());
        stack.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(tag));
    }
}
