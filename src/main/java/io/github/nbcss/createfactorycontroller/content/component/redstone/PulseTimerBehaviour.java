package io.github.nbcss.createfactorycontroller.content.component.redstone;

import com.simibubi.create.AllBlocks;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.world.item.Item;

/** Create's Pulse Timer: repeats short pulses every redstone interval; a powered input pauses and resets it. */
public class PulseTimerBehaviour extends BrassDiodeBehaviour {

    public static final Type TYPE = type("PULSE_TIMER", "lp2", AllBlocks.PULSE_TIMER.getId(), 0xA58BE0,
            PulseTimerBehaviour::new);

    public PulseTimerBehaviour(FactoryControllerBlockEntity controller, VirtualComponentPosition position, Item item) {
        super(controller, position, item);
    }

    @Override protected Type type() { return TYPE; }
    @Override protected String spriteName() { return "pulse_timer"; }
    @Override protected int defaultInterval() { return 20; }

    /** {@code PulseTimerBlockEntity#updateState}. */
    @Override
    protected void updateState(boolean server) {
        if (powered || state >= redstoneInterval - 1)
            state = 0;
        else
            state++;

        if (!server)
            return;

        powering = !powered && (redstoneInterval == 2 ? state == 0 : state <= 1);
    }
}
