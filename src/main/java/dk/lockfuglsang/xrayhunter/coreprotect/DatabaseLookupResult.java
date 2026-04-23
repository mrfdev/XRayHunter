package dk.lockfuglsang.xrayhunter.coreprotect;

import dk.lockfuglsang.xrayhunter.model.TrackedBlockEvent;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/**
 * Result of a direct CoreProtect database lookup.
 */
public record DatabaseLookupResult(
        @Nullable String errorMessage,
        long latestTrackedTimeSeconds,
        Map<String, Map<Material, Integer>> playerCounts,
        Map<String, Integer> comparisonTotals,
        boolean fromCache
) {
    public static DatabaseLookupResult success(
            long latestTrackedTimeSeconds,
            Map<String, Map<Material, Integer>> playerCounts,
            Map<String, Integer> comparisonTotals,
            boolean fromCache
    ) {
        return new DatabaseLookupResult(null, latestTrackedTimeSeconds, playerCounts, comparisonTotals, fromCache);
    }

    public static DatabaseLookupResult failure(String errorMessage, long latestTrackedTimeSeconds) {
        return new DatabaseLookupResult(errorMessage, latestTrackedTimeSeconds, Map.of(), Map.of(), false);
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }
}
