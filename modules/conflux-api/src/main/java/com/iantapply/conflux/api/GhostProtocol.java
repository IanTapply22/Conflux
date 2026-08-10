package com.iantapply.conflux.api;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Shared limits and validation helpers for the Conflux wire protocol. */
public final class GhostProtocol {
    /** Current wire protocol understood by this release. */
    public static final int CURRENT_VERSION = 2;

    /** Maximum players accepted in a single node snapshot. */
    public static final int MAX_PLAYERS_PER_FRAME = 250;

    /** Maximum encoded size of one equipment slot. */
    public static final int MAX_EQUIPMENT_LENGTH = 262_144;

    /** Maximum encoded skin texture value length. */
    public static final int MAX_SKIN_VALUE_LENGTH = 32_768;

    /** Maximum encoded skin signature length. */
    public static final int MAX_SKIN_SIGNATURE_LENGTH = 4_096;

    /** Maximum combined encoded appearance characters accepted in one frame. */
    public static final int MAX_APPEARANCE_CHARACTERS_PER_FRAME = 64_000_000;

    /** Prevents construction of this protocol utility class. */
    private GhostProtocol() {}

    /**
     * Validates the fields common to every protocol message.
     *
     * @param protocolVersion claimed wire protocol version
     * @param realmId logical network realm
     * @param nodeId source node identifier
     * @param sessionId source process identifier
     * @param sequence session sequence number
     * @param sentAtEpochMilli source publication time
     */
    static void validateEnvelope(
            int protocolVersion, String realmId, String nodeId, UUID sessionId, long sequence, long sentAtEpochMilli) {
        if (protocolVersion != CURRENT_VERSION) throw new IllegalArgumentException("unsupported protocolVersion");
        validateIdentifier(realmId, "realmId");
        validateIdentifier(nodeId, "nodeId");
        if (sessionId == null) throw new NullPointerException("sessionId");
        if (sequence < 0 || sentAtEpochMilli < 0) throw new IllegalArgumentException("negative message value");
    }

    /**
     * Defensively copies a player list while enforcing size and uniqueness limits.
     *
     * @param players source player values
     * @param idExtractor function returning each player identifier
     * @param <T> player value type
     * @return validated immutable player list
     */
    static <T> List<T> copyPlayers(List<T> players, java.util.function.Function<T, UUID> idExtractor) {
        List<T> copy = List.copyOf(players);
        if (copy.size() > MAX_PLAYERS_PER_FRAME) throw new IllegalArgumentException("too many players");
        Set<UUID> identifiers = new HashSet<>();
        for (T player : copy) {
            UUID playerId = idExtractor.apply(player);
            if (!identifiers.add(playerId)) throw new IllegalArgumentException("duplicate playerId");
        }
        return copy;
    }

    /**
     * Validates the combined encoded size of an appearance frame.
     *
     * @param appearances appearance updates to measure
     */
    static void validateAppearancePayload(List<GhostAppearance> appearances) {
        long length =
                appearances.stream().mapToLong(GhostProtocol::appearanceLength).sum();
        if (length > MAX_APPEARANCE_CHARACTERS_PER_FRAME) {
            throw new IllegalArgumentException("appearance frame is too large");
        }
    }

    /**
     * Validates the combined encoded appearance size of a full state frame.
     *
     * @param states full player states to measure
     */
    static void validateStatePayload(List<GhostState> states) {
        long length = states.stream()
                .map(GhostState::appearance)
                .mapToLong(GhostProtocol::appearanceLength)
                .sum();
        if (length > MAX_APPEARANCE_CHARACTERS_PER_FRAME) {
            throw new IllegalArgumentException("full frame is too large");
        }
    }

    /**
     * Calculates the variable-length portion of one encoded appearance.
     *
     * @param appearance appearance to measure
     * @return number of variable payload characters
     */
    private static long appearanceLength(GhostAppearance appearance) {
        GhostEquipment equipment = appearance.equipment();
        return appearance.username().length()
                + appearance.skinValue().length()
                + appearance.skinSignature().length()
                + equipment.mainHand().length()
                + equipment.offHand().length()
                + equipment.feet().length()
                + equipment.legs().length()
                + equipment.chest().length()
                + equipment.head().length();
    }

    /**
     * Validates a realm or node identifier.
     *
     * @param value identifier value
     * @param name field name used in validation errors
     */
    static void validateIdentifier(String value, String name) {
        if (value == null) throw new NullPointerException(name);
        if (!value.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("invalid " + name);
    }

    /**
     * Normalizes a nullable string and enforces its maximum length.
     *
     * @param value possibly-null value
     * @param maximumLength maximum permitted character count
     * @param name field name used in validation errors
     * @return non-null bounded value
     */
    static String boundedNullable(String value, int maximumLength, String name) {
        String normalized = value == null ? "" : value;
        if (normalized.length() > maximumLength) throw new IllegalArgumentException(name + " is too long");
        return normalized;
    }
}
