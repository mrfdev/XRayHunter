package com.onemb.cmiapi.xrayhunter.command;

import com.onemb.cmiapi.xrayhunter.XRayHunterFeature;
import com.onemb.cmiapi.xrayhunter.model.InvestigationSession;
import com.onemb.cmiapi.xrayhunter.model.LookupContext;
import com.onemb.cmiapi.xrayhunter.model.LookupSummary;
import com.onemb.cmiapi.xrayhunter.model.PlayerStats;
import com.onemb.cmiapi.xrayhunter.model.PlayerStatsComparator;
import com.onemb.cmiapi.xrayhunter.util.TimeUtil;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Looks up suspicious players within the requested time window.
 */
public final class LookupCommand {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    private static final String OPTION_ALL = "-all";
    private static final String TOKEN_ALLTIME = "alltime";
    private static final String TOKEN_ALLWORLDS = "allworlds";

    private final XRayHunterFeature feature;

    public LookupCommand(XRayHunterFeature feature) {
        this.feature = feature;
    }

    public boolean execute(final CommandSender sender, String @NonNull ... args) {
        final @Nullable ParsedLookupRequest request = parseLookupRequest(sender, args);
        if (request == null) {
            return true;
        }

        final Player player = sender instanceof Player ? (Player) sender : null;
        final @Nullable String lookupWorldName = request.explicitAllWorlds()
                ? null
                : request.lookupWorldName() != null
                ? request.lookupWorldName()
                : player != null ? player.getWorld().getName() : null;

        if (player == null && lookupWorldName == null) {
            if (!feature.getSettings().consoleAllowServerWideLookups()) {
                sender.sendMessage("§cServer-wide console lookups are disabled in config.");
                sender.sendMessage("§7Use §f/xrayhunter lookup <time> <world>§7 instead.");
                return true;
            }
            if (request.allTime() && !request.explicitAllWorlds()) {
                sender.sendMessage("§cFull archive console lookups must be explicit.");
                sender.sendMessage("§7Use §f/xrayhunter lookup alltime allworlds§7 or provide a single world.");
                return true;
            }
            if (!request.explicitAllWorlds() && request.requestedMillis() > feature.getSettings().consoleMaxAllWorldLookupMillis()) {
                sender.sendMessage("§cThat console lookup is too large for an implicit all-world query.");
                sender.sendMessage("§7Max implicit all-world console window: §f" + feature.getSettings().consoleMaxAllWorldLookupTime());
                sender.sendMessage("§7Use §f/xrayhunter lookup " + request.timeArgument() + " <world>§7 or §f/xrayhunter lookup " + request.timeArgument() + " allworlds§7.");
                return true;
            }
        }

        final World loadedWorld = lookupWorldName == null ? null : feature.getServer().getWorld(lookupWorldName);
        final boolean restrictWorld = lookupWorldName != null;
        final List<Material> allLookupMaterials = getLookupMaterials(loadedWorld, restrictWorld);
        final List<Material> lookupMaterials = getEffectiveLookupMaterials(sender, allLookupMaterials, request.showAllColumns());
        final List<Material> allDisplayMaterials = getDisplayMaterials(loadedWorld, restrictWorld);
        final List<Material> displayMaterials = getEffectiveDisplayMaterials(sender, allDisplayMaterials, request.showAllColumns());
        final List<Material> comparisonMaterials = allLookupMaterials;
        final String viewLabel = getViewLabel(sender, request.showAllColumns(), displayMaterials, allDisplayMaterials);
        final int sinceEpochSeconds = request.allTime()
                ? 0
                : (int) (System.currentTimeMillis() / 1000L) - TimeUtil.millisAsSeconds(request.requestedMillis());

        if (player == null) {
            if (lookupWorldName != null) {
                if (loadedWorld != null) {
                    sender.sendMessage("§7Running a world-scoped lookup for §f" + lookupWorldName + "§7.");
                } else {
                    sender.sendMessage("§7Running a database-world lookup for §f" + lookupWorldName + "§7.");
                }
            } else if (request.explicitAllWorlds()) {
                sender.sendMessage("§7Running an explicit all-world CoreProtect lookup across the full requested scope.");
                sender.sendMessage("§7Using batched aggregate queries and a temporary summary cache for this large archive lookup.");
            } else {
                sender.sendMessage("§7Running a server-wide lookup across all CoreProtect worlds.");
            }
            if (!request.showAllColumns() && !lookupMaterials.equals(allLookupMaterials)) {
                sender.sendMessage("§7Using the compact high-value lookup set. Add §f-all§7 to include lower-value and base materials.");
            }
            if (!request.showAllColumns() && feature.getSettings().consoleHighValueOnly() && !displayMaterials.equals(allDisplayMaterials)) {
                sender.sendMessage("§7Using the compact high-value console view. Add §f-all§7 to show every tracked column.");
            }
        }

        feature.getMiningHistory().performLookup(
                request.timeArgument(),
                sinceEpochSeconds,
                lookupWorldName,
                lookupMaterials,
                comparisonMaterials,
                displayMaterials,
                feature.getSettings().topResults(),
                feature.getSettings().excludedPlayers(),
                result -> handleLookupResult(
                        sender,
                        lookupWorldName,
                        loadedWorld,
                        request.timeArgument(),
                        request.allTime(),
                        request.requestedMillis(),
                        lookupMaterials,
                        displayMaterials,
                        viewLabel,
                        sinceEpochSeconds,
                        result
                )
        );
        return true;
    }

