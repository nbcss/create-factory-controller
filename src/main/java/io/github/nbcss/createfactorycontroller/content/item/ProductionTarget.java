package io.github.nbcss.createfactorycontroller.content.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * The data-component payload of a {@link ProductionPatternItem}
 */
public record ProductionTarget(UUID network, UUID patternId, ItemStack display, List<ItemStack> ingredients,
                               String address) {

    public static final Codec<ProductionTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("network").forGetter(ProductionTarget::network),
        UUIDUtil.CODEC.fieldOf("pattern").forGetter(ProductionTarget::patternId),
        ItemStack.CODEC.fieldOf("display").forGetter(ProductionTarget::display),
        ItemStack.CODEC.listOf().fieldOf("ingredients").forGetter(ProductionTarget::ingredients),
        Codec.STRING.fieldOf("address").forGetter(ProductionTarget::address)
    ).apply(i, ProductionTarget::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProductionTarget> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, ProductionTarget::network,
        UUIDUtil.STREAM_CODEC, ProductionTarget::patternId,
        ItemStack.STREAM_CODEC, ProductionTarget::display,
        ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), ProductionTarget::ingredients,
        ByteBufCodecs.STRING_UTF8, ProductionTarget::address,
        ProductionTarget::new);

    @Override
    public boolean equals(Object o) {
        return o instanceof ProductionTarget t && network.equals(t.network) && patternId.equals(t.patternId);
    }

    @Override
    public int hashCode() {
        return network.hashCode() * 31 + patternId.hashCode();
    }
}
