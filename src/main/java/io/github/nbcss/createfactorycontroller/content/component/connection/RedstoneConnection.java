package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.nbt.CompoundTag;

/**
 * A redstone connection whose {@link #state} is pushed by the source component and observed by the sink component.
 */
public class RedstoneConnection extends Connection {
    public enum State {
        POWERED(0xEF0000), UNPOWERED(0x580101), INACTIVE(0x888898);

        private final int color;

        State(int color) {
            this.color = color;
        }

        public boolean isPowered() { return this == POWERED; }
        public int color() { return color; }
    }

    private State state;

    public RedstoneConnection(VirtualComponentPosition from, VirtualComponentPosition to) {
        super(Type.REDSTONE, from, to);
        this.state = State.UNPOWERED;
    }

    public boolean powered() {
        return state.isPowered();
    }

    public State state() {
        return state;
    }

    public void setValue(boolean powered) {
        setState(powered ? State.POWERED : State.UNPOWERED);
    }

    public void setState(State state) {
        if (this.state == state) return;
        this.state = state;
        notifyChanged();
    }

    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        tag.putString("State", state.name());
        return tag;
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
