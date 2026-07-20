package io.github.nbcss.createfactorycontroller.content.gui.widget.indicator;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** One fixed-size tab in the controller's indicator column. */
public interface ControllerIndicator {
    int WIDTH = 21;
    int HEIGHT = 13;

    ResourceLocation icon();

    List<Component> tooltip();
}
