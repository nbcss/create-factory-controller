package io.github.nbcss.createfactorycontroller.content.gui.widget.indicator;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** One fixed-size tab in the controller's indicator column. */
public interface ControllerIndicator {
    int WIDTH = 21;
    int HEIGHT = 13;

    ResourceLocation icon();

    List<FormattedCharSequence> tooltip();
}
