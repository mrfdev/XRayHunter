package dk.lockfuglsang.xrayhunter;

import dk.lockfuglsang.xrayhunter.command.MainCommand;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;

/**
 * Bukkit Plugin for hunting X-Rayers using the CoreProtect API
 */
public class XRayHunter extends JavaPlugin {
    private static final Logger log = Logger.getLogger(XRayHunter.class.getName());

    private static CoreProtectAPI api;

    public static CoreProtectAPI getCoreProtectAPI() {
        return api;
    }

    @Override
    public void onEnable() {
        api = null;
        final CoreProtectAPI coreProtectAPI = getCoreProtect();

        if (coreProtectAPI == null) {
            log.info("No valid CoreProtect plugin was found!");
        }

        try {
            new Metrics(this, 3013);
        } catch (final Exception e) {
            log.log(Level.WARNING, "Failed to submit metrics data", e);
        }
        api = coreProtectAPI;
        Objects.requireNonNull(getCommand("xhunt")).setExecutor(new MainCommand(this));
    }

    private @Nullable CoreProtectAPI getCoreProtect() {
        for (Plugin pluginCP : getServer().getPluginManager().getPlugins()) {
            if (pluginCP.getName().toLowerCase().contains("coreprotect")) {
                if (!(pluginCP instanceof CoreProtect)) return null;
                CoreProtectAPI CoreProtect = ((CoreProtect) pluginCP).getAPI();
                if (!CoreProtect.isEnabled()) return null;
                int apiVersion = CoreProtect.APIVersion();
                return switch (apiVersion) {
                    case 7, 8, 9, 10, 11 -> CoreProtect;
                    default -> null;
                };
            }
        }
        return null;
    }
}