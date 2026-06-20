package io.github.nbcss.createfactorycontroller.content.component;

import io.github.nbcss.createfactorycontroller.content.VirtualPanelConnection;
import io.github.nbcss.createfactorycontroller.content.VirtualPanelPosition;
import net.minecraft.nbt.CompoundTag;

/**
 * A gauge ↔ redstone-link connection, held on the link ({@code link.targetedBy[gauge]}). {@link #powered} is the
 * per-connection power state — for a SEND link it tracks whether that specific gauge is driving the link; for a
 * RECEIVE link every connection mirrors the link's network power. It drives the connection-line colour.
 */
public class RedstoneConnection extends VirtualPanelConnection {

    public boolean powered;

    public RedstoneConnection(VirtualPanelPosition from) {
        super(from);
        this.powered = false;
    }

    @Override
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("From", from.toNBT());
        tag.putInt("ArrowBendMode", arrowBendMode);
        tag.putBoolean("Powered", powered);
        return tag;
    }

    public static RedstoneConnection fromNBT(CompoundTag tag) {
        VirtualPanelPosition pos = VirtualPanelPosition.fromNBT(tag.getCompound("From"));
        RedstoneConnection conn = new RedstoneConnection(pos);
        conn.arrowBendMode = tag.getInt("ArrowBendMode");
        conn.powered = tag.getBoolean("Powered");
        return conn;
    }
}
