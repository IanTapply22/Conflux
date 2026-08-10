package com.iantapply.conflux.api;

import java.util.List;
import java.util.UUID;

/**
 * Batched change-only appearance updates for one running Conflux node session.
 *
 * @param protocolVersion wire protocol version
 * @param realmId logical network realm
 * @param nodeId unique source node identifier
 * @param sessionId unique identifier of the producing process
 * @param sequence monotonically increasing session sequence
 * @param sentAtEpochMilli source wall-clock publication time
 * @param players changed player appearances
 */
public record GhostAppearanceFrame(
        int protocolVersion,
        String realmId,
        String nodeId,
        UUID sessionId,
        long sequence,
        long sentAtEpochMilli,
        List<GhostAppearance> players) {
    /** Validates and defensively copies the change frame. */
    public GhostAppearanceFrame {
        GhostProtocol.validateEnvelope(protocolVersion, realmId, nodeId, sessionId, sequence, sentAtEpochMilli);
        players = GhostProtocol.copyPlayers(players, GhostAppearance::playerId);
        GhostProtocol.validateAppearancePayload(players);
    }
}
