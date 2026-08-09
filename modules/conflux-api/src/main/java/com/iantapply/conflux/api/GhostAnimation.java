package com.iantapply.conflux.api;

import java.util.Objects;
import java.util.UUID;

/** One transient animation produced by a player on a remote Paper node. */
public record GhostAnimation(
        String nodeId, UUID playerId, GhostAnimationType type, long sequence, long sentAtEpochMilli) {
    public GhostAnimation {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
        if (!nodeId.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("invalid nodeId");
        if (sequence < 0 || sentAtEpochMilli < 0) throw new IllegalArgumentException("negative animation value");
    }
}
