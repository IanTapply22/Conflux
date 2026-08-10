package com.iantapply.conflux.paper.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Small command trees that adapt Paper senders to Conflux's ghost service. */
public final class ConfluxCommands {
    /** Permission required for the Conflux status command. */
    public static final String ADMIN_PERMISSION = "conflux.admin";

    /** Permission required for per-player ghost-density commands. */
    public static final String GHOSTS_PERMISSION = "conflux.ghosts";

    private final Supplier<Status> status;
    private final ToIntFunction<Player> viewerLimit;
    private final ObjIntConsumer<Player> viewerLimitUpdater;
    private final int maximumPerViewer;

    /**
     * Creates the command adapter.
     *
     * @param status current operational status supplier
     * @param viewerLimit function returning a player's active ghost limit
     * @param viewerLimitUpdater operation updating a player's ghost limit
     * @param maximumPerViewer configured maximum ghost limit
     */
    public ConfluxCommands(
            Supplier<Status> status,
            ToIntFunction<Player> viewerLimit,
            ObjIntConsumer<Player> viewerLimitUpdater,
            int maximumPerViewer) {
        this.status = status;
        this.viewerLimit = viewerLimit;
        this.viewerLimitUpdater = viewerLimitUpdater;
        this.maximumPerViewer = maximumPerViewer;
    }

    /**
     * Builds the complete {@code /conflux} administration command tree.
     *
     * @return lifecycle-registerable administration command root
     */
    public LiteralCommandNode<CommandSourceStack> createAdmin() {
        return Commands.literal("conflux")
                .requires(source -> source.getSender().hasPermission(ADMIN_PERMISSION))
                .executes(this::status)
                .build();
    }

    /**
     * Builds the complete {@code /ghosts} player-preference command tree.
     *
     * @return lifecycle-registerable player command root
     */
    public LiteralCommandNode<CommandSourceStack> createGhosts() {
        return Commands.literal("ghosts")
                .requires(source -> source.getSender().hasPermission(GHOSTS_PERMISSION))
                .executes(context -> showGhostLimit(context.getSource().getSender()))
                .then(Commands.literal("off").executes(context -> setGhostLimit(context, 0)))
                .then(Commands.literal("low").executes(context -> setGhostLimit(context, 5)))
                .then(Commands.literal("medium").executes(context -> setGhostLimit(context, 15)))
                .then(Commands.literal("high").executes(context -> setGhostLimit(context, maximumPerViewer)))
                .build();
    }

    /**
     * Sends current synchronization status to an administrator.
     *
     * @param context Paper command context
     * @return successful Brigadier result
     */
    private int status(CommandContext<CommandSourceStack> context) {
        Status current = status.get();
        context.getSource()
                .getSender()
                .sendMessage(Component.text("Conflux", NamedTextColor.GOLD)
                        .append(Component.text(
                                " node=" + current.nodeId() + ", session=" + current.sessionId() + ", realm="
                                        + current.realmId() + ", nodes=" + current.remoteNodes() + ", remote="
                                        + current.remotePlayers() + ", rendered=" + current.renderedGhosts(),
                                NamedTextColor.WHITE))
                        .append(Component.text(", metrics=" + current.metrics(), NamedTextColor.GRAY)));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Shows a player's current ghost display limit.
     *
     * @param sender command sender requesting the limit
     * @return Brigadier command result
     */
    private int showGhostLimit(CommandSender sender) {
        if (!(sender instanceof Player player)) return playersOnly(sender);
        sender.sendMessage(Component.text("Your ghost limit is ", NamedTextColor.GRAY)
                .append(Component.text(viewerLimit.applyAsInt(player), NamedTextColor.WHITE))
                .append(Component.text(". Use ", NamedTextColor.GRAY))
                .append(Component.text("/ghosts <off|low|medium|high>", NamedTextColor.WHITE))
                .append(Component.text(".", NamedTextColor.GRAY)));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Changes a player's ghost display limit.
     *
     * @param context Paper command context
     * @param limit requested maximum rendered ghosts
     * @return Brigadier command result
     */
    private int setGhostLimit(CommandContext<CommandSourceStack> context, int limit) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) return playersOnly(sender);
        viewerLimitUpdater.accept(player, limit);
        sender.sendMessage(Component.text(
                "Cross-world ghost limit set to " + viewerLimit.applyAsInt(player) + ".", NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Reports that a player-only command was invoked by another sender type.
     *
     * @param sender unsupported command sender
     * @return unsuccessful Brigadier result
     */
    private static int playersOnly(CommandSender sender) {
        sender.sendMessage(Component.text("Only players have a ghost display limit.", NamedTextColor.RED));
        return 0;
    }

    /**
     * Immutable operational values displayed by {@code /conflux}.
     *
     * @param nodeId local Relay node identifier
     * @param sessionId local process session identifier
     * @param realmId logical network realm
     * @param remoteNodes known remote node count
     * @param remotePlayers known remote player count
     * @param renderedGhosts currently rendered ghost count
     * @param metrics compact operational metrics
     */
    public record Status(
            String nodeId,
            String sessionId,
            String realmId,
            int remoteNodes,
            int remotePlayers,
            int renderedGhosts,
            String metrics) {}
}
