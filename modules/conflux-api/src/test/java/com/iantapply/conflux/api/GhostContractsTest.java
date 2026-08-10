package com.iantapply.conflux.api;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies validation and immutability guarantees of the public ghost contracts. */
class GhostContractsTest {
    /** Verifies that frame player collections cannot be mutated after construction. */
    @Test
    void frameDefensivelyCopiesPlayers() {
        GhostFrame frame = new GhostFrame(
                GhostProtocol.CURRENT_VERSION, "default", "world-1", UUID.randomUUID(), 1, 2, List.of(state()));
        assertThrows(UnsupportedOperationException.class, () -> frame.players().clear());
    }

    /** Verifies that non-finite world coordinates are rejected. */
    @Test
    void stateRejectsNonFiniteCoordinates() {
        GhostState valid = state();
        assertThrows(
                IllegalArgumentException.class,
                () -> new GhostState(
                        valid.playerId(),
                        valid.username(),
                        valid.world(),
                        Double.NaN,
                        2,
                        3,
                        0,
                        0,
                        true,
                        false,
                        false,
                        false,
                        false,
                        "",
                        "",
                        GhostEquipment.EMPTY));
    }

    /** Verifies that the shared empty equipment snapshot is safe to inspect. */
    @Test
    void emptyEquipmentIsSafe() {
        assertTrue(GhostEquipment.EMPTY.mainHand().isEmpty());
    }

    /** Verifies that duplicate players cannot make a full frame ambiguous. */
    @Test
    void frameRejectsDuplicatePlayers() {
        GhostState state = state();
        assertThrows(
                IllegalArgumentException.class,
                () -> new GhostFrame(
                        GhostProtocol.CURRENT_VERSION,
                        "default",
                        "node-1",
                        UUID.randomUUID(),
                        1,
                        2,
                        List.of(state, state)));
    }

    /** Verifies that protocol payload strings have defensive upper bounds. */
    @Test
    void equipmentRejectsOversizedValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GhostEquipment("x".repeat(GhostProtocol.MAX_EQUIPMENT_LENGTH + 1), "", "", "", "", ""));
    }

    /** Verifies that movement and appearance snapshots can reconstruct full state. */
    @Test
    void stateRoundTripsThroughSplitProtocol() {
        GhostState state = state();
        assertTrue(GhostState.combine(state.movement(), state.appearance()).equals(state));
    }

    /**
     * Creates a valid state used by contract tests.
     *
     * @return valid player ghost state
     */
    private static GhostState state() {
        return new GhostState(
                UUID.randomUUID(),
                "Player",
                "world",
                1,
                2,
                3,
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
