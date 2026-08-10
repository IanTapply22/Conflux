package com.iantapply.conflux.paper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

/** Pure nearest-ghost selection logic, independent of Bukkit and packet rendering. */
final class GhostSelector {
    /**
     * Selects the nearest unique remote players within a viewer's radius and limit.
     *
     * @param viewer immutable viewer position
     * @param remotePlayers spatially prefiltered remote players
     * @param limit maximum result count
     * @param radiusBlocks maximum three-dimensional distance
     * @return selections ordered nearest first
     */
    List<Selection> select(
            ViewerPosition viewer,
            Collection<RemoteFrameStore.RemoteGhost> remotePlayers,
            int limit,
            double radiusBlocks) {
        if (limit <= 0) return List.of();
        double radiusSquared = radiusBlocks * radiusBlocks;
        Map<UUID, Selection> newestByPlayer = new HashMap<>();
        for (RemoteFrameStore.RemoteGhost remote : remotePlayers) {
            if (remote.state().playerId().equals(viewer.playerId())) continue;
            double distanceSquared = distanceSquared(viewer, remote.state());
            if (distanceSquared > radiusSquared) continue;
            Selection candidate = new Selection(remote, distanceSquared);
            newestByPlayer.merge(
                    remote.state().playerId(),
                    candidate,
                    (first, second) ->
                            first.remote().receivedAtNanos() >= second.remote().receivedAtNanos() ? first : second);
        }

        PriorityQueue<Selection> nearest = new PriorityQueue<>(
                Comparator.comparingDouble(Selection::distanceSquared).reversed());
        for (Selection candidate : newestByPlayer.values()) {
            if (nearest.size() < limit) {
                nearest.add(candidate);
            } else if (candidate.distanceSquared() < nearest.element().distanceSquared()) {
                nearest.remove();
                nearest.add(candidate);
            }
        }
        List<Selection> selected = new ArrayList<>(nearest);
        selected.sort(Comparator.comparingDouble(Selection::distanceSquared));
        return List.copyOf(selected);
    }

    /**
     * Calculates squared three-dimensional distance without a square root.
     *
     * @param viewer viewer position
     * @param state remote player state
     * @return squared distance
     */
    private static double distanceSquared(ViewerPosition viewer, com.iantapply.conflux.api.GhostState state) {
        double dx = viewer.x() - state.x();
        double dy = viewer.y() - state.y();
        double dz = viewer.z() - state.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Immutable viewer coordinates used by the pure selector.
     *
     * @param playerId viewer identifier
     * @param world viewer world name
     * @param x viewer x-coordinate
     * @param y viewer y-coordinate
     * @param z viewer z-coordinate
     */
    record ViewerPosition(UUID playerId, String world, double x, double y, double z) {}

    /**
     * Remote candidate paired with its viewer-relative distance.
     *
     * @param remote remote player value
     * @param distanceSquared squared distance from the viewer
     */
    record Selection(RemoteFrameStore.RemoteGhost remote, double distanceSquared) {}
}
