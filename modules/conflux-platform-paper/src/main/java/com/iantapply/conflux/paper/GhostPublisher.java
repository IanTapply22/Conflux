package com.iantapply.conflux.paper;

import com.iantapply.conflux.api.GhostAnimation;
import com.iantapply.conflux.api.GhostAnimationType;
import com.iantapply.conflux.api.GhostAppearance;
import com.iantapply.conflux.api.GhostAppearanceFrame;
import com.iantapply.conflux.api.GhostFrame;
import com.iantapply.conflux.api.GhostMovement;
import com.iantapply.conflux.api.GhostMovementFrame;
import com.iantapply.conflux.api.GhostProtocol;
import com.iantapply.conflux.api.GhostState;
import com.iantapply.relay.api.Destination;
import com.iantapply.relay.api.MessagingService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Publishes lightweight motion, change-only appearance, and recovery snapshots. */
final class GhostPublisher {
    private final JavaPlugin plugin;
    private final MessagingService relay;
    private final GhostConfig config;
    private final ConfluxMetrics metrics;
    private final String nodeId;
    private final UUID sessionId = UUID.randomUUID();
    private final AtomicLong sequence = new AtomicLong();
    private final Map<UUID, CachedAppearance> appearances = new HashMap<>();
    private long lastFullSnapshotNanos = Long.MIN_VALUE;

    /**
     * Creates a publisher for one running Conflux process.
     *
     * @param plugin owning Paper plugin
     * @param relay Relay messaging service
     * @param config validated ghost configuration
     * @param metrics operational counters
     */
    GhostPublisher(JavaPlugin plugin, MessagingService relay, GhostConfig config, ConfluxMetrics metrics) {
        this.plugin = plugin;
        this.relay = relay;
        this.config = config;
        this.metrics = metrics;
        this.nodeId = relay.status().node();
    }

    /**
     * Returns the local Relay node identifier.
     *
     * @return local node identifier
     */
    String nodeId() {
        return nodeId;
    }

    /**
     * Returns the random identifier for this plugin process.
     *
     * @return process session identifier
     */
    UUID sessionId() {
        return sessionId;
    }

    /** Captures and publishes one scheduled state update without terminating the scheduler on failure. */
    void publishTick() {
        try {
            publishSnapshot();
        } catch (RuntimeException exception) {
            metrics.publishFailed();
            plugin.getLogger().log(Level.WARNING, "Could not capture or publish the Conflux snapshot", exception);
        }
    }

