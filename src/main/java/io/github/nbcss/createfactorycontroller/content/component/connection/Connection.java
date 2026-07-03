package io.github.nbcss.createfactorycontroller.content.component.connection;

import com.simibubi.create.foundation.utility.CreateLang;
import io.github.nbcss.createfactorycontroller.content.block.ComponentHolder;
import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base for a directed connection on the controller board. Holds only what every connection kind shares — the source,
 * sink and the arrow-bend rendering mode. Concrete kinds (in {@code content.component}) add their own payload:
 * {@code LogisticsConnection} for gauge ingredient wires (amount + last-request success) and {@code RedstoneConnection}
 * for gauge ↔ redstone-link wires (powered state).
 *
 * <p>Edges live in the controller's central {@link ConnectionGraph}, not on the endpoints, so each serialized edge
 * carries a {@code "Type"} discriminator; {@link #fromNBT} dispatches on it to the matching concrete subclass.</p>
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

    public int getConnectionColor(ComponentHolder holder) { return 0x888898; }

    public long getAnimationTick(ComponentHolder holder) { return -1; }

    /** Accept a value pushed by the source for a signal type; returns whether it changed (drives sink notification in
     *  {@code publish}). Non-signal types (logistics) carry no signal value and ignore it. */
    public boolean setValue(ConnectionValue value) { return false; }

    /** The value currently transported on this edge, or {@code null} for a non-signal type. */
    @Nullable
    public ConnectionValue value() { return null; }

    /** Whether this wire may be flipped ({@code from → to} becomes {@code to → from}) <i>right now</i>: its
     *  {@link Type#reversible() kind} allows reversal, both endpoints still exist, and the reversed orientation
     *  passes {@link ConnectionResolver#validate} (which already rejects a pre-existing {@code to → from} edge). One
     *  authority for both the client gate and the server apply, so the server can't be more permissive than the UI. */
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

    /**
     * The category of a connection between two components — <i>what flows</i> along the wire. Replaces hardcoded
     * pairwise {@code instanceof} rules: components declare which type of connections can be made via {@link ConnectionCapability}s, and a wire is
     * possible iff one end can be a {@link ConnectionCapability.Role#SOURCE source} and the other a {@link ConnectionCapability.Role#SINK sink} of the
     * same type (see {@code ConnectionResolver}). Direction is <b>not</b> a type property — it is inferred by trying
     * and validating the possible source/sink orientations.
     *
     * <p>See {@code CONNECTION_REWORK_PLAN.md}.</p>
     */
    public static abstract class Type {
        /** Item/fluid ingredient flow (gauge → gauge). */
        public static final Type LOGISTICS = new Type("LOGISTICS", 0x87FF87) {
            @Override
            public Connection create(VirtualComponentBehaviour source, VirtualComponentBehaviour sink) {
                int amount = source instanceof VirtualGaugeBehaviour g && FluidCompat.isFluidFilter(g.filter) ? 1000 : 1;
                return new LogisticsConnection(source.position(), sink.position(), amount);
            }

            @Override
            public Connection fromNBT(CompoundTag tag) {
                return LogisticsConnection.fromNBT(tag);
            }

            /** Ingredient wires are not reversible: direction encodes producer→consumer (and carries an ingredient
             *  {@code amount}), so a flip would silently swap the recipe's roles — redraw instead. */
            @Override
            public boolean reversible() { return false; }

            /** Ingredient flow: names the two gauges' filters (Create's own "panels connected" prompt). */
            @Override
            public Component successMessage(VirtualComponentBehaviour source, VirtualComponentBehaviour sink) {
                if (!(source instanceof VirtualGaugeBehaviour sourceBehaviour))
                    return super.successMessage(source, sink);
                if (!(sink instanceof VirtualGaugeBehaviour sinkBehaviour))
                    return super.successMessage(source, sink);
                return CreateLang.translate("factory_panel.panels_connected", sourceBehaviour.getFilterName(),
                        sinkBehaviour.getFilterName()).style(ChatFormatting.GREEN).component();
            }
        };
        /** Boolean gating / state signal. */
        public static final Type REDSTONE = new Type("REDSTONE", 0xFC8068) {
            @Override
            public Connection create(VirtualComponentBehaviour source, VirtualComponentBehaviour sink) {
                return new RedstoneConnection(source.position(), sink.position());
            }

            @Override
            public Connection fromNBT(CompoundTag tag) {
                return RedstoneConnection.fromNBT(tag);
            }
        };

        private final String name;
        private final int color;   // tooltip-title colour for this wire kind (0xRRGGBB)

        private Type(String name, int color) {
            this.name = name;
            this.color = color;
        }

        public String name() { return name; }

        public int color() { return color; }

        /** Short, human-readable name for this wire kind (kind-coloured), shown in the connection hover tooltip. */
        public Component displayName() {
            return Component.translatable("createfactorycontroller.connection.type." + name.toLowerCase(java.util.Locale.ROOT))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color)));
        }

        public abstract Connection create(VirtualComponentBehaviour source, VirtualComponentBehaviour sink);
        public abstract Connection fromNBT(CompoundTag tag);

        public Component successMessage(VirtualComponentBehaviour source, VirtualComponentBehaviour sink) {
            return Component.translatable("createfactorycontroller.connection.connected",
                    source.getName(), sink.getName()).withStyle(ChatFormatting.GREEN);
        }

        /** Whether a wire of this kind may be direction-flipped by the player. Signal wires (redstone/logic) default
         *  to reversible; {@link #LOGISTICS} overrides to false (direction is semantic). */
        public boolean reversible() { return true; }

        private static final List<Type> TYPES = List.of(LOGISTICS, REDSTONE);
        private static final Map<String, Type> REGISTRY = Map.of(
                LOGISTICS.name(), LOGISTICS,
                REDSTONE.name(), REDSTONE
        );

        public static Collection<Type> values() {
            return TYPES;
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