    public boolean isTimeToken(String token) {
        return isAllTimeToken(token) || TimeUtil.millisFromString(token) > 0;
    }

    private void handleLookupResult(
            CommandSender sender,
            @Nullable String lookupWorldName,
            @Nullable World loadedWorld,
            String timeArgument,
            boolean allTime,
            long requestedMillis,
            List<Material> lookupMaterials,
            List<Material> displayMaterials,
            String viewLabel,
            int sinceEpochSeconds,
            LookupSummary result
    ) {
        final InvestigationSession session = feature.getSession(sender);
        if (result.hasError()) {
            session.setLookupCache(List.of());
            sender.sendMessage("§c" + result.errorMessage());
            return;
        }

        final List<PlayerStats> topPlayers = new ArrayList<>();
        for (Map.Entry<String, Map<Material, Integer>> entry : result.playerCounts().entrySet()) {
            topPlayers.add(new PlayerStats(
                    entry.getKey(),
                    entry.getValue(),
                    result.comparisonTotals().getOrDefault(entry.getKey(), 0)
            ));
        }

        if (topPlayers.isEmpty()) {
            session.setLookupCache(List.of());
            sendNoActivityMessage(sender, lookupWorldName, allTime, requestedMillis, result.latestTrackedTimeSeconds());
            return;
        }

        topPlayers.sort(new PlayerStatsComparator(displayMaterials));
        session.setLookupCache(topPlayers);
        session.setLookupContext(new LookupContext(sinceEpochSeconds, timeArgument, lookupWorldName, lookupMaterials));

        final String scopeLabel;
        if (lookupWorldName == null) {
            scopeLabel = "all CoreProtect worlds";
        } else if (loadedWorld == null) {
            scopeLabel = lookupWorldName + " (database world)";
        } else {
            scopeLabel = lookupWorldName;
        }

        if (result.fromCache()) {
            sender.sendMessage("§7Reused the recent temporary summary cache for this lookup.");
        }

        for (Component line : LookupReportFormatter.buildReport(
                scopeLabel,
                timeArgument,
                viewLabel,
                result.latestTrackedTimeSeconds(),
                topPlayers,
                displayMaterials,
                feature.getSettings().topResults()
        )) {
            sender.sendMessage(line);
        }
    }

    private List<Material> getLookupMaterials(@Nullable World loadedWorld, boolean restrictWorld) {
        if (loadedWorld != null) {
            return feature.getLookupMaterials(loadedWorld.getEnvironment());
        }
        return combineMaterials(
                feature.getSettings().overworldLookupMaterials(),
                feature.getSettings().netherLookupMaterials()
        );
    }

