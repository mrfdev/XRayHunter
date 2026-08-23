package com.onemb.cmiapi.xrayhunter.model;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.jspecify.annotations.NonNull;

/** A spatial group of related tracked block events. */
public class OreVein {
    private static final int MAX_DISTANCE = 5;
    private final Material type;
    private final List<TrackedBlockEvent> find = new ArrayList<>();
    private long totalX;
    private long totalY;
    private long totalZ;
    private int time;
    private String worldName;

    public OreVein(@NonNull TrackedBlockEvent ore) {
        type = ore.type();
        add(ore);
    }

    /** Adds the event when it belongs to this vein. */
    public void add(TrackedBlockEvent ore) {
        if (find.isEmpty()) {
            time = ore.timeSeconds();
            worldName = ore.worldName();
            append(ore);
        } else if (isValid(ore)) {
            append(ore);
        }
    }

    /** Returns whether the event is within five blocks of the current vein center. */
    public boolean isValid(TrackedBlockEvent ore) {
        if (worldName != null && worldName.equalsIgnoreCase(ore.worldName())) {
            final double size = getSize();
            final double dx = ore.x() - (totalX / size);
            final double dy = ore.y() - (totalY / size);
            final double dz = ore.z() - (totalZ / size);
            return (dx * dx) + (dy * dy) + (dz * dz) <= (MAX_DISTANCE * MAX_DISTANCE) && ore.type() == type;
        }
        return false;
    }

    /** Resolves the vein center to a Bukkit location; call only on the server thread. */
    public Location getLocation() {
        final double n = getSize();
        return new Location(Bukkit.getWorld(worldName), totalX / n, totalY / n, totalZ / n);
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

    private void append(TrackedBlockEvent ore) {
        find.add(ore);
        totalX += ore.x();
        totalY += ore.y();
        totalZ += ore.z();
    }
}
