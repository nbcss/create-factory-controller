package io.github.nbcss.createfactorycontroller.content.packet;

import io.github.nbcss.createfactorycontroller.CreateFactoryController;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.RequestMode;
import io.github.nbcss.createfactorycontroller.content.ThresholdUnit;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sent by {@code ConfigureRecipeScreen} when the player confirms (or deletes) a gauge's recipe
 * configuration. Carries the editable fields: recipe address, output-per-craft, promise-clearing
 * interval, target threshold + unit, passive mode flag, per-incoming-connection ingredient amounts,
 * the optional mechanical-crafting arrangement, and the clear-promises / reset flags.
 * {@code reset == true} wipes the gauge's whole recipe config (mirrors Create's trash button).
 */
public record ConfigureRecipePacket(BlockPos pos, VirtualComponentPosition panelPos, String address,
                                    int recipeOutput, int craftBatch, int craftDimension, int promiseInterval,
                                    int count, ThresholdUnit mode, RequestMode requestMode,
                                    List<VirtualComponentPosition> inputPositions, List<Integer> inputAmounts,
                                    List<ItemStack> craftingArrangement, boolean clearPromises,
                                    boolean reset) implements CustomPacketPayload {

    public static final Type<ConfigureRecipePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "configure_recipe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigureRecipePacket> STREAM_CODEC =
        StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pos);
                buf.writeInt(pkt.panelPos.x());
                buf.writeInt(pkt.panelPos.y());
                buf.writeUtf(pkt.address);
                buf.writeInt(pkt.recipeOutput);
                buf.writeInt(pkt.craftBatch);
                buf.writeInt(pkt.craftDimension);
                buf.writeInt(pkt.promiseInterval);
                buf.writeInt(pkt.count);
                buf.writeVarInt(pkt.mode.ordinal());
                buf.writeVarInt(pkt.requestMode.ordinal());
                buf.writeBoolean(pkt.clearPromises);
                buf.writeBoolean(pkt.reset);
                int n = Math.min(pkt.inputPositions.size(), pkt.inputAmounts.size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeInt(pkt.inputPositions.get(i).x());
                    buf.writeInt(pkt.inputPositions.get(i).y());
                    buf.writeInt(pkt.inputAmounts.get(i));
                }
                buf.writeVarInt(pkt.craftingArrangement.size());
                for (ItemStack stack : pkt.craftingArrangement)
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                VirtualComponentPosition panelPos = new VirtualComponentPosition(buf.readInt(), buf.readInt());
                String address = buf.readUtf();
                int recipeOutput = buf.readInt();
                int craftBatch = buf.readInt();
                int craftDimension = buf.readInt();
                int promiseInterval = buf.readInt();
                int count = buf.readInt();
                ThresholdUnit mode = ThresholdUnit.values()[
                    Math.floorMod(buf.readVarInt(), ThresholdUnit.values().length)];
                RequestMode requestMode = RequestMode.byOrdinal(buf.readVarInt());
                boolean clearPromises = buf.readBoolean();
                boolean reset = buf.readBoolean();
                int n = buf.readVarInt();
                List<VirtualComponentPosition> positions = new ArrayList<>(n);
                List<Integer> amounts = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    positions.add(new VirtualComponentPosition(buf.readInt(), buf.readInt()));
                    amounts.add(buf.readInt());
                }
                int m = buf.readVarInt();
                List<ItemStack> arrangement = new ArrayList<>(m);
                for (int i = 0; i < m; i++)
                    arrangement.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                return new ConfigureRecipePacket(pos, panelPos, address, recipeOutput, craftBatch, craftDimension,
                    promiseInterval, count, mode, requestMode, positions, amounts, arrangement,
                    clearPromises, reset);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigureRecipePacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            // One amount per connection (the UI owns the slot split). Last write wins if a position repeats.
            Map<VirtualComponentPosition, Integer> inputs = new LinkedHashMap<>();
            int n = Math.min(packet.inputPositions().size(), packet.inputAmounts().size());
            for (int i = 0; i < n; i++)
                inputs.put(packet.inputPositions().get(i), packet.inputAmounts().get(i));
            be.configureRecipe(packet.panelPos(), packet.address(), packet.recipeOutput(), packet.craftBatch(),
                packet.craftDimension(), packet.promiseInterval(), packet.count(), packet.mode(),
                packet.requestMode(), inputs, packet.craftingArrangement(),
                packet.clearPromises(), packet.reset());
        });
    }
}
