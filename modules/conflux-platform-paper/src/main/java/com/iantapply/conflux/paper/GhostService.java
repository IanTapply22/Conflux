package com.iantapply.conflux.paper;

import com.iantapply.conflux.api.GhostAnimation;
import com.iantapply.conflux.api.GhostAnimationType;
import com.iantapply.conflux.api.GhostAppearanceFrame;
import com.iantapply.conflux.api.GhostFrame;
import com.iantapply.conflux.api.GhostMovementFrame;
import com.iantapply.relay.api.MessagingService;
import com.iantapply.relay.api.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Orchestrates publication, remote state ingestion, and per-viewer ghost rendering. */
final class GhostService implements Listener, AutoCloseable {
    /** Maximum messages retained before receive-side backpressure drops new arrivals. */
    private static final int MAX_PENDING_MESSAGES = 512;

    /** Maximum received messages applied during one server tick. */
    private static final int MAX_MESSAGES_PER_TICK = 128;

    private final GhostConfig config;
    private final ConfluxMetrics metrics = new ConfluxMetrics();
    private final GhostPublisher publisher;
    private final RemoteFrameStore remoteStore;
    private final ViewerGhostRenderer renderer;
    private final Map<UUID, Integer> viewerLimits = new java.util.HashMap<>();
    private final ConcurrentLinkedQueue<Runnable> inbox = new ConcurrentLinkedQueue<>();
    private final AtomicInteger inboxSize = new AtomicInteger();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final BukkitTask publishTask;
    private final BukkitTask renderTask;
    private long renderTicks;

