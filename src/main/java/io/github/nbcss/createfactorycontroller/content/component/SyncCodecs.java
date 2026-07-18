package io.github.nbcss.createfactorycontroller.content.component;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.util.function.Consumer;

/** Small binary read/write helpers shared by the components' client-sync codecs ({@code writeClient}/{@code fromClient}). */
public final class SyncCodecs {

    private SyncCodecs() {}

    /**
     * Runs {@code writer} into a fresh registry-aware buffer and returns the bytes it produced. The delta packet
     * captures each component/connection body this way on the server thread at flush time, so (a) the packet owns
     * an immutable copy — network-thread encoding never reads live mutable state — and (b) the byte array's length
     * frames the body, letting the client skip an entry it cannot apply instead of desyncing the whole buffer.
     */
    public static byte[] capture(RegistryAccess registries, Consumer<RegistryFriendlyByteBuf> writer) {
        // NEOFORGE connection type: the sync path is always this mod on both sides (never a vanilla peer).
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.NEOFORGE);
        writer.accept(buf);
        byte[] out = new byte[buf.readableBytes()];
        buf.readBytes(out);
        return out;
    }

    /** Read-side counterpart of {@link #capture}: a registry-aware view over a captured body. */
    public static RegistryFriendlyByteBuf wrap(byte[] data, RegistryAccess registries) {
        return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registries, ConnectionType.NEOFORGE);
    }

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
