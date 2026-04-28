package dk.lockfuglsang.xrayhunter;

import dk.lockfuglsang.util.TimeUtil;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Runtime configuration resolved from config.yml.
 */
public record PluginSettings(
        boolean startupSelfCheckEnabled,
        String defaultLookupTime,
        int topResults,
        int detailPageSize,
        boolean consoleAllowServerWideLookups,
        boolean consoleHighValueOnly,
        String consoleMaxAllWorldLookupTime,
        long consoleMaxAllWorldLookupMillis,
        List<Material> consoleHighValueDisplayMaterials,
        List<String> excludedPlayers,
        List<Material> overworldLookupMaterials,
        List<Material> overworldDisplayMaterials,
        List<Material> netherLookupMaterials,
        List<Material> netherDisplayMaterials
) {
    private static final List<String> LOGICAL_CONFIG_KEYS = List.of(
            "startup.self-check-enabled",
            "defaults.lookup-time",
            "display.top-results",
            "display.detail-page-size",
            "console.allow-server-wide-lookups",
            "console.high-value-only",
            "console.max-all-world-lookup-time"
    );

    private static final List<Material> DEFAULT_CONSOLE_HIGH_VALUE_DISPLAY_MATERIALS = List.of(
            Material.ANCIENT_DEBRIS,
            Material.DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.GOLD_ORE,
            Material.GILDED_BLACKSTONE,
            Material.NETHER_GOLD_ORE,
            Material.LAPIS_ORE,
            Material.REDSTONE_ORE
    );

    private static final List<Material> DEFAULT_OVERWORLD_LOOKUP_MATERIALS = List.of(
            Material.DIAMOND_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.DEEPSLATE_EMERALD_ORE,
            Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.IRON_ORE,
            Material.DEEPSLATE_IRON_ORE,
            Material.RAW_IRON_BLOCK,
            Material.COPPER_ORE,
            Material.DEEPSLATE_COPPER_ORE,
            Material.RAW_COPPER_BLOCK,
            Material.LAPIS_ORE,
            Material.DEEPSLATE_LAPIS_ORE,
            Material.REDSTONE_ORE,
            Material.DEEPSLATE_REDSTONE_ORE,
            Material.COAL_ORE,
            Material.DEEPSLATE_COAL_ORE,
            Material.STONE,
            Material.DEEPSLATE
    );

    private static final List<Material> DEFAULT_OVERWORLD_DISPLAY_MATERIALS = List.of(
            Material.DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.GOLD_ORE,
            Material.IRON_ORE,
            Material.RAW_IRON_BLOCK,
            Material.COPPER_ORE,
            Material.RAW_COPPER_BLOCK,
            Material.LAPIS_ORE,
            Material.REDSTONE_ORE,
            Material.COAL_ORE,
            Material.STONE
    );

    private static final List<Material> DEFAULT_NETHER_LOOKUP_MATERIALS = List.of(
            Material.ANCIENT_DEBRIS,
            Material.GILDED_BLACKSTONE,
            Material.NETHER_GOLD_ORE,
            Material.NETHER_QUARTZ_ORE,
            Material.NETHERRACK
    );

    private static final List<Material> DEFAULT_NETHER_DISPLAY_MATERIALS = List.of(
            Material.ANCIENT_DEBRIS,
            Material.GILDED_BLACKSTONE,
            Material.NETHER_GOLD_ORE,
            Material.NETHER_QUARTZ_ORE,
            Material.NETHERRACK
    );

    public static PluginSettings load(XRayHunter plugin, FileConfiguration configuration) {
        final String consoleMaxLookupTime = resolveConsoleMaxLookupTime(configuration.getString("console.max-all-world-lookup-time", "30d"));
        return new PluginSettings(
                configuration.getBoolean("startup.self-check-enabled", true),
                configuration.getString("defaults.lookup-time", "2d"),
                Math.max(1, configuration.getInt("display.top-results", 10)),
                Math.max(1, configuration.getInt("display.detail-page-size", 10)),
                configuration.getBoolean("console.allow-server-wide-lookups", true),
                configuration.getBoolean("console.high-value-only", true),
                consoleMaxLookupTime,
                TimeUtil.millisFromString(consoleMaxLookupTime),
                parseMaterialList(plugin, configuration, "console.high-value-display-materials", DEFAULT_CONSOLE_HIGH_VALUE_DISPLAY_MATERIALS),
                parsePlayerList(configuration, "filters.excluded-players"),
                parseMaterialList(plugin, configuration, "tracking.overworld.lookup-materials", DEFAULT_OVERWORLD_LOOKUP_MATERIALS),
                parseMaterialList(plugin, configuration, "tracking.overworld.display-materials", DEFAULT_OVERWORLD_DISPLAY_MATERIALS),
                parseMaterialList(plugin, configuration, "tracking.nether.lookup-materials", DEFAULT_NETHER_LOOKUP_MATERIALS),
                parseMaterialList(plugin, configuration, "tracking.nether.display-materials", DEFAULT_NETHER_DISPLAY_MATERIALS)
        );
    }

    public List<Material> getLookupMaterials(World.Environment environment) {
        return environment == World.Environment.NETHER ? netherLookupMaterials : overworldLookupMaterials;
    }

    public List<Material> getDisplayMaterials(World.Environment environment) {
        return environment == World.Environment.NETHER ? netherDisplayMaterials : overworldDisplayMaterials;
    }

    public static List<String> logicalConfigKeys() {
        return LOGICAL_CONFIG_KEYS;
    }

    private static String resolveConsoleMaxLookupTime(String configuredValue) {
        final String normalized = configuredValue == null ? "30d" : configuredValue.trim();
        return TimeUtil.millisFromString(normalized) > 0 ? normalized : "30d";
    }

    private static List<Material> parseMaterialList(
            XRayHunter plugin,
            FileConfiguration configuration,
            String path,
            List<Material> defaults
    ) {
        final List<String> configuredNames = configuration.getStringList(path);
        if (configuredNames.isEmpty()) {
            return defaults;
        }

        final Set<Material> materials = new LinkedHashSet<>();
        for (String configuredName : configuredNames) {
            if (configuredName == null || configuredName.isBlank()) {
                continue;
            }

            final Material material = Material.matchMaterial(configuredName.trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                plugin.getLogger().warning("Ignoring unknown material in " + path + ": " + configuredName);
                continue;
            }
            materials.add(material);
        }

        if (materials.isEmpty()) {
            plugin.getLogger().warning("No valid materials found in " + path + ", using defaults instead.");
            return defaults;
        }

        return List.copyOf(new ArrayList<>(materials));
    }

    private static List<String> parsePlayerList(FileConfiguration configuration, String path) {
        final List<String> configuredNames = configuration.getStringList(path);
        if (configuredNames.isEmpty()) {
            return List.of();
        }

        final Set<String> players = new LinkedHashSet<>();
        for (String configuredName : configuredNames) {
            if (configuredName == null || configuredName.isBlank()) {
                continue;
            }
            players.add(configuredName.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(new ArrayList<>(players));
    }
}
