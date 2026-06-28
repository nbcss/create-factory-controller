package io.github.nbcss.createfactorycontroller.content.display;

import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * A board component that contributes lines to a Create Display Link wired to the controller. Implemented per component
 * kind (today only the gauge); {@code FactoryControllerDisplaySource} collects from every component that implements
 * this, in board order, for the link's selected {@link DisplayMode}. Server-side — read live behaviour state directly.
 */
public interface DisplayDataProvider {
    /** This component's display lines for {@code mode}, already formatted; empty if it contributes nothing. */
    List<MutableComponent> provideDisplayLines(DisplayMode mode);
}
