package dk.lockfuglsang.xrayhunter.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/**
 * Responsible for remembering actions / data / lookups for a player, allowing
 * for easier command designs (i.e. you can refine previous searches).
 */
public class HuntSession {
    private static final Map<String, HuntSession> sessionMap = new ConcurrentHashMap<>();
    /**
     * timestamp of last activity (enables us to clear-cache).
     */
    private long activity;
    /**
     * Last invoked /xhunt lookup
     */
    private List<PlayerStats> lookupCache = Collections.emptyList();
    private PlayerStats currentStat;
    private @Nullable LookupContext lookupContext;
    private @Nullable List<OreVein> veins;

    private HuntSession() {
        activity = System.currentTimeMillis();
    }

    public synchronized static HuntSession getSession(@NonNull CommandSender sender) {
        if (!sessionMap.containsKey(sender.getName())) {
            sessionMap.put(sender.getName(), new HuntSession());
        }
        return sessionMap.get(sender.getName());
    }

    public static int getSessionCount() {
        return sessionMap.size();
    }

    @SuppressWarnings("unused")
    public long getActivity() {
        return activity;
    }

    public List<PlayerStats> getLookupCache() {
        activity = System.currentTimeMillis();
        return lookupCache;
    }

    public HuntSession setLookupCache(List<PlayerStats> lookupCache) {
        activity = System.currentTimeMillis();
        this.lookupCache = lookupCache;
        this.lookupContext = null;
        this.currentStat = null;
        this.veins = null;
        return this;
    }

    public PlayerStats getPlayerStats() {
        return currentStat;
    }

    public void setPlayerStat(PlayerStats currentStat) {
        activity = System.currentTimeMillis();
        this.currentStat = currentStat;
    }

    public PlayerStats getPlayerStats(String player) {
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
