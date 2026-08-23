package com.onemb.cmiapi.xrayhunter.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Vein detail returned by a single-player mining-history lookup. */
public record VeinLookupResult(
        @Nullable String errorMessage,
        List<OreVein> veins
) {
    public static VeinLookupResult success(List<OreVein> veins) {
        return new VeinLookupResult(null, List.copyOf(veins));
    }

    public static VeinLookupResult failure(String errorMessage) {
        return new VeinLookupResult(errorMessage, List.of());
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }
}
