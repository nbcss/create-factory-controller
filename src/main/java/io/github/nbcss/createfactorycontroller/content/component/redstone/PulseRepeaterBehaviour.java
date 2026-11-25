package io.github.nbcss.createfactorycontroller.content.component.redstone;

import com.simibubi.create.AllBlocks;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.world.item.Item;

/** Create's Pulse Repeater: one short pulse a redstone interval after each rising input. */
public class PulseRepeaterBehaviour extends BrassDiodeBehaviour {

    public static final Type TYPE = type("PULSE_REPEATER", "lp1", AllBlocks.PULSE_REPEATER.getId(), 0xD8A15B,
            PulseRepeaterBehaviour::new);

    public PulseRepeaterBehaviour(FactoryControllerBlockEntity controller, VirtualComponentPosition position, Item item) {
        super(controller, position, item);
    }

    @Override protected Type type() { return TYPE; }
    @Override protected String spriteName() { return "pulse_repeater"; }

    /** {@code PulseRepeaterBlockEntity#updateState}. */
    @Override
    protected void updateState(boolean server) {
        if (state <= 0 && !powered)
            return;
        if (state > redstoneInterval + 1) {
            if (!powered && !powering)
                state = 0;
            return;
        }

        state++;
        if (!server)
            return;

        if (state == redstoneInterval - 1 && !powering)
            powering = true;
        if (state == redstoneInterval + 1 && powering)
            powering = false;
    }
}
