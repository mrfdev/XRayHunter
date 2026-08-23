package com.onemb.cmiapi.xrayhunter.model;

import org.bukkit.Material;

/** Lightweight tracked block event sourced from authoritative mining history. */
public record TrackedBlockEvent(
        String player,
        String worldName,
        int x,
        int y,
        int z,
        int timeSeconds,
        Material type
) {
}