    /**
     * Starts publication, subscriptions, ingestion, and rendering.
     *
     * @param plugin owning Paper plugin
     * @param relay Relay messaging service
     * @param config validated ghost configuration
     */
    GhostService(JavaPlugin plugin, MessagingService relay, GhostConfig config) {
        this.config = config;
        publisher = new GhostPublisher(plugin, relay, config, metrics);
        remoteStore = new RemoteFrameStore(publisher.nodeId(), config);
        renderer = new ViewerGhostRenderer(plugin, config, metrics);
        subscriptions.add(relay.subscribe(RelayTopics.FRAME, message -> enqueue(message.payload())));
        subscriptions.add(relay.subscribe(RelayTopics.MOVEMENT, message -> enqueue(message.payload())));
        subscriptions.add(relay.subscribe(RelayTopics.APPEARANCE, message -> enqueue(message.payload())));
        subscriptions.add(relay.subscribe(RelayTopics.ANIMATION, message -> enqueue(message.payload())));
        publishTask =
                Bukkit.getScheduler().runTaskTimer(plugin, publisher::publishTick, 1L, config.publishPeriodTicks());
        renderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::renderTick, 1L, 1L);
    }

    /**
     * Returns the local Relay node identifier.
     *
     * @return local node identifier
     */
    String nodeId() {
        return publisher.nodeId();
    }

    /**
     * Returns the local process session identifier.
     *
     * @return process session identifier
     */
    String sessionId() {
        return publisher.sessionId().toString();
    }

    /**
     * Returns the configured logical network realm.
     *
     * @return realm identifier
     */
    String realmId() {
        return config.realmId();
    }

    /**
     * Counts complete remote players in the state store.
     *
     * @return known remote player count
     */
    int remotePlayers() {
        return remoteStore.playerCount();
    }

    /**
     * Counts active remote nodes in the state store.
     *
     * @return known remote node count
     */
    int remoteNodes() {
        return remoteStore.nodeCount();
    }

    /**
     * Counts currently rendered packet ghosts.
     *
     * @return rendered ghost count
     */
    int renderedGhosts() {
        return renderer.renderedGhosts();
    }

    /**
     * Returns compact operational counters for the status command.
     *
     * @return metrics summary
     */
    String metricsSummary() {
        return metrics.summary();
    }

    /**
     * Gets a viewer's active ghost limit.
     *
     * @param player local viewer
     * @return viewer-specific or default limit
     */
    int viewerLimit(Player player) {
        return viewerLimits.getOrDefault(player.getUniqueId(), config.maximumPerViewer());
    }

    /**
     * Updates a viewer's clamped ghost limit and rebuilds their rendered set.
     *
     * @param player local viewer
     * @param limit requested ghost limit
     */
    void setViewerLimit(Player player, int limit) {
        viewerLimits.put(player.getUniqueId(), Math.clamp(limit, 0, config.maximumPerViewer()));
        renderer.destroyViewer(player.getUniqueId());
    }

    /**
     * Publishes a monitored local hand-swing animation.
     *
     * @param event player animation event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        GhostAnimationType type = event.getAnimationType() == PlayerAnimationType.OFF_ARM_SWING
                ? GhostAnimationType.SWING_OFF_HAND
                : GhostAnimationType.SWING_MAIN_HAND;
        publisher.publishAnimation(event.getPlayer(), type);
    }

    /**
     * Publishes a monitored local-player hurt animation.
     *
     * @param event entity damage event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) publisher.publishAnimation(player, GhostAnimationType.HURT);
    }

    /**
     * Removes viewer preferences and packet state after disconnect.
     *
     * @param event player quit event
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        viewerLimits.remove(viewerId);
        renderer.destroyViewer(viewerId);
    }

    /**
     * Rebuilds client-only entities after the client processes a respawn packet.
     *
     * @param event player respawn event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        renderer.destroyViewer(event.getPlayer().getUniqueId());
    }

    /**
     * Rebuilds client-only entities after a viewer changes dimensions or worlds.
     *
     * @param event player world-change event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChanged(PlayerChangedWorldEvent event) {
        renderer.destroyViewer(event.getPlayer().getUniqueId());
    }

    /** Cancels tasks, closes subscriptions, clears queued work, and destroys rendered ghosts. */
    @Override
    public void close() {
        publishTask.cancel();
        renderTask.cancel();
        publisher.publishShutdown();
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
        inbox.clear();
        inboxSize.set(0);
        renderer.destroyAll();
        remoteStore.clear();
    }

    /**
     * Queues a received full recovery frame for main-thread application.
     *
     * @param frame received full frame
     */
    private void enqueue(GhostFrame frame) {
        enqueue(() -> record(remoteStore.accept(frame)));
    }

    /**
     * Queues a received movement frame for main-thread application.
     *
     * @param frame received movement frame
     */
    private void enqueue(GhostMovementFrame frame) {
        enqueue(() -> record(remoteStore.accept(frame)));
    }

    /**
     * Queues a received appearance frame for main-thread application.
     *
     * @param frame received appearance frame
     */
    private void enqueue(GhostAppearanceFrame frame) {
        enqueue(() -> record(remoteStore.accept(frame)));
    }

    /**
     * Queues a received transient animation for validation and rendering.
     *
     * @param animation received animation
     */
    private void enqueue(GhostAnimation animation) {
        enqueue(() -> {
            boolean accepted = remoteStore.accept(animation);
            record(accepted);
            if (accepted) renderer.animate(animation);
        });
    }

    /**
     * Adds bounded work to the cross-thread receive inbox.
     *
     * @param operation main-thread store or rendering operation
     */
    private void enqueue(Runnable operation) {
        int size = inboxSize.incrementAndGet();
        if (size > MAX_PENDING_MESSAGES) {
            inboxSize.decrementAndGet();
            metrics.dropped();
            return;
        }
        inbox.add(operation);
    }

    /** Drains received work, expires nodes, reconciles viewers, and advances interpolation. */
    private void renderTick() {
        drainInbox();
        remoteStore.expireStale();
        renderTicks++;
        if (renderTicks % config.selectionPeriodTicks() == 0) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                renderer.reconcile(
                        viewer,
                        remoteStore.playersNear(
                                viewer.getWorld().getName(), viewer.getX(), viewer.getZ(), config.viewRadiusBlocks()),
                        remoteStore.revision(),
                        viewerLimit(viewer));
            }
        }
        renderer.tick();
    }

    /** Applies a bounded number of received messages on the server thread. */
    private void drainInbox() {
        for (int processed = 0; processed < MAX_MESSAGES_PER_TICK; processed++) {
            Runnable operation = inbox.poll();
            if (operation == null) return;
            inboxSize.decrementAndGet();
            operation.run();
        }
    }

    /**
     * Records whether a received message was accepted by protocol validation.
     *
     * @param accepted whether the message was accepted
     */
    private void record(boolean accepted) {
        metrics.received();
        if (!accepted) metrics.dropped();
    }
}
