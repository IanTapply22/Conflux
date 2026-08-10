package com.iantapply.conflux.api;

/** Short-lived visual actions sent separately from movement snapshots. */
public enum GhostAnimationType {
    /** Swings the player's main hand. */
    SWING_MAIN_HAND,

    /** Swings the player's offhand. */
    SWING_OFF_HAND,

    /** Plays the player's hurt animation. */
    HURT
}
