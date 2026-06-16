package io.github.nbcss.content.factorycontroller.packet;

import io.github.nbcss.CreateFactoryController;
import io.github.nbcss.content.factorycontroller.gui.IngredientCheckClient;
import io.github.nbcss.content.factorycontroller.production.IngredientDemandResolver.Shortfall;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** S2C: result of the Send-button ingredient pre-check — the raw ingredients the network can't fully supply. */
public record IngredientCheckResultPacket(boolean patternMissing, List<Shortfall> shortfalls) implements CustomPacketPayload {

    public static final Type<IngredientCheckResultPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(CreateFactoryController.MODID, "ingredient_check_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, IngredientCheckResultPacket> STREAM_CODEC =
        StreamCodec.of((buf, pkt) -> {
            buf.writeBoolean(pkt.patternMissing);
            buf.writeVarInt(pkt.shortfalls.size());
            for (Shortfall s : pkt.shortfalls) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, s.item());
                buf.writeVarInt(s.inStock());
                buf.writeVarInt(s.required());
            }
        }, buf -> {
            boolean missing = buf.readBoolean();
            int n = buf.readVarInt();
            List<Shortfall> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                int inStock = buf.readVarInt();
                int required = buf.readVarInt();
                list.add(new Shortfall(item, inStock, required));
            }
            return new IngredientCheckResultPacket(missing, list);
        });

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(IngredientCheckResultPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> IngredientCheckClient.update(packet.patternMissing(), packet.shortfalls()));
    }
}