    private List<Material> getDisplayMaterials(@Nullable World loadedWorld, boolean restrictWorld) {
        if (loadedWorld != null) {
            return feature.getDisplayMaterials(loadedWorld.getEnvironment());
        }
        return combineMaterials(
                feature.getSettings().overworldDisplayMaterials(),
                feature.getSettings().netherDisplayMaterials()
        );
    }

    private List<Material> getEffectiveLookupMaterials(CommandSender sender, List<Material> allLookupMaterials, boolean showAllColumns) {
        if (showAllColumns || sender instanceof Player || !feature.getSettings().consoleHighValueOnly()) {
            return allLookupMaterials;
        }

        final Set<Material> allowed = new LinkedHashSet<>();
        for (Material material : feature.getSettings().consoleHighValueDisplayMaterials()) {
            allowed.add(PlayerStatsComparator.normalize(material));
        }

        final List<Material> filtered = allLookupMaterials.stream()
                .filter(material -> allowed.contains(PlayerStatsComparator.normalize(material)))
                .distinct()
                .toList();
        return filtered.isEmpty() ? allLookupMaterials : filtered;
    }

    private List<Material> getEffectiveDisplayMaterials(CommandSender sender, List<Material> allDisplayMaterials, boolean showAllColumns) {
        if (showAllColumns || sender instanceof Player || !feature.getSettings().consoleHighValueOnly()) {
            return allDisplayMaterials;
        }

        final Set<Material> allowed = new LinkedHashSet<>();
        for (Material material : feature.getSettings().consoleHighValueDisplayMaterials()) {
            allowed.add(PlayerStatsComparator.normalize(material));
        }

        final List<Material> filtered = allDisplayMaterials.stream()
                .map(PlayerStatsComparator::normalize)
                .distinct()
                .filter(allowed::contains)
                .toList();
        return filtered.isEmpty() ? allDisplayMaterials : filtered;
    }

    private String getViewLabel(
            CommandSender sender,
            boolean showAllColumns,
            List<Material> displayMaterials,
            List<Material> allDisplayMaterials
    ) {
        if (showAllColumns || sender instanceof Player || displayMaterials.equals(allDisplayMaterials)) {
            return "all tracked";
        }
        return "high-value";
    }

    private List<Material> combineMaterials(List<Material> primary, List<Material> secondary) {
        final Set<Material> combined = new LinkedHashSet<>(primary);
        combined.addAll(secondary);
        return List.copyOf(combined);
    }

    private @Nullable ParsedLookupRequest parseLookupRequest(CommandSender sender, String @NonNull ... args) {
        String timeArgument = feature.getSettings().defaultLookupTime();
        long requestedMillis = TimeUtil.millisFromString(timeArgument);
        @Nullable String rawWorldName = null;
        boolean sawExplicitTime = false;
        boolean allTime = false;
        boolean explicitAllWorlds = false;
        boolean showAllColumns = false;

        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            if (OPTION_ALL.equalsIgnoreCase(arg)) {
                showAllColumns = true;
                continue;
            }
            if (isAllTimeToken(arg) && !sawExplicitTime) {
                timeArgument = TOKEN_ALLTIME;
                requestedMillis = Long.MAX_VALUE;
                sawExplicitTime = true;
                allTime = true;
                continue;
            }

            final long parsedMillis = TimeUtil.millisFromString(arg);
            if (parsedMillis > 0 && !sawExplicitTime) {
                timeArgument = arg;
                requestedMillis = parsedMillis;
                sawExplicitTime = true;
                continue;
            }

            if (isAllWorldsToken(arg) && rawWorldName == null) {
                explicitAllWorlds = true;
                continue;
            }

            if (rawWorldName == null) {
                rawWorldName = arg;
                continue;
            }

            sender.sendMessage("§cUsage: §f/xrayhunter lookup [time|alltime] [world|allworlds] [-all]");
            return null;
        }

