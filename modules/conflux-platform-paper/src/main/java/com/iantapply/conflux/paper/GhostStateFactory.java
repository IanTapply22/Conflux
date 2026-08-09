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

final class GhostStateFactory {
    private GhostStateFactory() {}

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

    private static GhostEquipment equipment(EntityEquipment equipment) {
        return new GhostEquipment(
                encode(equipment.getItemInMainHand()),
                encode(equipment.getItemInOffHand()),
                encode(equipment.getBoots()),
                encode(equipment.getLeggings()),
                encode(equipment.getChestplate()),
                encode(equipment.getHelmet()));
    }

    private static String encode(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return "";
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }
}
