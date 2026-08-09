package com.iantapply.conflux.api;

import java.util.Objects;
import java.util.UUID;

/** One remote player's visual state at a point in time. */
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
    public GhostState {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(world, "world");
        skinValue = skinValue == null ? "" : skinValue;
        skinSignature = skinSignature == null ? "" : skinSignature;
        Objects.requireNonNull(equipment, "equipment");
        if (username.isBlank() || username.length() > 16) throw new IllegalArgumentException("invalid username");
        if (world.isBlank()) throw new IllegalArgumentException("world must not be blank");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("coordinates must be finite");
        }
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("rotation must be finite");
        }
    }
}
