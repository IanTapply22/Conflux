package com.iantapply.conflux.paper;

import com.iantapply.conflux.api.GhostEquipment;
import com.iantapply.conflux.api.GhostState;
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
     * Captures the visual state currently exposed by a player.
     *
     * @param player player to capture
     * @param showEquipment whether held and worn items should be serialized
     * @return immutable transport state for the player
     */
    static GhostState capture(Player player, boolean showEquipment) {
        var handle = ((CraftPlayer) player).getHandle();
        Property textures = handle.getGameProfile().properties().get("textures").stream()
                .findFirst()
                .orElse(null);
        var location = player.getLocation();
        return new GhostState(
                player.getUniqueId(),
                player.getName(),
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
                player.isGliding(),
                textures == null ? "" : textures.value(),
                textures == null || textures.signature() == null ? "" : textures.signature(),
                showEquipment ? equipment(player.getEquipment()) : GhostEquipment.EMPTY);
    }

    /**
     * Serializes all supported equipment slots.
     *
     * @param equipment Bukkit equipment to serialize
     * @return transport representation of the equipment
     */
    private static GhostEquipment equipment(EntityEquipment equipment) {
        return new GhostEquipment(
                encode(equipment.getItemInMainHand()),
                encode(equipment.getItemInOffHand()),
                encode(equipment.getBoots()),
                encode(equipment.getLeggings()),
                encode(equipment.getChestplate()),
                encode(equipment.getHelmet()));
    }

    /**
     * Encodes an item stack for transport.
     *
     * @param item stack to encode, possibly {@code null}
     * @return Base64 item data, or an empty string for an empty slot
     */
    private static String encode(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "";
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }
}
