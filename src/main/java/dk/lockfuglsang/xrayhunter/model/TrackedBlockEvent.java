package dk.lockfuglsang.xrayhunter.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jspecify.annotations.Nullable;

/**
 * Lightweight tracked block break event sourced from CoreProtect data.
 */
public record TrackedBlockEvent(
        String player,
        String worldName,
        int x,
        int y,
        int z,
        int timeSeconds,
        Material type
) {
    public String blockKey() {
        return worldName + ":" + x + "," + y + "," + z;
    }

    public long timeMillis() {
        return timeSeconds * 1000L;
    }

    public @Nullable Location location() {
        final World world = Bukkit.getWorld(worldName);
        return new Location(world, x, y, z);
    }
}
