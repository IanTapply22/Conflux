package com.iantapply.conflux.paper;

import com.iantapply.conflux.api.GhostAnimation;
import com.iantapply.conflux.api.GhostAppearance;
import com.iantapply.conflux.api.GhostAppearanceFrame;
import com.iantapply.conflux.api.GhostFrame;
import com.iantapply.conflux.api.GhostMovement;
import com.iantapply.conflux.api.GhostMovementFrame;
import com.iantapply.conflux.api.GhostProtocol;
import com.iantapply.conflux.api.GhostState;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Bounded, session-aware cache of remote player state. */
final class RemoteFrameStore {
    /** Number of prior process identifiers remembered to reject delayed messages. */
    private static final int MAX_RETIRED_SESSIONS_PER_NODE = 8;

    private final String localNodeId;
    private final String realmId;
    private final int maximumNodes;
    private final long staleAfterNanos;
    private final long maximumAnimationAgeMilliseconds;
    private final LongSupplier nanoTime;
    private final LongSupplier currentTimeMillis;
    private final Map<String, NodeState> nodes = new HashMap<>();
    private final Map<String, List<RemoteGhost>> worldIndex = new HashMap<>();
    private final Map<String, Map<Cell, List<RemoteGhost>>> spatialIndex = new HashMap<>();
    private long revision;

    /**
     * Creates a production store using monotonic and wall-clock system time.
     *
     * @param localNodeId local Relay node identifier
     * @param config validated ghost configuration
     */
    RemoteFrameStore(String localNodeId, GhostConfig config) {
        this(
                localNodeId,
                config.realmId(),
                config.maximumRemoteNodes(),
                config.staleAfterMilliseconds(),
                config.maximumAnimationAgeMilliseconds(),
                System::nanoTime,
                System::currentTimeMillis);
    }

    /**
     * Creates a store with injectable clocks for deterministic tests.
     *
     * @param localNodeId local Relay node identifier
     * @param realmId accepted logical realm
     * @param maximumNodes maximum simultaneously retained remote nodes
     * @param staleAfterMilliseconds node-state expiry interval
     * @param maximumAnimationAgeMilliseconds oldest accepted animation age
     * @param nanoTime monotonic clock
     * @param currentTimeMillis wall clock used for message age
     */
    RemoteFrameStore(
            String localNodeId,
            String realmId,
            int maximumNodes,
            long staleAfterMilliseconds,
            long maximumAnimationAgeMilliseconds,
            LongSupplier nanoTime,
            LongSupplier currentTimeMillis) {
        this.localNodeId = localNodeId;
        this.realmId = realmId;
        this.maximumNodes = maximumNodes;
        this.staleAfterNanos = staleAfterMilliseconds * 1_000_000L;
        this.maximumAnimationAgeMilliseconds = maximumAnimationAgeMilliseconds;
        this.nanoTime = nanoTime;
        this.currentTimeMillis = currentTimeMillis;
    }

    /**
     * Applies a newer authoritative full snapshot.
     *
     * @param frame received full frame
     * @return whether the frame was accepted
     */
    boolean accept(GhostFrame frame) {
        NodeState node = node(frame.protocolVersion(), frame.realmId(), frame.nodeId(), frame.sessionId(), true);
        if (node == null || frame.sequence() <= Math.max(node.fullSequence, node.movementSequence)) return false;
        node.fullSequence = frame.sequence();
        long receivedAtNanos = nanoTime.getAsLong();
        node.receivedAtNanos = receivedAtNanos;
        node.players.clear();
        node.appearanceSequence = frame.sequence();
        for (GhostState state : frame.players()) {
            node.players.put(
                    state.playerId(),
                    new PlayerParts(state.movement(), state.appearance(), frame.sequence(), receivedAtNanos));
        }
        rebuildIndex();
        return true;
    }

    /**
     * Applies a newer complete movement snapshot.
     *
     * @param frame received movement frame
     * @return whether the frame was accepted
     */
    boolean accept(GhostMovementFrame frame) {
        NodeState node = node(frame.protocolVersion(), frame.realmId(), frame.nodeId(), frame.sessionId(), true);
        if (node == null || frame.sequence() <= Math.max(node.movementSequence, node.fullSequence)) return false;
        node.movementSequence = frame.sequence();
        node.receivedAtNanos = nanoTime.getAsLong();
        Set<UUID> activePlayers = new HashSet<>();
        for (GhostMovement movement : frame.players()) {
            activePlayers.add(movement.playerId());
            PlayerParts previous = node.players.get(movement.playerId());
            GhostAppearance appearance = previous == null ? null : previous.appearance();
            node.players.put(
                    movement.playerId(), new PlayerParts(movement, appearance, frame.sequence(), node.receivedAtNanos));
        }
        node.players.keySet().removeIf(playerId -> !activePlayers.contains(playerId));
        rebuildIndex();
        return true;
    }

