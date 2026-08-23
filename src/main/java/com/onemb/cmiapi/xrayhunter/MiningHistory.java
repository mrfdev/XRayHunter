package com.onemb.cmiapi.xrayhunter;

import com.onemb.cmiapi.xrayhunter.model.LookupContext;
import com.onemb.cmiapi.xrayhunter.model.LookupSummary;
import com.onemb.cmiapi.xrayhunter.model.VeinLookupResult;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/**
 * XRayHunter's read boundary for authoritative mining history.
 *
 * <p>CoreProtect is the current implementation, while commands and sessions
 * remain independent of its database schema and internal classes. Lookup
 * callbacks must be delivered on the Paper main thread exactly once while the
 * feature remains active; an implementation may suppress a callback during
 * feature shutdown.
 */
public interface MiningHistory {
    void performLookup(
            String windowKey,
            int sinceEpochSeconds,
            @Nullable String worldName,
            List<Material> lookupMaterials,
            List<Material> comparisonMaterials,
            List<Material> rankingMaterials,
            int comparisonPlayerLimit,
            Collection<String> excludedPlayers,
            Consumer<LookupSummary> callback
    );

    void performPlayerVeinLookup(
            LookupContext lookupContext,
            String playerName,
            Consumer<VeinLookupResult> callback
    );

    List<String> getKnownWorldNames();

    @Nullable String resolveKnownWorldName(String rawWorldName);

    void refreshMetadata(Collection<Material> lookupMaterials);

    long getLatestTrackedTimeSnapshot();

    int getSummaryCacheSize();

    long getSummaryCacheHitCount();

    long getSummaryCacheMissCount();

    void clearCaches();
}
