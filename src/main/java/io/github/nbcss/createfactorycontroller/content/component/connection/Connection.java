package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.compat.fluids.FluidCompat;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Base for a directed connection on the controller board. Holds only what every connection kind shares — the source,
 * sink and the arrow-bend rendering mode. Concrete kinds (in {@code content.component}) add their own payload:
 * {@code LogisticsConnection} for gauge ingredient wires (amount + last-request success) and {@code RedstoneConnection}
 * for gauge ↔ redstone-link wires (powered state).
 *
 * <p>The connection kind is determined by the owning component (a gauge holds logistics wires, a link holds redstone
 * wires), so serialization needs no type discriminator — each component reads/writes its own concrete subclass.</p>
 */
public abstract class Connection {

    public final Type type;
    public VirtualComponentPosition from;
    public VirtualComponentPosition to;
    public int arrowBendMode; // -1 = auto, 0-3 = fixed bend direction
    private transient Consumer<Connection> onChanged;

    protected Connection(Type type, VirtualComponentPosition from, VirtualComponentPosition to, int arrowBendMode) {
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

    public void setOnChanged(@Nullable Consumer<Connection> onChanged) {
        this.onChanged = onChanged;
    }

    protected void notifyChanged() {
        if (onChanged != null) onChanged.accept(this);
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
        public static final Type LOGISTICS = new Type("LOGISTICS", Uniqueness.DIRECTED) {
            @Override
            public Connection create(VirtualComponentBehaviour source, VirtualComponentBehaviour sink) {
                int amount = source instanceof VirtualGaugeBehaviour g && FluidCompat.isFluidFilter(g.filter) ? 1000 : 1;
                return new LogisticsConnection(source.position(), sink.position(), amount);
            }

            @Override
            public Connection fromNBT(CompoundTag tag) {
                return LogisticsConnection.fromNBT(tag);
            }
        };
        /** Boolean gating / state signal. */
        public static final Type REDSTONE = new Type("REDSTONE", Uniqueness.UNDIRECTED) {
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
        private final Uniqueness uniqueness;

        private Type(String name, Uniqueness uniqueness) {
            this.name = name;
            this.uniqueness = uniqueness;
        }

        public String name() { return name; }
        public Uniqueness uniqueness() { return uniqueness; }

        public abstract Connection create(VirtualComponentBehaviour source, VirtualComponentBehaviour sink);
        public abstract Connection fromNBT(CompoundTag tag);

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

    /** How many connections of the same type may exist between a pair of components. */
    public enum Uniqueness {
        /** {@code A→B} and {@code B→A} are considered different. */
        DIRECTED,
        /** {@code A→B} and {@code B→A} are considered the same. */
        UNDIRECTED,
    }
}
