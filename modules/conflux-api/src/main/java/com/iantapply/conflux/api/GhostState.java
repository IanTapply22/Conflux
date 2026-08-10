package com.iantapply.conflux.api;

import java.util.Objects;
import java.util.UUID;

/**
 * One remote player's visual state at a point in time.
 *
 * @param playerId unique identifier of the remote player
 * @param username current Minecraft username of the remote player
 * @param world name of the world containing the remote player
 * @param x world x-coordinate
 * @param y world y-coordinate
 * @param z world z-coordinate
 * @param yaw horizontal rotation in degrees
 * @param pitch vertical rotation in degrees
 * @param onGround whether the player is touching the ground
 * @param sneaking whether the player is sneaking
 * @param sprinting whether the player is sprinting
 * @param swimming whether the player is swimming
 * @param gliding whether the player is gliding with an elytra
 * @param skinValue signed profile texture value, or an empty string
 * @param skinSignature profile texture signature, or an empty string
 * @param equipment equipment worn or held by the player
 */
public record GhostState(
        UUID playerId,
        String username,
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
        boolean gliding,
        String skinValue,
        String skinSignature,
        GhostEquipment equipment) {
    /** Validates and creates a player ghost state. */
    public GhostState {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(world, "world");
        skinValue = skinValue == null ? "" : skinValue;
        skinSignature = skinSignature == null ? "" : skinSignature;
        Objects.requireNonNull(equipment, "equipment");
        if (!username.matches("[A-Za-z0-9_]{1,16}")) throw new IllegalArgumentException("invalid username");
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

    /**
     * Returns the frequently changing portion of this full snapshot.
     *
     * @return movement portion
     */
    public GhostMovement movement() {
        return new GhostMovement(
                playerId, world, x, y, z, yaw, pitch, onGround, sneaking, sprinting, swimming, gliding);
    }

    /**
     * Returns the infrequently changing portion of this full snapshot.
     *
     * @return appearance portion
     */
    public GhostAppearance appearance() {
        return new GhostAppearance(playerId, username, skinValue, skinSignature, equipment);
    }

    /**
     * Combines movement and appearance state into a renderable full snapshot.
     *
     * @param movement movement portion
     * @param appearance appearance portion for the same player
     * @return combined renderable snapshot
     */
    public static GhostState combine(GhostMovement movement, GhostAppearance appearance) {
        if (!movement.playerId().equals(appearance.playerId())) {
            throw new IllegalArgumentException("movement and appearance playerId differ");
        }
        return new GhostState(
                movement.playerId(),
                appearance.username(),
                movement.world(),
                movement.x(),
                movement.y(),
                movement.z(),
                movement.yaw(),
                movement.pitch(),
                movement.onGround(),
                movement.sneaking(),
                movement.sprinting(),
                movement.swimming(),
                movement.gliding(),
                appearance.skinValue(),
                appearance.skinSignature(),
                appearance.equipment());
    }
}
