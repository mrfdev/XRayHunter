package com.onemb.cmiapi.xrayhunter.model;

import java.util.Map;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/** Ranked result of a mining-history lookup. */
public record LookupSummary(
        @Nullable String errorMessage,
        long latestTrackedTimeSeconds,
        Map<String, Map<Material, Integer>> playerCounts,
        Map<String, Integer> comparisonTotals,
        boolean fromCache
) {
    public static LookupSummary success(
            long latestTrackedTimeSeconds,
            Map<String, Map<Material, Integer>> playerCounts,
            Map<String, Integer> comparisonTotals,
            boolean fromCache
    ) {
        return new LookupSummary(null, latestTrackedTimeSeconds, playerCounts, comparisonTotals, fromCache);
    }

    public static LookupSummary failure(String errorMessage, long latestTrackedTimeSeconds) {
        return new LookupSummary(errorMessage, latestTrackedTimeSeconds, Map.of(), Map.of(), false);
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }
}
