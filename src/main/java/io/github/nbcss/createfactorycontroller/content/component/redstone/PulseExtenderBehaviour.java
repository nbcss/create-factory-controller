package io.github.nbcss.createfactorycontroller.content.component.redstone;

import com.simibubi.create.AllBlocks;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.world.item.Item;

/** Create's Pulse Extender: follows the input, then stays on for a redstone interval after it drops. */
public class PulseExtenderBehaviour extends BrassDiodeBehaviour {

    public static final Type TYPE = type("PULSE_EXTENDER", "lp0", AllBlocks.PULSE_EXTENDER.getId(), 0xD8A15B,
            PulseExtenderBehaviour::new);

    public PulseExtenderBehaviour(FactoryControllerBlockEntity controller, VirtualComponentPosition position, Item item) {
        super(controller, position, item);
    }

    @Override protected Type type() { return TYPE; }
    @Override protected String spriteName() { return "pulse_extender"; }

    /** {@code PulseExtenderBlockEntity#updateState}. Create sets POWERING in the first branch on both sides; the client
     *  snapshot must not, so it is guarded here. */
    @Override
    protected void updateState(boolean server) {
        boolean atMin = state <= 0;
        if (atMin && !powered)
            return;
        if (atMin || powered) {
            if (server) powering = true;
            state = redstoneInterval;
            return;
        }

        if (state == 1) {
            if (powering && server)
                powering = false;
            state = 0;
            return;
        }
        state--;
    }
}
