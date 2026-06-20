package io.github.nbcss.createfactorycontroller.content;

import net.minecraft.nbt.CompoundTag;

/**
 * Base for a directed connection on the controller board, keyed in the target's {@code targetedBy} map by its
 * {@link #from} source position. Holds only what every connection kind shares — the source and the arrow-bend
 * rendering mode. Concrete kinds (in {@code content.component}) add their own payload: {@code LogisticsConnection}
 * for gauge ingredient wires (amount + last-request success) and {@code RedstoneConnection} for gauge ↔ redstone-link
 * wires (powered state).
 *
 * <p>The connection kind is determined by the owning component (a gauge holds logistics wires, a link holds redstone
 * wires), so serialization needs no type discriminator — each component reads/writes its own concrete subclass.</p>
 */
public abstract class VirtualPanelConnection {

    public VirtualPanelPosition from;
    public int arrowBendMode; // -1 = auto, 0-3 = fixed bend direction

    protected VirtualPanelConnection(VirtualPanelPosition from) {
        this.from = from;
        this.arrowBendMode = -1;
    }

    public abstract CompoundTag toNBT();
}
