package com.iantapply.conflux.paper;

import com.iantapply.relay.api.MessagingService;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar()
                    .register(
                            Commands.literal("conflux")
                                    .requires(source -> source.getSender().hasPermission("conflux.admin"))
                                    .executes(context -> {
                                        context.getSource()
                                                .getSender()
                                                .sendRichMessage("<gold>Conflux</gold> node=<white>"
                                                        + ghosts.nodeId() + "</white>, session=<white>"
                                                        + ghosts.sessionId() + "</white>, realm=<white>"
                                                        + ghosts.realmId() + "</white>, nodes=<white>"
                                                        + ghosts.remoteNodes() + "</white>, remote=<white>"
                                                        + ghosts.remotePlayers()
                                                        + "</white>, rendered=<white>" + ghosts.renderedGhosts()
                                                        + "</white>, metrics=<gray>" + ghosts.metricsSummary()
                                                        + "</gray>");
                                        return Command.SINGLE_SUCCESS;
                                    })
                                    .build(),
                            "Show Conflux ghost synchronization status");
            event.registrar()
                    .register(
                            Commands.literal("ghosts")
                                    .requires(source -> source.getSender().hasPermission("conflux.ghosts"))
                                    .executes(context ->
                                            showGhostLimit(context.getSource().getSender()))
                                    .then(Commands.literal("off")
                                            .executes(context -> setGhostLimit(
                                                    context.getSource().getSender(), 0)))
                                    .then(Commands.literal("low")
                                            .executes(context -> setGhostLimit(
                                                    context.getSource().getSender(), 5)))
                                    .then(Commands.literal("medium")
                                            .executes(context -> setGhostLimit(
                                                    context.getSource().getSender(), 15)))
                                    .then(Commands.literal("high")
                                            .executes(context -> setGhostLimit(
                                                    context.getSource().getSender(), config.maximumPerViewer())))
                                    .build(),
                            "Control the number of cross-world player ghosts");
        });
    }

    /**
     * Shows a command sender's current ghost display limit.
     *
     * @param sender command sender requesting the limit
     * @return the Brigadier command result
     */
    private int showGhostLimit(org.bukkit.command.CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Only players have a ghost display limit.</red>");
            return 0;
        }
        sender.sendRichMessage("<gray>Your ghost limit is <white>" + ghosts.viewerLimit(player)
                + "</white>. Use <white>/ghosts <off|low|medium|high></white>.</gray>");
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Changes a player's ghost display limit.
     *
     * @param sender command sender changing the limit
     * @param limit requested maximum number of rendered ghosts
     * @return the Brigadier command result
     */
    private int setGhostLimit(org.bukkit.command.CommandSender sender, int limit) {
        if (!(sender instanceof Player player)) {
            sender.sendRichMessage("<red>Only players have a ghost display limit.</red>");
            return 0;
        }
        ghosts.setViewerLimit(player, limit);
        sender.sendRichMessage("<green>Cross-world ghost limit set to " + ghosts.viewerLimit(player) + ".</green>");
        return Command.SINGLE_SUCCESS;
    }
}
