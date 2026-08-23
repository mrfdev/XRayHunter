package com.onemb.cmiapi.xrayhunter.model;

import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Transient sender-scoped state used to refine the most recent lookup. */
public final class InvestigationSession {
    /** Timestamp of the last activity, retained for future cache expiry. */
    private long activity;
    /** Ranked player rows from the last lookup. */
    private List<PlayerStats> lookupCache = Collections.emptyList();
    private @Nullable PlayerStats currentStat;
    private @Nullable LookupContext lookupContext;
    private @Nullable List<OreVein> veins;

    InvestigationSession() {
        activity = System.currentTimeMillis();
    }

    @SuppressWarnings("unused")
    public long getActivity() {
        return activity;
    }

    public List<PlayerStats> getLookupCache() {
        activity = System.currentTimeMillis();
        return lookupCache;
    }

    public InvestigationSession setLookupCache(List<PlayerStats> lookupCache) {
        activity = System.currentTimeMillis();
        this.lookupCache = lookupCache;
        this.lookupContext = null;
        this.currentStat = null;
        this.veins = null;
        return this;
    }

    public @Nullable PlayerStats getPlayerStats() {
        return currentStat;
    }

    public void setPlayerStat(PlayerStats currentStat) {
        activity = System.currentTimeMillis();
        this.currentStat = currentStat;
    }

    public @Nullable PlayerStats getPlayerStats(String player) {
        for (PlayerStats stats : getLookupCache()) {
            if (stats.getPlayer().equalsIgnoreCase(player)) {
                currentStat = stats;
                return stats;
            }
        }
        currentStat = null;
        veins = null;
        return null;
    }

    public @Nullable LookupContext getLookupContext() {
        activity = System.currentTimeMillis();
        return lookupContext;
    }

    public void setLookupContext(@Nullable LookupContext lookupContext) {
        activity = System.currentTimeMillis();
        this.lookupContext = lookupContext;
        this.veins = null;
    }

    public @Nullable List<OreVein> getVeins() {
        activity = System.currentTimeMillis();
        return veins;
    }

    public void setVeins(@Nullable List<OreVein> veins) {
        activity = System.currentTimeMillis();
        this.veins = veins;
    }
}
