package com.iantapply.conflux.api;

import java.util.Objects;
import java.util.UUID;

/**
 * One transient animation produced by a player on a remote Paper node.
 *
 * @param protocolVersion wire protocol version
 * @param realmId logical network realm
 * @param nodeId unique identifier of the node that produced the animation
 * @param sessionId unique identifier of the producing process
 * @param playerId unique identifier of the animated player
 * @param type visual action to play
 * @param sequence monotonically increasing animation sequence for the source node
 * @param sentAtEpochMilli time at which the source node sent the animation, in Unix epoch milliseconds
 */
public record GhostAnimation(
        int protocolVersion,
        String realmId,
        String nodeId,
        UUID sessionId,
        UUID playerId,
        GhostAnimationType type,
        long sequence,
        long sentAtEpochMilli) {
    /** Validates and creates a ghost animation. */
    public GhostAnimation {
        GhostProtocol.validateEnvelope(protocolVersion, realmId, nodeId, sessionId, sequence, sentAtEpochMilli);
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
    }
}
