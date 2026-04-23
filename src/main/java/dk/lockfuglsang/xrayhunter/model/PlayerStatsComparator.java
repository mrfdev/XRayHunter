package dk.lockfuglsang.xrayhunter.model;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.jspecify.annotations.NonNull;

/**
 * Comparator for ordering players by tracked material counts.
 */
public class PlayerStatsComparator implements Comparator<PlayerStats> {
    private static final Map<Material, String> MATERIAL_COLORS = Map.ofEntries(
            Map.entry(Material.ANCIENT_DEBRIS, "§c"),
            Map.entry(Material.DIAMOND_ORE, "§b"),
            Map.entry(Material.EMERALD_ORE, "§a"),
            Map.entry(Material.GOLD_ORE, "§e"),
            Map.entry(Material.NETHER_GOLD_ORE, "§6"),
            Map.entry(Material.GILDED_BLACKSTONE, "§6"),
            Map.entry(Material.IRON_ORE, "§f"),
            Map.entry(Material.RAW_IRON_BLOCK, "§7"),
            Map.entry(Material.COPPER_ORE, "§6"),
            Map.entry(Material.RAW_COPPER_BLOCK, "§6"),
            Map.entry(Material.LAPIS_ORE, "§9"),
            Map.entry(Material.REDSTONE_ORE, "§c"),
            Map.entry(Material.COAL_ORE, "§8"),
            Map.entry(Material.NETHER_QUARTZ_ORE, "§f"),
            Map.entry(Material.STONE, "§7"),
            Map.entry(Material.NETHERRACK, "§4")
    );

    private static final Map<Material, String> SHORT_LABELS = Map.ofEntries(
            Map.entry(Material.ANCIENT_DEBRIS, "ADB"),
            Map.entry(Material.DIAMOND_ORE, "DIA"),
            Map.entry(Material.EMERALD_ORE, "EME"),
            Map.entry(Material.GOLD_ORE, "GOL"),
            Map.entry(Material.NETHER_GOLD_ORE, "NGO"),
            Map.entry(Material.GILDED_BLACKSTONE, "GIL"),
            Map.entry(Material.IRON_ORE, "IRO"),
            Map.entry(Material.RAW_IRON_BLOCK, "RIB"),
            Map.entry(Material.COPPER_ORE, "COP"),
            Map.entry(Material.RAW_COPPER_BLOCK, "RCB"),
            Map.entry(Material.LAPIS_ORE, "LAP"),
            Map.entry(Material.REDSTONE_ORE, "RED"),
            Map.entry(Material.COAL_ORE, "COA"),
            Map.entry(Material.NETHER_QUARTZ_ORE, "NQZ"),
            Map.entry(Material.STONE, "STO"),
            Map.entry(Material.NETHERRACK, "NTR")
    );

    private final List<Material> materials;

    public PlayerStatsComparator(@NonNull List<Material> materials) {
        this.materials = List.copyOf(materials);
    }

    public static Material normalize(Material material) {
        return switch (material) {
            case DEEPSLATE_DIAMOND_ORE -> Material.DIAMOND_ORE;
            case DEEPSLATE_EMERALD_ORE -> Material.EMERALD_ORE;
            case DEEPSLATE_GOLD_ORE -> Material.GOLD_ORE;
            case DEEPSLATE_IRON_ORE -> Material.IRON_ORE;
            case DEEPSLATE_COPPER_ORE -> Material.COPPER_ORE;
            case DEEPSLATE_LAPIS_ORE -> Material.LAPIS_ORE;
            case DEEPSLATE_REDSTONE_ORE -> Material.REDSTONE_ORE;
            case DEEPSLATE_COAL_ORE -> Material.COAL_ORE;
            case DEEPSLATE -> Material.STONE;
            default -> material;
        };
    }

    public static @NonNull String getColor(Material material) {
        return MATERIAL_COLORS.getOrDefault(normalize(material), "");
    }

    public static @NonNull String getShortLabel(Material material) {
        return SHORT_LABELS.getOrDefault(normalize(material), normalize(material).name());
    }

    @Override
    public int compare(PlayerStats left, PlayerStats right) {
        int comparison = 0;
        for (Material material : materials) {
            comparison = right.getCount(material) - left.getCount(material);
            if (comparison != 0) {
                return comparison;
            }
        }
        return comparison;
    }
}
