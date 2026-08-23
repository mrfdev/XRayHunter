package com.onemb.cmiapi.xrayhunter.command;

import com.onemb.cmiapi.xrayhunter.model.PlayerStats;
import com.onemb.cmiapi.xrayhunter.model.PlayerStatsComparator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/**
 * Renders lookup output as a pastel fixed-width table that stays readable in console.
 */
public final class LookupReportFormatter {
    private static final TextColor TITLE_COLOR = color("#f6bd60");
    private static final TextColor LABEL_COLOR = color("#84a59d");
    private static final TextColor VALUE_COLOR = color("#f7ede2");
    private static final TextColor SEPARATOR_COLOR = color("#6c7a89");
    private static final TextColor RANK_COLOR = color("#cdb4db");
    private static final TextColor PLAYER_COLOR = color("#bde0fe");
    private static final TextColor BASE_COLOR = color("#c9c2b8");
    private static final TextColor ORE_RATIO_COLOR = color("#b8f2e6");
    private static final TextColor TOTAL_COLOR = color("#d0f4de");
    private static final TextColor EMPTY_COLOR = color("#7c8a9a");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private LookupReportFormatter() {
    }

    public static List<Component> buildReport(
            String scopeLabel,
            String windowLabel,
            String viewLabel,
            long latestTrackedTimeSeconds,
            List<PlayerStats> topPlayers,
            List<Material> displayMaterials,
            int topResults
    ) {
        final List<PlayerStats> visiblePlayers = topPlayers.subList(0, Math.min(topPlayers.size(), topResults));
        final List<Material> visibleMaterials = getVisibleMaterials(displayMaterials, topPlayers);
        final boolean showBaseColumn = hasVisibleBase(topPlayers);
        final boolean usesComparisonTotals = usesComparisonTotals(visiblePlayers);
        final int playerWidth = Math.max(12, Math.min(20, longestPlayerWidth(visiblePlayers)));
        final int materialWidth = 7;
        final int oreRatioWidth = 6;
        final int baseWidth = 8;
        final int totalWidth = 8;
        final int shownResults = visiblePlayers.size();

        final List<Component> lines = new ArrayList<>();
        lines.add(Component.empty()
                .append(Component.text("1MB-XRayHunter Lookup", TITLE_COLOR))
                .append(Component.text("  ", SEPARATOR_COLOR))
                .append(metaPair("Scope", scopeLabel))
                .append(Component.text("  ", SEPARATOR_COLOR))
                .append(metaPair("Window", windowLabel))
                .append(Component.text("  ", SEPARATOR_COLOR))
                .append(metaPair("View", viewLabel))
                .append(Component.text("  ", SEPARATOR_COLOR))
                .append(metaPair("Shown", Integer.toString(shownResults))));

        if (latestTrackedTimeSeconds > 0) {
            lines.add(metaPair("Data through", formatInstant(latestTrackedTimeSeconds)));
        }
        if (usesComparisonTotals) {
            lines.add(metaPair("ORE% basis", "shown ores vs broader tracked totals for the shown players"));
        }

        lines.addAll(buildLegendLines(visibleMaterials, showBaseColumn));
        lines.add(buildSeparator(playerWidth, visibleMaterials.size(), materialWidth, oreRatioWidth, baseWidth, totalWidth, showBaseColumn));
        lines.add(buildHeader(
                playerWidth,
                visibleMaterials,
                materialWidth,
                oreRatioWidth,
                baseWidth,
                totalWidth,
                showBaseColumn,
                usesComparisonTotals ? "Shown" : "Total"
        ));
        lines.add(buildSeparator(playerWidth, visibleMaterials.size(), materialWidth, oreRatioWidth, baseWidth, totalWidth, showBaseColumn));

        int rank = 1;
        for (PlayerStats playerStats : visiblePlayers) {
            lines.add(buildRow(rank++, playerStats, playerWidth, visibleMaterials, materialWidth, oreRatioWidth, baseWidth, totalWidth, showBaseColumn));
        }

        if (topPlayers.size() > shownResults) {
            lines.add(metaPair("Hidden rows", Integer.toString(topPlayers.size() - shownResults)));
        }

        return lines;
    }

