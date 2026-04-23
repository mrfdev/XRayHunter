package dk.lockfuglsang.xrayhunter.model;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.jspecify.annotations.NonNull;

/**
 * Data object for holding the player stats.
 */
public class PlayerStats {
    private final String player;
    private final int total;
    private final int comparisonTotal;
    private final Map<Material, Integer> blockCount = new HashMap<>();

    public PlayerStats(String player, @NonNull Map<Material, Integer> map) {
        this(player, map, -1);
    }

    public PlayerStats(String player, @NonNull Map<Material, Integer> map, int comparisonTotal) {
        this.player = player;
        int sum = 0;
        for (Map.Entry<Material, Integer> entry : map.entrySet()) {
            int val = entry.getValue();
            blockCount.put(entry.getKey(), val);
            sum += val;
        }
        total = sum;
        this.comparisonTotal = comparisonTotal > 0 ? Math.max(sum, comparisonTotal) : sum;
    }

    public String getPlayer() {
        return player;
    }

    public int getCount(Material mat) {
        if (blockCount.containsKey(mat)) {
            return blockCount.get(mat);
        }
        return 0;
    }

    public int getTotal() {
        return total;
    }

    public int getComparisonTotal() {
        return comparisonTotal;
    }

    public float getRatio(Material mat) {
        return comparisonTotal == 0 ? 0.0F : getCount(mat) / (float) comparisonTotal;
    }
}
