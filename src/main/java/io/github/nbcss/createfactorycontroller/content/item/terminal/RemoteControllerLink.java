package io.github.nbcss.createfactorycontroller.content.item.terminal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record RemoteControllerLink(ResourceKey<Level> dimension, BlockPos pos, String cachedName) {

    public static final Codec<RemoteControllerLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceKey.codec(Registries.DIMENSION).fieldOf("Dimension").forGetter(RemoteControllerLink::dimension),
        BlockPos.CODEC.fieldOf("Pos").forGetter(RemoteControllerLink::pos),
        Codec.STRING.optionalFieldOf("Name", "").forGetter(RemoteControllerLink::cachedName)
    ).apply(instance, RemoteControllerLink::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteControllerLink> STREAM_CODEC = StreamCodec.composite(
        ResourceKey.streamCodec(Registries.DIMENSION), RemoteControllerLink::dimension,
        BlockPos.STREAM_CODEC, RemoteControllerLink::pos,
        ByteBufCodecs.STRING_UTF8, RemoteControllerLink::cachedName,
        RemoteControllerLink::new
    );

    public boolean isAt(ResourceKey<Level> dim, BlockPos at) {
        return dimension.equals(dim) && pos.equals(at);
    }

    public RemoteControllerLink withName(String name) {
        return name.equals(cachedName) ? this : new RemoteControllerLink(dimension, pos, name);
    }
}