    private static List<Component> buildLegendLines(List<Material> visibleMaterials, boolean showBaseColumn) {
        final List<Component> lines = new ArrayList<>();
        Component currentLine = Component.empty().append(Component.text("Legend", LABEL_COLOR)).append(Component.text(": ", SEPARATOR_COLOR));
        int currentLength = 8;

        for (int index = 0; index < visibleMaterials.size(); index++) {
            final Material material = visibleMaterials.get(index);
            final String token = PlayerStatsComparator.getShortLabel(material) + "=" + friendlyName(material);
            final int tokenLength = token.length() + (index > 0 ? 3 : 0);
            if (currentLength + tokenLength > 120 && currentLength > 8) {
                lines.add(currentLine);
                currentLine = Component.empty().append(Component.text("Legend", LABEL_COLOR)).append(Component.text(": ", SEPARATOR_COLOR));
                currentLength = 8;
            }
            if (currentLength > 8) {
                currentLine = currentLine.append(Component.text(" | ", SEPARATOR_COLOR));
                currentLength += 3;
            }
            currentLine = currentLine
                    .append(Component.text(PlayerStatsComparator.getShortLabel(material), colorFor(material)))
                    .append(Component.text("=", SEPARATOR_COLOR))
                    .append(Component.text(friendlyName(material), VALUE_COLOR));
            currentLength += token.length();
        }

        if (showBaseColumn) {
            if (currentLength > 8) {
                currentLine = currentLine.append(Component.text(" | ", SEPARATOR_COLOR));
            }
            currentLine = currentLine
                    .append(Component.text("BASE", BASE_COLOR))
                    .append(Component.text("=", SEPARATOR_COLOR))
                    .append(Component.text("stone+netherrack", VALUE_COLOR));
        }

        lines.add(currentLine);
        return lines;
    }

    private static Component buildHeader(
            int playerWidth,
            List<Material> visibleMaterials,
            int materialWidth,
            int oreRatioWidth,
            int baseWidth,
            int totalWidth,
            boolean showBaseColumn,
            String totalLabel
    ) {
        Component line = Component.empty()
                .append(headerCell("#", 4, RANK_COLOR))
                .append(separator())
                .append(headerCell("Player", playerWidth, PLAYER_COLOR));
        for (Material material : visibleMaterials) {
            line = line.append(separator())
                    .append(headerCell(PlayerStatsComparator.getShortLabel(material), materialWidth, colorFor(material)));
        }
        line = line.append(separator()).append(headerCell("ORE%", oreRatioWidth, ORE_RATIO_COLOR));
        if (showBaseColumn) {
            line = line.append(separator()).append(headerCell("BASE", baseWidth, BASE_COLOR));
        }
        return line.append(separator()).append(headerCell(totalLabel, totalWidth, TOTAL_COLOR));
    }

    private static Component buildRow(
            int rank,
            PlayerStats playerStats,
            int playerWidth,
            List<Material> visibleMaterials,
            int materialWidth,
            int oreRatioWidth,
            int baseWidth,
            int totalWidth,
            boolean showBaseColumn
    ) {
        Component line = Component.empty()
                .append(valueCell(Integer.toString(rank), 4, RANK_COLOR, true))
                .append(separator())
                .append(valueCell(playerStats.getPlayer(), playerWidth, PLAYER_COLOR, false));

        for (Material material : visibleMaterials) {
            line = line.append(separator())
                    .append(valueCell(formatCompactCount(playerStats.getCount(material)), materialWidth, colorFor(material), true));
        }

        line = line.append(separator())
                .append(valueCell(formatOreRatio(playerStats, visibleMaterials), oreRatioWidth, ORE_RATIO_COLOR, true));
        if (showBaseColumn) {
            line = line.append(separator())
                    .append(valueCell(formatCompactCount(baseCount(playerStats)), baseWidth, BASE_COLOR, true));
        }
        return line.append(separator())
                .append(valueCell(formatCompactCount(playerStats.getTotal()), totalWidth, TOTAL_COLOR, true));
    }

    private static Component buildSeparator(
            int playerWidth,
            int materialColumns,
            int materialWidth,
            int oreRatioWidth,
            int baseWidth,
            int totalWidth,
            boolean showBaseColumn
    ) {
        int totalLength = 4 + 3 + playerWidth + (materialColumns * (3 + materialWidth)) + 3 + oreRatioWidth + 3 + totalWidth;
        if (showBaseColumn) {
            totalLength += 3 + baseWidth;
        }
        return Component.text("-".repeat(Math.max(totalLength, 16)), SEPARATOR_COLOR);
    }

    private static Component metaPair(String label, String value) {
        return Component.empty()
                .append(Component.text(label, LABEL_COLOR))
                .append(Component.text(": ", SEPARATOR_COLOR))
                .append(Component.text(value, VALUE_COLOR));
    }

    private static Component headerCell(String value, int width, TextColor color) {
        return Component.text(pad(value, width, false), color);
    }

