package com.iantapply.conflux.paper;

import com.iantapply.conflux.api.GhostAppearance;
import com.iantapply.conflux.api.GhostEquipment;
import com.iantapply.conflux.api.GhostMovement;
import com.iantapply.conflux.api.GhostProtocol;
import com.mojang.authlib.properties.Property;
import java.util.Base64;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/** Captures Bukkit player state in transport-safe Conflux records. */
final class GhostStateFactory {
    /** Prevents construction of this utility class. */
    private GhostStateFactory() {}

    /**
     * Captures the frequently changing visual state exposed by a player.
     *
     * @param player player to capture
     * @return immutable movement state for the player
     */
    static GhostMovement captureMovement(Player player) {
        var handle = ((CraftPlayer) player).getHandle();
        var location = player.getLocation();
        return new GhostMovement(
                player.getUniqueId(),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                handle.onGround(),
                player.isSneaking(),
                player.isSprinting(),
                player.isSwimming(),
                player.isGliding());
    }

    /**
     * Captures cheap-to-compare identity, skin, and equipment values.
     *
     * @param player player to capture
     * @param showEquipment whether held and worn items should be included
     * @return appearance fingerprint used by the publication cache
     */
    static AppearanceFingerprint captureAppearanceFingerprint(Player player, boolean showEquipment) {
        var handle = ((CraftPlayer) player).getHandle();
        Property textures = handle.getGameProfile().properties().get("textures").stream()
                .findFirst()
                .orElse(null);
        EntityEquipment equipment = player.getEquipment();
        return new AppearanceFingerprint(
                player.getUniqueId(),
                player.getName(),
                textures == null ? "" : textures.value(),
                textures == null || textures.signature() == null ? "" : textures.signature(),
                showEquipment ? copy(equipment.getItemInMainHand()) : empty(),
                showEquipment ? copy(equipment.getItemInOffHand()) : empty(),
                showEquipment ? copy(equipment.getBoots()) : empty(),
                showEquipment ? copy(equipment.getLeggings()) : empty(),
                showEquipment ? copy(equipment.getChestplate()) : empty(),
                showEquipment ? copy(equipment.getHelmet()) : empty());
    }

    /**
     * Converts a changed appearance fingerprint to its bounded wire representation.
     *
     * @param fingerprint raw appearance fingerprint
     * @return encoded appearance update
     */
    static GhostAppearance encodeAppearance(AppearanceFingerprint fingerprint) {
        String skinValue = fingerprint.skinValue();
        String skinSignature = fingerprint.skinSignature();
        if (skinValue.length() > GhostProtocol.MAX_SKIN_VALUE_LENGTH
                || skinSignature.length() > GhostProtocol.MAX_SKIN_SIGNATURE_LENGTH) {
            skinValue = "";
            skinSignature = "";
        }
        return new GhostAppearance(
                fingerprint.playerId(),
                fingerprint.username(),
                skinValue,
                skinSignature,
                new GhostEquipment(
                        encode(fingerprint.mainHand()),
                        encode(fingerprint.offHand()),
                        encode(fingerprint.feet()),
                        encode(fingerprint.legs()),
                        encode(fingerprint.chest()),
                        encode(fingerprint.head())));
    }

    /**
     * Copies an item stack so later Bukkit mutations cannot alter a cached fingerprint.
     *
     * @param item source item stack
     * @return independent stack, or an empty stack for an empty slot
     */
    private static ItemStack copy(ItemStack item) {
        return item == null || item.getType() == Material.AIR ? empty() : item.clone();
    }

    /**
     * Creates a canonical empty item stack for appearance comparisons.
     *
     * @return empty Bukkit item stack
     */
    private static ItemStack empty() {
        return new ItemStack(Material.AIR);
    }

    /**
     * Encodes an item stack for transport.
     *
     * @param item stack to encode, possibly {@code null}
     * @return Base64 item data, or an empty string for an empty slot
     */
    private static String encode(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "";
        String encoded = Base64.getEncoder().encodeToString(item.serializeAsBytes());
        return encoded.length() <= GhostProtocol.MAX_EQUIPMENT_LENGTH ? encoded : "";
    }

    /**
     * Immutable raw values used to detect appearance changes before serialization.
     *
     * @param playerId player identifier
     * @param username current Minecraft username
     * @param skinValue profile texture value
     * @param skinSignature profile texture signature
     * @param mainHand main-hand item
     * @param offHand off-hand item
     * @param feet boots item
     * @param legs leggings item
     * @param chest chestplate item
     * @param head helmet item
     */
    record AppearanceFingerprint(
            java.util.UUID playerId,
            String username,
            String skinValue,
            String skinSignature,
            ItemStack mainHand,
            ItemStack offHand,
            ItemStack feet,
            ItemStack legs,
            ItemStack chest,
            ItemStack head) {}
}
