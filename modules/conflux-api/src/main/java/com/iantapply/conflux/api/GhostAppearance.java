package com.iantapply.conflux.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Infrequently changing identity, skin, and equipment state for a remote player.
 *
 * @param playerId unique identifier of the remote player
 * @param username current Minecraft username
 * @param skinValue signed profile texture value, or an empty string
 * @param skinSignature profile texture signature, or an empty string
 * @param equipment equipment worn or held by the player
 */
public record GhostAppearance(
        UUID playerId, String username, String skinValue, String skinSignature, GhostEquipment equipment) {
    /** Validates an appearance snapshot. */
    public GhostAppearance {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(username, "username");
        if (!username.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("invalid username");
        skinValue = GhostProtocol.boundedNullable(skinValue, GhostProtocol.MAX_SKIN_VALUE_LENGTH, "skinValue");
        skinSignature =
                GhostProtocol.boundedNullable(skinSignature, GhostProtocol.MAX_SKIN_SIGNATURE_LENGTH, "skinSignature");
        Objects.requireNonNull(equipment, "equipment");
    }
}
