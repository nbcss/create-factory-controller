package io.github.nbcss.content.factorycontroller.packet;

import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.FactoryControllerBlockEntity;
import io.github.nbcss.content.factorycontroller.VirtualPanelPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sent by {@code ConfigureRecipeScreen} when the player confirms (or deletes) a gauge's recipe
 * configuration. Carries the editable fields: recipe address, output-per-craft, promise-clearing
 * interval, and the per-incoming-connection ingredient amounts. {@code reset == true} wipes the
 * gauge's whole recipe config (mirrors Create's trash button).
 */
public record ConfigureRecipePacket(BlockPos pos, VirtualPanelPosition panelPos, String address,
                                    int recipeOutput, int promiseInterval,
                                    List<VirtualPanelPosition> inputPositions, List<Integer> inputAmounts,
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
                buf.writeInt(pkt.promiseInterval);
                buf.writeBoolean(pkt.reset);
                int n = Math.min(pkt.inputPositions.size(), pkt.inputAmounts.size());
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeInt(pkt.inputPositions.get(i).x());
                    buf.writeInt(pkt.inputPositions.get(i).y());
                    buf.writeInt(pkt.inputAmounts.get(i));
                }
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                VirtualPanelPosition panelPos = new VirtualPanelPosition(buf.readInt(), buf.readInt());
                String address = buf.readUtf();
                int recipeOutput = buf.readInt();
                int promiseInterval = buf.readInt();
                boolean reset = buf.readBoolean();
                int n = buf.readVarInt();
                List<VirtualPanelPosition> positions = new java.util.ArrayList<>(n);
                List<Integer> amounts = new java.util.ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    positions.add(new VirtualPanelPosition(buf.readInt(), buf.readInt()));
                    amounts.add(buf.readInt());
                }
                return new ConfigureRecipePacket(pos, panelPos, address, recipeOutput, promiseInterval,
                    positions, amounts, reset);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigureRecipePacket packet, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(packet.pos()) instanceof FactoryControllerBlockEntity be)) return;
            Map<VirtualPanelPosition, Integer> inputs = new LinkedHashMap<>();
            int n = Math.min(packet.inputPositions().size(), packet.inputAmounts().size());
            for (int i = 0; i < n; i++)
                inputs.put(packet.inputPositions().get(i), packet.inputAmounts().get(i));
            be.configureRecipe(packet.panelPos(), packet.address(), packet.recipeOutput(),
                packet.promiseInterval(), inputs, packet.reset());
        });
    }
}
