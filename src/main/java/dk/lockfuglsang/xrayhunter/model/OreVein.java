package dk.lockfuglsang.xrayhunter.model;

import java.util.ArrayList;
import java.util.List;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;

/**
 * Data object for an ore-find
 */
public class OreVein {
    private static final int MAX_DISTANCE = 5;
    private final Material type;
    private final List<CoreProtectAPI.ParseResult> find = new ArrayList<>();
    private int time;
    private String worldName;

    public OreVein(CoreProtectAPI.ParseResult ore) {
        type = ore.getType();
        add(ore);
    }

    /**
     * Returns true, if the ore can be part of this find.
     *
     * @param ore
     * @return
     */
    @SuppressWarnings("deprecation")
    public boolean add(CoreProtectAPI.ParseResult ore) {
        if (find.isEmpty()) {
            time = ore.getTime();
            worldName = ore.worldName();
            find.add(ore);
            return true;
        } else if (isValid(ore)) {
            find.add(ore);
            return true;
        }
        return false;
    }

    /**
     * An ore is valid, if it's found within 5 blocks of the first ore.
     *
     * @param ore
     * @return
     */
    public boolean isValid(CoreProtectAPI.ParseResult ore) {
        if (worldName != null && worldName.equalsIgnoreCase(ore.worldName())) {
            final Location loc = new Location(Bukkit.getWorld(ore.worldName()), ore.getX(), ore.getY(), ore.getZ());
            if (loc.distance(getLocation()) <= MAX_DISTANCE) {
                return ore.getType() == type;
            }
        }
        return false;
    }

    public Location getLocation() {
        double x = 0;
        double y = 0;
        double z = 0;
        for (final CoreProtectAPI.ParseResult r : find) {
            x += r.getX();
            y += r.getY();
            z += r.getZ();
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
