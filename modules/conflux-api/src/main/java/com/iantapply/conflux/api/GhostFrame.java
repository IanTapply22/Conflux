package com.iantapply.conflux.api;

import java.util.List;
import java.util.Objects;

/**
 * Complete visual snapshot for one Paper node. A newer frame replaces its preceding frame.
 *
 * @param nodeId unique identifier of the node that produced the frame
 * @param sequence monotonically increasing frame sequence for the source node
 * @param sentAtEpochMilli time at which the source node sent the frame, in Unix epoch milliseconds
 * @param players immutable visual states for players connected to the source node
 */
public record GhostFrame(String nodeId, long sequence, long sentAtEpochMilli, List<GhostState> players) {
    /** Validates the frame and defensively copies its player states. */
    public GhostFrame {
        Objects.requireNonNull(nodeId, "nodeId");
        players = List.copyOf(players);
        if (!nodeId.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("invalid nodeId");
        if (sequence < 0 || sentAtEpochMilli < 0) throw new IllegalArgumentException("negative frame value");
    }
}
