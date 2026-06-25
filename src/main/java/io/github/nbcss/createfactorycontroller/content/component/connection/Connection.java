package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

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
        tag.putString("Type", type.name);
        tag.put("From", from.toNBT());
        tag.put("To", to.toNBT());
        tag.putInt("ArrowBendMode", arrowBendMode);
        return tag;
    }

    @Nullable
    public static Connection fromNBT(CompoundTag tag) {
        var type = Type.get(tag.getString("Type"));
        if (type != null)
            return type.loader.apply(tag);
        else
            return null;
    }

    /**
     * The category of a connection between two components — <i>what flows</i> along the wire. Replaces hardcoded
     * pairwise {@code instanceof} rules: components declare which type of connections can be made via {@link ConnectionCapability}s, and a wire is
     * possible iff one end can be a {@link ConnectionCapability.Role#SOURCE source} and the other a {@link ConnectionCapability.Role#SINK sink} of the
     * same type (see {@code ConnectionResolver}). Direction is <b>not</b> a type property — it is derived from the
     * endpoints' {@link VirtualComponentBehaviour#liveRole live roles}.
     *
     * <p>See {@code CONNECTION_REWORK_PLAN.md}.</p>
     */
    public record Type(String name, Uniqueness uniqueness, Function<CompoundTag, Connection> loader) {
        /** Item/fluid ingredient flow (gauge → gauge). */
        public static final Type LOGISTICS = new Type("logistics", Uniqueness.DIRECTED, LogisticsConnection::fromNBT);
        /** Boolean gating / state signal (gauge ↔ redstone link; future logic tubes). */
        public static final Type REDSTONE = new Type("redstone", Uniqueness.UNDIRECTED, RedstoneConnection::fromNBT);

        private static final List<Type> ALL = List.of(LOGISTICS, REDSTONE);

        public static Collection<Type> values() {
            return ALL;
        }

        @Nullable
        public static Type get(String tag) {
            for (var x: ALL)
                if (x.name.equals(tag))
                    return x;
            return null;
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
