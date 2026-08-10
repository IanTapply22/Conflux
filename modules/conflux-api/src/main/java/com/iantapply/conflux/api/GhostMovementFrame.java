package com.iantapply.conflux.api;

import java.util.List;
import java.util.UUID;

/**
 * Complete lightweight movement snapshot for one running Conflux node session.
 *
 * @param protocolVersion wire protocol version
 * @param realmId logical network realm
 * @param nodeId unique source node identifier
 * @param sessionId unique identifier of the producing process
 * @param sequence monotonically increasing session sequence
 * @param sentAtEpochMilli source wall-clock publication time
 * @param players complete movement snapshot for the node
 */
public record GhostMovementFrame(
        int protocolVersion,
        String realmId,
        String nodeId,
        UUID sessionId,
        long sequence,
        long sentAtEpochMilli,
        List<GhostMovement> players) {
    /** Validates and defensively copies the frame. */
    public GhostMovementFrame {
        GhostProtocol.validateEnvelope(protocolVersion, realmId, nodeId, sessionId, sequence, sentAtEpochMilli);
        players = GhostProtocol.copyPlayers(players, GhostMovement::playerId);
    }
}
