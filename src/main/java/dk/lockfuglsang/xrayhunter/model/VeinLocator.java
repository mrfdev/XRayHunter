package dk.lockfuglsang.xrayhunter.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Material;
import org.jspecify.annotations.NonNull;

/**
 * Responsible for locating veins within mining data.
 */
public enum VeinLocator {
    ;
    private static final Collection<Material> IGNORE = Arrays.asList(Material.STONE, Material.IRON_ORE, Material.GOLD_ORE, Material.DEEPSLATE, Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE);

    public static @NonNull List<OreVein> getVeins(@NonNull List<CoreProtectAPI.ParseResult> data) {
        final List<OreVein> veins = new ArrayList<>();
        final Map<Material, OreVein> current = new HashMap<>();
        for (final CoreProtectAPI.ParseResult ore : data) {
            final Material mat = ore.getType();
            if (IGNORE.contains(mat)) {
                continue;
            }
            if (!current.containsKey(mat)) {
                current.put(mat, new OreVein(ore));
            } else {
                final OreVein existing = current.get(mat);
                if (existing.isValid(ore)) {
                    existing.add(ore);
                } else {
                    veins.add(existing);
                    current.put(mat, new OreVein(ore));
                }
            }
        }
        veins.addAll(current.values());
        veins.sort(new OreVeinComparator());
        return veins;
    }
}
