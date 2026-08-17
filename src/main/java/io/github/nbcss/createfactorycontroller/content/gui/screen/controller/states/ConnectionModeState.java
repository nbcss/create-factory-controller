package io.github.nbcss.createfactorycontroller.content.gui.screen.controller.states;

import io.github.nbcss.createfactorycontroller.content.block.ComponentHolder;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionResolver;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** State and resolution for an in-progress connection gesture. */
public final class ConnectionModeState {
    @Nullable private VirtualComponentPosition initiator;
    @Nullable private VirtualComponentPosition previewTarget;
    @Nullable private Connection.Type desiredType;
    private int previewBendMode = -1;

    public boolean isActive() {
        return initiator != null;
    }

    @Nullable
    public VirtualComponentPosition initiator() {
        return initiator;
    }

    public int previewBendMode() {
        return previewBendMode;
    }

    public void begin(VirtualComponentPosition initiator) {
        this.initiator = initiator;
        previewTarget = null;
        desiredType = null;
        previewBendMode = -1;
    }

    public void clear() {
        initiator = null;
        previewTarget = null;
        desiredType = null;
        previewBendMode = -1;
    }

    @Nullable
    public ConnectionResolver.Result resolve(ComponentHolder components,
                                             @Nullable VirtualComponentPosition hoveredPosition) {
        if (!isActive()) return null;
        updateHovered(hoveredPosition);
        if (hoveredPosition == null || hoveredPosition.equals(initiator)) return null;

        VirtualComponentBehaviour source = components.componentAt(initiator);
        VirtualComponentBehaviour hovered = components.componentAt(hoveredPosition);
        if (source == null || hovered == null) return null;
        return desiredType != null
                ? ConnectionResolver.resolveAs(hovered, source, source, desiredType)
                : ConnectionResolver.resolve(hovered, source, source);
    }

    public boolean cycleType(ComponentHolder components,
                             @Nullable VirtualComponentPosition hoveredPosition,
                             int direction) {
        if (!isActive()) return false;
        updateHovered(hoveredPosition);
        if (hoveredPosition == null || hoveredPosition.equals(initiator)) return false;

        VirtualComponentBehaviour source = components.componentAt(initiator);
        VirtualComponentBehaviour hovered = components.componentAt(hoveredPosition);
        if (source == null || hovered == null) return false;

        List<Connection.Type> possible = ConnectionResolver.possibleTypes(hovered, source, source);
        Connection.Type defaultType = ConnectionResolver.resolve(hovered, source, source).type();
        boolean hasChoice = possible.size() >= 2 || (possible.size() == 1 && !possible.contains(defaultType));
        if (!hasChoice) return false;

        Connection.Type current = desiredType != null ? desiredType : defaultType;
        int index = possible.indexOf(current);
        index = index < 0
                ? (direction >= 0 ? 0 : possible.size() - 1)
                : Math.floorMod(index + direction, possible.size());
        desiredType = possible.get(index);
        return true;
    }

    public boolean cycleBend(ComponentHolder components,
                             @Nullable VirtualComponentPosition hoveredPosition) {
        ConnectionResolver.Result result = resolve(components, hoveredPosition);
        if (result == null || !result.ok()) return false;
        previewBendMode = (previewBendMode + 1) % 4;
        return true;
    }

    public Completion finish(ComponentHolder components,
                             VirtualComponentPosition clickedPosition,
                             @Nullable VirtualComponentBehaviour clicked) {
        VirtualComponentPosition sourcePosition = initiator;
        if (sourcePosition == null) return new Completion(CompletionStatus.INVALID, null, -1);
        if (clicked == null || clickedPosition.equals(sourcePosition)) {
            clear();
            return new Completion(CompletionStatus.ABORTED, null, -1);
        }

        VirtualComponentBehaviour source = components.componentAt(sourcePosition);
        if (source == null) {
            clear();
            return new Completion(CompletionStatus.INVALID, null, -1);
        }

        boolean usePreview = clickedPosition.equals(previewTarget);
        ConnectionResolver.Result result = usePreview && desiredType != null
                ? ConnectionResolver.resolveAs(clicked, source, source, desiredType)
                : ConnectionResolver.resolve(clicked, source, source);
        int bendMode = usePreview ? previewBendMode : -1;
        clear();
        return new Completion(CompletionStatus.RESOLVED, result, bendMode);
    }

    private void updateHovered(@Nullable VirtualComponentPosition hoveredPosition) {
        if (Objects.equals(hoveredPosition, previewTarget)) return;
        previewTarget = hoveredPosition;
        desiredType = null;
        previewBendMode = -1;
    }

    public enum CompletionStatus {
        ABORTED,
        INVALID,
        RESOLVED
    }

    public record Completion(CompletionStatus status,
                             @Nullable ConnectionResolver.Result result,
                             int bendMode) {}
}
