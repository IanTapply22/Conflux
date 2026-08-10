package com.iantapply.conflux.paper;

import com.iantapply.conflux.api.GhostAnimation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns packet ghosts and reconciles them against pure selector results. */
final class ViewerGhostRenderer {
    /** Minimum viewer movement that triggers selection when remote state is unchanged. */
    private static final double VIEWER_RESELECT_DISTANCE_SQUARED = 1.0;

    private final JavaPlugin plugin;
    private final GhostConfig config;
    private final ConfluxMetrics metrics;
    private final GhostSelector selector = new GhostSelector();
    private final Map<UUID, Map<GhostKey, PacketGhost>> rendered = new HashMap<>();
    private final Map<UUID, SelectionState> selectionStates = new HashMap<>();

    /**
     * Creates a renderer for all local viewers.
     *
     * @param plugin owning Paper plugin
     * @param config validated rendering configuration
     * @param metrics operational counters
     */
    ViewerGhostRenderer(JavaPlugin plugin, GhostConfig config, ConfluxMetrics metrics) {
        this.plugin = plugin;
        this.config = config;
        this.metrics = metrics;
    }

    /**
     * Reconciles one viewer's packet ghosts against current nearby remote players.
     *
     * @param viewer local packet recipient
     * @param remotePlayers spatially prefiltered remote players
     * @param storeRevision remote-state revision
     * @param limit viewer-specific ghost limit
     */
    void reconcile(
            Player viewer, Collection<RemoteFrameStore.RemoteGhost> remotePlayers, long storeRevision, int limit) {
        GhostSelector.ViewerPosition position = new GhostSelector.ViewerPosition(
                viewer.getUniqueId(), viewer.getWorld().getName(), viewer.getX(), viewer.getY(), viewer.getZ());
        SelectionState previous = selectionStates.get(viewer.getUniqueId());
        if (previous != null && previous.canReuse(position, storeRevision, limit)) return;

        long started = System.nanoTime();
        List<GhostSelector.Selection> selected =
                selector.select(position, remotePlayers, limit, config.viewRadiusBlocks());
        metrics.selection(System.nanoTime() - started);
        selectionStates.put(viewer.getUniqueId(), new SelectionState(position, storeRevision, limit));

        Map<GhostKey, GhostSelector.Selection> desired = new LinkedHashMap<>();
        selected.forEach(candidate -> desired.put(candidate.remote().key(), candidate));
        Map<GhostKey, PacketGhost> ghosts = rendered.computeIfAbsent(viewer.getUniqueId(), ignored -> new HashMap<>());
        List<GhostKey> removed = new ArrayList<>();
        ghosts.forEach((key, ghost) -> {
            if (!desired.containsKey(key)) {
                ghost.destroy();
                removed.add(key);
            }
        });
        removed.forEach(ghosts::remove);

        desired.forEach((key, candidate) -> {
            PacketGhost ghost = ghosts.get(key);
            if (ghost != null && !ghost.sameIdentity(candidate.remote().state())) {
                ghost.destroy();
                ghosts.remove(key);
                ghost = null;
            }
            if (ghost == null) {
                ghost = new PacketGhost(plugin, viewer, candidate.remote().state(), config);
                ghosts.put(key, ghost);
            }
            ghost.target(candidate.remote().state(), candidate.remote().sequence());
        });
    }

    /** Advances interpolation for every ghost and removes disconnected viewers. */
    void tick() {
        rendered.values().forEach(ghosts -> ghosts.values().forEach(PacketGhost::tick));
        rendered.keySet().stream()
                .filter(viewerId -> Bukkit.getPlayer(viewerId) == null)
                .toList()
                .forEach(this::destroyViewer);
    }

    /**
     * Sends an accepted remote animation to viewers rendering its source player.
     *
     * @param animation accepted animation message
     */
    void animate(GhostAnimation animation) {
        GhostKey key = new GhostKey(animation.nodeId(), animation.playerId());
        rendered.values().forEach(ghosts -> {
            PacketGhost ghost = ghosts.get(key);
            if (ghost != null) ghost.animate(animation.type());
        });
    }

    /**
     * Destroys all packet ghosts and cached selection state for one viewer.
     *
     * @param viewerId viewer identifier
     */
    void destroyViewer(UUID viewerId) {
        selectionStates.remove(viewerId);
        Map<GhostKey, PacketGhost> ghosts = rendered.remove(viewerId);
        if (ghosts != null) ghosts.values().forEach(PacketGhost::destroy);
    }

    /** Destroys all packet ghosts owned by this renderer. */
    void destroyAll() {
        rendered.keySet().stream().toList().forEach(this::destroyViewer);
    }

    /**
     * Counts packet ghosts currently rendered across all viewers.
     *
     * @return rendered ghost count
     */
    int renderedGhosts() {
        return rendered.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * Cached inputs used to skip redundant viewer selection passes.
     *
     * @param position viewer position at the previous selection
     * @param storeRevision state-store revision at the previous selection
     * @param limit viewer limit at the previous selection
     */
    private record SelectionState(GhostSelector.ViewerPosition position, long storeRevision, int limit) {
        /**
         * Determines whether the previous selection remains reusable.
         *
         * @param current current viewer position
         * @param revision current remote-state revision
         * @param currentLimit current viewer limit
         * @return whether selection can be skipped
         */
        private boolean canReuse(GhostSelector.ViewerPosition current, long revision, int currentLimit) {
            if (revision != storeRevision
                    || currentLimit != limit
                    || !position.world().equals(current.world())) {
                return false;
            }
            double dx = current.x() - position.x();
            double dy = current.y() - position.y();
            double dz = current.z() - position.z();
            return dx * dx + dy * dy + dz * dz < VIEWER_RESELECT_DISTANCE_SQUARED;
        }
    }
}
