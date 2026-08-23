package com.onemb.cmiapi.xrayhunter.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.jspecify.annotations.NonNull;

/**
 * Responsible for locating veins within mining data.
 */
public enum VeinLocator {
    ;
    private static final Collection<Material> IGNORE = Arrays.asList(Material.STONE, Material.IRON_ORE, Material.GOLD_ORE, Material.DEEPSLATE, Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE);

    public static @NonNull List<OreVein> getVeins(@NonNull List<TrackedBlockEvent> data) {
        final Collector collector = collector();
        for (TrackedBlockEvent ore : data) {
            collector.accept(ore);
        }
        return collector.finish();
    }

    public static Collector collector() {
        return new Collector();
    }

    public static final class Collector {
        private final List<OreVein> veins = new ArrayList<>();
        private final Map<Material, OreVein> current = new HashMap<>();

        private Collector() {
        }

        public void accept(TrackedBlockEvent ore) {
            final Material mat = ore.type();
            if (IGNORE.contains(mat)) {
                return;
            }
            if (!current.containsKey(mat)) {
                current.put(mat, new OreVein(ore));
                return;
            }

            final OreVein existing = current.get(mat);
            if (existing.isValid(ore)) {
                existing.add(ore);
            } else {
                veins.add(existing);
                current.put(mat, new OreVein(ore));
            }
        }

        public @NonNull List<OreVein> finish() {
            final List<OreVein> completed = new ArrayList<>(veins);
            completed.addAll(current.values());
            completed.sort(new OreVeinComparator());
            return completed;
        }
    }
}
