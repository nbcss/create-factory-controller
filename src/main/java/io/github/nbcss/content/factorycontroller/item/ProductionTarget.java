package io.github.nbcss.content.factorycontroller.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * The data-component payload of a {@link ProductionPatternItem}: a robust reference to the specific orderable
 * gauge that backs the blueprint ({@link #network} + position-independent {@link #patternId}), plus a cached
 * {@link #display} stack of the item that gauge produces (for the blueprint's icon overlay and tooltip without
 * resolving the gauge client-side).
 */
public record ProductionTarget(UUID network, UUID patternId, ItemStack display) {

    public static final Codec<ProductionTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
        UUIDUtil.CODEC.fieldOf("network").forGetter(ProductionTarget::network),
        UUIDUtil.CODEC.fieldOf("pattern").forGetter(ProductionTarget::patternId),
        ItemStack.CODEC.fieldOf("display").forGetter(ProductionTarget::display)
    ).apply(i, ProductionTarget::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProductionTarget> STREAM_CODEC = StreamCodec.composite(
        UUIDUtil.STREAM_CODEC, ProductionTarget::network,
        UUIDUtil.STREAM_CODEC, ProductionTarget::patternId,
        ItemStack.STREAM_CODEC, ProductionTarget::display,
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
