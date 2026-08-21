package io.github.nbcss.createfactorycontroller.content.gui.screen.controller.states;

import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TargetAnimationState {
    public static final long FADE_DURATION_MS = 120;
    private static final float END_SCALE = 0.8f;

    private final Map<TargetKey, TrackedTarget> targets = new LinkedHashMap<>();
    private long frameTimeMs;

    public void beginFrame(long frameTimeMs) {
        this.frameTimeMs = frameTimeMs;
        targets.values().forEach(target -> target.visible = false);
    }

    public void track(VirtualComponentPosition position, ResourceLocation sprite, int color, boolean raised) {
        TrackedTarget target = targets.computeIfAbsent(new TargetKey(position, sprite), ignored -> new TrackedTarget());
        target.color = color;
        target.raised = raised;
        target.visible = true;
        target.fadeStartedMs = -1;
    }

    public void discard(VirtualComponentPosition position, ResourceLocation sprite) {
        targets.remove(new TargetKey(position, sprite));
    }

    public List<AnimatedTarget> fadingTargets(boolean animate) {
        List<AnimatedTarget> fading = new ArrayList<>();
        Iterator<Map.Entry<TargetKey, TrackedTarget>> iterator = targets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TargetKey, TrackedTarget> entry = iterator.next();
            TrackedTarget target = entry.getValue();
            if (target.visible) continue;
            if (!animate) {
                iterator.remove();
                continue;
            }
            if (target.fadeStartedMs < 0) target.fadeStartedMs = frameTimeMs;

            float progress = (frameTimeMs - target.fadeStartedMs) / (float) FADE_DURATION_MS;
            if (progress >= 1) {
                iterator.remove();
                continue;
            }
            progress = Math.max(0, progress);
            fading.add(new AnimatedTarget(entry.getKey().position(), entry.getKey().sprite(), target.color,
                    target.raised, 1 - progress, 1 - (1 - END_SCALE) * progress));
        }
        return fading;
    }

    private record TargetKey(VirtualComponentPosition position, ResourceLocation sprite) {}

    public record AnimatedTarget(VirtualComponentPosition position, ResourceLocation sprite, int color,
                                 boolean raised, float alpha, float scale) {}

    private static final class TrackedTarget {
        private int color;
        private boolean raised;
        private boolean visible;
        private long fadeStartedMs = -1;
    }
}
