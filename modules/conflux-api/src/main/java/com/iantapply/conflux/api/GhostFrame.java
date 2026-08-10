package com.iantapply.conflux.api;

import java.util.List;

/**
 * Complete visual snapshot for one Paper node. A newer frame replaces its preceding frame.
 *
 * @param protocolVersion wire protocol version
 * @param realmId logical network realm
 * @param nodeId unique identifier of the node that produced the frame
 * @param sessionId unique identifier of the producing process
 * @param sequence monotonically increasing frame sequence for the source node
 * @param sentAtEpochMilli time at which the source node sent the frame, in Unix epoch milliseconds
 * @param players immutable visual states for players connected to the source node
 */
public record GhostFrame(
        int protocolVersion,
        String realmId,
        String nodeId,
        java.util.UUID sessionId,
        long sequence,
        long sentAtEpochMilli,
        List<GhostState> players) {
    /** Validates the frame and defensively copies its player states. */
    public GhostFrame {
        GhostProtocol.validateEnvelope(protocolVersion, realmId, nodeId, sessionId, sequence, sentAtEpochMilli);
        players = GhostProtocol.copyPlayers(players, GhostState::playerId);
        GhostProtocol.validateStatePayload(players);
    }
}
