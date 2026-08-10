package com.iantapply.conflux.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Frequently changing position, rotation, and pose state for a remote player.
 *
 * @param playerId unique identifier of the remote player
 * @param world logical world name
 * @param x world x-coordinate
 * @param y world y-coordinate
 * @param z world z-coordinate
 * @param yaw horizontal rotation in degrees
 * @param pitch vertical rotation in degrees
 * @param onGround whether the player is touching the ground
 * @param sneaking whether the player is sneaking
 * @param sprinting whether the player is sprinting
 * @param swimming whether the player is swimming
 * @param gliding whether the player is gliding
 */
public record GhostMovement(
        UUID playerId,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        boolean onGround,
        boolean sneaking,
        boolean sprinting,
        boolean swimming,
        boolean gliding) {
    /** Validates a movement snapshot. */
    public GhostMovement {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(world, "world");
        if (world.isBlank() || world.length() > 128 || world.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid world");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite");
        }
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("rotation must be finite");
        }
    }
}
