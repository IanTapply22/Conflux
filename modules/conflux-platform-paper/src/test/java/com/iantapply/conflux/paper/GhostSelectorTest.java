package com.iantapply.conflux.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.iantapply.conflux.api.GhostEquipment;
import com.iantapply.conflux.api.GhostState;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies pure per-viewer ghost selection behavior. */
class GhostSelectorTest {
    private final GhostSelector selector = new GhostSelector();

    /** Verifies radius filtering, distance ordering, and result limits. */
    @Test
    void selectsNearestPlayersWithinLimitAndRadius() {
        UUID viewerId = UUID.randomUUID();
        List<RemoteFrameStore.RemoteGhost> ghosts = List.of(
                remote("node-a", state(UUID.randomUUID(), 3, 0, 0), 10),
                remote("node-a", state(UUID.randomUUID(), 1, 0, 0), 10),
                remote("node-a", state(UUID.randomUUID(), 200, 0, 0), 10));

        List<GhostSelector.Selection> selected =
                selector.select(new GhostSelector.ViewerPosition(viewerId, "world", 0, 0, 0), ghosts, 1, 50);

        assertEquals(1, selected.size());
        assertEquals(1, selected.getFirst().remote().state().x());
    }

    /** Verifies that cross-node duplicates resolve to the freshest observation. */
    @Test
    void keepsNewestCopyOfPlayerSeenOnMultipleNodes() {
        UUID playerId = UUID.randomUUID();
        List<RemoteFrameStore.RemoteGhost> ghosts = List.of(
                remote("node-old", state(playerId, 1, 0, 0), 10), remote("node-new", state(playerId, 4, 0, 0), 20));

        List<GhostSelector.Selection> selected =
                selector.select(new GhostSelector.ViewerPosition(UUID.randomUUID(), "world", 0, 0, 0), ghosts, 10, 50);

        assertEquals(1, selected.size());
        assertEquals("node-new", selected.getFirst().remote().key().nodeId());
    }

    /**
     * Wraps a test state in source-node metadata.
     *
     * @param node source node
     * @param state player state
     * @param receivedAt monotonic receipt time
     * @return remote ghost fixture
     */
    private static RemoteFrameStore.RemoteGhost remote(String node, GhostState state, long receivedAt) {
        return new RemoteFrameStore.RemoteGhost(new GhostKey(node, state.playerId()), 1, receivedAt, state);
    }

    /**
     * Creates a valid player-state fixture at the supplied coordinates.
     *
     * @param playerId player identifier
     * @param x x-coordinate
     * @param y y-coordinate
     * @param z z-coordinate
     * @return valid ghost state
     */
    static GhostState state(UUID playerId, double x, double y, double z) {
        return new GhostState(
                playerId,
                "Player",
                "world",
                x,
                y,
                z,
                0,
                0,
                true,
                false,
                false,
                false,
                false,
                "",
                "",
                GhostEquipment.EMPTY);
    }
}
