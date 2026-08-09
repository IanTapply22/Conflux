package com.iantapply.conflux.paper;

import com.iantapply.conflux.api.GhostAnimationType;
import com.iantapply.conflux.api.GhostEquipment;
import com.iantapply.conflux.api.GhostState;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** A client-only player entity. It is never inserted into the local world's entity list. */
final class PacketGhost {
    private static final int INTERPOLATION_TICKS = 2;

    private final Plugin plugin;
    private final Player viewer;
    private final ServerPlayer entity;
    private GhostState identity;
    private GhostEquipment equipment;
    private long targetSequence = -1;
    private double x;
    private double y;
    private double z;
    private double startX;
    private double startY;
    private double startZ;
    private double targetX;
    private double targetY;
    private double targetZ;
    private int interpolationTick = INTERPOLATION_TICKS;
    private boolean active = true;

    PacketGhost(Plugin plugin, Player viewer, GhostState state) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.identity = state;
        this.equipment = state.equipment();
        GameProfile profile = new GameProfile(state.playerId(), state.username());
        if (!state.skinValue().isEmpty()) {
            Property property = state.skinSignature().isEmpty()
                    ? new Property("textures", state.skinValue())
                    : new Property("textures", state.skinValue(), state.skinSignature());
            profile.properties().put("textures", property);
        }
        entity = new ServerPlayer(
                ((CraftServer) Bukkit.getServer()).getServer(),
                ((CraftWorld) viewer.getWorld()).getHandle(),
                profile,
                ClientInformation.createDefault());
        x = targetX = state.x();
        y = targetY = state.y();
        z = targetZ = state.z();
        applyState(state, true);
        spawn();
    }

    boolean sameIdentity(GhostState state) {
        return identity.username().equals(state.username())
                && identity.skinValue().equals(state.skinValue())
                && identity.skinSignature().equals(state.skinSignature());
    }

    void target(GhostState state, long sequence) {
        identity = state;
        if (sequence != targetSequence) {
            targetSequence = sequence;
            startX = x;
            startY = y;
            startZ = z;
            targetX = state.x();
            targetY = state.y();
            targetZ = state.z();
            interpolationTick = 0;
            applyState(state, false);
            if (!equipment.equals(state.equipment())) {
                equipment = state.equipment();
                sendEquipment();
            }
        }
    }

    void tick() {
        if (interpolationTick >= INTERPOLATION_TICKS) return;
        interpolationTick++;
        double progress = interpolationTick / (double) INTERPOLATION_TICKS;
        x = lerp(startX, targetX, progress);
        y = lerp(startY, targetY, progress);
        z = lerp(startZ, targetZ, progress);
        entity.setPos(x, y, z);
        send(ClientboundTeleportEntityPacket.teleport(
                entity.getId(), PositionMoveRotation.of(entity), Set.of(), identity.onGround()));
        send(new ClientboundRotateHeadPacket(entity, angle(identity.yaw())));
    }

    void animate(GhostAnimationType animation) {
        switch (animation) {
            case SWING_MAIN_HAND ->
                send(new ClientboundAnimatePacket(entity, ClientboundAnimatePacket.SWING_MAIN_HAND));
            case SWING_OFF_HAND -> send(new ClientboundAnimatePacket(entity, ClientboundAnimatePacket.SWING_OFF_HAND));
            case HURT -> send(new ClientboundEntityEventPacket(entity, (byte) 2));
        }
    }

    void destroy() {
        if (!active) return;
        active = false;
        send(new ClientboundRemoveEntitiesPacket(entity.getId()));
        send(new ClientboundPlayerInfoRemovePacket(List.of(entity.getUUID())));
    }

    private void spawn() {
        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                entity.getUUID(), entity.getGameProfile(), true, 0, GameType.SURVIVAL, null, true, 0, null);
        send(new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(
                        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                entry));
        send(new ClientboundAddEntityPacket(
                entity.getId(),
                entity.getUUID(),
                x,
                y,
                z,
                identity.pitch(),
                identity.yaw(),
                entity.getType(),
                0,
                Vec3.ZERO,
                identity.yaw()));
        var values = entity.getEntityData().packAll();
        if (values != null && !values.isEmpty()) {
            send(new ClientboundSetEntityDataPacket(entity.getId(), values));
        }
        sendEquipment();
        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (active) send(new ClientboundPlayerInfoRemovePacket(List.of(entity.getUUID())));
                        },
                        20L);
    }

    private void applyState(GhostState state, boolean initial) {
        entity.setPos(state.x(), state.y(), state.z());
        entity.setYRot(state.yaw());
        entity.setXRot(state.pitch());
        entity.setYHeadRot(state.yaw());
        entity.setOnGround(state.onGround());
        entity.setSprinting(state.sprinting());
        entity.setSwimming(state.swimming());
        entity.setShiftKeyDown(state.sneaking());
        entity.setPose(pose(state));
        if (!initial) {
            var dirty = entity.getEntityData().packDirty();
            if (dirty != null && !dirty.isEmpty()) send(new ClientboundSetEntityDataPacket(entity.getId(), dirty));
        }
    }

    private void sendEquipment() {
        List<Pair<EquipmentSlot, ItemStack>> slots = List.of(
                Pair.of(EquipmentSlot.MAINHAND, decode(equipment.mainHand())),
                Pair.of(EquipmentSlot.OFFHAND, decode(equipment.offHand())),
                Pair.of(EquipmentSlot.FEET, decode(equipment.feet())),
                Pair.of(EquipmentSlot.LEGS, decode(equipment.legs())),
                Pair.of(EquipmentSlot.CHEST, decode(equipment.chest())),
                Pair.of(EquipmentSlot.HEAD, decode(equipment.head())));
        send(new ClientboundSetEquipmentPacket(entity.getId(), slots));
    }

    private void send(Packet<?> packet) {
        if (viewer.isConnected()) ((CraftPlayer) viewer).getHandle().connection.send(packet);
    }

    private static ItemStack decode(String encoded) {
        if (encoded.isEmpty()) return ItemStack.EMPTY;
        try {
            org.bukkit.inventory.ItemStack item = org.bukkit.inventory.ItemStack.deserializeBytes(
                    Base64.getDecoder().decode(encoded));
            if (item.getType() == Material.AIR) return ItemStack.EMPTY;
            return CraftItemStack.asNMSCopy(item);
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }

    private static Pose pose(GhostState state) {
        if (state.gliding()) return Pose.FALL_FLYING;
        if (state.swimming()) return Pose.SWIMMING;
        if (state.sneaking()) return Pose.CROUCHING;
        return Pose.STANDING;
    }

    private static byte angle(float degrees) {
        return (byte) Math.floor(degrees * 256.0F / 360.0F);
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }
}
