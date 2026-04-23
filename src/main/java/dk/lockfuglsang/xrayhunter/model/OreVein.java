package dk.lockfuglsang.xrayhunter.model;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.jspecify.annotations.NonNull;

/**
 * Data object for an ore-find
 */
@SuppressWarnings("JavadocDeclaration")
public class OreVein {
    private static final int MAX_DISTANCE = 5;
    private final Material type;
    private final List<TrackedBlockEvent> find = new ArrayList<>();
    private int time;
    private String worldName;

    public OreVein(@NonNull TrackedBlockEvent ore) {
        type = ore.type();
        add(ore);
    }

    /**
     * Returns true, if the ore can be part of this find.
     *
     * @param ore
     */
    @SuppressWarnings("deprecation")
    public void add(TrackedBlockEvent ore) {
        if (find.isEmpty()) {
            time = ore.timeSeconds();
            worldName = ore.worldName();
            find.add(ore);
        } else if (isValid(ore)) {
            find.add(ore);
        }
    }

    /**
     * An ore is valid, if it's found within 5 blocks of the first ore.
     *
     * @param ore
     * @return
     */
    public boolean isValid(TrackedBlockEvent ore) {
        if (worldName != null && worldName.equalsIgnoreCase(ore.worldName())) {
            final Location loc = ore.location();
            final Location current = getLocation();
            final double dx = loc.getX() - current.getX();
            final double dy = loc.getY() - current.getY();
            final double dz = loc.getZ() - current.getZ();
            return (dx * dx) + (dy * dy) + (dz * dz) <= (MAX_DISTANCE * MAX_DISTANCE) && ore.type() == type;
        }
        return false;
    }

    public Location getLocation() {
        double x = 0;
        double y = 0;
        double z = 0;
        for (final TrackedBlockEvent r : find) {
            x += r.x();
            y += r.y();
            z += r.z();
        }
        final double n = getSize();
        return new Location(Bukkit.getWorld(worldName), x / n, y / n, z / n);
    }

    public int getSize() {
        return find.size();
    }

    public Material getType() {
        return type;
    }

    public long getTime() {
        return time * 1000L;
    }
}
