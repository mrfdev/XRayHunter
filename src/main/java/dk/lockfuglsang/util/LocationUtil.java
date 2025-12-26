package dk.lockfuglsang.util;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import static dk.lockfuglsang.util.BlockUtil.isBreathable;

/**
 * Responsible for various transformations of locations.
 */
public enum LocationUtil {
    ;

    public static String asString(Location loc) {
        if (loc == null) {
            return null;
        }
        String s = "";
        if (loc.getWorld() != null) {
            loc.getWorld().getName();
            s += loc.getWorld().getName() + ":";
        }
        s += String.format("%5.2f,%5.2f,%5.2f", loc.getX(), loc.getY(), loc.getZ());
        if (loc.getYaw() != 0f || loc.getPitch() != 0f) {
            s += String.format(":%3.2f:%3.2f", loc.getYaw(), loc.getPitch());
        }
        return s;
    }

    public static String asShortString(Location loc) {
        if (loc == null) {
            return null;
        }
        String s = "";
        if (loc.getWorld() != null) {
            loc.getWorld().getName();
            s += loc.getWorld().getName() + ":";
        }
        s += String.format("%1.0f,%1.0f,%1.0f", loc.getX(), loc.getY(), loc.getZ());
        if (loc.getYaw() != 0f || loc.getPitch() != 0f) {
            s += String.format(":%1.0f:%1.0f", loc.getYaw(), loc.getPitch());
        }
        return s;
    }

    public static @Nullable Location findSafeLocation(@NonNull Location loc, int radius) {
        final int px = loc.getBlockX();
        final int py = loc.getBlockY();
        final int pz = loc.getBlockZ();
        final Set<ChunkSnapshot> chunks = new HashSet<>();
        ChunkSnapshot chunk = loc.getWorld().getChunkAt(loc).getChunkSnapshot();
        chunks.add(chunk);
        for (int y = 1; y <= radius * 2; y++) {
            final int cy = py + ((y % 2) == 0 ? -y / 2 : y / 2);
            for (int x = 1; x <= radius * 2; x++) {
                final int cx = px + ((x % 2) == 0 ? -x / 2 : x / 2);
                for (int z = 1; z <= radius * 2; z++) {
                    final int cz = pz + ((z % 2) == 0 ? -z / 2 : z / 2);
                    chunk = getSnapshot(loc.getWorld(), chunks, cx >> 4, cz >> 4);
                    if (isSafeLocation(cx, cy, cz, chunk)) {
                        return new Location(loc.getWorld(), cx, cy, cz);
                    }
                }
            }
        }
        return null;
    }

    private static @NonNull ChunkSnapshot getSnapshot(World world, @NonNull Set<ChunkSnapshot> chunks, int x, int z) {
        for (final ChunkSnapshot chunk : chunks) {
            if (chunk.getX() == x && chunk.getZ() == z) {
                return chunk;
            }
        }
        final ChunkSnapshot snapshot = world.getChunkAt(x, z).getChunkSnapshot();
        chunks.add(snapshot);
        return snapshot;
    }

    private static boolean isSafeLocation(int cx, int dy, int cz, @NonNull ChunkSnapshot chunk) {
        final int dx = cx < 0 ? ((cx % 16) + 16) % 16 : cx % 16;
        final int dz = cz < 0 ? ((cz % 16) + 16) % 16 : cz % 16;
        final Material ground = chunk.getBlockType(dx, dy - 1, dz);
        final Material air1 = chunk.getBlockType(dx, dy, dz);
        final Material air2 = chunk.getBlockType(dx, dy + 1, dz);
        return ground.isSolid() && isBreathable(air1) && isBreathable(air2);
    }
}
