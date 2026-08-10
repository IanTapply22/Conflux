package com.iantapply.conflux.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.iantapply.conflux.api.GhostAnimation;
import com.iantapply.conflux.api.GhostAnimationType;
import com.iantapply.conflux.api.GhostAppearance;
import com.iantapply.conflux.api.GhostAppearanceFrame;
import com.iantapply.conflux.api.GhostFrame;
import com.iantapply.conflux.api.GhostMovementFrame;
import com.iantapply.conflux.api.GhostProtocol;
import com.iantapply.conflux.api.GhostState;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Verifies ordering, restart, expiry, realm, and node-bound behavior. */
class RemoteFrameStoreTest {
    private final AtomicLong nanos = new AtomicLong(1_000_000);
    private final AtomicLong millis = new AtomicLong(1_000);
    private final RemoteFrameStore store =
            new RemoteFrameStore("local", "realm", 2, 1_500, 2_000, nanos::get, millis::get);

    /** Verifies restart acceptance and delayed-message rejection across sessions. */
    @Test
    void acceptsLowSequenceFromNewSessionAndRejectsRetiredSession() {
        UUID oldSession = UUID.randomUUID();
        UUID newSession = UUID.randomUUID();
        assertTrue(store.accept(frame("remote", oldSession, 50, 1)));
        assertTrue(store.accept(frame("remote", newSession, 1, 2)));
        assertFalse(store.accept(frame("remote", oldSession, 51, 3)));
        assertEquals(2, store.playersInWorld("world").iterator().next().state().x());
    }

    /** Verifies logical realm isolation and the configured remote-node bound. */
    @Test
    void rejectsWrongRealmAndBoundsNodes() {
        UUID session = UUID.randomUUID();
        assertFalse(store.accept(new GhostFrame(
                GhostProtocol.CURRENT_VERSION,
                "other",
                "remote",
                session,
                1,
                1,
                List.of(GhostSelectorTest.state(UUID.randomUUID(), 1, 0, 0)))));
        assertTrue(store.accept(frame("one", UUID.randomUUID(), 1, 1)));
        assertTrue(store.accept(frame("two", UUID.randomUUID(), 1, 2)));
        assertFalse(store.accept(frame("three", UUID.randomUUID(), 1, 3)));
    }

    /** Verifies cross-topic ordering and monotonic node expiry. */
    @Test
    void rejectsMovementOlderThanFullSnapshotAndExpiresMonotonically() {
        UUID session = UUID.randomUUID();
        GhostFrame full = frame("remote", session, 10, 5);
        assertTrue(store.accept(full));
        GhostState older = GhostSelectorTest.state(full.players().getFirst().playerId(), 1, 0, 0);
        assertFalse(store.accept(new GhostMovementFrame(
                GhostProtocol.CURRENT_VERSION, "realm", "remote", session, 9, 1, List.of(older.movement()))));
        nanos.addAndGet(1_500_000_001L);
        assertTrue(store.expireStale());
        assertEquals(0, store.playerCount());
    }

    /** Verifies animation replay, age, and session protections. */
    @Test
    void rejectsReplayedAndExpiredAnimationsWithoutReplacingSession() {
        UUID session = UUID.randomUUID();
        GhostFrame frame = frame("remote", session, 1, 5);
        UUID playerId = frame.players().getFirst().playerId();
        assertTrue(store.accept(frame));
        assertTrue(store.accept(animation(session, playerId, 2, 1_000)));
        assertFalse(store.accept(animation(session, playerId, 2, 1_000)));
        assertFalse(store.accept(animation(UUID.randomUUID(), playerId, 3, 1_000)));
        millis.set(4_000);
        assertFalse(store.accept(animation(session, playerId, 4, 1_000)));
        assertEquals(1, store.playerCount());
    }

    /** Verifies appearance merging and per-stream sequence ordering. */
    @Test
    void mergesBatchedAppearanceChangesAndRejectsOlderBatch() {
        UUID session = UUID.randomUUID();
        GhostFrame full = frame("remote", session, 1, 5);
        GhostState player = full.players().getFirst();
        assertTrue(store.accept(full));
        GhostAppearance changed = new GhostAppearance(
                player.playerId(), "Changed", player.skinValue(), player.skinSignature(), player.equipment());
        assertTrue(store.accept(appearances(session, 2, changed)));
        assertFalse(store.accept(appearances(session, 2, player.appearance())));
        assertEquals(
                "Changed",
                store.playersInWorld("world").iterator().next().state().username());
    }

    /**
     * Creates a full-frame test fixture.
     *
     * @param node source node
     * @param session source process session
     * @param sequence message sequence
     * @param x player x-coordinate
     * @return full-frame fixture
     */
    private static GhostFrame frame(String node, UUID session, long sequence, double x) {
        return new GhostFrame(
                GhostProtocol.CURRENT_VERSION,
                "realm",
                node,
                session,
                sequence,
                1,
                List.of(GhostSelectorTest.state(UUID.randomUUID(), x, 0, 0)));
    }

    /**
     * Creates an animation test fixture.
     *
     * @param session source process session
     * @param playerId animated player identifier
     * @param sequence message sequence
     * @param sentAt source wall-clock time
     * @return animation fixture
     */
    private static GhostAnimation animation(UUID session, UUID playerId, long sequence, long sentAt) {
        return new GhostAnimation(
                GhostProtocol.CURRENT_VERSION,
                "realm",
                "remote",
                session,
                playerId,
                GhostAnimationType.HURT,
                sequence,
                sentAt);
    }

    /**
     * Creates a one-player appearance-frame fixture.
     *
     * @param session source process session
     * @param sequence message sequence
     * @param appearance changed appearance
     * @return appearance-frame fixture
     */
    private static GhostAppearanceFrame appearances(UUID session, long sequence, GhostAppearance appearance) {
        return new GhostAppearanceFrame(
                GhostProtocol.CURRENT_VERSION, "realm", "remote", session, sequence, 1, List.of(appearance));
    }
}
