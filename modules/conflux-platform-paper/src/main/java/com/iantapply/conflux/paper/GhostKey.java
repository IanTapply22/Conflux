package com.iantapply.conflux.paper;

import java.util.UUID;

/**
 * Identifies a rendered player from a particular remote node.
 *
 * @param nodeId source Relay node identifier
 * @param playerId remote player's unique identifier
 */
record GhostKey(String nodeId, UUID playerId) {}
