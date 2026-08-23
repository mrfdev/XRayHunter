package com.onemb.cmiapi.xrayhunter.coreprotect;

import com.onemb.cmiapi.xrayhunter.MiningHistory;
import com.onemb.cmiapi.xrayhunter.XRayHunterFeature;
import com.onemb.cmiapi.xrayhunter.model.LookupContext;
import com.onemb.cmiapi.xrayhunter.model.LookupSummary;
import com.onemb.cmiapi.xrayhunter.model.OreVein;
import com.onemb.cmiapi.xrayhunter.model.PlayerStats;
import com.onemb.cmiapi.xrayhunter.model.PlayerStatsComparator;
import com.onemb.cmiapi.xrayhunter.model.TrackedBlockEvent;
import com.onemb.cmiapi.xrayhunter.model.VeinLookupResult;
import com.onemb.cmiapi.xrayhunter.model.VeinLocator;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.coreprotect.database.Database;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/**
 * Direct lookup access to the CoreProtect database for database-only worlds and stale archives.
 */
public final class CoreProtectLookupService implements MiningHistory {
    private static final long WORLD_CACHE_TTL_MILLIS = 60_000L;
    private static final long SUMMARY_CACHE_TTL_MILLIS = 10L * 60_000L;
    private static final int SUMMARY_CACHE_MAX_ENTRIES = 8;
    private static final int AGGREGATE_BATCH_WINDOW_SECONDS = 30 * 24 * 60 * 60;

