package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.block.ComponentHolder;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.SyncCodecs;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Base for a directed connection on the controller board. Holds only what every connection kind shares — the source,
 * sink and the arrow-bend rendering mode.
 */
public abstract class Connection {
    public final Type type;
    public VirtualComponentPosition from;
    public VirtualComponentPosition to;
    public int arrowBendMode; // -1 = auto, 0-3 = fixed bend direction

    protected Connection(Type type,
                         VirtualComponentPosition from,
                         VirtualComponentPosition to,
                         int arrowBendMode) {
        this.type = type;
        this.from = from;
        this.to = to;
        this.arrowBendMode = arrowBendMode;
    }

    protected Connection(Type type, VirtualComponentPosition from, VirtualComponentPosition to) {
        this(type, from, to, -1);
    }

    protected Connection(CompoundTag tag) {
        this(
                Type.get(tag.getString("Type")),
                VirtualComponentPosition.fromNBT(tag.getCompound("From")),
                VirtualComponentPosition.fromNBT(tag.getCompound("To")),
                tag.getInt("ArrowBendMode")
        );
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type.name());
        tag.put("From", from.toNBT());
        tag.put("To", to.toNBT());
        tag.putInt("ArrowBendMode", arrowBendMode);
        return tag;
    }

    /**
     * Writes the persistent configuration that belongs in a component blueprint. Runtime state must be omitted.
     * This is deliberately abstract so every new connection kind has to make an explicit export decision.
     */
    public abstract CompoundTag toExportNBT();

    public int getConnectionColor(ComponentHolder holder) { return 0x888898; }

    public long getAnimationTick(ComponentHolder holder) { return -1; }

    /** Accept a value pushed by the source for a signal type; returns whether it changed (drives sink notification in
     *  {@code publish}). Non-signal types carry no signal value and ignore it. */
    public boolean setValue(ConnectionValue value) { return false; }

    /** The value currently transported on this edge, or {@code null} for a non-signal type. */
    @Nullable
    public ConnectionValue value() { return null; }

    /** Whether this wire may be flipped ({@code from → to} becomes {@code to → from}) */
    public boolean canReverse(ComponentHolder holder) {
        if (!type.reversible()) return false;
        VirtualComponentBehaviour newSource = holder.componentAt(to);
        VirtualComponentBehaviour newSink = holder.componentAt(from);
        return newSource != null && newSink != null
                && ConnectionResolver.validate(type, newSource, newSink).isSuccess();
    }

    @Nullable
    public static Connection fromNBT(CompoundTag tag) {
        var type = Type.get(tag.getString("Type"));
        if (type != null)
            return type.fromNBT(tag);
        else
            return null;
    }

    // ── Client sync (binary) ──────────────────────────────────────────────────

    public void writeClient(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(type.name());
        SyncCodecs.writePos(buf, from);
        SyncCodecs.writePos(buf, to);
        buf.writeByte(arrowBendMode);
        writeClientExtra(buf);
    }

    /** Per-kind payload after the shared header. Read back by {@link Type#fromClient}. */
    protected void writeClientExtra(RegistryFriendlyByteBuf buf) {}

    @Nullable
    public static Connection fromClient(RegistryFriendlyByteBuf buf) {
        Type type = Type.get(buf.readUtf());
        VirtualComponentPosition from = SyncCodecs.readPos(buf);
        VirtualComponentPosition to = SyncCodecs.readPos(buf);
        int bend = buf.readByte();
        return type == null ? null : type.fromClient(buf, from, to, bend);
    }

    /**
     * The category of a connection between two components — <i>what flows</i> along the wire.
     */
    public static abstract class Type {
        private final String name;
        private final int color;

        public Type(String name, int color) {
            this.name = name;
            this.color = color;
        }

        public String name() { return name; }

        public int color() { return color; }

        /** Short, human-readable name for this wire kind (kind-coloured) */
        public Component displayName() {
            return Component.translatable("createfactorycontroller.connection.type." +
                            name.toLowerCase(java.util.Locale.ROOT))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
        }

        public abstract Connection create(VirtualComponentBehaviour source, VirtualComponentBehaviour sink);
        public abstract Connection fromNBT(CompoundTag tag);

        /** Reconstructs a client edge from {@link Connection#writeClientExtra}; header fields already read. */
        public abstract Connection fromClient(RegistryFriendlyByteBuf buf,
                                              VirtualComponentPosition from,
                                              VirtualComponentPosition to,
                                              int arrowBendMode);

        public Component successMessage(VirtualComponentBehaviour source, VirtualComponentBehaviour sink) {
            return Component.translatable("createfactorycontroller.connection.connected",
                    source.getName(), sink.getName()).withStyle(ChatFormatting.GREEN);
        }

        /** Whether a wire of this kind may be direction-flipped by the player. */
        public boolean reversible() { return true; }

        private static final Map<String, Type> REGISTRY = new LinkedHashMap<>();

        public static void registerConnections() {
            registerType(LogisticsConnection.TYPE);
            registerType(RedstoneConnection.TYPE);
        }

        public static void registerType(Connection.Type type) {
            REGISTRY.put(type.name(), type);
        }

        public static Collection<Type> values() {
            return REGISTRY.values();
        }

        @Nullable
        public static Type get(String name) {
            return REGISTRY.get(name);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Type type)) return false;
            return Objects.equals(name, type.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }
    }
}
