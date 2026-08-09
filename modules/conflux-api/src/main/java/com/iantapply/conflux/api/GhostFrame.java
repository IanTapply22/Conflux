package com.iantapply.conflux.api;

import java.util.List;
import java.util.Objects;

/** Complete visual snapshot for one Paper node. A newer frame replaces its preceding frame. */
public record GhostFrame(String nodeId, long sequence, long sentAtEpochMilli, List<GhostState> players) {
    public GhostFrame {
        Objects.requireNonNull(nodeId, "nodeId");
        players = List.copyOf(players);
        if (!nodeId.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("invalid nodeId");
        if (sequence < 0 || sentAtEpochMilli < 0) throw new IllegalArgumentException("negative frame value");
    }
}