    /**
     * Merges a newer batch of changed appearances into existing movement state.
     *
     * @param frame received appearance frame
     * @return whether the frame was accepted
     */
    boolean accept(GhostAppearanceFrame frame) {
        NodeState node = node(frame.protocolVersion(), frame.realmId(), frame.nodeId(), frame.sessionId(), false);
        if (node == null || frame.sequence() <= Math.max(node.appearanceSequence, node.fullSequence)) return false;
        node.appearanceSequence = frame.sequence();
        long receivedAtNanos = nanoTime.getAsLong();
        for (GhostAppearance appearance : frame.players()) {
            PlayerParts previous = node.players.get(appearance.playerId());
            GhostMovement movement = previous == null ? null : previous.movement();
            long movementReceivedAt = previous == null ? receivedAtNanos : previous.receivedAtNanos();
            node.players.put(
                    appearance.playerId(), new PlayerParts(movement, appearance, frame.sequence(), movementReceivedAt));
        }
        rebuildIndex();
        return true;
    }

    /**
     * Validates animation age, session, ordering, and player presence.
     *
     * @param animation received animation
     * @return whether the animation may be rendered
     */
    boolean accept(GhostAnimation animation) {
        long age = currentTimeMillis.getAsLong() - animation.sentAtEpochMilli();
        if (age > maximumAnimationAgeMilliseconds || age < -5_000) return false;
        NodeState node = node(
                animation.protocolVersion(), animation.realmId(), animation.nodeId(), animation.sessionId(), false);
        if (node == null || animation.sequence() <= node.animationSequence) return false;
        node.animationSequence = animation.sequence();
        return node.players.containsKey(animation.playerId());
    }

    /**
     * Removes nodes whose authoritative state has stopped arriving.
     *
     * @return whether at least one node expired
     */
    boolean expireStale() {
        long now = nanoTime.getAsLong();
        boolean removed = nodes.entrySet().removeIf(entry -> now - entry.getValue().receivedAtNanos > staleAfterNanos);
        if (removed) rebuildIndex();
        return removed;
    }

    /**
     * Returns every complete remote player indexed in one world.
     *
     * @param world world name
     * @return immutable remote-player collection
     */
    Collection<RemoteGhost> playersInWorld(String world) {
        return worldIndex.getOrDefault(world, List.of());
    }

    /**
     * Uses the spatial-cell index to find players near a horizontal search area.
     *
     * @param world world name
     * @param x search-center x-coordinate
     * @param z search-center z-coordinate
     * @param radius horizontal search radius
     * @return spatially prefiltered remote players
     */
    Collection<RemoteGhost> playersNear(String world, double x, double z, double radius) {
        Map<Cell, List<RemoteGhost>> cells = spatialIndex.get(world);
        if (cells == null || cells.isEmpty()) return List.of();
        int minimumX = cellCoordinate(x - radius);
        int maximumX = cellCoordinate(x + radius);
        int minimumZ = cellCoordinate(z - radius);
        int maximumZ = cellCoordinate(z + radius);
        long queriedCells = (long) (maximumX - minimumX + 1) * (maximumZ - minimumZ + 1);
        if (queriedCells > cells.size() * 2L) return playersInWorld(world);
        List<RemoteGhost> nearby = new ArrayList<>();
        for (int cellX = minimumX; cellX <= maximumX; cellX++) {
            for (int cellZ = minimumZ; cellZ <= maximumZ; cellZ++) {
                nearby.addAll(cells.getOrDefault(new Cell(cellX, cellZ), List.of()));
            }
        }
        return nearby;
    }

