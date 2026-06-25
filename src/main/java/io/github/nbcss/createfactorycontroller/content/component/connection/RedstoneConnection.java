package io.github.nbcss.createfactorycontroller.content.component.connection;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.nbt.CompoundTag;

/**
 * A gauge ↔ redstone-link connection, held on the link ({@code link.targetedBy[gauge]}). {@link #powered} is the
 * per-connection power state — for a SEND link it tracks whether that specific gauge is driving the link; for a
 * RECEIVE link every connection mirrors the link's network power. It drives the connection-line colour.
 */
public class RedstoneConnection extends Connection {

    public boolean powered;

    public RedstoneConnection(VirtualComponentPosition from, VirtualComponentPosition to) {
        super(Type.REDSTONE, from, to);
        this.powered = false;
    }

    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = super.toNBT();
        tag.putBoolean("Powered", powered);
        return tag;
    }

    private RedstoneConnection(CompoundTag tag) {
        super(tag);
        this.powered = tag.getBoolean("Powered");
    }

    public static RedstoneConnection fromNBT(CompoundTag tag) {
        return new RedstoneConnection(tag);
    }
}
