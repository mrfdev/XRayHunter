package dk.lockfuglsang.xrayhunter.coreprotect;

import dk.lockfuglsang.xrayhunter.model.OreVein;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Result of a single-player detail lookup against CoreProtect.
 */
public record PlayerVeinLookupResult(
        @Nullable String errorMessage,
        List<OreVein> veins
) {
    public static PlayerVeinLookupResult success(List<OreVein> veins) {
        return new PlayerVeinLookupResult(null, List.copyOf(veins));
    }

    public static PlayerVeinLookupResult failure(String errorMessage) {
        return new PlayerVeinLookupResult(errorMessage, List.of());
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }
}
