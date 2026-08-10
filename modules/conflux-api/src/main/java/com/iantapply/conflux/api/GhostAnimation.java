package com.iantapply.conflux.api;

import java.util.Objects;
import java.util.UUID;

/**
 * One transient animation produced by a player on a remote Paper node.
 *
 * @param nodeId unique identifier of the node that produced the animation
 * @param playerId unique identifier of the animated player
 * @param type visual action to play
 * @param sequence monotonically increasing animation sequence for the source node
 * @param sentAtEpochMilli time at which the source node sent the animation, in Unix epoch milliseconds
 */
public record GhostAnimation(
        String nodeId, UUID playerId, GhostAnimationType type, long sequence, long sentAtEpochMilli) {
    /** Validates and creates a ghost animation. */
    public GhostAnimation {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
        if (!nodeId.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("invalid nodeId");
        if (sequence < 0 || sentAtEpochMilli < 0) throw new IllegalArgumentException("negative animation value");
    }
}
