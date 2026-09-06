package io.github.nbcss.createfactorycontroller.content.helper;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class TooltipBuilder {
    private final Font font;
    private final List<FormattedCharSequence> tooltip;

    private TooltipBuilder(Font font) {
        this.font = font;
        tooltip = new ArrayList<>();
    }

    public static TooltipBuilder of(Font font) {
        return new TooltipBuilder(font);
    }

    public TooltipBuilder wrapped(@Nullable Component component, int maxWidth) {
        if (component == null) return this;
        List<FormattedCharSequence> lines = font.split(component, maxWidth);
        if (lines.isEmpty()) tooltip.add(FormattedCharSequence.EMPTY);
        else tooltip.addAll(lines);
        return this;
    }

    public TooltipBuilder selector(Component component, boolean selected) {
        tooltip.add(Component.literal(selected ? "-> " : "> ").append(component)
                .withStyle(selected ? ChatFormatting.WHITE : ChatFormatting.GRAY).getVisualOrderText());
        return this;
    }

    public TooltipBuilder line(@Nullable Component component) {
        if (component != null) tooltip.add(component.getVisualOrderText());
        return this;
    }

    public TooltipBuilder lines(Iterable<? extends Component> components) {
        for (Component component : components) line(component);
        return this;
    }

    public TooltipBuilder empty() {
        tooltip.add(FormattedCharSequence.EMPTY);
        return this;
    }

    public List<FormattedCharSequence> build() {
        return List.copyOf(tooltip);
    }
}
