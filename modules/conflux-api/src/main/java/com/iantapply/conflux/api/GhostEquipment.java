package com.iantapply.conflux.api;

import java.util.Objects;

/** Base64-encoded Bukkit item stacks worn or held by a remote player. */
public record GhostEquipment(String mainHand, String offHand, String feet, String legs, String chest, String head) {
    public static final GhostEquipment EMPTY = new GhostEquipment("", "", "", "", "", "");

    public GhostEquipment {
        Objects.requireNonNull(mainHand, "mainHand");
        Objects.requireNonNull(offHand, "offHand");
        Objects.requireNonNull(feet, "feet");
        Objects.requireNonNull(legs, "legs");
        Objects.requireNonNull(chest, "chest");
        Objects.requireNonNull(head, "head");
    }
}