    private static Component valueCell(String value, int width, TextColor color, boolean alignRight) {
        final String rendered = value == null || value.isBlank() ? "-" : value;
        final TextColor effectiveColor = "-".equals(rendered) ? EMPTY_COLOR : color;
        return Component.text(pad(rendered, width, alignRight), effectiveColor);
    }

    private static Component separator() {
        return Component.text(" | ", SEPARATOR_COLOR);
    }

    private static List<Material> getVisibleMaterials(List<Material> displayMaterials, List<PlayerStats> topPlayers) {
        final Set<Material> normalizedMaterials = new LinkedHashSet<>();
        for (Material material : displayMaterials) {
            final Material normalized = PlayerStatsComparator.normalize(material);
            if (!isBaseMaterial(normalized)) {
                normalizedMaterials.add(normalized);
            }
        }

        final List<Material> visible = new ArrayList<>();
        for (Material material : normalizedMaterials) {
            final boolean hasValue = topPlayers.stream().anyMatch(playerStats -> playerStats.getCount(material) > 0);
            if (hasValue) {
                visible.add(material);
            }
        }

        if (!visible.isEmpty()) {
            return visible;
        }
        return List.copyOf(normalizedMaterials);
    }

    private static boolean hasVisibleBase(List<PlayerStats> topPlayers) {
        return topPlayers.stream().anyMatch(playerStats -> baseCount(playerStats) > 0);
    }

    private static boolean usesComparisonTotals(List<PlayerStats> players) {
        return players.stream().anyMatch(playerStats -> playerStats.getComparisonTotal() > playerStats.getTotal());
    }

    private static int longestPlayerWidth(List<PlayerStats> players) {
        int width = "Player".length();
        for (PlayerStats player : players) {
            width = Math.max(width, player.getPlayer().length());
        }
        return width;
    }

    private static String formatCompactCount(int count) {
        if (count <= 0) {
            return "-";
        }
        if (count >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fm", count / 1_000_000.0D);
        }
        if (count >= 1_000) {
            return String.format(Locale.ROOT, "%.1fk", count / 1_000.0D);
        }
        return Integer.toString(count);
    }

    private static String friendlyName(Material material) {
        return PlayerStatsComparator.normalize(material).name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static int baseCount(PlayerStats playerStats) {
        return playerStats.getCount(Material.STONE) + playerStats.getCount(Material.NETHERRACK);
    }

    private static String formatOreRatio(PlayerStats playerStats, List<Material> visibleMaterials) {
        if (playerStats.getComparisonTotal() <= 0) {
            return "-";
        }

        int oreTotal = 0;
        for (Material material : visibleMaterials) {
            oreTotal += playerStats.getCount(material);
        }
        final int percent = Math.round((oreTotal / (float) playerStats.getComparisonTotal()) * 100.0F);
        return percent + "%";
    }

    private static boolean isBaseMaterial(Material material) {
        return material == Material.STONE || material == Material.NETHERRACK;
    }

    private static String pad(String value, int width, boolean alignRight) {
        if (value.length() >= width) {
            return value;
        }
        final String padding = " ".repeat(width - value.length());
        return alignRight ? padding + value : value + padding;
    }

    private static String formatInstant(long epochSeconds) {
        return DATE_TIME_FORMATTER.format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()));
    }

    private static TextColor colorFor(Material material) {
        return switch (PlayerStatsComparator.normalize(material)) {
            case ANCIENT_DEBRIS -> color("#d7997b");
            case DIAMOND_ORE -> color("#9ad7ff");
            case EMERALD_ORE -> color("#b6e3b1");
            case GOLD_ORE -> color("#f8d49d");
            case NETHER_GOLD_ORE -> color("#f0bb78");
            case GILDED_BLACKSTONE -> color("#d8ba7d");
            case IRON_ORE -> color("#e5e9f0");
            case RAW_IRON_BLOCK -> color("#c8c8be");
            case COPPER_ORE -> color("#e7b38a");
            case RAW_COPPER_BLOCK -> color("#d99873");
            case LAPIS_ORE -> color("#a3b8ff");
            case REDSTONE_ORE -> color("#f4a7b2");
            case COAL_ORE -> color("#b2b8c2");
            case NETHER_QUARTZ_ORE -> color("#f8f4ea");
            case STONE -> color("#c9c2b8");
            case NETHERRACK -> color("#d1a1a1");
            default -> VALUE_COLOR;
        };
    }

    private static TextColor color(String hex) {
        return Objects.requireNonNull(TextColor.fromHexString(hex), "Invalid color: " + hex);
    }
}
