package io.github.nbcss.createfactorycontroller.content.display;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Exposes a Factory Controller's live data to a Create Display Link. Each board component that implements
 * {@link DisplayDataProvider} contributes lines for the link's selected {@link DisplayMode}, gathered in board order
 * (stable — {@code components} is insertion-ordered) and capped to the target's row count.
 *
 * <p>Currently the only mode is {@link DisplayMode#ACTIVE_REQUESTS} (active, understocked gauges). The mode is read from
 * the per-link config ({@code "Mode"} = ordinal), so adding modes later needs no change here.</p>
 */
public class FactoryControllerDisplaySource extends DisplaySource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof FactoryControllerBlockEntity be)) return EMPTY;
        DisplayMode mode = DisplayMode.byIndex(context.sourceConfig().getInt("Mode"));

        List<MutableComponent> lines = new ArrayList<>();
        for (VirtualComponentBehaviour component : be.components.values()) {
            if (component instanceof DisplayDataProvider provider)
                lines.addAll(provider.provideDisplayLines(mode));
            if (lines.size() >= stats.maxRows()) break;
        }
        if (lines.isEmpty()) return EMPTY;
        return lines.size() > stats.maxRows() ? lines.subList(0, stats.maxRows()) : lines;
    }
}
