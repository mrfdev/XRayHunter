package dk.lockfuglsang.xrayhunter.model;

import java.util.List;
import org.bukkit.Material;
import org.jspecify.annotations.Nullable;

/**
 * Remembers the lookup scope needed for lazy detail queries.
 */
public record LookupContext(
        int sinceEpochSeconds,
        String timeArgument,
        @Nullable String worldName,
        List<Material> lookupMaterials
) {
    public LookupContext {
        lookupMaterials = List.copyOf(lookupMaterials);
    }
}