    private final XRayHunterFeature feature;
    private final Object cacheStateLock = new Object();
    private final Map<LookupSummaryCacheKey, CachedLookupSummary> summaryCache =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<LookupSummaryCacheKey, CachedLookupSummary> eldest) {
                    return size() > SUMMARY_CACHE_MAX_ENTRIES;
                }
            };

    private final AtomicLong summaryCacheHits = new AtomicLong();
    private final AtomicLong summaryCacheMisses = new AtomicLong();
    private final AtomicLong latestTrackedTimeSnapshot = new AtomicLong();
    private final AtomicLong metadataGeneration = new AtomicLong();
    private final AtomicBoolean metadataRefreshInProgress = new AtomicBoolean();

    private volatile long cachedWorldsExpiresAt = 0L;
    private volatile List<String> cachedWorldNames = List.of();

    public CoreProtectLookupService(XRayHunterFeature feature) {
        this.feature = Objects.requireNonNull(feature, "feature");
    }

    @Override
    public void performLookup(
            String windowKey,
            int sinceEpochSeconds,
            @Nullable String worldName,
            List<Material> lookupMaterials,
            List<Material> comparisonMaterials,
            List<Material> rankingMaterials,
            int comparisonPlayerLimit,
            Collection<String> excludedPlayers,
            Consumer<LookupSummary> callback
    ) {
        final long lifecycleGeneration = feature.getLifecycleGeneration();
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
        final @Nullable LookupSummary cachedResult = getCachedSummary(cacheKey, lifecycleGeneration);
        if (cachedResult != null) {
            feature.runOnMainThread(lifecycleGeneration, () -> callback.accept(cachedResult));
            return;
        }

        feature.runLookupTaskAsync(lifecycleGeneration, () -> {
            LookupSummary result;
            try (Connection connection = openConnection()) {
                result = runLookup(
                        connection,
                        lifecycleGeneration,
                        sinceEpochSeconds,
                        worldName,
                        lookupMaterials,
                        comparisonMaterials,
                        rankingMaterials,
                        comparisonPlayerLimit,
                        normalizedExcludedPlayers
                );
                if (!result.hasError()) {
                    cacheSummary(cacheKey, result, lifecycleGeneration);
                }
                updateLatestTrackedTime(result.latestTrackedTimeSeconds(), lifecycleGeneration);
            } catch (SQLException exception) {
                feature.getLogger().log(Level.WARNING, "Unable to query CoreProtect database", exception);
                if (!feature.isActive(lifecycleGeneration)) {
                    return;
                }
                final long latestTrackedTime = safeLatestTrackedTime(lookupMaterials);
                updateLatestTrackedTime(latestTrackedTime, lifecycleGeneration);
                final String message = MessageFormat.format(
                        "Unable to query CoreProtect database: {0}",
                        exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()
                );
                result = LookupSummary.failure(message, latestTrackedTime);
            } catch (RuntimeException exception) {
                feature.getLogger().log(Level.WARNING, "Unexpected error while querying CoreProtect database", exception);
                if (!feature.isActive(lifecycleGeneration)) {
                    return;
                }
                final long latestTrackedTime = safeLatestTrackedTime(lookupMaterials);
                updateLatestTrackedTime(latestTrackedTime, lifecycleGeneration);
                result = LookupSummary.failure(
                        "Unexpected lookup error: " + safeExceptionMessage(exception),
                        latestTrackedTime
                );
            }
            if (!feature.isActive(lifecycleGeneration)) {
                return;
            }
            final LookupSummary finalResult = result;
            feature.runOnMainThread(lifecycleGeneration, () -> callback.accept(finalResult));
        });
    }

    @Override
    public void performPlayerVeinLookup(
            LookupContext lookupContext,
            String playerName,
            Consumer<VeinLookupResult> callback
    ) {
        final long lifecycleGeneration = feature.getLifecycleGeneration();
        feature.runLookupTaskAsync(lifecycleGeneration, () -> {
            VeinLookupResult result;
            try (Connection connection = openConnection()) {
                result = runPlayerVeinLookup(connection, lifecycleGeneration, lookupContext, playerName);
            } catch (SQLException exception) {
                feature.getLogger().log(Level.WARNING, "Unable to load CoreProtect detail data for " + playerName, exception);
                final String message = MessageFormat.format(
                        "Unable to load detail data for {0}: {1}",
                        playerName,
                        exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()
                );
                result = VeinLookupResult.failure(message);
            } catch (RuntimeException exception) {
                feature.getLogger().log(Level.WARNING, "Unexpected error while loading CoreProtect detail data for " + playerName, exception);
                result = VeinLookupResult.failure(
                        MessageFormat.format(
                                "Unable to load detail data for {0}: {1}",
                                playerName,
                                safeExceptionMessage(exception)
                        )
                );
            }
            if (!feature.isActive(lifecycleGeneration)) {
                return;
            }
            final VeinLookupResult finalResult = result;
            feature.runOnMainThread(lifecycleGeneration, () -> callback.accept(finalResult));
        });
    }

    @Override
    public void clearCaches() {
        metadataGeneration.incrementAndGet();
        metadataRefreshInProgress.set(false);
        synchronized (cacheStateLock) {
            cachedWorldsExpiresAt = 0L;
            cachedWorldNames = List.of();
            summaryCache.clear();
            summaryCacheHits.set(0L);
            summaryCacheMisses.set(0L);
            latestTrackedTimeSnapshot.set(0L);
        }
    }

    @Override
    public int getSummaryCacheSize() {
        synchronized (cacheStateLock) {
            pruneExpiredSummaryCache(System.currentTimeMillis());
            return summaryCache.size();
        }
    }

    @Override
    public long getSummaryCacheHitCount() {
        return summaryCacheHits.get();
    }

    @Override
    public long getSummaryCacheMissCount() {
        return summaryCacheMisses.get();
    }

    @Override
    public List<String> getKnownWorldNames() {
        final long now = System.currentTimeMillis();
        final List<String> cached;
        final boolean expired;
        synchronized (cacheStateLock) {
            cached = cachedWorldNames;
            expired = now >= cachedWorldsExpiresAt;
        }
        if (expired) {
            refreshMetadata(List.of());
        }
        return cached;
    }

    @Override
    public @Nullable String resolveKnownWorldName(String rawWorldName) {
        if (rawWorldName == null || rawWorldName.isBlank()) {
            return null;
        }
        final String requestedWorldName = rawWorldName.trim();
        for (String worldName : getKnownWorldNames()) {
            if (worldName.equalsIgnoreCase(requestedWorldName)) {
                return worldName;
            }
        }
        // The asynchronous lookup remains authoritative and will return a clear
        // error if this database-only world does not exist.
        return requestedWorldName;
    }

    @Override
    public void refreshMetadata(Collection<Material> lookupMaterials) {
        if (!feature.isActive() || !metadataRefreshInProgress.compareAndSet(false, true)) {
            return;
        }
        final long lifecycleGeneration = feature.getLifecycleGeneration();
        final long refreshGeneration = metadataGeneration.get();
        final List<Material> materials = List.copyOf(lookupMaterials);
        final boolean scheduled = feature.runLookupTaskAsync(lifecycleGeneration, () -> {
            try (Connection connection = openConnection()) {
                final List<String> worldNames = loadKnownWorldNames(connection);
                long latest = 0L;
                if (!materials.isEmpty()) {
                    final Map<Integer, Material> materialById = resolveTrackedMaterialIds(connection, materials);
                    latest = latestTrackedTime(connection, materialById.keySet(), null);
                }
                synchronized (cacheStateLock) {
                    if (feature.isActive(lifecycleGeneration) && metadataGeneration.get() == refreshGeneration) {
                        cachedWorldNames = worldNames;
                        cachedWorldsExpiresAt = System.currentTimeMillis() + WORLD_CACHE_TTL_MILLIS;
                        latestTrackedTimeSnapshot.accumulateAndGet(latest, Math::max);
                    }
                }
            } catch (SQLException | RuntimeException exception) {
                feature.getLogger().log(Level.FINE, "Unable to refresh CoreProtect metadata", exception);
            } finally {
                if (metadataGeneration.get() == refreshGeneration) {
                    metadataRefreshInProgress.set(false);
                }
            }
        });
        if (!scheduled && metadataGeneration.get() == refreshGeneration) {
            metadataRefreshInProgress.set(false);
        }
    }

    @Override
    public long getLatestTrackedTimeSnapshot() {
        synchronized (cacheStateLock) {
            return latestTrackedTimeSnapshot.get();
        }
    }

    private long safeLatestTrackedTime(Collection<Material> lookupMaterials) {
        try (Connection connection = openConnection()) {
            final Map<Integer, Material> materialById = resolveTrackedMaterialIds(connection, lookupMaterials);
            return latestTrackedTime(connection, materialById.keySet(), null);
        } catch (SQLException | RuntimeException exception) {
            feature.getLogger().log(Level.FINE, "Unable to inspect latest tracked CoreProtect time", exception);
            return 0L;
        }
    }

    private static Connection openConnection() throws SQLException {
        final Connection connection = Database.getConnection(true);
        if (connection == null) {
            throw new SQLException("CoreProtect did not provide a database connection");
        }
        return connection;
    }

    private List<String> loadKnownWorldNames(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT world FROM co_world ORDER BY world COLLATE NOCASE ASC"
        ); ResultSet resultSet = statement.executeQuery()) {
            final List<String> worlds = new ArrayList<>();
            while (resultSet.next()) {
                final String world = resultSet.getString(1);
                if (world != null && !world.isBlank()) {
                    worlds.add(world);
                }
            }
            return List.copyOf(worlds);
        }
    }

    private LookupSummary runLookup(
            Connection connection,
            long lifecycleGeneration,
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
            return LookupSummary.success(0L, Map.of(), Map.of(), false);
        }

        final @Nullable Integer worldId = worldName == null ? null : resolveWorldId(connection, worldName);
        if (worldName != null && worldId == null) {
            return LookupSummary.failure("That world was not found in CoreProtect: " + worldName, 0L);
        }

        final long latestTrackedTimeSeconds = latestTrackedTime(connection, materialById.keySet(), worldId);
        final Map<String, Map<Material, Integer>> playerCounts = new HashMap<>();
        if (latestTrackedTimeSeconds <= 0 || latestTrackedTimeSeconds < sinceEpochSeconds) {
            return LookupSummary.success(latestTrackedTimeSeconds, freezePlayerCounts(playerCounts), Map.of(), false);
        }

        long batchStart = Math.max(0L, sinceEpochSeconds);
        final long endExclusive = latestTrackedTimeSeconds + 1L;
        while (batchStart < endExclusive) {
            if (shouldAbort(lifecycleGeneration)) {
                return LookupSummary.failure("Lookup canceled while the plugin was shutting down.", latestTrackedTimeSeconds);
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
        return LookupSummary.success(latestTrackedTimeSeconds, frozenPlayerCounts, comparisonTotals, false);
    }

    private VeinLookupResult runPlayerVeinLookup(
            Connection connection,
            long lifecycleGeneration,
            LookupContext lookupContext,
            String playerName
    ) throws SQLException {
        final Map<Integer, Material> materialById = resolveTrackedMaterialIds(connection, lookupContext.lookupMaterials());
        if (materialById.isEmpty()) {
            return VeinLookupResult.success(List.of());
        }

        final @Nullable Integer worldId = lookupContext.worldName() == null
                ? null
                : resolveWorldId(connection, lookupContext.worldName());
        if (lookupContext.worldName() != null && worldId == null) {
            return VeinLookupResult.failure("That world was not found in CoreProtect: " + lookupContext.worldName());
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
            statement.setInt(parameter++, CoreProtectAction.BREAK.id());
            statement.setInt(parameter++, CoreProtectAction.PLACE.id());
            if (worldId != null) {
                statement.setInt(parameter, worldId);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (shouldAbort(lifecycleGeneration)) {
                        return VeinLookupResult.failure("Detail lookup canceled while the plugin was shutting down.");
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
                    if (action == CoreProtectAction.PLACE.id()) {
                        userPlacedBlocks.add(key);
                        continue;
                    }
                    if (action != CoreProtectAction.BREAK.id() || userPlacedBlocks.remove(key)) {
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
        return VeinLookupResult.success(veins);
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
            statement.setInt(parameter++, CoreProtectAction.BREAK.id());
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
        statement.setInt(parameter++, CoreProtectAction.BREAK.id());
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

    private @Nullable LookupSummary getCachedSummary(
            LookupSummaryCacheKey cacheKey,
            long lifecycleGeneration
    ) {
        synchronized (cacheStateLock) {
            if (!feature.isActive(lifecycleGeneration)) {
                return null;
            }
            pruneExpiredSummaryCache(System.currentTimeMillis());
            final CachedLookupSummary cachedSummary = summaryCache.get(cacheKey);
            if (cachedSummary == null) {
                summaryCacheMisses.incrementAndGet();
                return null;
            }
            summaryCacheHits.incrementAndGet();
            return LookupSummary.success(
                    cachedSummary.latestTrackedTimeSeconds(),
                    cachedSummary.playerCounts(),
                    cachedSummary.comparisonTotals(),
                    true
            );
        }
    }

    private void cacheSummary(
            LookupSummaryCacheKey cacheKey,
            LookupSummary result,
            long lifecycleGeneration
    ) {
        synchronized (cacheStateLock) {
            if (!feature.isActive(lifecycleGeneration)) {
                return;
            }
            final long now = System.currentTimeMillis();
            pruneExpiredSummaryCache(now);
            summaryCache.put(
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

    private void updateLatestTrackedTime(long latestTrackedTime, long lifecycleGeneration) {
        synchronized (cacheStateLock) {
            if (feature.isActive(lifecycleGeneration)) {
                latestTrackedTimeSnapshot.accumulateAndGet(latestTrackedTime, Math::max);
            }
        }
    }

    private void pruneExpiredSummaryCache(long now) {
        summaryCache.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    }

    private boolean shouldAbort(long lifecycleGeneration) {
        return !feature.isActive(lifecycleGeneration) || Thread.currentThread().isInterrupted();
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
