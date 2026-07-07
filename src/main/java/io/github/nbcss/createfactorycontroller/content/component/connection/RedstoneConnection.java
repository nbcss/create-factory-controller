package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.block.ComponentHolder;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.nbt.CompoundTag;

/**
 * A redstone connection whose {@link #state} is pushed by the source component and observed by the sink component.
 */
public class RedstoneConnection extends Connection {
    public static final Type TYPE = new Type("REDSTONE", 0xFC8068) {
        @Override
        public Connection create(VirtualComponentBehaviour source, VirtualComponentBehaviour sink) {
            return new RedstoneConnection(source.position(), sink.position());
        }

        @Override
        public Connection fromNBT(CompoundTag tag) {
            return RedstoneConnection.fromNBT(tag);
        }

        @Override
        public Connection fromClient(net.minecraft.network.RegistryFriendlyByteBuf buf,
                                     VirtualComponentPosition from, VirtualComponentPosition to, int arrowBendMode) {
            RedstoneConnection c = new RedstoneConnection(from, to);
            c.arrowBendMode = arrowBendMode;
            c.state = io.github.nbcss.createfactorycontroller.content.component.SyncCodecs.readEnum(buf, State.values());
            return c;
        }
    };
    public enum State implements ConnectionValue {
        POWERED(0xEF0000), UNPOWERED(0x580101), INACTIVE(0x888898);

        private final int color;

        State(int color) {
            this.color = color;
        }

        public boolean isPowered() { return this == POWERED; }
    }

    private State state;

    public RedstoneConnection(VirtualComponentPosition from, VirtualComponentPosition to) {
        super(TYPE, from, to);
        this.state = State.UNPOWERED;
    }

    public boolean powered() {
        return state.isPowered();
    }

    public State state() {
        return state;
    }

    @Override
    public int getConnectionColor(ComponentHolder holder) {
        return state.color;
    }

    @Override
    public boolean setValue(ConnectionValue value) {
        State next = (State) value;
        if (next == this.state) return false;
        this.state = next;
        return true;
    }

    @Override
    public ConnectionValue value() {
        return state;
    }

    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        tag.putString("State", state.name());
        return tag;
    }

    @Override
    protected void writeClientExtra(net.minecraft.network.RegistryFriendlyByteBuf buf) {
        io.github.nbcss.createfactorycontroller.content.component.SyncCodecs.writeEnum(buf, state);
    }

    private RedstoneConnection(CompoundTag tag) {
        super(tag);
        this.state = tag.contains("State") ? State.valueOf(tag.getString("State"))
                : tag.getBoolean("Powered") ? State.POWERED : State.UNPOWERED;
    }

    public static RedstoneConnection fromNBT(CompoundTag tag) {
        return new RedstoneConnection(tag);
    }
}
