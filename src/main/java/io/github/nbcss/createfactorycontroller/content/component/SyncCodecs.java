package io.github.nbcss.createfactorycontroller.content.component;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;

/** Small binary read/write helpers shared by the components' client-sync codecs ({@code writeClient}/{@code fromClient}). */
public final class SyncCodecs {

    private SyncCodecs() {}

    public static void writePos(RegistryFriendlyByteBuf buf, VirtualComponentPosition p) {
        buf.writeInt(p.x());
        buf.writeInt(p.y());
    }

    public static VirtualComponentPosition readPos(RegistryFriendlyByteBuf buf) {
        return new VirtualComponentPosition(buf.readInt(), buf.readInt());
    }

    public static void writeEnum(RegistryFriendlyByteBuf buf, Enum<?> e) {
        buf.writeVarInt(e.ordinal());
    }

    public static <E extends Enum<E>> E readEnum(RegistryFriendlyByteBuf buf, E[] values) {
        return values[Mth.clamp(buf.readVarInt(), 0, values.length - 1)];
    }
}
