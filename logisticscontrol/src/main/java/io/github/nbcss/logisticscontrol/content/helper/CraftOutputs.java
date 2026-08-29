package io.github.nbcss.logisticscontrol.content.helper;

import com.mojang.serialization.Codec;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Transient carry for a multi-crafting-recipe JEI order: the recipe outputs the player picked, one per crafting entry
 *  in {@code orderedCrafts} order. Stamped on the packager's fragments so the re-packager pins each split box to the
 *  intended recipe (positionally) instead of first-matching an ambiguous ingredient pattern. */
public record CraftOutputs(List<ItemStack> outputs) {
    public CraftOutputs {
        outputs = outputs == null ? List.of() : outputs.stream()
            .map(s -> s != null && PackageFilter.canLabel(s) ? s : ItemStack.EMPTY)
            .toList();
    }

    public static final Codec<CraftOutputs> CODEC =
        ItemStack.OPTIONAL_CODEC.listOf().xmap(CraftOutputs::new, CraftOutputs::outputs);

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftOutputs> STREAM_CODEC =
        CatnipStreamCodecBuilders.list(ItemStack.OPTIONAL_STREAM_CODEC).map(CraftOutputs::new, CraftOutputs::outputs);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CraftOutputs other) || outputs.size() != other.outputs.size()) return false;
        for (int i = 0; i < outputs.size(); i++)
            if (!ItemStack.isSameItemSameComponents(outputs.get(i), other.outputs.get(i))) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (ItemStack s : outputs) h = 31 * h + ItemStack.hashItemAndComponents(s);
        return h;
    }
}
