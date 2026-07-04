package io.github.nbcss.createfactorycontroller.content.gui.widget;

import io.github.nbcss.createfactorycontroller.content.component.LogicalTubeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualGaugeBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualRedstoneLinkBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Client-side map from a component behaviour to its canvas {@link VirtualComponentWidget}, replacing the {@code
 * instanceof} chains that used to live in the screen. Lookup walks up the behaviour's superclasses, so a subclass
 * (e.g. {@code FluidGaugeBehaviour} extends {@link VirtualGaugeBehaviour}) reuses its parent's widget with no extra
 * registration. A new component kind just {@link #register}s its widget here.
 */
@OnlyIn(Dist.CLIENT)
public final class ComponentWidgetRegistry {

    private static final Map<Class<?>, Function<VirtualComponentBehaviour, VirtualComponentWidget>> FACTORIES =
            new LinkedHashMap<>();

    static {
        register(VirtualGaugeBehaviour.class, VirtualGaugeWidget::new);
        register(VirtualRedstoneLinkBehaviour.class, VirtualRedstoneLinkWidget::new);
        register(LogicalTubeBehaviour.class, VirtualLogicalTubeWidget::new);
    }

    private ComponentWidgetRegistry() {}

    @SuppressWarnings("unchecked")
    public static <T extends VirtualComponentBehaviour> void register(Class<T> type, Function<T, VirtualComponentWidget> factory) {
        FACTORIES.put(type, (Function<VirtualComponentBehaviour, VirtualComponentWidget>) factory);
    }

    /** The widget for {@code behaviour}, matching the nearest registered superclass; {@code null} if none is registered. */
    @Nullable
    public static VirtualComponentWidget create(VirtualComponentBehaviour behaviour) {
        for (Class<?> c = behaviour.getClass(); c != null; c = c.getSuperclass()) {
            Function<VirtualComponentBehaviour, VirtualComponentWidget> factory = FACTORIES.get(c);
            if (factory != null) return factory.apply(behaviour);
        }
        return null;
    }
}
