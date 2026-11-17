package io.github.nbcss.createfactorycontroller.content.supply;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Snapshot of the component items in a controller's adjacent containers.
 */
public record ComponentSupplyStock(Map<Item, Integer> counts, Set<UUID> networks, boolean present) {

    public static final ComponentSupplyStock EMPTY = new ComponentSupplyStock(Map.of(), Set.of(), false);

    public static final StreamCodec<RegistryFriendlyByteBuf, ComponentSupplyStock> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.<RegistryFriendlyByteBuf, Item, Integer, Map<Item, Integer>>map(
            HashMap::new, ByteBufCodecs.registry(Registries.ITEM), ByteBufCodecs.VAR_INT), ComponentSupplyStock::counts,
        UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.<ByteBuf, UUID, Set<UUID>>collection(LinkedHashSet::new)),
        ComponentSupplyStock::networks,
        ByteBufCodecs.BOOL, ComponentSupplyStock::present,
        ComponentSupplyStock::new
    );

    public int count(Item item) {
        return counts.getOrDefault(item, 0);
    }
}
