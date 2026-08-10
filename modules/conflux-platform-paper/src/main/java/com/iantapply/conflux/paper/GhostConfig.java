package com.iantapply.conflux.paper;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Validated settings controlling ghost publication, selection, and rendering.
 *
 * @param realmId logical network realm
 * @param updateRateHz number of state snapshots published per second
 * @param fullSnapshotIntervalSeconds seconds between recovery snapshots
 * @param selectionPeriodTicks ticks between nearby-player selection passes
 * @param viewRadiusBlocks maximum distance at which a ghost can be selected
 * @param maximumPerViewer maximum ghosts rendered to one viewer
 * @param maximumRemoteNodes maximum remote node states retained at once
 * @param staleAfterMilliseconds time without a frame before a remote node is discarded
 * @param maximumAnimationAgeMilliseconds oldest animation accepted for rendering
 * @param showEquipment whether equipment is included in published states
 * @param teleportThresholdBlocks movement distance that bypasses interpolation
 * @param tabListRemovalDelayTicks ticks before a spawned ghost leaves the player list
 */
record GhostConfig(
        String realmId,
        int updateRateHz,
        int fullSnapshotIntervalSeconds,
        int selectionPeriodTicks,
        double viewRadiusBlocks,
        int maximumPerViewer,
        int maximumRemoteNodes,
        long staleAfterMilliseconds,
        long maximumAnimationAgeMilliseconds,
        boolean showEquipment,
        double teleportThresholdBlocks,
        int tabListRemovalDelayTicks) {
    /**
     * Loads and validates ghost settings from a Bukkit configuration.
     *
     * @param source source configuration
     * @return validated ghost settings
     * @throws IllegalArgumentException when a setting is outside its supported range
     */
    static GhostConfig load(FileConfiguration source) {
        GhostConfig config = new GhostConfig(
                source.getString("ghosts.realm-id", "default"),
                source.getInt("ghosts.update-rate-hz", 10),
                source.getInt("ghosts.full-snapshot-interval-seconds", 5),
                source.getInt("ghosts.selection-period-ticks", 2),
                source.getDouble("ghosts.view-radius-blocks", 96),
                source.getInt("ghosts.maximum-per-viewer", 30),
                source.getInt("ghosts.maximum-remote-nodes", 128),
                source.getLong("ghosts.stale-after-milliseconds", 1500),
                source.getLong("ghosts.maximum-animation-age-milliseconds", 2000),
                source.getBoolean("ghosts.show-equipment", true),
                source.getDouble("ghosts.teleport-threshold-blocks", 16),
                source.getInt("ghosts.tab-list-removal-delay-ticks", 20));
        if (config.realmId == null || !config.realmId.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("ghosts.realm-id must contain 1-64 safe identifier characters");
        }
        if (config.updateRateHz < 1 || config.updateRateHz > 20) {
            throw new IllegalArgumentException("ghosts.update-rate-hz must be between 1 and 20");
        }
        if (config.viewRadiusBlocks < 1 || config.viewRadiusBlocks > 512) {
            throw new IllegalArgumentException("ghosts.view-radius-blocks must be between 1 and 512");
        }
        if (config.fullSnapshotIntervalSeconds < 1 || config.fullSnapshotIntervalSeconds > 300) {
            throw new IllegalArgumentException("ghosts.full-snapshot-interval-seconds must be between 1 and 300");
        }
        if (config.selectionPeriodTicks < 1 || config.selectionPeriodTicks > 20) {
            throw new IllegalArgumentException("ghosts.selection-period-ticks must be between 1 and 20");
        }
        if (config.maximumRemoteNodes < 1 || config.maximumRemoteNodes > 1000) {
            throw new IllegalArgumentException("ghosts.maximum-remote-nodes must be between 1 and 1000");
        }
        if (config.maximumPerViewer < 0 || config.maximumPerViewer > 200) {
            throw new IllegalArgumentException("ghosts.maximum-per-viewer must be between 0 and 200");
        }
        if (config.staleAfterMilliseconds < 500 || config.staleAfterMilliseconds > 30_000) {
            throw new IllegalArgumentException("ghosts.stale-after-milliseconds must be between 500 and 30000");
        }
        if (config.maximumAnimationAgeMilliseconds < 100 || config.maximumAnimationAgeMilliseconds > 30_000) {
            throw new IllegalArgumentException(
                    "ghosts.maximum-animation-age-milliseconds must be between 100 and 30000");
        }
        if (config.teleportThresholdBlocks < 1 || config.teleportThresholdBlocks > 512) {
            throw new IllegalArgumentException("ghosts.teleport-threshold-blocks must be between 1 and 512");
        }
        if (config.tabListRemovalDelayTicks < 1 || config.tabListRemovalDelayTicks > 100) {
            throw new IllegalArgumentException("ghosts.tab-list-removal-delay-ticks must be between 1 and 100");
        }
        return config;
    }

    /**
     * Converts the configured publication frequency to scheduler ticks.
     *
     * @return ticks between published frames
     */
    long publishPeriodTicks() {
        return Math.max(1, Math.round(20.0 / updateRateHz));
    }

    /**
     * Converts the recovery-snapshot interval to scheduler ticks.
     *
     * @return ticks between full recovery snapshots
     */
    long fullSnapshotPeriodTicks() {
        return fullSnapshotIntervalSeconds * 20L;
    }

    /**
     * Chooses interpolation duration from the effective state-selection cadence.
     *
     * @return interpolation duration in server ticks
     */
    int interpolationTicks() {
        return Math.max(Math.toIntExact(publishPeriodTicks()), selectionPeriodTicks);
    }
}
