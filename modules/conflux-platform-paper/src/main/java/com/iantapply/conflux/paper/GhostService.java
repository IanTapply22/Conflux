package com.iantapply.conflux.paper;

import com.iantapply.conflux.api.GhostAnimation;
import com.iantapply.conflux.api.GhostAnimationType;
import com.iantapply.conflux.api.GhostFrame;
import com.iantapply.conflux.api.GhostState;
import com.iantapply.relay.api.Destination;
import com.iantapply.relay.api.MessagingService;
import com.iantapply.relay.api.Subscription;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

final class GhostService implements Listener, AutoCloseable {
    private static final int MAX_PLAYERS_PER_REMOTE_FRAME = 250;

    private final JavaPlugin plugin;
    private final MessagingService relay;
    private final GhostConfig config;
    private final String nodeId;
    private final AtomicLong frameSequence = new AtomicLong();
    private final AtomicLong animationSequence = new AtomicLong();
    private final Map<String, NodeFrame> remoteFrames = new HashMap<>();
    private final Map<UUID, Map<GhostKey, PacketGhost>> rendered = new HashMap<>();
    private final Map<UUID, Integer> viewerLimits = new HashMap<>();
    private final Subscription frameSubscription;
    private final Subscription animationSubscription;
    private final BukkitTask publishTask;
    private final BukkitTask renderTask;

