package io.github.nbcss.createfactorycontroller.content.gui.screen.controller.states;

import io.github.nbcss.createfactorycontroller.content.block.FactoryControllerBlockEntity;
import io.github.nbcss.createfactorycontroller.content.component.VirtualComponentPosition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** State, validation and server-sync reconciliation for moving a component selection as one batch. */
public final class BatchMoveState {
    private static final int PENDING_SYNC_LIMIT = 20;

    private boolean active;
    @Nullable private VirtualComponentPosition anchor;
    private int dx;
    private int dy;

    @Nullable private Set<VirtualComponentPosition> pendingSources;
    @Nullable private Set<VirtualComponentPosition> pendingDestinations;
    private int pendingSyncs;

    public void begin(VirtualComponentPosition anchor) {
        active = true;
        this.anchor = anchor;
        dx = 0;
        dy = 0;
    }

    public boolean isActive() {
        return active;
    }

    public boolean hasMoved() {
        return dx != 0 || dy != 0;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    /** Recomputes the delta from the current cursor cell, including after keyboard camera movement. */
    public void update(VirtualComponentPosition cursorCell) {
        if (!active || anchor == null) return;
        dx = cursorCell.x() - anchor.x();
        dy = cursorCell.y() - anchor.y();
    }

    public void cancel() {
        active = false;
        anchor = null;
        dx = 0;
        dy = 0;
    }

    public Set<VirtualComponentPosition> movingSources(Set<VirtualComponentPosition> selection,
                                                       Collection<VirtualComponentPosition> occupied) {
        Set<VirtualComponentPosition> result = new LinkedHashSet<>();
        for (VirtualComponentPosition position : selection)
            if (occupied.contains(position)) result.add(position);
        return result;
    }

    public VirtualComponentPosition destinationOf(VirtualComponentPosition source) {
        return new VirtualComponentPosition(source.x() + dx, source.y() + dy);
    }

    /** Mirrors the server's batch-move bounds and collision validation for the drag preview. */
    public boolean destinationValid(VirtualComponentPosition destination,
                                    Set<VirtualComponentPosition> selection,
                                    Collection<VirtualComponentPosition> occupied) {
        boolean occupiedByOther = occupied.contains(destination) && !selection.contains(destination);
        return !FactoryControllerBlockEntity.isOutBoard(destination) && !occupiedByOther;
    }

    /** Ends the active drag and, when valid, starts tracking the move across panel syncs. */
    public FinishResult finish(Set<VirtualComponentPosition> selection,
                               Collection<VirtualComponentPosition> occupied) {
        if (!active) return FinishResult.none();

        List<VirtualComponentPosition> sources = new ArrayList<>(movingSources(selection, occupied));
        int completedDx = dx;
        int completedDy = dy;
        cancel();

        if ((completedDx == 0 && completedDy == 0) || sources.isEmpty()) return FinishResult.none();

        Set<VirtualComponentPosition> destinations = new LinkedHashSet<>();
        for (VirtualComponentPosition source : sources) {
            VirtualComponentPosition destination = new VirtualComponentPosition(
                    source.x() + completedDx, source.y() + completedDy);
            if (!destinationValid(destination, selection, occupied)) return FinishResult.invalid();
            destinations.add(destination);
        }

        pendingSources = new LinkedHashSet<>(sources);
        pendingDestinations = destinations;
        pendingSyncs = PENDING_SYNC_LIMIT;
        return FinishResult.ready(new MoveRequest(
                List.copyOf(sources), Set.copyOf(destinations), completedDx, completedDy));
    }

    /**
     * Reconciles an outstanding request with newly synced board cells. A returned set replaces the current selection;
     * an empty result means the sync does not require a selection change.
     */
    public Optional<Set<VirtualComponentPosition>> reconcile(Collection<VirtualComponentPosition> occupied) {
        if (pendingDestinations == null) return Optional.empty();
        if (occupied.containsAll(pendingDestinations)) {
            Set<VirtualComponentPosition> destinations = new LinkedHashSet<>(pendingDestinations);
            clearPending();
            return Optional.of(destinations);
        }
        if (--pendingSyncs <= 0) {
            clearPending();
            return Optional.empty();
        }
        assert pendingSources != null;
        return Optional.of(new LinkedHashSet<>(pendingSources));
    }

    private void clearPending() {
        pendingSources = null;
        pendingDestinations = null;
        pendingSyncs = 0;
    }

    public enum FinishStatus {
        NONE,
        INVALID,
        READY
    }

    public record MoveRequest(List<VirtualComponentPosition> sources,
                              Set<VirtualComponentPosition> destinations,
                              int dx, int dy) {}

    public record FinishResult(FinishStatus status, @Nullable MoveRequest request) {
        private static FinishResult none() {
            return new FinishResult(FinishStatus.NONE, null);
        }

        private static FinishResult invalid() {
            return new FinishResult(FinishStatus.INVALID, null);
        }

        private static FinishResult ready(MoveRequest request) {
            return new FinishResult(FinishStatus.READY, request);
        }
    }
}
