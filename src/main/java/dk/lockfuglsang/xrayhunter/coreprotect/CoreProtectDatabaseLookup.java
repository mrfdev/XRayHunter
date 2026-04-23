package dk.lockfuglsang.xrayhunter.coreprotect;

import dk.lockfuglsang.xrayhunter.XRayHunter;
import dk.lockfuglsang.xrayhunter.model.LookupContext;
import dk.lockfuglsang.xrayhunter.model.OreVein;
import dk.lockfuglsang.xrayhunter.model.PlayerStats;
import dk.lockfuglsang.xrayhunter.model.PlayerStatsComparator;
import dk.lockfuglsang.xrayhunter.model.TrackedBlockEvent;
import dk.lockfuglsang.xrayhunter.model.VeinLocator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.coreprotect.database.Database;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/**
 * Direct lookup access to the CoreProtect database for database-only worlds and stale archives.
 */
public final class CoreProtectDatabaseLookup {
    private static final Logger log = Logger.getLogger(CoreProtectDatabaseLookup.class.getName());
    private static final long WORLD_CACHE_TTL_MILLIS = 60_000L;
    private static final long SUMMARY_CACHE_TTL_MILLIS = 10L * 60_000L;
    private static final int SUMMARY_CACHE_MAX_ENTRIES = 8;
    private static final int AGGREGATE_BATCH_WINDOW_SECONDS = 30 * 24 * 60 * 60;

