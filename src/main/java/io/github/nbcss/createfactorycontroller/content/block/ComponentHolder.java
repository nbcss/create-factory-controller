package io.github.nbcss.createfactorycontroller.content.block;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;

/**
 * Could be BlockEntity (Server Side) or Menu (Client Side)
 */
public interface ComponentHolder {
    VirtualComponentBehaviour componentAt(VirtualComponentPosition position);
}