    /**
     * Counts complete renderable remote players.
     *
     * @return remote player count
     */
    int playerCount() {
        return worldIndex.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Counts retained remote nodes, including nodes awaiting appearance data.
     *
     * @return remote node count
     */
    int nodeCount() {
        return nodes.size();
    }

    /**
     * Returns the revision incremented whenever indexed state changes.
     *
     * @return current state revision
     */
    long revision() {
        return revision;
    }

    /** Clears all remote node state and rebuilds empty indexes. */
    void clear() {
        nodes.clear();
        rebuildIndex();
    }

    /**
     * Resolves a valid node session, optionally creating or transitioning it.
     *
     * @param protocolVersion message protocol version
     * @param messageRealm message realm
     * @param nodeId source node identifier
     * @param sessionId source process identifier
     * @param allowTransition whether this message can establish a node session
     * @return matching node state, or {@code null} when rejected
     */
    private NodeState node(
            int protocolVersion, String messageRealm, String nodeId, UUID sessionId, boolean allowTransition) {
        if (protocolVersion != GhostProtocol.CURRENT_VERSION
                || !realmId.equals(messageRealm)
                || localNodeId.equals(nodeId)) {
            return null;
        }
        NodeState existing = nodes.get(nodeId);
        if (existing == null) {
            if (!allowTransition) return null;
            expireStale();
            if (nodes.size() >= maximumNodes) return null;
            NodeState created = new NodeState(sessionId, nanoTime.getAsLong());
            nodes.put(nodeId, created);
            return created;
        }
        if (existing.sessionId.equals(sessionId)) return existing;
        if (!allowTransition || existing.retiredSessions.contains(sessionId)) return null;
        existing.retireCurrentSession();
        existing.sessionId = sessionId;
        existing.reset(nanoTime.getAsLong());
        rebuildIndex();
        return existing;
    }

    /** Rebuilds immutable world and spatial indexes from complete player parts. */
    private void rebuildIndex() {
        Map<String, List<RemoteGhost>> rebuilt = new HashMap<>();
        nodes.forEach((nodeId, node) -> node.players.forEach((playerId, parts) -> {
            if (parts.movement() == null || parts.appearance() == null) return;
            GhostState state = GhostState.combine(parts.movement(), parts.appearance());
            rebuilt.computeIfAbsent(state.world(), ignored -> new ArrayList<>())
                    .add(new RemoteGhost(
                            new GhostKey(nodeId, playerId), parts.sequence(), parts.receivedAtNanos(), state));
        }));
        worldIndex.clear();
        spatialIndex.clear();
        rebuilt.forEach((world, players) -> worldIndex.put(world, List.copyOf(players)));
        worldIndex.forEach((world, players) -> {
            Map<Cell, List<RemoteGhost>> cells = new HashMap<>();
            for (RemoteGhost player : players) {
                Cell cell = new Cell(
                        cellCoordinate(player.state().x()),
                        cellCoordinate(player.state().z()));
                cells.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(player);
            }
            Map<Cell, List<RemoteGhost>> immutableCells = new HashMap<>();
            cells.forEach((cell, occupants) -> immutableCells.put(cell, List.copyOf(occupants)));
            spatialIndex.put(world, Map.copyOf(immutableCells));
        });
        revision++;
    }

    /**
     * Maps a block coordinate to its 32-block spatial cell.
     *
     * @param coordinate block coordinate
     * @return spatial-cell coordinate
     */
    private static int cellCoordinate(double coordinate) {
        return (int) Math.floor(coordinate / 32.0);
    }

    /**
     * Complete renderable player state qualified by source node.
     *
     * @param key node-qualified player key
     * @param sequence latest movement or appearance sequence
     * @param receivedAtNanos monotonic movement receipt time
     * @param state combined player state
     */
    record RemoteGhost(GhostKey key, long sequence, long receivedAtNanos, GhostState state) {}

    /**
     * Spatial-cell coordinates used as an index key.
     *
     * @param x cell x-coordinate
     * @param z cell z-coordinate
     */
    private record Cell(int x, int z) {}

    /**
     * Independently received movement and appearance portions for one player.
     *
     * @param movement latest movement portion, if known
     * @param appearance latest appearance portion, if known
     * @param sequence latest relevant message sequence
     * @param receivedAtNanos monotonic movement receipt time
     */
    private record PlayerParts(
            GhostMovement movement, GhostAppearance appearance, long sequence, long receivedAtNanos) {}

    /** Mutable ordering and player state retained for one remote Relay node. */
    private static final class NodeState {
        private UUID sessionId;
        private final LinkedHashSet<UUID> retiredSessions = new LinkedHashSet<>();
        private final Map<UUID, PlayerParts> players = new HashMap<>();
        private long fullSequence = -1;
        private long movementSequence = -1;
        private long appearanceSequence = -1;
        private long animationSequence = -1;
        private long receivedAtNanos;

        /**
         * Creates state for a newly observed process session.
         *
         * @param sessionId process session identifier
         * @param receivedAtNanos initial monotonic receipt time
         */
        private NodeState(UUID sessionId, long receivedAtNanos) {
            this.sessionId = sessionId;
            this.receivedAtNanos = receivedAtNanos;
        }

        /** Remembers the current process identifier before transitioning sessions. */
        private void retireCurrentSession() {
            retiredSessions.add(sessionId);
            while (retiredSessions.size() > MAX_RETIRED_SESSIONS_PER_NODE) {
                retiredSessions.remove(retiredSessions.iterator().next());
            }
        }

        /**
         * Clears sequence and player state for a newly accepted process session.
         *
         * @param now monotonic transition time
         */
        private void reset(long now) {
            players.clear();
            fullSequence = -1;
            movementSequence = -1;
            appearanceSequence = -1;
            animationSequence = -1;
            receivedAtNanos = now;
        }
    }
}