    private static final Map<LookupSummaryCacheKey, CachedLookupSummary> SUMMARY_CACHE =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<LookupSummaryCacheKey, CachedLookupSummary> eldest) {
                    return size() > SUMMARY_CACHE_MAX_ENTRIES;
                }
            };

    private static final AtomicLong SUMMARY_CACHE_HITS = new AtomicLong();
    private static final AtomicLong SUMMARY_CACHE_MISSES = new AtomicLong();

    private static volatile long cachedWorldsExpiresAt = 0L;
    private static volatile List<String> cachedWorldNames = List.of();

    private CoreProtectDatabaseLookup() {
    }

    public static void performLookup(
            XRayHunter plugin,
            String windowKey,
            int sinceEpochSeconds,
            @Nullable String worldName,
            List<Material> lookupMaterials,
            List<Material> comparisonMaterials,
            List<Material> rankingMaterials,
            int comparisonPlayerLimit,
            Collection<String> excludedPlayers,
            Consumer<DatabaseLookupResult> callback
    ) {
        final List<String> normalizedExcludedPlayers = normalizePlayerNames(excludedPlayers);
        final LookupSummaryCacheKey cacheKey = buildSummaryCacheKey(
                windowKey,
                worldName,
                lookupMaterials,
                comparisonMaterials,
                rankingMaterials,
                comparisonPlayerLimit,
                normalizedExcludedPlayers
        );
        final @Nullable DatabaseLookupResult cachedResult = getCachedSummary(cacheKey);
        if (cachedResult != null) {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(cachedResult));
            }
            return;
        }

        plugin.runLookupTaskAsync(() -> {
            DatabaseLookupResult result;
            try (Connection connection = Database.getConnection(true)) {
                result = runLookup(
                        plugin,
                        connection,
                        sinceEpochSeconds,
                        worldName,
                        lookupMaterials,
                        comparisonMaterials,
                        rankingMaterials,
                        comparisonPlayerLimit,
                        normalizedExcludedPlayers
                );
                if (plugin.isEnabled() && !result.hasError()) {
                    cacheSummary(cacheKey, result);
                }
            } catch (SQLException exception) {
                log.log(Level.WARNING, "Unable to query CoreProtect database", exception);
                final long latestTrackedTime = safeLatestTrackedTime(lookupMaterials);
                final String message = MessageFormat.format(
                        "Unable to query CoreProtect database: {0}",
                        exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()
                );
                result = DatabaseLookupResult.failure(message, latestTrackedTime);
            } catch (RuntimeException exception) {
                log.log(Level.WARNING, "Unexpected error while querying CoreProtect database", exception);
                final long latestTrackedTime = safeLatestTrackedTime(lookupMaterials);
                result = DatabaseLookupResult.failure(
                        "Unexpected lookup error: " + safeExceptionMessage(exception),
                        latestTrackedTime
                );
            }
            if (!plugin.isEnabled()) {
                return;
            }
            final DatabaseLookupResult finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalResult));
        });
    }

    public static void performPlayerVeinLookup(
            XRayHunter plugin,
            LookupContext lookupContext,
            String playerName,
            Consumer<PlayerVeinLookupResult> callback
    ) {
        plugin.runLookupTaskAsync(() -> {
            PlayerVeinLookupResult result;
            try (Connection connection = Database.getConnection(true)) {
                result = runPlayerVeinLookup(plugin, connection, lookupContext, playerName);
            } catch (SQLException exception) {
                log.log(Level.WARNING, "Unable to load CoreProtect detail data for " + playerName, exception);
                final String message = MessageFormat.format(
                        "Unable to load detail data for {0}: {1}",
                        playerName,
                        exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()
                );
                result = PlayerVeinLookupResult.failure(message);
            } catch (RuntimeException exception) {
                log.log(Level.WARNING, "Unexpected error while loading CoreProtect detail data for " + playerName, exception);
                result = PlayerVeinLookupResult.failure(
                        MessageFormat.format(
                                "Unable to load detail data for {0}: {1}",
                                playerName,
                                safeExceptionMessage(exception)
                        )
                );
            }
            if (!plugin.isEnabled()) {
                return;
            }
            final PlayerVeinLookupResult finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalResult));
        });
    }

    public static void clearCaches() {
        cachedWorldsExpiresAt = 0L;
        cachedWorldNames = List.of();
        synchronized (SUMMARY_CACHE) {
            SUMMARY_CACHE.clear();
        }
        SUMMARY_CACHE_HITS.set(0L);
        SUMMARY_CACHE_MISSES.set(0L);
    }

    public static int getSummaryCacheSize() {
        synchronized (SUMMARY_CACHE) {
            pruneExpiredSummaryCache(System.currentTimeMillis());
            return SUMMARY_CACHE.size();
        }
    }

    public static long getSummaryCacheHitCount() {
        return SUMMARY_CACHE_HITS.get();
    }

    public static long getSummaryCacheMissCount() {
        return SUMMARY_CACHE_MISSES.get();
    }

    public static List<String> getKnownWorldNames() {
        final long now = System.currentTimeMillis();
        final List<String> cached = cachedWorldNames;
        if (now < cachedWorldsExpiresAt && !cached.isEmpty()) {
            return cached;
        }

        try (Connection connection = Database.getConnection(true);
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT world FROM co_world ORDER BY world COLLATE NOCASE ASC"
             );
             ResultSet resultSet = statement.executeQuery()) {
            final List<String> worlds = new ArrayList<>();
            while (resultSet.next()) {
                final String world = resultSet.getString(1);
                if (world != null && !world.isBlank()) {
                    worlds.add(world);
                }
            }
            cachedWorldNames = List.copyOf(worlds);
            cachedWorldsExpiresAt = now + WORLD_CACHE_TTL_MILLIS;
            return cachedWorldNames;
        } catch (SQLException exception) {
            log.log(Level.FINE, "Unable to load CoreProtect world names", exception);
            return cached;
        }
    }

    public static @Nullable String resolveKnownWorldName(String rawWorldName) {
        for (String worldName : getKnownWorldNames()) {
            if (worldName.equalsIgnoreCase(rawWorldName)) {
                return worldName;
            }
        }
        return null;
    }

    public static long safeLatestTrackedTime(Collection<Material> lookupMaterials) {
        try (Connection connection = Database.getConnection(true)) {
            final Map<Integer, Material> materialById = resolveTrackedMaterialIds(connection, lookupMaterials);
            return latestTrackedTime(connection, materialById.keySet(), null);
        } catch (SQLException exception) {
            log.log(Level.FINE, "Unable to inspect latest tracked CoreProtect time", exception);
            return 0L;
        }
    }

    private static DatabaseLookupResult runLookup(
            XRayHunter plugin,
            Connection connection,
            int sinceEpochSeconds,
            @Nullable String worldName,
            Collection<Material> lookupMaterials,
            Collection<Material> comparisonMaterials,
            List<Material> rankingMaterials,
            int comparisonPlayerLimit,
            Collection<String> excludedPlayers
    ) throws SQLException {
        final Map<Integer, Material> materialById = resolveTrackedMaterialIds(connection, lookupMaterials);
        if (materialById.isEmpty()) {
            return DatabaseLookupResult.success(0L, Map.of(), Map.of(), false);
        }

        final @Nullable Integer worldId = worldName == null ? null : resolveWorldId(connection, worldName);
        if (worldName != null && worldId == null) {
            return DatabaseLookupResult.failure("That world was not found in CoreProtect: " + worldName, 0L);
        }

        final long latestTrackedTimeSeconds = latestTrackedTime(connection, materialById.keySet(), worldId);
        final Map<String, Map<Material, Integer>> playerCounts = new HashMap<>();
        if (latestTrackedTimeSeconds <= 0 || latestTrackedTimeSeconds < sinceEpochSeconds) {
            return DatabaseLookupResult.success(latestTrackedTimeSeconds, freezePlayerCounts(playerCounts), Map.of(), false);
        }

        long batchStart = Math.max(0L, sinceEpochSeconds);
        final long endExclusive = latestTrackedTimeSeconds + 1L;
        while (batchStart < endExclusive) {
            if (shouldAbort(plugin)) {
                return DatabaseLookupResult.failure("Lookup canceled while the plugin was shutting down.", latestTrackedTimeSeconds);
            }
            final long batchEndExclusive = Math.min(batchStart + AGGREGATE_BATCH_WINDOW_SECONDS, endExclusive);
            aggregateLookupBatch(
                    connection,
                    batchStart,
                    batchEndExclusive,
                    worldId,
                    materialById,
                    excludedPlayers,
                    playerCounts
            );
            batchStart = batchEndExclusive;
        }

        final Map<String, Map<Material, Integer>> frozenPlayerCounts = freezePlayerCounts(playerCounts);
        final Map<String, Integer> comparisonTotals = resolveComparisonTotals(
                connection,
                sinceEpochSeconds,
                worldId,
                frozenPlayerCounts,
                materialById.values(),
                comparisonMaterials,
                rankingMaterials,
                comparisonPlayerLimit
        );
        return DatabaseLookupResult.success(latestTrackedTimeSeconds, frozenPlayerCounts, comparisonTotals, false);
    }

    private static PlayerVeinLookupResult runPlayerVeinLookup(
            XRayHunter plugin,
            Connection connection,
            LookupContext lookupContext,
            String playerName
    ) throws SQLException {
        final Map<Integer, Material> materialById = resolveTrackedMaterialIds(connection, lookupContext.lookupMaterials());
        if (materialById.isEmpty()) {
            return PlayerVeinLookupResult.success(List.of());
        }

        final @Nullable Integer worldId = lookupContext.worldName() == null
                ? null
                : resolveWorldId(connection, lookupContext.worldName());
        if (lookupContext.worldName() != null && worldId == null) {
            return PlayerVeinLookupResult.failure("That world was not found in CoreProtect: " + lookupContext.worldName());
        }

        final String sql = buildPlayerVeinLookupSql(materialById.size(), worldId != null);
        final VeinLocator.Collector collector = VeinLocator.collector();
        final Set<BlockPositionKey> userPlacedBlocks = new LinkedHashSet<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setFetchSize(512);
            int parameter = 1;
            statement.setString(parameter++, playerName);
            statement.setInt(parameter++, lookupContext.sinceEpochSeconds());
            for (Integer materialId : materialById.keySet()) {
                statement.setInt(parameter++, materialId);
            }
            statement.setInt(parameter++, CoreProtectHandler.ACTION_BREAK);
            statement.setInt(parameter++, CoreProtectHandler.ACTION_PLACE);
            if (worldId != null) {
                statement.setInt(parameter, worldId);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (shouldAbort(plugin)) {
                        return PlayerVeinLookupResult.failure("Detail lookup canceled while the plugin was shutting down.");
                    }
                    final int time = resultSet.getInt("time");
                    final String resolvedWorldName = resultSet.getString("world_name");
                    final int x = resultSet.getInt("x");
                    final int y = resultSet.getInt("y");
                    final int z = resultSet.getInt("z");
                    final int action = resultSet.getInt("action");
                    final Material material = materialById.get(resultSet.getInt("type"));
                    if (resolvedWorldName == null || material == null) {
                        continue;
                    }

                    final BlockPositionKey key = new BlockPositionKey(resolvedWorldName, x, y, z);
                    if (action == CoreProtectHandler.ACTION_PLACE) {
                        userPlacedBlocks.add(key);
                        continue;
                    }
                    if (action != CoreProtectHandler.ACTION_BREAK || userPlacedBlocks.remove(key)) {
                        continue;
                    }

                    collector.accept(new TrackedBlockEvent(
                            playerName,
                            resolvedWorldName,
                            x,
                            y,
                            z,
                            time,
                            material
                    ));
                }
            }
        }

        final List<OreVein> veins = collector.finish();
        return PlayerVeinLookupResult.success(veins);
    }

    private static void aggregateLookupBatch(
            Connection connection,
            long startInclusive,
            long endExclusive,
            @Nullable Integer worldId,
            Map<Integer, Material> materialById,
            Collection<String> excludedPlayers,
            Map<String, Map<Material, Integer>> playerCounts
    ) throws SQLException {
        if (startInclusive >= endExclusive) {
            return;
        }

        for (Map.Entry<Integer, Material> materialEntry : materialById.entrySet()) {
            aggregateLookupMaterialBatch(
                    connection,
                    startInclusive,
                    endExclusive,
                    worldId,
                    materialEntry.getKey(),
                    materialEntry.getValue(),
                    excludedPlayers,
                    playerCounts
            );
        }
    }

    private static void aggregateLookupMaterialBatch(
            Connection connection,
            long startInclusive,
            long endExclusive,
            @Nullable Integer worldId,
            int materialId,
            Material material,
            Collection<String> excludedPlayers,
            Map<String, Map<Material, Integer>> playerCounts
    ) throws SQLException {
        final String sql = buildAggregateLookupSql(worldId != null, excludedPlayers.size());
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setFetchSize(512);
            int parameter = 1;
            statement.setLong(parameter++, startInclusive);
            statement.setLong(parameter++, endExclusive);
            statement.setInt(parameter++, materialId);
            statement.setInt(parameter++, CoreProtectHandler.ACTION_BREAK);
            if (worldId != null) {
                statement.setInt(parameter++, worldId);
            }
            for (String excludedPlayer : excludedPlayers) {
                statement.setString(parameter++, excludedPlayer);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final String playerName = resultSet.getString("player_name");
                    final int count = resultSet.getInt("break_count");
                    if (playerName == null || count <= 0 || isIgnoredPseudoUser(playerName)) {
                        continue;
                    }

                    final Material normalized = PlayerStatsComparator.normalize(material);
                    playerCounts.computeIfAbsent(playerName, ignored -> new HashMap<>())
                            .merge(normalized, count, Integer::sum);
                }
            }
        }
    }

    private static Map<Integer, Material> resolveTrackedMaterialIds(
            Connection connection,
            Collection<Material> lookupMaterials
    ) throws SQLException {
        final Map<String, Material> requestedByDatabaseName = new LinkedHashMap<>();
        for (Material material : lookupMaterials) {
            requestedByDatabaseName.put("minecraft:" + material.name().toLowerCase(Locale.ROOT), material);
        }
        if (requestedByDatabaseName.isEmpty()) {
            return Map.of();
        }

        final String sql = "SELECT id, material FROM co_material_map WHERE material IN (" + placeholders(requestedByDatabaseName.size()) + ")";
        final Map<Integer, Material> materialsById = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (String materialName : requestedByDatabaseName.keySet()) {
                statement.setString(parameter++, materialName);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final int id = resultSet.getInt("id");
                    final String materialName = resultSet.getString("material");
                    final Material material = requestedByDatabaseName.get(materialName);
                    if (material != null) {
                        materialsById.put(id, material);
                    }
                }
            }
        }
        return materialsById;
    }

    private static long latestTrackedTime(
            Connection connection,
            Collection<Integer> materialIds,
            @Nullable Integer worldId
    ) throws SQLException {
        if (materialIds.isEmpty()) {
            return 0L;
        }

        long latestTime = 0L;
        final String sql = worldId == null
                ? "SELECT MAX(time) AS latest_time FROM co_block WHERE type = ?"
                : "SELECT MAX(time) AS latest_time FROM co_block WHERE type = ? AND wid = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Integer materialId : materialIds) {
                statement.setInt(1, materialId);
                if (worldId != null) {
                    statement.setInt(2, worldId);
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        latestTime = Math.max(latestTime, resultSet.getLong("latest_time"));
                    }
                }
            }
        }
        return latestTime;
    }

    private static @Nullable Integer resolveWorldId(Connection connection, String worldName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM co_world WHERE lower(world) = lower(?) LIMIT 1")) {
            statement.setString(1, worldName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt("id") : null;
            }
        }
    }

    private static String buildAggregateLookupSql(boolean filterByWorld, int excludedPlayerCount) {
        final StringBuilder sql = new StringBuilder("""
                SELECT u.user AS player_name, COUNT(*) AS break_count
                FROM co_block b
                JOIN co_user u ON u.id = b.user
                WHERE b.time >= ?
                  AND b.time < ?
                """);
        sql.append("  AND b.type = ?\n");
        sql.append("  AND b.action = ?\n");
        sql.append("  AND u.user NOT LIKE '#%'\n");
        if (filterByWorld) {
            sql.append("  AND b.wid = ?\n");
        }
        if (excludedPlayerCount > 0) {
            sql.append("  AND lower(u.user) NOT IN (").append(placeholders(excludedPlayerCount)).append(")\n");
        }
        sql.append("GROUP BY u.user");
        return sql.toString();
    }

    private static String buildPlayerVeinLookupSql(int materialCount, boolean filterByWorld) {
        final StringBuilder sql = new StringBuilder("""
                SELECT b.time, b.x, b.y, b.z, b.type, b.action, w.world AS world_name
                FROM co_block b
                JOIN co_user u ON u.id = b.user
                JOIN co_world w ON w.id = b.wid
                WHERE lower(u.user) = lower(?)
                  AND b.time >= ?
                """);
        sql.append("  AND b.type IN (").append(placeholders(materialCount)).append(")\n");
        sql.append("  AND b.action IN (?, ?)\n");
        if (filterByWorld) {
            sql.append("  AND b.wid = ?\n");
        }
        sql.append("ORDER BY b.time ASC");
        return sql.toString();
    }

    private static Map<String, Integer> resolveComparisonTotals(
            Connection connection,
            int sinceEpochSeconds,
            @Nullable Integer worldId,
            Map<String, Map<Material, Integer>> playerCounts,
            Collection<Material> lookupMaterials,
            Collection<Material> comparisonMaterials,
            List<Material> rankingMaterials,
            int comparisonPlayerLimit
    ) throws SQLException {
        if (playerCounts.isEmpty()) {
            return Map.of();
        }

        final Map<String, Integer> comparisonTotals = new HashMap<>();
        for (Map.Entry<String, Map<Material, Integer>> entry : playerCounts.entrySet()) {
            comparisonTotals.put(entry.getKey(), sumMaterialCounts(entry.getValue()));
        }

        if (!needsComparisonEnrichment(lookupMaterials, comparisonMaterials)) {
            return Map.copyOf(comparisonTotals);
        }

        final Map<Integer, Material> comparisonMaterialById = resolveTrackedMaterialIds(connection, comparisonMaterials);
        if (comparisonMaterialById.isEmpty()) {
            return Map.copyOf(comparisonTotals);
        }

        final List<String> shortlistedPlayers = selectTopPlayersForComparison(playerCounts, rankingMaterials, comparisonPlayerLimit);
        if (shortlistedPlayers.isEmpty()) {
            return Map.copyOf(comparisonTotals);
        }

        final Map<String, Integer> userIdsByPlayer = resolveUserIds(connection, shortlistedPlayers);
        if (userIdsByPlayer.isEmpty()) {
            return Map.copyOf(comparisonTotals);
        }

        final String sql = buildPlayerTotalSql(comparisonMaterialById.size(), worldId != null);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String playerName : shortlistedPlayers) {
                final Integer userId = userIdsByPlayer.get(playerName);
                if (userId == null) {
                    continue;
                }
                final int total = countPlayerTrackedBreaks(
                        statement,
                        sinceEpochSeconds,
                        worldId,
                        userId,
                        comparisonMaterialById.keySet()
                );
                if (total > 0) {
                    comparisonTotals.put(playerName, Math.max(total, comparisonTotals.getOrDefault(playerName, 0)));
                }
            }
        }

        return Map.copyOf(comparisonTotals);
    }

    private static List<String> selectTopPlayersForComparison(
            Map<String, Map<Material, Integer>> playerCounts,
            List<Material> rankingMaterials,
            int comparisonPlayerLimit
    ) {
        final List<Material> normalizedRankingMaterials = rankingMaterials.stream()
                .map(PlayerStatsComparator::normalize)
                .distinct()
                .toList();
        final List<Material> effectiveRankingMaterials = normalizedRankingMaterials.isEmpty()
                ? playerCounts.values().stream()
                .flatMap(counts -> counts.keySet().stream())
                .map(PlayerStatsComparator::normalize)
                .distinct()
                .toList()
                : normalizedRankingMaterials;
        final List<PlayerStats> rankedPlayers = new ArrayList<>();
        for (Map.Entry<String, Map<Material, Integer>> entry : playerCounts.entrySet()) {
            rankedPlayers.add(new PlayerStats(entry.getKey(), entry.getValue()));
        }
        rankedPlayers.sort(new PlayerStatsComparator(effectiveRankingMaterials));

        final int limit = Math.max(1, comparisonPlayerLimit);
        final List<String> players = new ArrayList<>(Math.min(limit, rankedPlayers.size()));
        for (PlayerStats playerStats : rankedPlayers.subList(0, Math.min(limit, rankedPlayers.size()))) {
            players.add(playerStats.getPlayer());
        }
        return List.copyOf(players);
    }

    private static Map<String, Integer> resolveUserIds(Connection connection, List<String> playerNames) throws SQLException {
        if (playerNames.isEmpty()) {
            return Map.of();
        }

        final Map<String, Integer> userIdsByPlayer = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM co_user WHERE lower(user) = lower(?) ORDER BY id ASC LIMIT 1"
        )) {
            for (String playerName : playerNames) {
                statement.setString(1, playerName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        userIdsByPlayer.put(playerName, resultSet.getInt(1));
                    }
                }
            }
        }
        return Map.copyOf(userIdsByPlayer);
    }

    private static int countPlayerTrackedBreaks(
            PreparedStatement statement,
            int sinceEpochSeconds,
            @Nullable Integer worldId,
            int userId,
            Collection<Integer> materialIds
    ) throws SQLException {
        statement.clearParameters();
        int parameter = 1;
        statement.setInt(parameter++, userId);
        statement.setInt(parameter++, sinceEpochSeconds);
        statement.setInt(parameter++, CoreProtectHandler.ACTION_BREAK);
        if (worldId != null) {
            statement.setInt(parameter++, worldId);
        }
        for (Integer materialId : materialIds) {
            statement.setInt(parameter++, materialId);
        }

        try (ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt("break_count") : 0;
        }
    }

    private static String buildPlayerTotalSql(int materialCount, boolean filterByWorld) {
        final StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*) AS break_count
                FROM co_block b
                WHERE b.user = ?
                  AND b.time >= ?
                  AND b.action = ?
                """);
        if (filterByWorld) {
            sql.append("  AND b.wid = ?\n");
        }
        sql.append("  AND b.type IN (").append(placeholders(materialCount)).append(")\n");
        return sql.toString();
    }

    private static boolean needsComparisonEnrichment(
            Collection<Material> lookupMaterials,
            Collection<Material> comparisonMaterials
    ) {
        return !normalizedMaterialNames(lookupMaterials).equals(normalizedMaterialNames(comparisonMaterials));
    }

    private static Set<String> normalizedMaterialNames(Collection<Material> materials) {
        final Set<String> normalized = new LinkedHashSet<>();
        for (Material material : materials) {
            if (material == null) {
                continue;
            }
            normalized.add(PlayerStatsComparator.normalize(material).name().toLowerCase(Locale.ROOT));
        }
        return normalized;
    }

    private static int sumMaterialCounts(Map<Material, Integer> counts) {
        int total = 0;
        for (Integer count : counts.values()) {
            if (count != null && count > 0) {
                total += count;
            }
        }
        return total;
    }

    private static boolean isIgnoredPseudoUser(String playerName) {
        return playerName.trim().startsWith("#");
    }

    private static String safeExceptionMessage(RuntimeException exception) {
        final String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private static Map<String, Map<Material, Integer>> freezePlayerCounts(
            Map<String, Map<Material, Integer>> playerCounts
    ) {
        final Map<String, Map<Material, Integer>> immutable = new HashMap<>();
        for (Map.Entry<String, Map<Material, Integer>> entry : playerCounts.entrySet()) {
            immutable.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Map.copyOf(immutable);
    }

    private static List<String> normalizePlayerNames(Collection<String> playerNames) {
        final Set<String> normalized = new LinkedHashSet<>();
        for (String playerName : playerNames) {
            if (playerName == null || playerName.isBlank()) {
                continue;
            }
            if (isIgnoredPseudoUser(playerName)) {
                continue;
            }
            normalized.add(playerName.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    private static LookupSummaryCacheKey buildSummaryCacheKey(
            String windowKey,
            @Nullable String worldName,
            Collection<Material> lookupMaterials,
            Collection<Material> comparisonMaterials,
            List<Material> rankingMaterials,
            int comparisonPlayerLimit,
            Collection<String> excludedPlayers
    ) {
        final List<String> materialNames = normalizedMaterialNames(lookupMaterials).stream().sorted().toList();
        final List<String> comparisonMaterialNames = normalizedMaterialNames(comparisonMaterials).stream().sorted().toList();
        final List<String> rankingMaterialNames = rankingMaterials.stream()
                .filter(Objects::nonNull)
                .map(PlayerStatsComparator::normalize)
                .map(Material::name)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        final List<String> excludedNames = excludedPlayers.stream()
                .filter(Objects::nonNull)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
        return new LookupSummaryCacheKey(
                windowKey.toLowerCase(Locale.ROOT),
                worldName == null ? null : worldName.toLowerCase(Locale.ROOT),
                materialNames,
                comparisonMaterialNames,
                rankingMaterialNames,
                comparisonPlayerLimit,
                excludedNames
        );
    }

    private static @Nullable DatabaseLookupResult getCachedSummary(LookupSummaryCacheKey cacheKey) {
        synchronized (SUMMARY_CACHE) {
            pruneExpiredSummaryCache(System.currentTimeMillis());
            final CachedLookupSummary cachedSummary = SUMMARY_CACHE.get(cacheKey);
            if (cachedSummary == null) {
                SUMMARY_CACHE_MISSES.incrementAndGet();
                return null;
            }
            SUMMARY_CACHE_HITS.incrementAndGet();
            return DatabaseLookupResult.success(
                    cachedSummary.latestTrackedTimeSeconds(),
                    cachedSummary.playerCounts(),
                    cachedSummary.comparisonTotals(),
                    true
            );
        }
    }

    private static void cacheSummary(LookupSummaryCacheKey cacheKey, DatabaseLookupResult result) {
        synchronized (SUMMARY_CACHE) {
            final long now = System.currentTimeMillis();
            pruneExpiredSummaryCache(now);
            SUMMARY_CACHE.put(
                    cacheKey,
                    new CachedLookupSummary(
                            now + SUMMARY_CACHE_TTL_MILLIS,
                            result.latestTrackedTimeSeconds(),
                            result.playerCounts(),
                            result.comparisonTotals()
                    )
            );
        }
    }

    private static void pruneExpiredSummaryCache(long now) {
        SUMMARY_CACHE.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    }

    private static boolean shouldAbort(XRayHunter plugin) {
        return !plugin.isEnabled() || Thread.currentThread().isInterrupted();
    }

    private record LookupSummaryCacheKey(
            String windowKey,
            @Nullable String worldName,
            List<String> materialNames,
            List<String> comparisonMaterialNames,
            List<String> rankingMaterialNames,
            int comparisonPlayerLimit,
            List<String> excludedPlayers
    ) {
    }

    private record CachedLookupSummary(
            long expiresAtMillis,
            long latestTrackedTimeSeconds,
            Map<String, Map<Material, Integer>> playerCounts,
            Map<String, Integer> comparisonTotals
    ) {
    }

    private record BlockPositionKey(
            String worldName,
            int x,
            int y,
            int z
    ) {
    }
}
