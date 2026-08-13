package io.github.nbcss.createfactorycontroller.content.helper;

import com.mojang.serialization.Codec;
import com.simibubi.create.content.logistics.BigItemStack;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Transient carry for non-crafting JEI orders: one group per recipe, laid out as [output, ...consolidated ingredient
 *  totals] (all BigItemStack so ingredient counts aren't clamped). Stamped on the packager's fragments; the re-packager
 *  bundles each group's ingredients into its own filtered box. Keeps the recipe's ingredients off Create's orderedCrafts,
 *  so intermediate fragments don't carry a crafting-grid pattern. */
public record NonCraftGroups(List<List<BigItemStack>> groups) {
    public static final Codec<NonCraftGroups> CODEC =
        BigItemStack.CODEC.listOf().listOf().xmap(NonCraftGroups::new, NonCraftGroups::groups);

    public static final StreamCodec<RegistryFriendlyByteBuf, NonCraftGroups> STREAM_CODEC =
        CatnipStreamCodecBuilders.list(CatnipStreamCodecBuilders.list(BigItemStack.STREAM_CODEC))
            .map(NonCraftGroups::new, NonCraftGroups::groups);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NonCraftGroups other) || groups.size() != other.groups.size()) return false;
        for (int g = 0; g < groups.size(); g++) {
            List<BigItemStack> a = groups.get(g), b = other.groups.get(g);
            if (a.size() != b.size()) return false;
            for (int i = 0; i < a.size(); i++)
                if (a.get(i).count != b.get(i).count
                    || !ItemStack.isSameItemSameComponents(a.get(i).stack, b.get(i).stack))
                    return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (List<BigItemStack> g : groups)
            for (BigItemStack b : g)
                h = 31 * (31 * h + ItemStack.hashItemAndComponents(b.stack)) + b.count;
        return h;
    }
}