        final @Nullable String resolvedWorldName = rawWorldName == null ? null : resolveWorldName(sender, rawWorldName);
        if (rawWorldName != null && resolvedWorldName == null) {
            return null;
        }

        return new ParsedLookupRequest(
                timeArgument,
                requestedMillis,
                allTime,
                resolvedWorldName,
                explicitAllWorlds,
                showAllColumns
        );
    }

    private @Nullable String resolveWorldName(CommandSender sender, String rawWorldName) {
        final World directMatch = feature.getServer().getWorld(rawWorldName);
        if (directMatch != null) {
            return directMatch.getName();
        }

        for (World world : feature.getServer().getWorlds()) {
            if (world.getName().equalsIgnoreCase(rawWorldName)) {
                return world.getName();
            }
        }

        final @Nullable String databaseWorldName = feature.getMiningHistory().resolveKnownWorldName(rawWorldName);
        if (databaseWorldName != null) {
            return databaseWorldName;
        }

        sender.sendMessage("§cThat world was not found in loaded worlds or CoreProtect: §f" + rawWorldName);
        final List<String> suggestions = getWorldNameSuggestions();
        if (!suggestions.isEmpty()) {
            sender.sendMessage("§7Known worlds: §f" + String.join("§7, §f", suggestions));
        }
        return null;
    }

    private void sendNoActivityMessage(
            CommandSender sender,
            @Nullable String lookupWorldName,
            boolean allTime,
            long requestedMillis,
            long latestTrackedTimeSeconds
    ) {
        if (lookupWorldName != null) {
            sender.sendMessage("§eNo suspicious activity within that time frame in §f" + lookupWorldName + "§e.");
        } else if (allTime) {
            sender.sendMessage("§eNo suspicious activity found across the full CoreProtect archive.");
        } else {
            sender.sendMessage("§eNo suspicious activity within that time frame across all CoreProtect worlds.");
        }

        if (latestTrackedTimeSeconds <= 0) {
            return;
        }

        sender.sendMessage("§7Latest tracked block data: §f" + formatTimestamp(latestTrackedTimeSeconds));
        if (allTime) {
            return;
        }

        final long latestTrackedMillis = latestTrackedTimeSeconds * 1000L;
        if (latestTrackedMillis < System.currentTimeMillis() - requestedMillis) {
            sender.sendMessage("§7This database is older than that lookup window from the current server time.");
            sender.sendMessage("§7Try at least §f" + TimeUtil.millisAsString(System.currentTimeMillis() - latestTrackedMillis) + "§7.");
            if (!(sender instanceof Player) && lookupWorldName == null) {
                sender.sendMessage("§7Likely active database worlds here include §fwild§7, §fgeneral§7, §foneblock§7, and §fskyblock§7.");
            }
        }
    }

    private boolean isAllTimeToken(String token) {
        return TOKEN_ALLTIME.equalsIgnoreCase(token);
    }

    private boolean isAllWorldsToken(String token) {
        return TOKEN_ALLWORLDS.equalsIgnoreCase(token);
    }

    private String formatTimestamp(long epochSeconds) {
        return DATE_TIME_FORMATTER.format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()));
    }

    public List<String> getWorldNameSuggestions() {
        final Set<String> suggestions = new LinkedHashSet<>();
        feature.getServer().getWorlds().stream()
                .map(World::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(suggestions::add);
        final List<String> knownDatabaseWorlds = new ArrayList<>(feature.getMiningHistory().getKnownWorldNames());
        knownDatabaseWorlds.sort(String.CASE_INSENSITIVE_ORDER);
        suggestions.addAll(knownDatabaseWorlds);
        return List.copyOf(suggestions);
    }

    private record ParsedLookupRequest(
            String timeArgument,
            long requestedMillis,
            boolean allTime,
            @Nullable String lookupWorldName,
            boolean explicitAllWorlds,
            boolean showAllColumns
    ) {
    }
}