    GhostService(JavaPlugin plugin, MessagingService relay, GhostConfig config) {
        this.plugin = plugin;
        this.relay = relay;
        this.config = config;
        this.nodeId = relay.status().node();
        frameSubscription = relay.subscribe(RelayTopics.FRAME, message -> receive(message.payload()));
        animationSubscription = relay.subscribe(RelayTopics.ANIMATION, message -> receive(message.payload()));
        publishTask = Bukkit.getScheduler().runTaskTimer(plugin, this::publishFrame, 1L, config.publishPeriodTicks());
        renderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::renderTick, 1L, 1L);
    }

    String nodeId() {
        return nodeId;
    }

    int remotePlayers() {
        return remoteFrames.values().stream()
                .mapToInt(frame -> frame.players().size())
                .sum();
    }

    int renderedGhosts() {
        return rendered.values().stream().mapToInt(Map::size).sum();
    }

    int viewerLimit(Player player) {
        return viewerLimits.getOrDefault(player.getUniqueId(), config.maximumPerViewer());
    }

    void setViewerLimit(Player player, int limit) {
        viewerLimits.put(player.getUniqueId(), Math.clamp(limit, 0, config.maximumPerViewer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        GhostAnimationType type = event.getAnimationType() == PlayerAnimationType.OFF_ARM_SWING
                ? GhostAnimationType.SWING_OFF_HAND
                : GhostAnimationType.SWING_MAIN_HAND;
        publishAnimation(event.getPlayer(), type);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) publishAnimation(player, GhostAnimationType.HURT);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        viewerLimits.remove(event.getPlayer().getUniqueId());
        destroyViewer(event.getPlayer().getUniqueId());
    }

    @Override
    public void close() {
        publishTask.cancel();
        renderTask.cancel();
        relay.publish(
                RelayTopics.FRAME,
                Destination.paperServers(),
                new GhostFrame(nodeId, frameSequence.incrementAndGet(), System.currentTimeMillis(), List.of()));
        frameSubscription.close();
        animationSubscription.close();
        rendered.keySet().stream().toList().forEach(this::destroyViewer);
        remoteFrames.clear();
    }

    private void publishFrame() {
        List<GhostState> players = Bukkit.getOnlinePlayers().stream()
                .map(player -> GhostStateFactory.capture(player, config.showEquipment()))
                .toList();
        relay.publish(
                RelayTopics.FRAME,
                Destination.paperServers(),
                new GhostFrame(nodeId, frameSequence.incrementAndGet(), System.currentTimeMillis(), players));
    }

    private void publishAnimation(Player player, GhostAnimationType type) {
        relay.publish(
                RelayTopics.ANIMATION,
                Destination.paperServers(),
                new GhostAnimation(
                        nodeId,
                        player.getUniqueId(),
                        type,
                        animationSequence.incrementAndGet(),
                        System.currentTimeMillis()));
    }

    private void receive(GhostFrame frame) {
        if (frame.nodeId().equals(nodeId) || frame.players().size() > MAX_PLAYERS_PER_REMOTE_FRAME) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            NodeFrame previous = remoteFrames.get(frame.nodeId());
            if (previous == null || frame.sequence() > previous.sequence()) {
                remoteFrames.put(
                        frame.nodeId(), new NodeFrame(frame.sequence(), System.currentTimeMillis(), frame.players()));
            }
        });
    }

    private void receive(GhostAnimation animation) {
        if (animation.nodeId().equals(nodeId)) return;
        Bukkit.getScheduler().runTask(plugin, () -> rendered.values().forEach(ghosts -> {
            PacketGhost ghost = ghosts.get(new GhostKey(animation.nodeId(), animation.playerId()));
            if (ghost != null) ghost.animate(animation.type());
        }));
    }

    private void renderTick() {
        long now = System.currentTimeMillis();
        remoteFrames
                .entrySet()
                .removeIf(entry -> now - entry.getValue().receivedAt() > config.staleAfterMilliseconds());
        for (Player viewer : Bukkit.getOnlinePlayers()) reconcile(viewer);
        rendered.keySet().removeIf(viewerId -> Bukkit.getPlayer(viewerId) == null);
    }

    private void reconcile(Player viewer) {
        int limit = viewerLimit(viewer);
        double radiusSquared = config.viewRadiusBlocks() * config.viewRadiusBlocks();
        Map<UUID, Candidate> newestByPlayer = new HashMap<>();
        for (Map.Entry<String, NodeFrame> node : remoteFrames.entrySet()) {
            for (GhostState state : node.getValue().players()) {
                if (state.playerId().equals(viewer.getUniqueId())) continue;
                if (!state.world().equals(viewer.getWorld().getName())) continue;
                double distanceSquared = distanceSquared(viewer, state);
                if (distanceSquared > radiusSquared) continue;
                Candidate candidate = new Candidate(
                        new GhostKey(node.getKey(), state.playerId()),
                        node.getValue().sequence(),
                        node.getValue().receivedAt(),
                        distanceSquared,
                        state);
                newestByPlayer.merge(
                        state.playerId(),
                        candidate,
                        (first, second) -> first.receivedAt() >= second.receivedAt() ? first : second);
            }
        }
        List<Candidate> selected = newestByPlayer.values().stream()
                .sorted(Comparator.comparingDouble(Candidate::distanceSquared))
                .limit(limit)
                .toList();
        Map<GhostKey, Candidate> desired = new LinkedHashMap<>();
        selected.forEach(candidate -> desired.put(candidate.key(), candidate));

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
            if (ghost != null && !ghost.sameIdentity(candidate.state())) {
                ghost.destroy();
                ghosts.remove(key);
                ghost = null;
            }
            if (ghost == null) {
                ghost = new PacketGhost(plugin, viewer, candidate.state());
                ghosts.put(key, ghost);
            }
            ghost.target(candidate.state(), candidate.sequence());
            ghost.tick();
        });
    }

    private void destroyViewer(UUID viewerId) {
        Map<GhostKey, PacketGhost> ghosts = rendered.remove(viewerId);
        if (ghosts != null) ghosts.values().forEach(PacketGhost::destroy);
    }

    private static double distanceSquared(Player viewer, GhostState state) {
        double dx = viewer.getX() - state.x();
        double dy = viewer.getY() - state.y();
        double dz = viewer.getZ() - state.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private record NodeFrame(long sequence, long receivedAt, List<GhostState> players) {}

    private record Candidate(GhostKey key, long sequence, long receivedAt, double distanceSquared, GhostState state) {}
}
