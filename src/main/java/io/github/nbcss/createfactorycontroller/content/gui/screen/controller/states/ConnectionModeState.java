package io.github.nbcss.createfactorycontroller.content.gui.screen.controller.states;

import io.github.nbcss.createfactorycontroller.content.block.ComponentHolder;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentBehaviour;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import io.github.nbcss.createfactorycontroller.content.component.connection.Connection;
import io.github.nbcss.createfactorycontroller.content.component.connection.ConnectionResolver;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

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
        if (hoveredPosition == null) return null;

        VirtualComponentBehaviour source = components.componentAt(initiator);
        if (source == null) return null;

        if (hoveredPosition.equals(initiator)) //loop
            return desiredType != null
                    ? ConnectionResolver.resolveLoopAs(source, desiredType)
                    : ConnectionResolver.resolveLoop(source);

        VirtualComponentBehaviour hovered = components.componentAt(hoveredPosition);
        if (hovered == null) return null;
        return desiredType != null
                ? ConnectionResolver.resolveAs(hovered, source, source, desiredType)
                : ConnectionResolver.resolve(hovered, source, source);
    }

    /** Resolves the current self-hover as a loop, or returns null when the hover cannot represent a loop. */
    @Nullable
    public ConnectionResolver.Result resolveLoopPreview(ComponentHolder components,
                                                        @Nullable VirtualComponentPosition hoveredPosition) {
        if (!isInitiatorHovered(hoveredPosition)) return null;
        ConnectionResolver.Result result = resolve(components, hoveredPosition);
        return result != null && result.type() != null ? result : null;
    }

    public boolean isInitiatorHovered(@Nullable VirtualComponentPosition hoveredPosition) {
        return isActive() && hoveredPosition != null && hoveredPosition.equals(initiator);
    }

    public boolean cycleType(ComponentHolder components,
                             @Nullable VirtualComponentPosition hoveredPosition,
                             int direction) {
        if (!isActive()) return false;
        updateHovered(hoveredPosition);
        if (hoveredPosition == null) return false;

        VirtualComponentBehaviour source = components.componentAt(initiator);
        if (source == null) return false;

        boolean loop = hoveredPosition.equals(initiator);
        VirtualComponentBehaviour target = loop ? source : components.componentAt(hoveredPosition);
        if (target == null) return false;
        TypeChoice choice = typeChoice(source, target, loop);
        List<Connection.Type> possible = choice.possible();
        Connection.Type defaultType = choice.defaultType();
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

    /** Status lines describing what clicking the hovered cell would do. */
    public List<Component> hoverPrompt(ComponentHolder components, @Nullable VirtualComponentPosition hoveredPosition) {
        if (!isActive()) return List.of();
        updateHovered(hoveredPosition);
        if (hoveredPosition == null) return List.of();
        VirtualComponentBehaviour source = components.componentAt(initiator);
        if (source == null) return List.of();

        boolean loop = hoveredPosition.equals(initiator);
        VirtualComponentBehaviour target = loop ? source : components.componentAt(hoveredPosition);
        if (target == null) return List.of();

        TypeChoice choice = typeChoice(source, target, loop);
        if (choice.possible().isEmpty()) {
            if (loop) return List.of();
            return List.of(Component.translatable("createfactorycontroller.connection.unable_to_connect",
                            target.getName().copy().withColor(target.getColor()))
                    .withStyle(ChatFormatting.RED));
        }

        Connection.Type current = desiredType != null ? desiredType : choice.defaultType();
        assert current != null;
        ConnectionResolver.Result result = loop
                ? ConnectionResolver.resolveLoopAs(source, current)
                : ConnectionResolver.resolveAs(target, source, source, current);
        Component status = Component.translatable(result.ok()
                        ? "createfactorycontroller.connection.click_to_connect"
                        : "createfactorycontroller.connection.unable_to_have_more",
                        current.displayName())
                .withStyle(result.ok() ? ChatFormatting.WHITE : ChatFormatting.RED);
        if (choice.possible().size() < 2) return List.of(status);

        MutableComponent squares = Component.empty();
        for (Connection.Type type : choice.possible())
            squares.append(Component.literal(type.equals(current) ? "■" : "□").withColor(type.color()));
        return List.of(status, Component.translatable("createfactorycontroller.connection.cycle_type_hint",
                squares.withStyle(ChatFormatting.GRAY)));
    }

    private static TypeChoice typeChoice(VirtualComponentBehaviour source, VirtualComponentBehaviour target,
                                         boolean loop) {
        return loop
                ? new TypeChoice(ConnectionResolver.loopTypes(source), ConnectionResolver.resolveLoop(source).type())
                : new TypeChoice(ConnectionResolver.possibleTypes(target, source, source),
                                 ConnectionResolver.resolve(target, source, source).type());
    }

    /** Cycles the current preview path, using four bends for a regular wire or four quadrants for a loop. */
    public boolean cyclePath(ComponentHolder components, @Nullable VirtualComponentPosition hoveredPosition,
                             IntSupplier autoLoopOrientation) {
        ConnectionResolver.Result result = resolve(components, hoveredPosition);
        if (result == null) return false;
        if (isInitiatorHovered(hoveredPosition) && result.type() != null) {
            previewBendMode = ((previewBendMode < 0 ? autoLoopOrientation.getAsInt() : previewBendMode) + 2) % 8;
            return true;
        }
        if (!result.ok()) return false;
        previewBendMode = (previewBendMode + 1) % 4;
        return true;
    }

    /** Flips a loop preview's arrow direction while keeping its quadrant. */
    public boolean flipLoopDirection(ComponentHolder components, @Nullable VirtualComponentPosition hoveredPosition,
                                     IntSupplier autoLoopOrientation) {
        if (resolveLoopPreview(components, hoveredPosition) == null) return false;
        int orientation = previewBendMode < 0 ? autoLoopOrientation.getAsInt() : previewBendMode;
        previewBendMode = (orientation & 7) ^ 1;
        return true;
    }

    public Completion finish(ComponentHolder components,
                             VirtualComponentPosition clickedPosition,
                             @Nullable VirtualComponentBehaviour clicked) {
        VirtualComponentPosition sourcePosition = initiator;
        if (sourcePosition == null) return new Completion(CompletionStatus.INVALID, null, -1);

        VirtualComponentBehaviour source = components.componentAt(sourcePosition);
        if (source == null) {
            clear();
            return new Completion(CompletionStatus.INVALID, null, -1);
        }

        // Clicking the initiator itself makes a loop.
        if (clickedPosition.equals(sourcePosition)) {
            if (ConnectionResolver.loopTypes(source).isEmpty()) {
                clear();
                return new Completion(CompletionStatus.ABORTED, null, -1);
            }
            ConnectionResolver.Result result = desiredType != null
                    ? ConnectionResolver.resolveLoopAs(source, desiredType)
                    : ConnectionResolver.resolveLoop(source);
            int bendMode = previewBendMode;
            clear();
            return new Completion(CompletionStatus.RESOLVED, result, bendMode);
        }

        if (clicked == null) {
            clear();
            return new Completion(CompletionStatus.ABORTED, null, -1);
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

    private record TypeChoice(List<Connection.Type> possible, @Nullable Connection.Type defaultType) {}
}
