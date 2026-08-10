package com.iantapply.conflux.paper;

import com.iantapply.conflux.paper.command.ConfluxCommands;
import com.iantapply.relay.api.MessagingService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper entry point for cross-world client-only player ghosts. */
public final class ConfluxPaper extends JavaPlugin {
    private GhostService ghosts;

    /** Creates the Paper plugin entry point. */
    public ConfluxPaper() {}

    /** Starts ghost synchronization and registers the plugin commands. */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            GhostConfig config = GhostConfig.load(getConfig());
            MessagingService relay = Objects.requireNonNull(
                    Bukkit.getServicesManager().load(MessagingService.class),
                    "Relay is unavailable; install and configure Relay before Conflux");
            ghosts = new GhostService(this, relay, config);
            Bukkit.getPluginManager().registerEvents(ghosts, this);
            registerCommands(config);
            getLogger().info("Conflux ghost synchronization enabled on Relay node " + ghosts.nodeId());
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, "Conflux could not start", exception);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    /** Stops synchronization and removes every rendered ghost. */
    @Override
    public void onDisable() {
        if (ghosts != null) ghosts.close();
    }

    /**
     * Registers the status and per-player ghost-limit commands.
     *
     * @param config validated plugin configuration
     */
    private void registerCommands(GhostConfig config) {
        ConfluxCommands commands = new ConfluxCommands(
                () -> new ConfluxCommands.Status(
                        ghosts.nodeId(),
                        ghosts.sessionId(),
                        ghosts.realmId(),
                        ghosts.remoteNodes(),
                        ghosts.remotePlayers(),
                        ghosts.renderedGhosts(),
                        ghosts.metricsSummary()),
                ghosts::viewerLimit,
                ghosts::setViewerLimit,
                config.maximumPerViewer());
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(commands.createAdmin(), "Show Conflux ghost synchronization status");
            event.registrar().register(commands.createGhosts(), "Control the number of cross-world player ghosts");
        });
    }
}
