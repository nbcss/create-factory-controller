package io.github.nbcss.createfactorycontroller.content.helper;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record PackageFilter(ItemStack stack) {
    public static final Codec<PackageFilter> CODEC =
        ItemStack.CODEC.xmap(PackageFilter::new, PackageFilter::stack);

    public static final StreamCodec<RegistryFriendlyByteBuf, PackageFilter> STREAM_CODEC =
        ItemStack.STREAM_CODEC.map(PackageFilter::new, PackageFilter::stack);

    public static PackageFilter of(ItemStack stack) {
        return new PackageFilter(stack.copyWithCount(1));
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof PackageFilter other && ItemStack.isSameItemSameComponents(stack, other.stack));
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(stack);
    }
}
