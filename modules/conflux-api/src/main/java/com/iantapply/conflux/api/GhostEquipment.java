package com.iantapply.conflux.api;

import java.util.Objects;

/**
 * Base64-encoded Bukkit item stacks worn or held by a remote player.
 *
 * @param mainHand item held in the main hand, or an empty string
 * @param offHand item held in the offhand, or an empty string
 * @param feet boots worn by the player, or an empty string
 * @param legs leggings worn by the player, or an empty string
 * @param chest chestplate worn by the player, or an empty string
 * @param head helmet worn by the player, or an empty string
 */
public record GhostEquipment(String mainHand, String offHand, String feet, String legs, String chest, String head) {
    /** Equipment state in which every slot is empty. */
    public static final GhostEquipment EMPTY = new GhostEquipment("", "", "", "", "", "");

    /** Validates and creates a ghost equipment snapshot. */
    public GhostEquipment {
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offHand, "offHand");
        Objects.requireNonNull(feet, "feet");
        Objects.requireNonNull(legs, "legs");
        Objects.requireNonNull(chest, "chest");
        Objects.requireNonNull(head, "head");
        validateLength(mainHand, "mainHand");
        validateLength(offHand, "offHand");
        validateLength(feet, "feet");
        validateLength(legs, "legs");
        validateLength(chest, "chest");
        validateLength(head, "head");
    }

    /**
     * Enforces the per-slot encoded transport bound.
     *
     * @param value encoded equipment value
     * @param name slot name used in validation errors
     */
    private static void validateLength(String value, String name) {
        if (value.length() > GhostProtocol.MAX_EQUIPMENT_LENGTH) {
            throw new IllegalArgumentException(name + " is too long");
        }
    }
}
