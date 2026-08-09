package com.iantapply.conflux.paper;

import org.bukkit.configuration.file.FileConfiguration;

record GhostConfig(
        int updateRateHz,
        double viewRadiusBlocks,
        int maximumPerViewer,
        long staleAfterMilliseconds,
        boolean showEquipment) {
    static GhostConfig load(FileConfiguration source) {
        GhostConfig config = new GhostConfig(
                source.getInt("ghosts.update-rate-hz", 10),
                source.getDouble("ghosts.view-radius-blocks", 96),
                source.getInt("ghosts.maximum-per-viewer", 30),
                source.getLong("ghosts.stale-after-milliseconds", 1500),
                source.getBoolean("ghosts.show-equipment", true));
        if (config.updateRateHz < 1 || config.updateRateHz > 20) {
            throw new IllegalArgumentException("ghosts.update-rate-hz must be between 1 and 20");
        }
        if (config.viewRadiusBlocks < 1 || config.viewRadiusBlocks > 512) {
            throw new IllegalArgumentException("ghosts.view-radius-blocks must be between 1 and 512");
        }
        if (config.maximumPerViewer < 0 || config.maximumPerViewer > 200) {
            throw new IllegalArgumentException("ghosts.maximum-per-viewer must be between 0 and 200");
        }
        if (config.staleAfterMilliseconds < 500 || config.staleAfterMilliseconds > 30_000) {
            throw new IllegalArgumentException("ghosts.stale-after-milliseconds must be between 500 and 30000");
        }
        return config;
    }

    long publishPeriodTicks() {
        return Math.max(1, Math.round(20.0 / updateRateHz));
    }
}