    /** Captures current players and emits movement, changed appearance, and recovery frames. */
    private void publishSnapshot() {
        long nowMillis = System.currentTimeMillis();
        List<GhostMovement> movements = new ArrayList<>();
        List<GhostState> fullStates = new ArrayList<>();
        List<GhostAppearance> changedAppearances = new ArrayList<>();
        Set<UUID> online = new HashSet<>();
        List<? extends Player> publishedPlayers = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getUniqueId))
                .limit(GhostProtocol.MAX_PLAYERS_PER_FRAME)
                .toList();
        for (Player player : publishedPlayers) {
            online.add(player.getUniqueId());
            GhostMovement movement = GhostStateFactory.captureMovement(player);
            movements.add(movement);
            GhostStateFactory.AppearanceFingerprint fingerprint =
                    GhostStateFactory.captureAppearanceFingerprint(player, config.showEquipment());
            CachedAppearance cached = appearances.get(player.getUniqueId());
            if (cached == null || !cached.fingerprint().equals(fingerprint)) {
                GhostAppearance appearance = GhostStateFactory.encodeAppearance(fingerprint);
                cached = new CachedAppearance(fingerprint, appearance);
                appearances.put(player.getUniqueId(), cached);
                changedAppearances.add(appearance);
            }
            fullStates.add(GhostState.combine(movement, cached.appearance()));
        }
        appearances.keySet().removeIf(playerId -> !online.contains(playerId));
        publish(
                RelayTopics.MOVEMENT,
                new GhostMovementFrame(
                        GhostProtocol.CURRENT_VERSION,
                        config.realmId(),
                        nodeId,
                        sessionId,
                        sequence.incrementAndGet(),
                        nowMillis,
                        movements));
        metrics.movementPlayers(movements.size());
        if (!changedAppearances.isEmpty()) publishAppearances(changedAppearances, nowMillis);

        long nowNanos = System.nanoTime();
        long fullIntervalNanos = config.fullSnapshotIntervalSeconds() * 1_000_000_000L;
        if (lastFullSnapshotNanos == Long.MIN_VALUE || nowNanos - lastFullSnapshotNanos >= fullIntervalNanos) {
            lastFullSnapshotNanos = nowNanos;
            publish(
                    RelayTopics.FRAME,
                    new GhostFrame(
                            GhostProtocol.CURRENT_VERSION,
                            config.realmId(),
                            nodeId,
                            sessionId,
                            sequence.incrementAndGet(),
                            nowMillis,
                            fullStates));
        }
    }

    /**
     * Publishes one transient local-player animation.
     *
     * @param player player producing the animation
     * @param type animation type
     */
    void publishAnimation(Player player, GhostAnimationType type) {
        publish(
                RelayTopics.ANIMATION,
                new GhostAnimation(
                        GhostProtocol.CURRENT_VERSION,
                        config.realmId(),
                        nodeId,
                        sessionId,
                        player.getUniqueId(),
                        type,
                        sequence.incrementAndGet(),
                        System.currentTimeMillis()));
    }

    /** Publishes an authoritative empty frame to remove this node's remote ghosts. */
    void publishShutdown() {
        publish(
                RelayTopics.FRAME,
                new GhostFrame(
                        GhostProtocol.CURRENT_VERSION,
                        config.realmId(),
                        nodeId,
                        sessionId,
                        sequence.incrementAndGet(),
                        System.currentTimeMillis(),
                        List.of()));
    }

    /**
     * Publishes a batch containing only changed player appearances.
     *
     * @param appearances changed appearances
     * @param nowMillis common source timestamp
     */
    private void publishAppearances(List<GhostAppearance> appearances, long nowMillis) {
        metrics.appearanceCharacters(appearances.stream()
                .mapToLong(appearance -> appearance.skinValue().length()
                        + appearance.skinSignature().length()
                        + appearance.equipment().mainHand().length()
                        + appearance.equipment().offHand().length()
                        + appearance.equipment().feet().length()
                        + appearance.equipment().legs().length()
                        + appearance.equipment().chest().length()
                        + appearance.equipment().head().length())
                .sum());
        publish(
                RelayTopics.APPEARANCE,
                new GhostAppearanceFrame(
                        GhostProtocol.CURRENT_VERSION,
                        config.realmId(),
                        nodeId,
                        sessionId,
                        sequence.incrementAndGet(),
                        nowMillis,
                        appearances));
    }

    /**
     * Publishes a typed Relay message and records its asynchronous outcome.
     *
     * @param topic destination topic
     * @param payload typed message payload
     * @param <T> payload type
     */
    private <T> void publish(com.iantapply.relay.api.Topic<T> topic, T payload) {
        metrics.estimatedPayloadBytes(estimatePayloadBytes(payload));
        try {
            CompletionStage<?> result = relay.publish(topic, Destination.paperServers(), payload);
            result.whenComplete((ignored, failure) -> {
                if (failure == null) {
                    metrics.published();
                } else {
                    metrics.publishFailed();
                    plugin.getLogger()
                            .log(Level.WARNING, "Could not publish Conflux message on " + topic.name(), failure);
                }
            });
        } catch (RuntimeException exception) {
            metrics.publishFailed();
            plugin.getLogger().log(Level.WARNING, "Could not publish Conflux message on " + topic.name(), exception);
        }
    }

    /**
     * Estimates encoded message size without performing a second JSON serialization.
     *
     * @param payload outgoing protocol payload
     * @return approximate encoded size in bytes
     */
    private static long estimatePayloadBytes(Object payload) {
        if (payload instanceof GhostMovementFrame frame) {
            return 160L
                    + frame.players().stream()
                            .mapToLong(player -> 120L + player.world().length())
                            .sum();
        }
        if (payload instanceof GhostAppearanceFrame frame) {
            return 180L
                    + frame.players().stream()
                            .mapToLong(appearance -> 140L
                                    + appearance.username().length()
                                    + appearance.skinValue().length()
                                    + appearance.skinSignature().length()
                                    + appearance.equipment().mainHand().length()
                                    + appearance.equipment().offHand().length()
                                    + appearance.equipment().feet().length()
                                    + appearance.equipment().legs().length()
                                    + appearance.equipment().chest().length()
                                    + appearance.equipment().head().length())
                            .sum();
        }
        if (payload instanceof GhostFrame frame) {
            return 180L
                    + frame.players().stream()
                            .mapToLong(state -> 220L
                                    + state.world().length()
                                    + state.username().length()
                                    + state.skinValue().length()
                                    + state.skinSignature().length()
                                    + state.equipment().mainHand().length()
                                    + state.equipment().offHand().length()
                                    + state.equipment().feet().length()
                                    + state.equipment().legs().length()
                                    + state.equipment().chest().length()
                                    + state.equipment().head().length())
                            .sum();
        }
        return 200L;
    }

    /**
     * Cached raw fingerprint and its corresponding encoded appearance.
     *
     * @param fingerprint raw comparison value
     * @param appearance encoded wire value
     */
    private record CachedAppearance(GhostStateFactory.AppearanceFingerprint fingerprint, GhostAppearance appearance) {}
}
