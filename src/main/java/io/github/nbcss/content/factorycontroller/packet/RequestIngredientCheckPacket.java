package io.github.nbcss.content.factorycontroller.packet;

import com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity;
import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.ServerConfig;
import io.github.nbcss.content.factorycontroller.production.IngredientDemandResolver;
import io.github.nbcss.content.factorycontroller.production.IngredientDemandResolver.PatternDemand;
import io.github.nbcss.content.factorycontroller.production.IngredientDemandResolver.Reserved;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * C2S: the player is hovering the Stock Keeper's Send button over an order containing Production Patterns. Carries
 * the staged patterns (gauge id + ordered amount) plus the order's real items (subtracted from stock); the server
 * computes the ingredient pre-check and replies with an {@link IngredientCheckResultPacket}. Throttled client-side.
 */
public record RequestIngredientCheckPacket(BlockPos keeperPos, List<PatternDemand> patterns,
                                           List<Reserved> reserved) implements CustomPacketPayload {

    public static final Type<RequestIngredientCheckPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "request_ingredient_check"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestIngredientCheckPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> {
            buf.writeBlockPos(pkt.keeperPos);
            buf.writeVarInt(pkt.patterns.size());
            for (PatternDemand pd : pkt.patterns) {
                buf.writeUUID(pd.patternId());
                buf.writeVarInt(pd.demand());
            }
            buf.writeVarInt(pkt.reserved.size());
            for (Reserved r : pkt.reserved) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, r.stack());
                buf.writeVarInt(r.amount());
            }
        }, buf -> {
            BlockPos pos = buf.readBlockPos();
            int n = buf.readVarInt();
            List<PatternDemand> patterns = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                UUID id = buf.readUUID();
                int demand = buf.readVarInt();
                patterns.add(new PatternDemand(id, demand));
            }
            int m = buf.readVarInt();
            List<Reserved> reserved = new ArrayList<>(m);
            for (int i = 0; i < m; i++) {
                ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                int amount = buf.readVarInt();
                reserved.add(new Reserved(stack, amount));
            }
            return new RequestIngredientCheckPacket(pos, patterns, reserved);
        });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestIngredientCheckPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ServerConfig.checkIngredientsOnSend()) return;
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (packet.patterns().isEmpty()) return;
            if (!(player.level().getBlockEntity(packet.keeperPos()) instanceof StockTickerBlockEntity keeper)) return;
            UUID network = keeper.behaviour == null ? null : keeper.behaviour.freqId;
            if (network == null) return;
            long now = player.getServer().overworld().getGameTime();
            IngredientDemandResolver.Result result =
                IngredientDemandResolver.resolve(player.getServer(), network, packet.patterns(), packet.reserved(), now);
            PacketDistributor.sendToPlayer(player,
                new IngredientCheckResultPacket(result.patternMissing(), result.shortfalls()));
        });
    }
}
